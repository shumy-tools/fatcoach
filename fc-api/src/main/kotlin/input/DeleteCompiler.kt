package fc.api.input

import fc.api.FcSchema
import fc.api.RefID
import fc.api.SEntity
import fc.api.spi.FcDelete
import fc.api.spi.InputInstructions
import fc.dsl.input.DeleteBaseListener
import fc.dsl.input.DeleteLexer
import fc.dsl.input.DeleteParser
import fc.dsl.input.DeleteParser.*
import org.antlr.v4.runtime.CharStreams
import org.antlr.v4.runtime.CommonTokenStream
import org.antlr.v4.runtime.tree.ErrorNode
import org.antlr.v4.runtime.tree.ParseTreeWalker

internal class DeleteCompiler(private val dsl: String, private val schema: FcSchema, private val tx: InputInstructions, private val arg: RefID?): DeleteBaseListener() {
  lateinit var entity: SEntity
  lateinit var refID: RefID
  val errors = mutableListOf<String>()

  init { compile() }

  private fun compile() {
    val lexer = DeleteLexer(CharStreams.fromString(dsl))
    val tokens = CommonTokenStream(lexer)
    val parser = DeleteParser(tokens)
    val tree = parser.delete()

    val walker = ParseTreeWalker()
    walker.walk(this, tree)
  }

  override fun visitErrorNode(error: ErrorNode) {
    errors.add(error.text)
  }

  override fun enterDelete(ctx: DeleteContext) {
    val eText = ctx.entity().text
    entity = schema.find(eText)
    refID = if (ctx.id.LONG() != null) RefID(ctx.id.LONG().text.toLong()) else {
      arg ?: throw Exception("Expecting an argument value for '${entity.name}.@id'.")
    }

    tx.add(FcDelete(entity, refID))
  }
}