package fc.api

import fc.api.query.FcData
import fc.api.query.QueryBuilder
import fc.api.spi.IAdaptor

object FcFactory {
  fun connect(url: String, adaptor: IAdaptor): FcDatabase {
    return FcDatabase(url, adaptor)
  }
}

class FcDatabase(private val url: String, private val adaptor: IAdaptor) {
  val schema: FcSchema
    get() = adaptor.schema

  fun tx(transaction: FcTxData.() -> Unit) {
    val txData = FcTxData(schema)
    transaction(txData)
    adaptor.execInput(txData.instructions)
  }

  fun get(sEntity: SEntity, id: Long): FcData {
    return adaptor.getById(sEntity, id)
  }

  fun query(sEntity: SEntity): QueryBuilder {
    return QueryBuilder(sEntity, adaptor)
  }
}