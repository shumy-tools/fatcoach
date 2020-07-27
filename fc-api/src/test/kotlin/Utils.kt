package fc.test

import fc.api.FcData
import fc.api.FcSchema
import fc.api.SEntity
import fc.api.query.IResult
import fc.api.query.QTree
import fc.api.spi.IAdaptor
import fc.api.input.Transaction

open class TestAdaptor(override val schema: FcSchema) : IAdaptor {
  override fun changeSchema(updated: FcSchema): Unit = throw Exception("Not used!")
  override fun execQuery(query: QTree, args: Map<String, Any>): IResult = throw Exception("Not used!")
  override fun getById(sEntity: SEntity, id: Long): FcData = throw Exception("Not used!")
  override fun execTransaction(instructions: Transaction): Unit = throw Exception("Not used!")
}

fun createCorrectSchema() = FcSchema {
  master("TextInvalid") {
    text("aText") {
      check = { it.endsWith("Text") }
    }
  }

  /* -------------- fields -------------- */
  master("Simple") {
    text("aText")
    int("aInt")
    long("aLong")
    float("aFloat")
    double("aDouble")
    bool("aBool")
    time("aTime")
    date("aDate")
    datetime("aDateTime")
    list("aList")
    map("aMap")
  }

  master("ComplexJSON") {
    list("aList")
    map("aMap")
  }

  /* -------------- owned/linked refs -------------- */
  val Country = master("Country") {
    text("name")
    text("code")
  }

  master("User") {
    text("name")
    ownedRef("address", owned = detail("Address") {
      text("city")
      linkedRef("country", Country)
    })
  }

  /* -------------- owned/linked cols -------------- */
  val Permission = master("Permission") {
    text("name")
    text("url")
  }

  master("Role") {
    text("name")
    ownedCol("details", owned = detail("RoleDetail") {
      text("name")
      bool("active")
      linkedCol("perms", Permission)
    })
  }
}