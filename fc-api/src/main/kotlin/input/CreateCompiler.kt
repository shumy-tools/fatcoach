package fc.api.input

import fc.api.FcData
import fc.api.FcSchema
import fc.api.SEntity
import fc.dsl.input.CreateBaseListener
import fc.dsl.input.CreateParser
import fc.dsl.input.CreateParser.*
import fc.dsl.query.QueryLexer
import org.antlr.v4.runtime.CharStreams
import org.antlr.v4.runtime.CommonTokenStream
import org.antlr.v4.runtime.tree.ErrorNode
import org.antlr.v4.runtime.tree.ParseTreeWalker
import java.util.*

internal class CreateCompiler(private val dsl: String, private val schema: FcSchema): CreateBaseListener() {
  val entity: SEntity
  val data = linkedMapOf<String, Any?>()
  val errors = mutableListOf<String>()

  private var tmpEntity: SEntity? = null
  private val stack = Stack<SEntity>()
  init {
    compile()
    entity = tmpEntity!!
  }

  private fun <R: Any> scope(sEntity: SEntity, scope: () -> R): R {
    stack.push(sEntity)
      val result = scope()
    stack.pop()
    return result
  }

  private fun compile() {
    val lexer = QueryLexer(CharStreams.fromString(dsl))
    val tokens = CommonTokenStream(lexer)
    val parser = CreateParser(tokens)
    val tree = parser.create()

    val walker = ParseTreeWalker()
    walker.walk(this, tree)
  }

  override fun visitErrorNode(error: ErrorNode) {
    errors.add(error.text)
  }

  override fun enterCreate(ctx: CreateContext) {
    val eText = ctx.entity().text
    tmpEntity = schema.find(eText)

    stack.push(tmpEntity)
    ctx.data().processData()
  }

  private fun DataContext.processData(): FcData {
    //entry().map {  }
    TODO()
  }
}