package fc.test

import fc.api.FcDatabase
import fc.api.FcSchema
import fc.api.RefTree
import fc.api.SProperty
import fc.api.input.*
import fc.api.spi.IAuthorizer
import org.junit.Test
import kotlin.concurrent.getOrSet
import kotlin.test.assertFails

private fun Set<SProperty>.text() = map{"${it.entity.name}::${it.simpleString()}"}.toString()

private fun FcInstruction.type() = when (this) {
  is FcCreate -> "CREATE"
  is FcUpdate -> "UPDATE"
  is FcDelete -> "DELETE"
}

private class InputChecker(private val instructions: Transaction) {
  internal var next = 0
  fun check(value: String) {
    assert(instructions.all[next].toString() == value)
    next += 1
  }
}

private class SecurityChecker(private val instructions: List<FcInstruction>) {
  internal var next = 0
  fun check(type: String, value: String) {
    val inst = instructions[next]
    assert(inst.type() == type)
    assert(inst.accessed.text() == value)
    next += 1
  }
}

private class InputSecurity: IAuthorizer {
  private val session = ThreadLocal<List<FcInstruction>>()

  override fun canQuery(props: Set<SProperty>) { throw Exception("Not used!") }
  override fun canInput(instruction: FcInstruction) {
    val instructions = session.getOrSet { mutableListOf() } as MutableList<FcInstruction>
    instructions.add(instruction)
  }

  fun checker(scope: SecurityChecker.() -> Unit) {
    val instructions = session.get()
    val checker = SecurityChecker(instructions)
    checker.scope()
    val remaining = instructions.size - checker.next
    assert(remaining == 0) {
      """Check all available security instructions! Remaining:
        |${instructions.drop(instructions.size - remaining).joinToString("\n")}
      """.trimMargin()
    }

    session.set(mutableListOf())
  }

  fun print() {
    println("Access:")
    session.get().forEach {
      println("(${it.type()}) - ${it.accessed.text()}")
    }
  }
}

private class TestInputAdaptor(override val schema: FcSchema) : TestAdaptor(schema) {
  private val session = ThreadLocal<Transaction>()
  override fun execTransaction(instructions: Transaction) {
    session.set(instructions)
  }

  fun checker(scope: InputChecker.() -> Unit) {
    val instructions = session.get()
    val checker = InputChecker(instructions)
    checker.scope()
    val remaining = instructions.all.size - checker.next
    assert(remaining == 0) {
      """Check all available instructions! Remaining:
        |${instructions.all.drop(instructions.all.size - remaining).joinToString("\n")}
      """.trimMargin()
    }
  }

  fun print() = session.get().all.forEach(::println)
}

class TestInput {
  private val schema = createCorrectSchema()
  private val adaptor = TestInputAdaptor(schema)
  private val security = InputSecurity()
  private val db = FcDatabase(adaptor, security)

