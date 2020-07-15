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
    create("""Simple {
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
      email: "pedro@mail.com",
      address: {
        city: "Aveiro",
        country: ?country
      }
    }""", "country" to portugalID)

    create("""User {
      name: "Alex",
      email: "alex@mail.com",
      address: {
        city: "Barcelona",
        country: ?country
      }
    }""", "country" to spainID)


    val permID1 = create("""Permission {
      name: "perm-1",
      url: "http://url-1"
    }""")

    val permID2 = create("""Permission {
      name: "perm-2",
      url: "http://url-2"
    }""")

    val permID3 = create("""Permission {
      name: "perm-3",
      url: "http://url-3"
    }""")

    val permID4 = create("""Permission {
      name: "perm-4",
      url: "http://url-4"
    }""")

    create("""Role {
      name: "role-name-1",
      details: [
        { name: "role-det-11", active: true, perms: ?perms1 },
        { name: "role-det-12", active: false, perms: ?perms2 }
      ]
    }""","perms1" to listOf(permID1, permID2), "perms2" to listOf(permID3, permID4))

    create("""Role {
      name: "role-name-2",
      details: [
        { name: "role-det-21", active: true, perms: ?perms1 },
        { name: "role-det-22", active: false, perms: ?perms2 }
      ]
    }""","perms1" to listOf(permID4, permID3), "perms2" to listOf(permID2, permID1))
  }
}

class TestQuery {
  @Test fun testSimpleQuery() {
    val sQuery = db.query("""Simple | aText == "newText" | {
      *
    }""")

    val sRes = sQuery.exec()
    assert(sRes.rows.toString() == "[{@id=1, aText=newText, aInt=10, aLong=20, aFloat=10.0, aDouble=20.0, aBool=true, aTime=15:10:30, aDate=2020-10-25, aDateTime=2020-10-25T15:10:30, aList=[1.0, 2], aMap={one=1.0, two=2.0}}]")

    val query = db.query("""User | name == ?name and email == "alex@mail.com" | {
      *
    }""")

    val res = query.exec("name" to "Alex")
    assert(res.rows.toString() == "[{@id=2, name=Alex, email=alex@mail.com}]")
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
      @parent { name }
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
    assert(res.rows.toString() == "[{@id=1, city=Aveiro, country={@id=1, name=Portugal, code=PT}}, {@id=2, city=Barcelona, country={@id=2, name=Spain, code=ES}}]")
  }

  @Test fun testOwnedCollections() {
    val query = db.query("""Role | name == "role-name-2" | {
      *,
      details { * }
    }""")

    val res = query.exec()
    assert(res.rows.toString() == "[{@id=2, name=role-name-2, details=[{@id=3, name=role-det-21, active=true}, {@id=4, name=role-det-22, active=false}]}]")
  }

  @Test fun testLinkedCollections() {
    val query1 = db.query("""RoleDetail | name == "role-det-11" | {
      @id,
      perms { name }
    }""")

    val res1 = query1.exec()
    assert(res1.rows.toString() == "[{@id=1, perms=[{@id=1, name=perm-1}, {@id=2, name=perm-2}]}]")

    val query2 = db.query("""RoleDetail | name == "role-det-21" | {
      @id,
      perms { name }
    }""")

    val res2 = query2.exec()
    assert(res2.rows.toString() == "[{@id=3, perms=[{@id=3, name=perm-3}, {@id=4, name=perm-4}]}]")
  }

  @Test fun testFilteredReferences() {
    val query1 = db.query("""Address | country.name == "Portugal" | {
      *,
      country { * }
    }""")

    val res1 = query1.exec()
    assert(res1.rows.toString() == "[{@id=1, city=Aveiro, country={@id=1, name=Portugal, code=PT}}]")

    /*val query2 = db.query("""Address | country.name == "Portugal" | {
      city
    }""")

    val res2 = query2.exec()
    println(res2.rows.toString())
    assert(res2.rows.toString() == "[{@id=1, city=Aveiro}]")*/
  }
}