package fc.api.input

import fc.api.FcSchema
import fc.api.RefID
import fc.api.SEntity
import fc.dsl.input.DeleteBaseListener
import fc.dsl.input.DeleteParser
import fc.dsl.input.DeleteParser.*
import fc.dsl.query.QueryLexer
import org.antlr.v4.runtime.CharStreams
import org.antlr.v4.runtime.CommonTokenStream
import org.antlr.v4.runtime.tree.ErrorNode
import org.antlr.v4.runtime.tree.ParseTreeWalker

internal class DeleteCompiler(private val dsl: String, private val schema: FcSchema): DeleteBaseListener() {
  val entity: SEntity
  val refID: RefID
  val errors = mutableListOf<String>()

  private var tmpEntity: SEntity? = null
  private var tmpRefID: RefID? = null
  init {
    compile()
    entity = tmpEntity!!
    refID = tmpRefID!!
  }

  private fun compile() {
    val lexer = QueryLexer(CharStreams.fromString(dsl))
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
    tmpEntity = schema.find(eText)
    tmpRefID = RefID(ctx.id.text.toLong())
  }
}