  @Test fun testFieldTypes() {
    var id: RefTree? = null
    var complexID1: RefTree? = null
    var complexID2: RefTree? = null

    db.tx {
      id = create("""Simple {
        aText: "newText",
        aInt: 10,
        aLong: 20,
        aFloat: 10.0,
        aDouble: 20.0,
        aBool: true,
        aTime: #15:10:30,
        aDate: #2020-10-25,
        aDateTime: #2020-10-25T15:10:30,
        aList: [1, "2"],
        aMap: { one: 1, two: 2 }
      }""")

      update("""Simple @id == ?id {
        aText: "updatedText",
        aInt: 100,
        aLong: 200,
        aFloat: 100.0,
        aDouble: 200.0,
        aBool: false,
        aTime: #13:10:30,
        aDate: #2019-10-25,
        aDateTime: #2019-10-25T13:10:30,
        aList: [10, "20"],
        aMap: { one-u: 10, two-u: 20 }
      }""", "id" to id!!)

      complexID1 = create("""ComplexJSON {
        aList: [ 1, 1.2, false, #15:10:30, #2020-10-25, #2020-10-25T15:10:30, { one: 1, two: 2 } ],
        aMap: { }
      }""")

      complexID2 = create("""ComplexJSON {
        aList: [ ],
        aMap: { long: 1, double: 1.2, bool: false, time: #15:10:30, date: #2020-10-25, dt: #2020-10-25T15:10:30, list: [ 1, 2 ] }
      }""")
    }

    security.checker {
      check("CREATE", "[Simple::(long@@id), Simple::(text@aText), Simple::(int@aInt), Simple::(long@aLong), Simple::(float@aFloat), Simple::(double@aDouble), Simple::(bool@aBool), Simple::(time@aTime), Simple::(date@aDate), Simple::(datetime@aDateTime), Simple::(list@aList), Simple::(map@aMap)]")
      check("UPDATE", "[Simple::(long@@id), Simple::(text@aText), Simple::(int@aInt), Simple::(long@aLong), Simple::(float@aFloat), Simple::(double@aDouble), Simple::(bool@aBool), Simple::(time@aTime), Simple::(date@aDate), Simple::(datetime@aDateTime), Simple::(list@aList), Simple::(map@aMap)]")
      check("CREATE", "[ComplexJSON::(long@@id), ComplexJSON::(list@aList), ComplexJSON::(map@aMap)]")
      check("CREATE", "[ComplexJSON::(long@@id), ComplexJSON::(list@aList), ComplexJSON::(map@aMap)]")
    }

    adaptor.checker {
      check("FcCreate(Simple) @id=$id - {aText=(String@newText), aInt=(Int@10), aLong=(Long@20), aFloat=(Float@10.0), aDouble=(Double@20.0), aBool=(Boolean@true), aTime=(LocalTime@15:10:30), aDate=(LocalDate@2020-10-25), aDateTime=(LocalDateTime@2020-10-25T15:10:30), aList=[(Long@1), (String@2)], aMap={one=(Long@1), two=(Long@2)}}")
      check("FcUpdate(Simple) @id=$id - {aText=(String@updatedText), aInt=(Int@100), aLong=(Long@200), aFloat=(Float@100.0), aDouble=(Double@200.0), aBool=(Boolean@false), aTime=(LocalTime@13:10:30), aDate=(LocalDate@2019-10-25), aDateTime=(LocalDateTime@2019-10-25T13:10:30), aList=[(Long@10), (String@20)], aMap={one-u=(Long@10), two-u=(Long@20)}}")
      check("FcCreate(ComplexJSON) @id=$complexID1 - {aList=[(Long@1), (Double@1.2), (Boolean@false), (LocalTime@15:10:30), (LocalDate@2020-10-25), (LocalDateTime@2020-10-25T15:10:30), {one=(Long@1), two=(Long@2)}], aMap={}}")
      check("FcCreate(ComplexJSON) @id=$complexID2 - {aList=[], aMap={long=(Long@1), double=(Double@1.2), bool=(Boolean@false), time=(LocalTime@15:10:30), date=(LocalDate@2020-10-25), dt=(LocalDateTime@2020-10-25T15:10:30), list=[(Long@1), (Long@2)]}}")
    }
  }

