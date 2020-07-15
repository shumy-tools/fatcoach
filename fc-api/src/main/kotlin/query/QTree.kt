package fc.api.query

import fc.api.*
import kotlin.concurrent.getOrSet

class QTree(
  val entity: SEntity,
  val filter: QExpression?,
  val limit: Any?,            // expecting Long or QParameter
  val page: Any?,             // expecting Long or QParameter
  val select: QSelect
) {
  constructor(entity: SEntity, rel: QRelation): this(entity, rel.filter, rel.limit, rel.page, rel.select)

  override fun toString() =
    """QTree(${entity.name})${QRelation(filter, limit, page, select)}"""
      .trimIndent().replace(Regex("\\n\\s*\\n"), "\n")
}

class QParameter(val name: String, val type: FType, val isLimitOrPage: Boolean = false) {
  override fun toString() = "?(${type.name.toLowerCase()}@$name)"
}

// ----------- filter structure -----------
class QExpression(
  val left: QExpression?,
  val oper: OperType?,
  val right: QExpression?,
  val predicate: QPredicate?
) {
  override fun toString() = when {
    oper != null -> "$left ${oper.name.toLowerCase()} $right"
    left != null -> "($left)"
    predicate != null -> "$predicate"
    else -> ""
  }
}

class QPredicate(
  val start: SEntity,
  val path: List<SProperty>,
  val end: SField,

  val comp: CompType,
  val param: Any?                // expecting the 'end' type or QParameter
) {
  private fun paramToString() = if (param !is QParameter) "(${TypeEngine.convert(param!!.javaClass.kotlin).name.toLowerCase()}@$param)" else param.toString()
  override fun toString() = "${start.name}::[${path.joinToString{it.simpleString()}}] ${comp.name.toLowerCase()} ${paramToString()}"
}

// ----------- select structure -----------
class QSelect(
  val fields: Map<SField, QField>,
  val relations: Map<SRelation, QRelation>
) {
  private fun SRelation.text() = when (this) {
    is SReference -> "-> ${ref.name}"
    is SCollection -> "-< ${ref.name}"
  }

  override fun toString() = SpaceStack.scope {
    """{
      ${if (fields.isNotEmpty()) "\n|${SpaceStack.spaces()}${fields.map{"${it.value} ${it.key.simpleString()}"}.joinToString("\n|${SpaceStack.spaces()}")}" else ""}
      ${if (relations.isNotEmpty()) "\n|${SpaceStack.spaces()}${relations.map{"${it.key.entity.name}::${it.key.name} ${it.key.text()}${it.value}"}.joinToString("\n|${SpaceStack.spaces()}")}" else ""}
    |${SpaceStack.end()}}""".trimMargin()
  }
}

class QField(
  val sort: SortType,
  val order: Int
) {
  override fun toString() = "(${sort.name.toLowerCase()} $order)"
}

class QRelation(
  val filter: QExpression?,
  val limit: Any?,            // expecting Long or QParameter
  val page: Any?,             // expecting Long or QParameter
  val select: QSelect
) {
  override fun toString() = """${filter?.let {" | $filter |"} ?: ""}${limit?.let{" limit $limit"} ?: ""}${page?.let{" page $page"} ?: ""} $select"""
}

/* ------------------------- helpers -------------------------*/
private object SpaceStack {
  private const val SPACES = 2
  private val stack = ThreadLocal<Int>()

  fun scope(scope: () -> String): String {
    push()
      val result = scope()
    pop()
    return result
  }

  fun push() = stack.set(stack.getOrSet{0} + SPACES)
  fun pop() = stack.set(stack.getOrSet{0} - SPACES)
  fun spaces() = " ".repeat(stack.getOrSet{0})
  fun end() = " ".repeat(stack.getOrSet{0} - SPACES)
}
