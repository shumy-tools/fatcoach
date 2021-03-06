package fc.api

import fc.api.input.Transaction
import fc.api.query.Query
import fc.api.spi.IAuthorizer
import fc.api.spi.IAdaptor

class FcDatabase(private val adaptor: IAdaptor, private val authorizer: IAuthorizer? = null) {
  val schema: FcSchema
    get() = adaptor.schema

  fun tx(transaction: FcTransaction.() -> Unit): Transaction {
    val txData = FcTransaction(this, schema)
    transaction(txData)

    // check security constraints
    authorizer?.let {
      txData.tx.all.forEach { authorizer.canInput(it) }
    }

    adaptor.execTransaction(txData.tx)
    return txData.tx
  }

  fun get(sEntity: SEntity, id: Long): FcData {
    // check security constraints
    val accessedFields = setOf(sEntity.id).plus(sEntity.fields.values)
    authorizer?.canQuery(accessedFields)

    return adaptor.getById(sEntity, id)
  }

  fun query(dsl: String): Query {
    val query = Query(dsl, adaptor)

    // check security constraints
    authorizer?.canQuery(query.accessed)
    return query
  }
}