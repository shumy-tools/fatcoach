import fc.api.FType.*
import fc.api.FcData
import fc.api.FcSchema
import fc.api.SEntity
import fc.api.query.IResult
import fc.api.query.QTree
import fc.api.spi.IAdaptor
import fc.api.input.Transaction

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

val schema = createCorrectSchema()