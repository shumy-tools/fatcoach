package fc.api.query

import fc.api.*
import fc.api.spi.IAdaptor
import java.security.MessageDigest
import java.util.*

enum class SortType { NONE, ASC, DSC }
enum class OperType { OR, AND }
enum class CompType { EQUAL, DIFFERENT, MORE, LESS, MORE_EQ, LESS_EQ, IN }

class Query internal constructor(dsl: String, private val adaptor: IAdaptor) {
  val hash: String
  val tree: QTree
  val parameters: List<QParameter>
  val accessed: Set<SProperty>

  init {
    val compiled = QueryCompiler(dsl, adaptor.schema)
    if (compiled.errors.isNotEmpty())
      throw Exception("Failed to compile query! ${compiled.errors}")

    tree = compiled.tree
    parameters = compiled.parameters.values.toList()
    accessed = compiled.accessed

    hash = dsl.hash()
  }

  private fun String.hash(): String {
    val compact = replace(Regex("\\s"), "")
    val digest = MessageDigest.getInstance("SHA-256")
    val bytes = digest.digest(compact.toByteArray())
    return Base64.getUrlEncoder().encodeToString(bytes)
  }

  fun exec(args: Map<String, Any>): IResult {
    // TODO: check security on accessed properties?

    parameters.forEach {
      val arg = args[it.name] ?: throw Exception("Expected parameter value for: '${it.name}'")
      if (!TypeEngine.check(it.type, arg.javaClass.kotlin))
        throw Exception("Invalid parameter type for: '${it.name}'. Expected '${it.type}', found '${arg.javaClass.kotlin.simpleName}'!")

      if (it.isLimitOrPage) {
        if ((arg as Long) < 1)
          throw Exception("Invalid parameter value for: '${it.name}'. 'limit' and 'page' must be > 0")
      }
    }

    return adaptor.execQuery(tree, args)
  }
}

// ----------- query structure -----------
data class QParameter(val name: String, val type: FType, val isLimitOrPage: Boolean = false)

data class QTree(
  val entity: SEntity,
  val filter: QExpression?,
  val limit: Any?,            // expecting Long or QParameter
  val page: Any?,             // expecting Long or QParameter
  val select: QSelect
) {
  constructor(entity: SEntity, rel: QRelation): this(entity, rel.filter, rel.limit, rel.page, rel.select)
}

// ----------- filter structure -----------
data class QExpression(
  val left: QExpression?,
  val oper: OperType?,
  val right: QExpression?,
  val predicate: QPredicate?
)

data class QPredicate(
  val start: SEntity,
  val path: List<SProperty>,
  val end: SField,

  val comp: CompType,
  val param: Any?                // expecting the 'end' type or QParameter
)

// ----------- select structure -----------
data class QSelect(
  val fields: Map<SField, QField>,
  val relations: Map<SRelation, QRelation>
)

data class QField(
  val sort: SortType,
  val order: Int
)

data class QRelation(
  val filter: QExpression?,
  val limit: Any?,            // expecting Long or QParameter
  val page: Any?,             // expecting Long or QParameter
  val select: QSelect
)