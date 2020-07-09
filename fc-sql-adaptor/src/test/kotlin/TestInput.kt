import fc.adaptor.sql.SQLAdaptor
import fc.api.FcDatabase
import fc.api.RefTree
import org.junit.Test

private val adaptor = SQLAdaptor("jdbc:h2:mem:InputTest").also {
  it.createSchema(schema)
}

class TestInput {
  private val db = FcDatabase(adaptor)

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
  }
}