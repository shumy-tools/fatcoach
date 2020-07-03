package fc.api

import fc.api.input.Transaction
import fc.api.query.Query
import fc.api.spi.IAuthorizer
import fc.api.spi.IAdaptor

class FcDatabase(private val adaptor: IAdaptor, private val authorizer: IAuthorizer? = null) {
  val schema: FcSchema
    get() = adaptor.schema

  fun tx(transaction: FcTxData.() -> Unit): Transaction {
    if (!schema.committed)
      throw Exception("Cannot execute inputs on a non-committed schema!")

    val txData = FcTxData(schema)
    transaction(txData)

    // check security constraints
    authorizer?.let {
      txData.tx.all.forEach { authorizer.canInput(it) }
    }

    adaptor.execInput(txData.tx)
    return txData.tx
  }

  fun get(sEntity: SEntity, id: Long): FcData {
    if (!schema.committed)
      throw Exception("Cannot execute a get on a non-committed schema!")

    // check security constraints
    val accessedFields = setOf(sEntity.id).plus(sEntity.fields.values)
    authorizer?.canQuery(accessedFields)

    return adaptor.getById(sEntity, id)
  }

  fun query(dsl: String): Query {
    if (!schema.committed)
      throw Exception("Cannot execute a query on a non-committed schema!")

    val query = Query(dsl, adaptor)

    // check security constraints
    authorizer?.canQuery(query.accessed)
    return query
  }
}