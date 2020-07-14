package fc.adaptor.test

import fc.adaptor.sql.SQLAdaptor
import fc.api.FcDatabase
import org.junit.Test

private val adaptor = SQLAdaptor("jdbc:h2:mem:QueryTest", sqlListener).also {
  it.createSchema(schema)
}

private val db = FcDatabase(adaptor).also {
  init()
  it.tx {
    val portugalID = create("""Country {
        name: "Portugal",
        code: "PT"
      }""")

    val spainID = create("""Country {
        name: "Spain",
        code: "ES"
      }""")

    create("""User {
        name: "Pedro",
        address: {
          city: "Aveiro",
          country: ?country
        }
      }""", "country" to portugalID)

    create("""User {
        name: "Alex",
        address: {
          city: "Barcelona",
          country: ?country
        }
      }""", "country" to spainID)
  }
}

class QueryTest {
  @Test fun testSimpleQuery() {
    val query = db.query("""User | name == ?name | {
      *
    }""")

    val res = query.exec("name" to "Alex")
    assert(res.rows.toString() == "[{@id=2, name=Alex}]")
  }

  @Test fun testOwnedReferences() {
    val query = db.query("""User limit 1 {
      name,
      address { * }
    }""")

    val res = query.exec()
    assert(res.rows.toString() == "[{@id=1, name=Pedro, address={@id=1, city=Aveiro}}]")
  }

  @Test fun testParentReference() {
    val query = db.query("""Address {
      *,
      @parent { * }
    }""")

    val res = query.exec()
    assert(res.rows.toString() == "[{@id=1, city=Aveiro, @parent={@id=1, name=Pedro}}, {@id=2, city=Barcelona, @parent={@id=2, name=Alex}}]")
  }

  @Test fun testLinkedReferences() {
    val query = db.query("""Address {
      *,
      country { * }
    }""")

    val res = query.exec()
    println(res.rows.toString())
    assert(res.rows.toString() == "[{@id=1, city=Aveiro, country={@id=1, name=Portugal, code=PT}}, {@id=2, city=Barcelona, country={@id=2, name=Spain, code=ES}}]")
  }
}