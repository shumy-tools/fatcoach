package fc.api.query

import fc.api.*
import fc.dsl.query.QueryBaseListener
import fc.dsl.query.QueryLexer
import fc.dsl.query.QueryParser
import fc.dsl.query.QueryParser.*
import org.antlr.v4.runtime.CharStreams
import org.antlr.v4.runtime.CommonTokenStream
import org.antlr.v4.runtime.tree.ErrorNode
import org.antlr.v4.runtime.tree.ParseTreeWalker
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.util.*

internal class QueryCompiler(private val dsl: String, private val schema: FcSchema): QueryBaseListener() {
  val tree: QTree
  val parameters = linkedMapOf<String, QParameter>()
  val accessed = mutableSetOf<SProperty>()
  val errors = mutableListOf<String>()

  private var tmpTree: QTree? = null
  private val stack = Stack<SEntity>()
  init {
    compile()
    tree = tmpTree!!
  }


  private fun <R: Any> scope(sEntity: SEntity, scope: () -> R): R {
    stack.push(sEntity)
      val result = scope()
    stack.pop()
    return result
  }

  private fun addParameter(param: QParameter): QParameter {
    if (parameters.containsKey(param.name))
      throw Exception("Query already contains the '?${param.name}' parameter.")

    parameters[param.name] = param
    return param
  }

  private fun compile() {
    val lexer = QueryLexer(CharStreams.fromString(dsl))
    val tokens = CommonTokenStream(lexer)
    val parser = QueryParser(tokens)
    val tree = parser.query()

    val walker = ParseTreeWalker()
    walker.walk(this, tree)
  }

  override fun visitErrorNode(error: ErrorNode) {
    errors.add(error.text)
  }

  override fun enterQuery(ctx: QueryContext) {
    val eText = ctx.entity().text
    val sEntity = schema.find(eText)

    stack.push(sEntity)
    val rel = ctx.qline().processQLine()
    tmpTree = QTree(sEntity, rel)
  }

  private fun QlineContext.processQLine(isReference: Boolean = false): QRelation {
    val fields = select().fields().processFields()
    val relations = select().relation().processRelations()

    val limit = limit()?.let {
      if (isReference)
        throw Exception("Direct references don't support 'limit'! Use it on the top entity.")
      it.intOrParam().processLimitOrPage()
    }

    val page = page()?.let {
      if (isReference)
        throw Exception("Direct references don't support 'page'! Use it on the top entity.")
      it.intOrParam().processLimitOrPage()
    }

    // process filter paths
    val filter = filter()?.expr()?.processExpr()
    if (isReference && filter != null)
      throw Exception("Direct references don't support 'filter'! Use it on the top entity.")

    val select = QSelect(fields, relations)
    return QRelation(filter, limit, page, select)
  }

  private fun IntOrParamContext.processLimitOrPage(): Any {
    return if (INT() != null) {
      val value = INT().text.toInt()
      if (value < 1)
        throw Exception("'limit' and 'page' must be > 0")
      value
    } else {
      val value = PARAM().text.substring(1)
      val qParam = QParameter(value, FType.LONG, isLimitOrPage = true)
      addParameter(qParam)
    }
  }

  private fun List<RelationContext>.processRelations(): Map<SRelation, QRelation> {
    val sEntity = stack.peek()
    return map {
      val prop = it.ID().text
      val rel = sEntity.rels[prop] ?: throw Exception("Invalid relation '$${sEntity.name}.$prop'")
      accessed.add(rel)

      scope(rel.ref) {
        rel to it.qline().processQLine(rel is SReference)
      }
    }.toMap()
  }

  private fun FieldsContext.processFields(): Map<SField, QField> {
    val sEntity = stack.peek()
    return if (ALL() != null) {
      sEntity.fields.values.map {
        accessed.add(it)
        it to QField(SortType.NONE, 0)
      }.toMap()
    } else {
      field().map {
        val prop = it.ID().text
        val field = sEntity.fields[prop] ?: throw Exception("Invalid field '$${sEntity.name}.$prop'")
        accessed.add(field)

        val sortType = sortType(it.order()?.text)
        val order = it.INT()?.let { ord ->
          val order = ord.text.toInt()
          if (order < 1)
            throw Exception("Order must be > 0")
          order
        } ?: 0

        field to QField(sortType, order)
      }.toMap()
    }
  }

