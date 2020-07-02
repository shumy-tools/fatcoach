package fc.test

import fc.api.FType.*
import fc.api.FcData
import fc.api.FcSchema
import fc.api.SEntity
import fc.api.query.IResult
import fc.api.query.QTree
import fc.api.spi.IAdaptor
import fc.api.spi.InputInstructions

open class TestAdaptor(override val schema: FcSchema) : IAdaptor {
  override fun changeSchema(updated: FcSchema): Unit = throw Exception("Not used!")
  override fun execQuery(query: QTree, args: Map<String, Any>): IResult = throw Exception("Not used!")
  override fun getById(sEntity: SEntity, id: Long): FcData = throw Exception("Not used!")
  override fun execInput(instructions: InputInstructions): Unit = throw Exception("Not used!")
}

fun createCorrectSchema() = FcSchema {
  /* -------------- fields -------------- */
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
    field("aMap", MAP)
  }

  master("ComplexJSON") {
    field("aList", LIST)
    field("aMap", MAP)
  }

  /* -------------- owned/linked refs -------------- */
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

  /* -------------- owned/linked cols -------------- */
  val Permission = master("Permission") {
    field("name", TEXT)
    field("url", TEXT)
  }

  master("Role") {
    field("name", TEXT)
    ownedCol("details", owned = detail("RoleDetail") {
      field("name", TEXT)
      field("active", BOOL)
      linkedCol("perms", Permission)
    })
  }
}