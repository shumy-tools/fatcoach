package fc.api

import fc.api.query.Query
import fc.api.security.IAccessed
import fc.api.security.InstructionType
import fc.api.spi.IAdaptor

class FcDatabase(private val adaptor: IAdaptor, private val accessed: IAccessed? = null) {
  val schema: FcSchema
    get() = adaptor.schema

  fun tx(transaction: FcTxData.() -> Unit) {
    if (!schema.committed)
      throw Exception("Cannot execute inputs on a non-committed schema!")

    val txData = FcTxData(schema)
    transaction(txData)

    // check security constraints
    accessed?.let {
      txData.tx.all.forEach { accessed.canAccess(it.type, it.accessed) }
    }

    adaptor.execInput(txData.tx)
  }

  fun get(sEntity: SEntity, id: Long): FcData {
    if (!schema.committed)
      throw Exception("Cannot execute a get on a non-committed schema!")

    // check security constraints
    val accessedFields = setOf(sEntity.id).plus(sEntity.fields.values)
    accessed?.canAccess(InstructionType.QUERY, accessedFields)

    return adaptor.getById(sEntity, id)
  }

  fun query(dsl: String): Query {
    if (!schema.committed)
      throw Exception("Cannot execute a query on a non-committed schema!")

    val query = Query(dsl, adaptor)

    // check security constraints
    accessed?.canAccess(InstructionType.QUERY, query.accessed)
    return query
  }
}