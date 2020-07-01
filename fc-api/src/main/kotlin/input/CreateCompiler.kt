package fc.api.input

import fc.api.*
import fc.api.spi.FcCreate
import fc.api.spi.InputInstructions
import fc.dsl.input.CreateBaseListener
import fc.dsl.input.CreateLexer
import fc.dsl.input.CreateParser
import fc.dsl.input.CreateParser.*
import org.antlr.v4.runtime.CharStreams
import org.antlr.v4.runtime.CommonTokenStream
import org.antlr.v4.runtime.tree.ErrorNode
import org.antlr.v4.runtime.tree.ParseTreeWalker
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.util.*

internal class CreateCompiler(private val dsl: String, private val schema: FcSchema, private val tx: InputInstructions, private val args: Map<String, Any>): CreateBaseListener() {
  val tree = RefTree()

  lateinit var entity: SEntity
    internal set

  val accessed = mutableSetOf<SProperty>()
  val errors = mutableListOf<String>()

  private val parentStack = Stack<RefTree>()
  private val selfStack = Stack<Pair<RefTree, SEntity>>()
  init { compile() }

  private fun scope(parent: RefTree, rel: SRelation, scope: () -> Unit): RefID {
    val selfTree = if (rel is SReference) parent.pushRef(rel.name) else parent.pushCol(rel.name)
    parentStack.push(parent)
    selfStack.push(Pair(selfTree, rel.ref))
      scope()
    selfStack.pop()
    parentStack.pop()
    return selfTree.root
  }

  private fun compile() {
    val lexer = CreateLexer(CharStreams.fromString(dsl))
    val tokens = CommonTokenStream(lexer)
    val parser = CreateParser(tokens)
    val tree = parser.create()

    //tokens.tokens.forEach { println("${lexer.vocabulary.getDisplayName(it.type)} -> ${it.text}") }

    val walker = ParseTreeWalker()
    walker.walk(this, tree)
  }

  override fun visitErrorNode(error: ErrorNode) {
    errors.add(error.text)
  }

  override fun enterCreate(ctx: CreateContext) {
    val eText = ctx.entity().text
    entity = schema.find(eText)

    selfStack.push(Pair(tree, entity))
    ctx.data().processData()
  }

  private fun DataContext.processData() {
    val (selfTree, self) = selfStack.peek()
    val fcCreate = FcCreate(self, selfTree.root)
    tx.add(fcCreate)

    val inputs = entry().mapNotNull {
      val prop = it.ID().text
      val sProperty = self.all[prop] ?: throw Exception("Property '$prop' not found in entity '${self.name}.")

      // parse input properties
      val value = if (sProperty.isInput) {
        accessed.add(sProperty)
        when {
          it.value() != null -> it.value().processValue(sProperty)
          it.list() != null -> it.list().processList(sProperty)
          it.data() != null -> {
            // TODO: process field MAP ?

            if (sProperty !is SReference)
              throw Exception("Invalid reference for '${self.name}.$prop'.")

            if (sProperty.type == RType.LINKED)
              throw Exception("Expecting typeOf (null, long, param) for '${self.name}.$prop'.")

            scope(selfTree, sProperty) { it.data().processData() }
          }
          else -> null
        }
      } else null
      sProperty.name to value
    }.toMap()

    // check if all input properties are present
    val completed = if (parentStack.isNotEmpty()) inputs.plus(self.parent!!.name to parentStack.peek().root) else inputs
    self.all.values.filter { it.isInput }.forEach {
      if (!completed.containsKey(it.name))
        throw Exception("Expecting an input value for '${self.name}:${it.name}'.")
    }

    // TODO: process derive fields

    fcCreate.values = completed
  }

  private fun ValueContext.processValue(prop: SProperty): Any? {
    return when (prop) {
      is SField -> processField(prop)
      is SReference -> processRefID(prop, prop.isOptional)
      is SCollection -> throw Exception("Expecting a collection for '${prop.entity!!.name}.${prop.name}'.")
    }
  }

