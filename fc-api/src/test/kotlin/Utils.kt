package fc.test

import fc.api.*
import fc.api.query.IResult
import fc.api.query.QTree
import fc.api.spi.IAdaptor
import fc.api.spi.InputInstructions

open class TestAdaptor(override val schema: FcSchema) : IAdaptor {
  override fun changeSchema(updated: FcSchema) {
    TODO("Not yet implemented")
  }

  override fun execQuery(query: QTree, args: Map<String, Any>): IResult {
    TODO("Not yet implemented")
  }

  override fun getById(sEntity: SEntity, id: Long): FcData {
    TODO("Not yet implemented")
  }

  override fun execInput(instructions: InputInstructions) {
    TODO("Not yet implemented")
  }
}

fun createCorrectSchema() = FcSchema {
  entity("Simple", EType.MASTER) {
    field("aText", FType.TEXT)
    field("aInt", FType.INT)
    field("aLong", FType.LONG)
    field("aFloat", FType.FLOAT)
    field("aDouble", FType.DOUBLE)
    field("aBool", FType.BOOL)
    field("aTime", FType.TIME)
    field("aDate", FType.DATE)
    field("aDateTime", FType.DATETIME)
    field("aList", FType.LIST)
    // TODO: field("aMap", MAP)
  }
}