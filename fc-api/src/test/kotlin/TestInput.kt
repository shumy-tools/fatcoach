package fc.test

import fc.api.FcDatabase
import fc.api.FcSchema
import fc.api.RefID
import fc.api.RefTree
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

  @Test fun testFieldTypes() {
    var id: RefTree? = null
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
        aList: [1, "2"]
      }""")

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
        aList: [10, "20"]
      }""", "id" to id!!)
    }

    adaptor.checker {
      check("FcInsert(Simple) @id=$id - {aText=(String@newText), aInt=(Int@10), aLong=(Long@20), aFloat=(Float@10.0), aDouble=(Double@20.0), aBool=(Boolean@true), aTime=(LocalTime@15:10:30), aDate=(LocalDate@2020-10-25), aDateTime=(LocalDateTime@2020-10-25T15:10:30), aList=[(Long@1), (String@2)]}")
      check("FcUpdate(Simple) @id=$id - {aText=(String@updatedText), aInt=(Int@100), aLong=(Long@200), aFloat=(Float@100.0), aDouble=(Double@200.0), aBool=(Boolean@false), aTime=(LocalTime@13:10:30), aDate=(LocalDate@2019-10-25), aDateTime=(LocalDateTime@2019-10-25T13:10:30), aList=[(Long@10), (String@20)]}")
    }
  }

  @Test fun testReferences() {
    var portugalID: RefTree? = null
    var spainID: RefTree? = null

    var userID: RefTree? = null
    var addressID: RefID? = null
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

      update("""Address @id = ?id {
        city: "Barcelona",
        country: @add ?country
      }""", "id" to addressID!!, "country" to spainID!!)

      update("""Address @id = ?id {
        city: "None",
        country: @del ?country
      }""", "id" to addressID!!, "country" to spainID!!)
    }

    adaptor.print()
    adaptor.checker {
      check("FcInsert(Country) @id=$portugalID - {name=(String@Portugal), code=(String@PT)}")
      check("FcInsert(Country) @id=$spainID - {name=(String@Spain), code=(String@ES)}")
      check("FcInsert(User) @id=$userID - {name=(String@Alex), address=(RefID@$addressID)}")
      check("FcInsert(Address) @id=$addressID - {city=(String@Aveiro), country=(RefID@$portugalID), @parent=(RefID@$userID)}")
      check("FcUpdate(Address) @id=$addressID - {city=(String@Barcelona), country=(RefLink@(@add -> $spainID))}")
      check("FcUpdate(Address) @id=$addressID - {city=(String@None), country=(RefLink@(@del -> $spainID))}")
    }
  }

  @Test fun testCollections() {

  }
}