  private fun ListContext.processList(prop: SProperty): List<Any?> {
    return when (prop) {
      is SField -> {
        when (prop.type) {
          FType.LIST -> {
            val value = value() ?: throw Exception("Expecting a collection of values for '${prop.entity!!.name}.${prop.name}'.")
            value.map { it.processField(prop) }
          }

          // TODO: process field MAP ?
          FType.MAP -> TODO()

          else -> throw Exception("Expecting a collection of values or objects for '${prop.entity!!.name}.${prop.name}'.")
        }
      }

      is SCollection -> when (prop.type) {
        RType.OWNED -> {
          if (data() == null)
            throw Exception("Expecting a collection of objects for '${prop.entity!!.name}.${prop.name}'.")

          val selfTree = selfStack.peek().first
          data().map { scope(selfTree, prop) { it.processData() } }
        }

        RType.LINKED -> {
          if (value() == null)
            throw Exception("Expecting a collection of references for '${prop.entity!!.name}.${prop.name}'.")
          value().map { it.processRefID(prop, false) }
        }
      }

      is SReference -> throw Exception("Expecting a reference for '${prop.entity!!.name}.${prop.name}'.")
    }
  }

  private fun ValueContext.processField(field: SField): Any? = when {
    NULL() != null -> {
      if (!field.isOptional)
        throw Exception("Expecting an input value for non-optional field '${field.entity!!.name}.${field.name}'.")
      null
    }

    TEXT() != null -> {
      field.tryType(FType.TEXT)
      TEXT().text.substring(1, TEXT().text.length-1)
    }

    LONG() != null -> {
      field.tryType(FType.LONG)
      val value = LONG().text
      if (field.type == FType.INT) value.toInt() else value.toLong()
    }

    DOUBLE() != null -> {
      field.tryType(FType.DOUBLE)
      val value = DOUBLE().text
      if (field.type == FType.FLOAT) value.toFloat() else value.toDouble()
    }

    BOOL() != null -> {
      field.tryType(FType.BOOL)
      BOOL().text!!.toBoolean()
    }

    TIME() != null -> {
      field.tryType(FType.TIME)
      LocalTime.parse(TIME().text.substring(1))
    }

    DATE() != null -> {
      field.tryType(FType.DATE)
      LocalDate.parse(DATE().text.substring(1))
    }

    DATETIME() != null -> {
      field.tryType(FType.DATETIME)
      LocalDateTime.parse(DATETIME().text.substring(1))
    }

    PARAM() != null -> {
      val key = PARAM().text.substring(1)
      val value = args[key] ?: throw Exception("Expecting an argument value for '${field.entity!!.name}.${field.name}'.")
      field.tryType(TypeEngine.convert(value.javaClass.kotlin))
      value
    }

    else -> throw Exception("Expecting typeOf (null, text, long, double, bool, time, data, datetime, param) for '${field.entity!!.name}.${field.name}'.")
  }

  private fun ValueContext.processRefID(ref: SRelation, isOptional: Boolean): RefID {
    if (ref.type == RType.OWNED)
      throw Exception("Expecting an object for '${ref.entity!!.name}.${ref.name}'.")

    return when {
      NULL() != null -> {
        if (!isOptional)
          throw Exception("Expecting an input value for non-optional reference '${ref.entity!!.name}.${ref.name}'.")
        RefID()
      }

      LONG() != null -> RefID(LONG().text.toLong())

      PARAM() != null -> {
        val key = PARAM().text.substring(1)
        val value = args[key] ?: throw Exception("Expecting an argument value for '${ref.entity!!.name}.${ref.name}'.")
        when (value) {
          is RefID -> value
          is RefTree -> value.root
          else -> throw Exception("Expecting typeOf RefID for '${ref.entity!!.name}.${ref.name}'.")
        }
      }

      else -> throw Exception("Expecting typeOf (null, long, param=RefID) for '${ref.entity!!.name}.${ref.name}'.")
    }
  }
}