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
import java.util.*

internal class CreateCompiler(private val dsl: String, private val schema: FcSchema, private val tx: InputInstructions, private val args: Map<String, Any>): CreateBaseListener() {
  val tree = RefTree()
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
    val entity = schema.find(eText)

    selfStack.push(Pair(tree, entity))
    ctx.data().processData()
  }

  private fun DataContext.processData() {
    val (selfTree, self) = selfStack.peek()
    val fcCreate = FcCreate(self, selfTree.root)
    fcCreate.accessed.add(self.id)
    tx.add(fcCreate)

    val inputs = entry()?.map {
      val prop = it.ID().text
      val sProperty = self.all[prop] ?: throw Exception("Property '$prop' not found in entity '${self.name}.")

      // parse input properties
      val value = if (sProperty.isInput) {
        fcCreate.accessed.add(sProperty)
        when {
          it.value() != null -> FieldProxy(it.value(), args, false).process(sProperty)
          it.list() != null -> it.list().processList(sProperty)
          it.data() != null -> {
            val obj = it.data()
            when (sProperty) {
              is SField -> { sProperty.tryType(FType.MAP); MapProxy(obj).parse() }
              is SReference -> {
                if (sProperty.type == RType.LINKED)
                  throw Exception("Expecting typeOf (null, long, param) for '${self.name}.$prop'.")
                scope(selfTree, sProperty) { it.data().processData() }
              }
              is SCollection -> throw Exception("Expecting a collection for '${self.name}.$prop'.")
            }
          }
          else -> throw Exception("Bug - CreateCompiler.processData() - 1. Unrecognized dsl branch!")
        }
      } else throw Exception("Bug - CreateCompiler.processData() - 2. Unrecognized dsl branch!")

      sProperty.name to value
    }?.toMap() ?: emptyMap()

    // check if all input properties are present
    val completed = if (parentStack.isNotEmpty()) inputs.plus(self.parent!!.name to parentStack.peek().root) else inputs
    self.all.values.filter { it.isInput }.forEach {
      if (!completed.containsKey(it.name))
        throw Exception("Expecting an input value for '${self.name}:${it.name}'.")
    }

    // TODO: process derive fields ?
    fcCreate.values = completed
  }

  private fun ListContext.processList(prop: SProperty): List<Any> {
    return when (prop) {
      is SField -> {
        when (prop.type) {
          FType.LIST -> ListProxy(this).parse()
          FType.MAP -> throw Exception("Expecting an object for '${prop.entity!!.name}.${prop.name}'.")
          else -> throw Exception("A list is not compatible with the type '${prop.type.name.toLowerCase()}' for '${prop.entity!!.name}.${prop.name}'.")
        }
      }

      is SCollection -> when (prop.type) {
        RType.OWNED -> {
          val selfTree = selfStack.peek().first
          item().map {
            if (it.data() == null)
              throw Exception("Expecting a collection of objects for '${prop.entity!!.name}.${prop.name}'.")
            scope(selfTree, prop) { it.data().processData() }
          }
        }

        RType.LINKED -> item().map { FieldProxy(it.value(), args, false).processRefID(prop, false) }
      }

      is SReference -> throw Exception("Expecting a reference for '${prop.entity!!.name}.${prop.name}'.")
    }
  }
}