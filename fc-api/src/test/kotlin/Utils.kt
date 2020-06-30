package fc.test

import fc.api.*
import fc.api.query.IResult
import fc.api.query.QTree
import fc.api.spi.IAdaptor
import fc.api.spi.InputInstructions
import fc.api.EType.*
import fc.api.FType.*

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
  master("Simple") {
    field("aText", TEXT)
    field("aInt", INT)
    field("aLong", LONG)
    field("aFloat", FLOAT)
    field("aDouble", DOUBLE)
    field("aBool", BOOL)
    field("aTime", TIME)
    field("aDate", DATE)
    field("aDateTime", DATETIME)
    field("aList", LIST)
    // TODO: field("aMap", MAP)
  }

  val Country = master("Country") {
    field("name", TEXT)
    field("code", TEXT)
  }

  master("User") {
    field("name", TEXT)
    ownedRef("address", owned = detail("Address") {
      field("city", TEXT)
      linkedRef("country", Country)
    })
  }
}