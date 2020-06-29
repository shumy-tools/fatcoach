package fc.api.spi

import fc.api.FcSchema
import fc.api.SEntity
import fc.api.query.FcData
import fc.api.query.IResult
import fc.api.query.QTree

interface IAdaptor {
  val schema: FcSchema
  fun changeSchema(updated: FcSchema)

  fun execInput(instructions: InputInstructions)

  fun getById(sEntity: SEntity, id: Long): FcData
  fun execQuery(query: QTree, args: Map<String, Any>): IResult
}