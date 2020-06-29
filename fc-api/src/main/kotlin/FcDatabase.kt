package fc.api

import fc.api.query.Query
import fc.api.spi.IAdaptor

class FcDatabase(private val adaptor: IAdaptor) {
  val schema: FcSchema
    get() = adaptor.schema

  fun tx(transaction: FcTxData.() -> Unit) {
    if (!schema.committed)
      throw Exception("Cannot execute inputs on a non-committed schema!")

    val txData = FcTxData(schema)
    transaction(txData)
    adaptor.execInput(txData.instructions)
  }

  fun get(sEntity: SEntity, id: Long): FcData {
    if (!schema.committed)
      throw Exception("Cannot execute a get on a non-committed schema!")
    return adaptor.getById(sEntity, id)
  }

  fun query(dsl: String): Query {
    if (!schema.committed)
      throw Exception("Cannot execute a query on a non-committed schema!")
    return Query(dsl, adaptor)
  }
}