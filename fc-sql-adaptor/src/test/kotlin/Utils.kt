package fc.adaptor.test

import fc.api.FcSchema
import kotlin.concurrent.getOrSet

fun createCorrectSchema() = FcSchema {
  master("Ordered") {
    text("aText")
    float("aFloat")
    datetime("aDateTime")
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
    text("email") { optional = true }
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