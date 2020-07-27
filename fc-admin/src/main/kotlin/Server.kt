package fc.admin

import fc.adaptor.sql.SQLAdaptor
import fc.api.FcSchema
import fc.ws.FcServer

fun main() {
  val schema = FcSchema {
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

  val adaptor = SQLAdaptor("jdbc:h2:mem:AdminTest").also {
    it.createSchema(schema)
  }

  FcServer(adaptor).start(9090)
}
