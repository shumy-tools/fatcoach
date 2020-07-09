package fc.admin

import fc.api.FType
import fc.api.FcData
import fc.api.FcSchema
import fc.api.SEntity
import fc.api.input.Transaction
import fc.api.query.IResult
import fc.api.query.QTree
import fc.api.spi.IAdaptor
import fc.ws.FcServer

open class TestAdaptor(override val schema: FcSchema) : IAdaptor {
  override fun changeSchema(updated: FcSchema): Unit = throw Exception("Not used!")
  override fun execQuery(query: QTree, args: Map<String, Any>): IResult = throw Exception("Not used!")
  override fun getById(sEntity: SEntity, id: Long): FcData = throw Exception("Not used!")
  override fun execTransaction(instructions: Transaction): Unit = throw Exception("Not used!")
}

fun main() {
  val schema = FcSchema {
    /* -------------- fields -------------- */
    master("Simple") {
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
      field("aMap", FType.MAP)
    }

    master("ComplexJSON") {
      field("aList", FType.LIST)
      field("aMap", FType.MAP)
    }

    /* -------------- owned/linked refs -------------- */
    val Country = master("Country") {
      field("name", FType.TEXT)
      field("code", FType.TEXT)
    }

    master("User") {
      field("name", FType.TEXT)
      ownedRef("address", owned = detail("Address") {
        field("city", FType.TEXT)
        linkedRef("country", Country)
      })
    }

    /* -------------- owned/linked cols -------------- */
    val Permission = master("Permission") {
      field("name", FType.TEXT)
      field("url", FType.TEXT)
    }

    master("Role") {
      field("name", FType.TEXT)
      ownedCol("details", owned = detail("RoleDetail") {
        field("name", FType.TEXT)
        field("active", FType.BOOL)
        linkedCol("perms", Permission)
      })
    }
  }

  val adaptor = TestAdaptor(schema)
  FcServer(adaptor).start(9090)
}
