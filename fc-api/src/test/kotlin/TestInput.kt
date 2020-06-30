package fc.test

import fc.api.FcDatabase
import fc.api.FcSchema
import fc.api.RefID
import fc.api.spi.InputInstructions
import org.junit.FixMethodOrder
import org.junit.Test

class Checker(private val instructions: InputInstructions) {
  private var next = 0
  fun check(value: String) {
    assert(instructions.all[next].toString() == value)
    next += 1
  }
}

class TestInputAdaptor(override val schema: FcSchema) : TestAdaptor(schema) {
  private val session = ThreadLocal<InputInstructions>()
  override fun execInput(instructions: InputInstructions) {
    session.set(instructions)
  }

  fun checker(scope: Checker.() -> Unit) = Checker(session.get()).scope()
  fun print() = session.get().all.forEach(::println)
}

@FixMethodOrder
class TestInput {
  private val schema = createCorrectSchema()
  private val adaptor = TestInputAdaptor(schema)
  private val db = FcDatabase(adaptor)

  @Test fun testSimple() {
    var id: RefID? = null
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
        aList: [1, "2"] }
      """)

      update("""Simple @id = ?id {
        aText: "updatedText",
        aInt: 100,
        aLong: 200,
        aFloat: 100.0,
        aDouble: 200.0,
        aBool: false,
        aTime: #13:10:30,
        aDate: #2019-10-25,
        aDateTime: #2019-10-25T13:10:30,
        aList: [10, "20"] } } """, "id" to id!!)
    }

    adaptor.checker {
      check("FcInsert(Simple) @id=$id - {aText=(String@newText), aInt=(Int@10), aLong=(Long@20), aFloat=(Float@10.0), aDouble=(Double@20.0), aBool=(Boolean@true), aTime=(LocalTime@15:10:30), aDate=(LocalDate@2020-10-25), aDateTime=(LocalDateTime@2020-10-25T15:10:30), aList=[(Long@1), (String@2)]}")
      check("FcUpdate(Simple) @id=$id - {aText=(String@updatedText), aInt=(Int@100), aLong=(Long@200), aFloat=(Float@100.0), aDouble=(Double@200.0), aBool=(Boolean@false), aTime=(LocalTime@13:10:30), aDate=(LocalDate@2019-10-25), aDateTime=(LocalDateTime@2019-10-25T13:10:30), aList=[(Long@10), (String@20)]}")
    }
  }

  @Test fun testUser() {
    var countryID: RefID? = null
    var userID: RefID? = null
    db.tx {
      countryID = create("""Country {
        name: "Portugal",
        code: "PT"
      }""")

      userID = create("""User {
        name: "Alex",
        address: {
          city: "Aveiro",
          country: ?country
        }
      }""", "country" to countryID!!)
    }

    adaptor.print()
    adaptor.checker {
      check("FcInsert(Country) @id=$countryID - {name=(String@Portugal), code=(String@PT)}")
      check("FcInsert(Address) @id=RefID(786712380) - {city=(String@Aveiro), country=(RefID@$countryID), @parent=(RefID@RefID(1926240119))}")
      check("FcInsert(User) @id=$userID - {name=(String@Alex), address=(RefID@RefID(786712380))}")
    }
  }
}