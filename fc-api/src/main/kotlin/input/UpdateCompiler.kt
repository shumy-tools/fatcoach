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
    val entity = schema.find(eText)

    val refID = if (ctx.id.LONG() != null) RefID(ctx.id.LONG().text.toLong()) else {
      val key = ctx.id.PARAM().text.substring(1)
      args.getRefID(key)
    }

    ctx.data().processData(entity, refID)
  }

  private fun DataContext.processData(self: SEntity, selfID: RefID) {
    val fcUpdate = FcUpdate(self, selfID)
    fcUpdate.accessed.add(self.id)
    tx.add(fcUpdate)

    val inputs = entry()?.map {
      val prop = it.ID().text
      val sProperty = self.all[prop] ?: throw Exception("Property '$prop' not found in entity '${self.name}.")

      // parse input properties
      val value = if (sProperty.isInput) {
        fcUpdate.accessed.add(sProperty)
        when {
          it.value() != null -> FieldProxy(it.value(), args, true).process(sProperty)
          it.list() != null -> it.list().processList(sProperty)
          it.oper() != null -> it.oper().processOperation(sProperty)
          it.data() != null -> {
            val obj = it.data()
            when (sProperty) {
              is SField -> { sProperty.tryType(FType.MAP); MapProxy(obj).parse() }
              is SReference -> throw Exception("Expecting typeOf (null, long, param) for '${self.name}.$prop'.")
              is SCollection -> throw Exception("Expecting a collection for '${self.name}.$prop'.")
            }
          }
          else -> throw Exception("Bug - UpdateCompiler.processData() - 1. Unrecognized dsl branch!")
        }
      } else throw Exception("Bug - UpdateCompiler.processData() - 2. Unrecognized dsl branch!")

      sProperty.name to value
    }?.toMap() ?: emptyMap()

    // TODO: process derive fields ?
    fcUpdate.values = inputs
  }

  private fun OperContext.processOperation(sProperty: SProperty): Any {
    val prop = sProperty.name
    val sEntity = sProperty.entity!!
    if (sProperty !is SRelation)
      throw Exception("Unexpected operation (@add, @del) for field '${sEntity.name}.$prop'.")

    val operType = if (add != null) OType.ADD else OType.DEL
    return when {
      value() != null -> {
        val refs = FieldProxy(value(), args, true).process(sProperty)
        if (sProperty is SCollection) {
          (refs as List<*>).map { refID -> RefLink(operType, refID as RefID) }
        } else {
          RefLink(operType, refs as RefID)
        }
      }

      list() != null -> {
        if (sProperty !is SCollection)
          throw Exception("Unexpected list for field/reference '${sEntity.name}.$prop'.")
        list().processList(sProperty).map { refID -> RefLink(operType, refID as RefID) }
      }

      else -> throw Exception("Bug - UpdateCompiler.processOperation(). Unrecognized dsl branch!")
    }
  }

  private fun ListContext.processList(prop: SProperty): List<Any?> {
    return when (prop) {
      is SField -> {
        when (prop.type) {
          FType.LIST -> ListProxy(this).parse()
          FType.MAP -> throw Exception("Expecting an object for '${prop.entity!!.name}.${prop.name}'.")
          else -> throw Exception("A list is not compatible with the type '${prop.type.name.toLowerCase()}' for '${prop.entity!!.name}.${prop.name}'.")
        }
      }

      is SCollection -> when (prop.type) {
        RType.OWNED -> throw Exception("Updating an owned reference/collection is not supported. Update '${prop.entity!!.name}.${prop.name}' using the @parent field.")
        RType.LINKED -> item().map { FieldProxy(it.value(), args, false).processRefID(prop, false) }
      }

      is SReference -> throw Exception("Expecting a reference for '${prop.entity!!.name}.${prop.name}'.")
    }
  }
}