  @Test fun testReferences() {
    var portugalID: RefTree? = null
    var spainID: RefTree? = null

    var userID: RefTree? = null
    var addressID: RefTree? = null
    db.tx {
      portugalID = create("""Country {
        name: "Portugal",
        code: "PT"
      }""")

      spainID = create("""Country {
        name: "Spain",
        code: "ES"
      }""")

      userID = create("""User {
        name: "Alex",
        address: {
          city: "Aveiro",
          country: ?country
        }
      }""", "country" to portugalID!!)

      addressID = userID!!.find("address")

      update("""Address @id == ?id {
        city: "Barcelona",
        country: @add ?country
      }""", "id" to addressID!!, "country" to spainID!!)

      update("""Address @id == ?id {
        city: "None",
        country: @del ?country
      }""", "id" to addressID!!, "country" to spainID!!)

      delete("Country @id == ?id", portugalID!!)
    }

    security.checker {
      check("CREATE","[Country::(long@@id), Country::(text@name), Country::(text@code)]")
      check("CREATE", "[Country::(long@@id), Country::(text@name), Country::(text@code)]")
      check("CREATE", "[User::(long@@id), User::(text@name), User::(Address@address)]")
      check("CREATE","[Address::(long@@id), Address::(text@city), Address::(Country@country)]")
      check("UPDATE","[Address::(long@@id), Address::(text@city), Address::(Country@country)]")
      check("UPDATE","[Address::(long@@id), Address::(text@city), Address::(Country@country)]")
      check("DELETE","[Country::(long@@id)]")
    }

    adaptor.checker {
      check("FcCreate(Country) @id=$portugalID - {name=(String@Portugal), code=(String@PT)}")
      check("FcCreate(Country) @id=$spainID - {name=(String@Spain), code=(String@ES)}")

      check("FcCreate(User) @id=$userID - {name=(String@Alex), address=(RefID@$addressID)}")
      check("FcCreate(Address) @id=$addressID - {city=(String@Aveiro), country=(RefID@$portugalID), @parent=(RefID@$userID)}")

      check("FcUpdate(Address) @id=$addressID - {city=(String@Barcelona), country=(RefLink@(@add -> $spainID))}")
      check("FcUpdate(Address) @id=$addressID - {city=(String@None), country=(RefLink@(@del -> $spainID))}")

      check("FcDelete(Country) @id=$portugalID")
    }
  }

