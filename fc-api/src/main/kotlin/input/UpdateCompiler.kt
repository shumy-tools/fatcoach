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
      args.getRefID(key)
    }

    val inputs = ctx.data().processData(entity!!)
    tx.add(FcUpdate(entity, refID, inputs))
  }

  private fun DataContext.processData(sEntity: SEntity): Map<String, Any?> {
    return entry().map {
      val prop = it.ID().text
      val sProperty = sEntity.all[prop] ?: throw Exception("Property '$prop' not found in entity '${sEntity.name}.")

      // parse input properties
      val value = if (sProperty.isInput) {
        accessed.add(sProperty)
        when {
          it.value() != null -> ValueProxy(it.value(), args, true).process(sProperty)
          it.list() != null -> it.list().processList(sProperty)

          it.oper() != null -> {
            if (sProperty !is SRelation)
              throw Exception("Unexpected operation (@add, @del) for field '${sEntity.name}.$prop'.")

            val oper = it.oper()
            val operType = if (oper.add != null) OType.ADD else OType.DEL
            when {
              oper.value() != null -> {
                val refs = ValueProxy(oper.value(), args, true).process(sProperty)
                if (sProperty is SCollection) {
                  (refs as List<*>).map { refID -> RefLink(operType, refID as RefID) }
                } else {
                  RefLink(operType, refs as RefID)
                }
              }

              oper.list() != null -> {
                if (sProperty !is SCollection)
                  throw Exception("Unexpected list for field/reference '${sEntity.name}.$prop'.")

                oper.list().processList(sProperty).map { refID -> RefLink(operType, refID as RefID) }
              }

              else -> throw Exception("Update bug - 1. Unrecognized dsl branch!")
            }
          }

          it.data() != null -> {
            // TODO: process field MAP ?
            TODO()
          }
          else -> throw Exception("Update bug - 2. Unrecognized dsl branch!")
        }
      } else throw Exception("Update bug - 3. Unrecognized dsl branch!")

      sProperty.name to value
    }.toMap()

    // TODO: process derive fields ?
  }

  private fun ListContext.processList(prop: SProperty): List<Any?> {
    return when (prop) {
      is SField -> {
        when (prop.type) {
          FType.LIST -> {
            val value = value() ?: throw Exception("Expecting a collection of values for '${prop.entity!!.name}.${prop.name}'.")
            value.map { ValueProxy(it, args, true).processField(prop) }
          }

          // TODO: process field MAP ?
          FType.MAP -> TODO()

          else -> throw Exception("Expecting a collection of values or objects for '${prop.entity!!.name}.${prop.name}'.")
        }
      }

      is SCollection -> when (prop.type) {
        RType.OWNED -> throw Exception("Updating an owned reference/collection is not supported. Update '${prop.entity!!.name}.${prop.name}' using the @parent field.")

        RType.LINKED -> {
          if (value() == null)
            throw Exception("Expecting a collection of references for '${prop.entity!!.name}.${prop.name}'.")
          value().map { ValueProxy(it, args, true).processRefID(prop, false) }
        }
      }

      is SReference -> throw Exception("Expecting a reference for '${prop.entity!!.name}.${prop.name}'.")
    }
  }
}