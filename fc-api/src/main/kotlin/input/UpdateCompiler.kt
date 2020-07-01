package fc.api.input

import fc.api.*
import fc.api.spi.FcUpdate
import fc.api.spi.InputInstructions
import fc.dsl.input.UpdateBaseListener
import fc.dsl.input.UpdateLexer
import fc.dsl.input.UpdateParser
import fc.dsl.input.UpdateParser.*
import org.antlr.v4.runtime.CharStreams
import org.antlr.v4.runtime.CommonTokenStream
import org.antlr.v4.runtime.tree.ErrorNode
import org.antlr.v4.runtime.tree.ParseTreeWalker
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

internal class UpdateCompiler(private val dsl: String, private val schema: FcSchema, private val tx: InputInstructions, private val args: Map<String, Any>): UpdateBaseListener() {
  lateinit var refID: RefID
    internal set

  lateinit var entity: SEntity
    internal set

  val accessed = mutableSetOf<SProperty>()
  val errors = mutableListOf<String>()

  init { compile() }

  private fun compile() {
    val lexer = UpdateLexer(CharStreams.fromString(dsl))
    val tokens = CommonTokenStream(lexer)
    val parser = UpdateParser(tokens)
    val tree = parser.update()

    val walker = ParseTreeWalker()
    walker.walk(this, tree)
  }

  override fun visitErrorNode(error: ErrorNode) {
    errors.add(error.text)
  }

  override fun enterUpdate(ctx: UpdateContext) {
    val eText = ctx.entity().text
    entity = schema.find(eText)
    refID = if (ctx.id.LONG() != null) RefID(ctx.id.LONG().text.toLong()) else {
      val key = ctx.id.PARAM().text.substring(1)
      val value = args[key] ?: throw Exception("Expecting an argument value for '${entity!!.name}.@id'.")
      when (value) {
        is RefID -> value
        is RefTree -> value.root
        else -> throw Exception("Expecting typeOf RefID for '${entity!!.name}.@id'.")
      }
    }

    val inputs = ctx.data().processData(entity!!)
    tx.add(FcUpdate(entity, refID, inputs))
  }

  private fun DataContext.processData(sEntity: SEntity): Map<String, Any?> {
    return entry().mapNotNull {
      val prop = it.ID().text
      val sProperty = sEntity.all[prop] ?: throw Exception("Property '$prop' not found in entity '${sEntity.name}.")

      // parse input properties
      val value = if (sProperty.isInput) {
        accessed.add(sProperty)
        when {
          it.value() != null -> it.value().processValue(sProperty)
          it.list() != null -> it.list().processList(sProperty)

          it.oper() != null -> {
            if (sProperty !is SReference)
              throw Exception("Invalid reference for '${sEntity.name}.$prop'.")

            val oper = if (it.oper().add != null) OType.ADD else OType.DEL
            it.oper().value().processRefLink(oper, sProperty, sProperty.isOptional)
          }

          it.data() != null -> {
            // TODO: process field MAP ?
            TODO()
          }
          else -> null
        }
      } else null
      sProperty.name to value
    }.toMap()
  }

  private fun ValueContext.processValue(prop: SProperty): Any? {
    return when (prop) {
      is SField -> processField(prop)
      is SReference -> throw Exception("Expecting an operation (@add | @del) for '${prop.entity!!.name}.${prop.name}'.")
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
        RType.OWNED -> throw Exception("Updating an owned reference/collection is not supported. Update '${prop.entity!!.name}.${prop.name}' using the @parent field.")

        RType.LINKED -> {
          if (oper() == null)
            throw Exception("Expecting an operation (@add | @del) for '${prop.entity!!.name}.${prop.name}'.")
          oper().map {
            val oper = if (it.add != null) OType.ADD else OType.DEL
            it.value().processRefLink(oper, prop, false)
          }
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

  private fun ValueContext.processRefLink(oper: OType, ref: SRelation, isOptional: Boolean): RefLink {
    if (ref.type == RType.OWNED)
      throw Exception("Updating an owned reference/collection is not supported. Update '${ref.entity!!.name}.${ref.name}' using the @parent field.")

    return when {
      NULL() != null -> {
        if (!isOptional)
          throw Exception("Expecting an input value for non-optional reference '${ref.entity!!.name}.${ref.name}'.")
        RefLink(oper, RefID())
      }

      LONG() != null -> RefLink(oper, RefID(LONG().text.toLong()))

      PARAM() != null -> {
        val key = PARAM().text.substring(1)
        val refID = args.getRefID(key)
        RefLink(oper, refID)
      }

      else -> throw Exception("Expecting typeOf (@add | @del) (null, long, param=RefID) for '${ref.entity!!.name}.${ref.name}'.")
    }
  }
}