  @Test fun testCollections() {
    var permID1: RefTree? = null
    var permID2: RefTree? = null
    var permID3: RefTree? = null
    var permID4: RefTree? = null

    var roleID1: RefTree? = null
    var role1Det1: RefTree? = null
    var role1Det2: RefTree? = null
    var role1AddDet: RefTree? = null

    var roleID2: RefTree? = null
    var role2Det1: RefTree? = null
    var role2Det2: RefTree? = null

    db.tx {
      permID1 = create("""Permission {
        name: "perm-1",
        url: "http://url-1"
      }""")

      permID2 = create("""Permission {
        name: "perm-2",
        url: "http://url-2"
      }""")

      permID3 = create("""Permission {
        name: "perm-3",
        url: "http://url-3"
      }""")

      permID4 = create("""Permission {
        name: "perm-4",
        url: "http://url-4"
      }""")

      // setting individual parameters
      roleID1 = create("""Role {
        name: "role-name",
        details: [
          { name: "role-det-1", active: true, perms: [?perm1, ?perm2] },
          { name: "role-det-2", active: false, perms: [?perm3, ?perm4] }
        ]
      }""", "perm1" to permID1!!, "perm2" to permID2!!, "perm3" to permID3!!, "perm4" to permID4!!)

      role1Det1 = roleID1!!.find("details", 0)
      role1Det2 = roleID1!!.find("details", 1)

      // setting lists of parameters
      roleID2 = create("""Role {
        name: "role-name",
        details: [
          { name: "role-det-1", active: true, perms: ?perms1 },
          { name: "role-det-2", active: false, perms: ?perms2 }
        ]
      }""", "perms1" to listOf(permID1, permID2), "perms2" to listOf(permID3, permID4))

      role2Det1 = roleID2!!.find("details", 0)
      role2Det2 = roleID2!!.find("details", 1)

      // create RoleDetail via parent
      role1AddDet = create("""RoleDetail {
        @parent: ?parent,
        name: "role-det-5",
        active: true,
        perms: ?perms
      }""", "parent" to roleID1!!, "perms" to listOf(permID1, permID4))

      update("""RoleDetail @id == ?id {
        perms: @add ?perms
      }""", "id" to role1AddDet!!, "perms" to listOf(permID2, permID3))

      update("""RoleDetail @id == ?id {
        perms: @del ?perms
      }""", "id" to role1AddDet!!, "perms" to listOf(permID2, permID3))
    }

    security.checker {
      check("CREATE", "[Permission::(long@@id), Permission::(text@name), Permission::(text@url)]")
      check("CREATE", "[Permission::(long@@id), Permission::(text@name), Permission::(text@url)]")
      check("CREATE", "[Permission::(long@@id), Permission::(text@name), Permission::(text@url)]")
      check("CREATE", "[Permission::(long@@id), Permission::(text@name), Permission::(text@url)]")
      check("CREATE", "[Role::(long@@id), Role::(text@name), Role::(RoleDetail@details)]")
      check("CREATE", "[RoleDetail::(long@@id), RoleDetail::(text@name), RoleDetail::(bool@active), RoleDetail::(Permission@perms)]")
      check("CREATE", "[RoleDetail::(long@@id), RoleDetail::(text@name), RoleDetail::(bool@active), RoleDetail::(Permission@perms)]")
      check("CREATE", "[Role::(long@@id), Role::(text@name), Role::(RoleDetail@details)]")
      check("CREATE", "[RoleDetail::(long@@id), RoleDetail::(text@name), RoleDetail::(bool@active), RoleDetail::(Permission@perms)]")
      check("CREATE", "[RoleDetail::(long@@id), RoleDetail::(text@name), RoleDetail::(bool@active), RoleDetail::(Permission@perms)]")
      check("CREATE", "[RoleDetail::(long@@id), RoleDetail::(Role@@parent), RoleDetail::(text@name), RoleDetail::(bool@active), RoleDetail::(Permission@perms)]")
      check("UPDATE", "[RoleDetail::(long@@id), RoleDetail::(Permission@perms)]")
      check("UPDATE", "[RoleDetail::(long@@id), RoleDetail::(Permission@perms)]")
    }

    adaptor.checker {
      check("FcCreate(Permission) @id=$permID1 - {name=(String@perm-1), url=(String@http://url-1)}")
      check("FcCreate(Permission) @id=$permID2 - {name=(String@perm-2), url=(String@http://url-2)}")
      check("FcCreate(Permission) @id=$permID3 - {name=(String@perm-3), url=(String@http://url-3)}")
      check("FcCreate(Permission) @id=$permID4 - {name=(String@perm-4), url=(String@http://url-4)}")

      check("FcCreate(Role) @id=$roleID1 - {name=(String@role-name), details=[(RefID@$role1Det1), (RefID@$role1Det2)]}")
      check("FcCreate(RoleDetail) @id=$role1Det1 - {name=(String@role-det-1), active=(Boolean@true), perms=[(RefID@$permID1), (RefID@$permID2)], @parent=(RefID@$roleID1)}")
      check("FcCreate(RoleDetail) @id=$role1Det2 - {name=(String@role-det-2), active=(Boolean@false), perms=[(RefID@$permID3), (RefID@$permID4)], @parent=(RefID@$roleID1)}")

      check("FcCreate(Role) @id=$roleID2 - {name=(String@role-name), details=[(RefID@$role2Det1), (RefID@$role2Det2)]}")
      check("FcCreate(RoleDetail) @id=$role2Det1 - {name=(String@role-det-1), active=(Boolean@true), perms=[(RefID@$permID1), (RefID@$permID2)], @parent=(RefID@$roleID2)}")
      check("FcCreate(RoleDetail) @id=$role2Det2 - {name=(String@role-det-2), active=(Boolean@false), perms=[(RefID@$permID3), (RefID@$permID4)], @parent=(RefID@$roleID2)}")

      check("FcCreate(RoleDetail) @id=$role1AddDet - {@parent=(RefID@$roleID1), name=(String@role-det-5), active=(Boolean@true), perms=[(RefID@$permID1), (RefID@$permID4)]}")
      check("FcUpdate(RoleDetail) @id=$role1AddDet - {perms=[(RefLink@(@add -> $permID2)), (RefLink@(@add -> $permID3))]}")
      check("FcUpdate(RoleDetail) @id=$role1AddDet - {perms=[(RefLink@(@del -> $permID2)), (RefLink@(@del -> $permID3))]}")
    }
  }

  @Test fun testInvalidInputField() {
    db.tx {
      assertFails("Check constraint failed for TextInvalid::(text@aText)") {
        create("""TextInvalid {
          aText: "newNo"
        }""")
      }
    }
  }
}