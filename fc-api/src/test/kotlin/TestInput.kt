package fc.test

import fc.api.FcDatabase
import fc.api.FcSchema
import fc.api.spi.InputInstructions
import org.junit.FixMethodOrder
import org.junit.Test

class TestInputAdaptor(override val schema: FcSchema) : TestAdaptor(schema) {
  val session = ThreadLocal<InputInstructions>()
  override fun execInput(instructions: InputInstructions) {
    session.set(instructions)
  }
}

@FixMethodOrder
class TestInput {
  val schema = createCorrectSchema()
  val adaptor = TestInputAdaptor(schema)
  val db = FcDatabase(adaptor)

  @Test fun testCreate() {
    db.tx {
      create("""Simple { aText: "newText", aInt: 10, aLong: 20, aFloat: 10.0, aDouble: 20.0, aBool: true, aTime: #15:10:30, aDate: #2020-10-25, aDateTime: #2020-10-25T15:10:30, aList: [1, "2"] }""")
    }

    adaptor.session.get().all.forEach(::println)
    assert(true)
  }
}