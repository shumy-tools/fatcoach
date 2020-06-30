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
  lateinit var refID: RefID
    internal set

  lateinit var entity: SEntity
    internal set

  val accessed = mutableSetOf<SProperty>()
  val errors = mutableListOf<String>()

  private val stack = Stack<SEntity>()
  init { compile() }

  private fun <R: Any> scope(sEntity: SEntity, scope: () -> R): R {
    stack.push(sEntity)
      val result = scope()
    stack.pop()
    return result
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
    println(eText)
    entity = schema.find(eText)

    stack.push(entity)
    refID = ctx.data().processData() as RefID
  }

  private fun DataContext.processData(): Any {
    val sEntity = stack.peek()
    val inputs = entry().mapNotNull {
      val prop = it.ID().text
      val sProperty = sEntity.all[prop] ?: throw Exception("Property '$prop' not found in entity '${sEntity.name}.")

      // parse input properties
      val value = if (sProperty.isInput) {
        accessed.add(sProperty)
        when {
          it.value() != null -> it.value().processValue(sProperty)
          it.list() != null -> it.list().processList(sProperty)
          it.data() != null -> {
            // TODO: process field MAP ?

            if (sProperty !is SReference)
              throw Exception("Invalid reference for '${sEntity.name}.$prop'.")

            if (sProperty.type == RType.LINKED)
              throw Exception("Expecting typeOf (null, long, param) for '${sEntity.name}.$prop'.")

            scope(sProperty.ref) { it.data().processData() }
          }
          else -> null
        }
      } else null
      sProperty to value
    }.toMap()

    // check if all input properties are present
    sEntity.all.values.filter { it.isInput }.forEach {
      if (!inputs.containsKey(it))
        throw Exception("Expecting an input value for '${sEntity.name}:${it.name}'.")
    }

    val refID = RefID()
    tx.add(FcCreate(sEntity, refID, inputs))
    return refID
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
          scope(prop.ref) { data().map { it.processData() } }
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
      LONG().text.toLong()
    }

    DOUBLE() != null -> {
      field.tryType(FType.DOUBLE)
      DOUBLE().text.toDouble()
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
        if (value !is RefID)
          throw Exception("Expecting typeOf RefID for '${ref.entity!!.name}.${ref.name}'.")
        value
      }

      else -> throw Exception("Expecting typeOf (null, long, param=RefID) for '${ref.entity!!.name}.${ref.name}'.")
    }
  }
}