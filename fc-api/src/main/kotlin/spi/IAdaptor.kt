package fc.api.spi

import fc.api.FcData
import fc.api.FcSchema
import fc.api.SEntity
import fc.api.input.Transaction
import fc.api.query.IResult
import fc.api.query.QTree

interface IAdaptor {
  val schema: FcSchema
  fun changeSchema(updated: FcSchema)

  fun getById(sEntity: SEntity, id: Long): FcData
  fun execTransaction(instructions: Transaction)
  fun execQuery(query: QTree, args: Map<String, Any>): IResult
}