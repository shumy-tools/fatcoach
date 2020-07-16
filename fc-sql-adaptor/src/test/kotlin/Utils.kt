package fc.adaptor.test

import fc.adaptor.sql.SQLAdaptor
import fc.api.FType.*
import fc.api.FcSchema
import kotlin.concurrent.getOrSet

fun createCorrectSchema() = FcSchema {
  master("Ordered") {
    field("aText", TEXT)
    field("aFloat", FLOAT)
    field("aDateTime", DATETIME)
  }

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
    field("email", TEXT, isOptional = true)
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

val ctx = ThreadLocal<MutableList<String>>()

val sqlListener: (String) -> Unit = {
  val instructions = ctx.getOrSet { mutableListOf() }
  instructions.add(it)
}

fun init() {
  val instructions = ctx.getOrSet { mutableListOf() }
  instructions.clear()
}

fun check(index: Int, sql: String) {
  val instructions = ctx.get()
  assert(instructions[index] == sql)
}