  private fun ExprContext.processExpr(): QExpression {
    val eList = expr()
    return when {
      eList.size == 1 -> {
        val qExpression = eList.last().processExpr()
        QExpression(qExpression, null, null, null)
      }

      oper != null -> {
        val qLeft = left.processExpr()
        val qRight = right.processExpr()
        val operType = operType(oper.text)
        QExpression(qLeft, operType, qRight, null)
      }

      else -> {
        val qPredicate = predicate().processPredicate()
        QExpression(null, null, null, qPredicate)
      }
    }
  }

  private fun PredicateContext.processPredicate(): QPredicate {
    val start = stack.peek()
    val full = path().ID()

    var ctx: SEntity = start
    val path = mutableListOf<SProperty>()
    full.forEachIndexed { index, it ->
      val prop = it.text
      val sProperty = ctx.all[prop]

      // first and middle token requires a SRelation
      if (index < full.size - 1 && sProperty !is SRelation) {
        val oneOf = ctx.rels.map { it.key }
        throw Exception("Invalid path '${ctx.name}:$prop'. Please complete the path with one of $oneOf")
      }

      // last token requires a SField
      if (index == full.size - 1 && sProperty !is SField) {
        val oneOf = ctx.fields.map { it.key }
        throw Exception("Invalid path termination '${ctx.name}:$prop'. Please complete the path with one of $oneOf")
      }

      val next = sProperty as SProperty // is not null
      path.add(next)
      accessed.add(next)

      if (next is SRelation)
        ctx = next.ref
    }

    // path termination is already checked, this is a SField
    val end = path.last() as SField

    // process comparator and parameter
    val compType = compType(comp().text)
    val qParam = param().parse()

    if (qParam is Parameter)
      addParameter(QParameter(qParam.value, end.type))

    if (qParam is List<*>)
      qParam.forEach {
        if (it is Parameter)
          addParameter(QParameter(it.value, end.type))
      }

    return QPredicate(start, path, end, compType, qParam)
  }
}

/* ------------------------- helpers -------------------------*/
private class Parameter(val value: String)

private fun sortType(sort: String?) = when(sort) {
  "asc" -> SortType.ASC
  "dsc" -> SortType.DSC
  null -> SortType.NONE
  else -> throw Exception("Unrecognized sort operator! Use (asc, dsc)")
}


private fun operType(oper: String) = when(oper) {
  "or" -> OperType.OR
  "and" -> OperType.AND
  else -> throw Exception("Unrecognized logic operator! Use (or, and)")
}

private fun compType(comp: String) = when (comp) {
  "==" -> CompType.EQUAL
  "!=" -> CompType.DIFFERENT
  ">" -> CompType.MORE
  "<" -> CompType.LESS
  ">=" -> CompType.MORE_EQ
  "<=" -> CompType.LESS_EQ
  "in" -> CompType.IN
  else -> throw Exception("Unrecognized comparator! Use ('==', '!=', '>', '<', '>=', '<=', 'in')")
}

private fun ParamContext.parse() = when {
  value() != null -> value().parse()
  list() != null -> list().value().map { it.parse() }
  else -> throw Exception("Unrecognized parameter type!")
}

private fun ValueContext.parse(): Any = when {
  TEXT() != null -> TEXT().text.substring(1, TEXT().text.length-1)
  INT() != null -> INT().text.toInt()
  FLOAT() != null -> FLOAT().text.toFloat()
  BOOL() != null -> BOOL().text!!.toBoolean()
  TIME() != null -> LocalTime.parse(TIME().text.substring(1))
  DATE() != null -> LocalDate.parse(DATE().text.substring(1))
  DATETIME() != null -> LocalDateTime.parse(DATETIME().text.substring(1))
  PARAM() != null -> Parameter(PARAM().text.substring(1))
  else -> throw Exception("Unrecognized parameter type!")
}