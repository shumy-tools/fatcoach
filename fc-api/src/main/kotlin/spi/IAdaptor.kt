package fc.api.spi

import fc.api.FcSchema
import fc.api.SEntity
import fc.api.query.FcData

interface IAdaptor {
  val schema: FcSchema

  fun execInput(instructions: InputInstructions)
  fun getById(sEntity: SEntity, id: Long): FcData
  //fun execQuery(query: Query): IResult
}