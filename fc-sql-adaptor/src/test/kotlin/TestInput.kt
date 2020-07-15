package fc.adaptor.test

import fc.adaptor.sql.SQLAdaptor
import fc.api.FcDatabase
import fc.api.RefTree
import org.junit.Test

private val adaptor = SQLAdaptor("jdbc:h2:mem:InputTest", sqlListener).also {
  it.createSchema(schema)
}

class TestInput {
  private val db = FcDatabase(adaptor)

  @Test fun testFieldTypes() {
    var id: RefTree? = null
    var complexID1: RefTree? = null
    var complexID2: RefTree? = null

    init()
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

    check(0, """insert into Simple ("aText", "aInt", "aLong", "aFloat", "aDouble", "aBool", "aTime", "aDate", "aDateTime", "aList", "aMap") values (cast(? as varchar), cast(? as int), cast(? as bigint), cast(? as real), cast(? as double), cast(? as boolean), cast(? as time), cast(? as date), cast(? as timestamp), cast(? as varchar), cast(? as varchar)); {"aText"=newText, "aInt"=10, "aLong"=20, "aFloat"=10.0, "aDouble"=20.0, "aBool"=true, "aTime"=15:10:30, "aDate"=2020-10-25, "aDateTime"=2020-10-25T15:10:30, "aList"=[1,"2"], "aMap"={"one":1,"two":2}} - @id=${id!!.root.id}""")
    check(1, """update Simple set "aText" = cast(? as varchar), "aInt" = cast(? as int), "aLong" = cast(? as bigint), "aFloat" = cast(? as real), "aDouble" = cast(? as double), "aBool" = cast(? as boolean), "aTime" = cast(? as time), "aDate" = cast(? as date), "aDateTime" = cast(? as timestamp), "aList" = cast(? as varchar), "aMap" = cast(? as varchar) where "@id" = cast(? as bigint); {"aText"=updatedText, "aInt"=100, "aLong"=200, "aFloat"=100.0, "aDouble"=200.0, "aBool"=false, "aTime"=13:10:30, "aDate"=2019-10-25, "aDateTime"=2019-10-25T13:10:30, "aList"=[10,"20"], "aMap"={"one-u":10,"two-u":20}} - @id=${id!!.root.id}""")
    check(2, """insert into ComplexJSON ("aList", "aMap") values (cast(? as varchar), cast(? as varchar)); {"aList"=[1,1.2,false,"15:10:30","2020-10-25","2020-10-25T15:10:30",{"one":1,"two":2}], "aMap"={}} - @id=${complexID1!!.root.id}""")
    check(3, """insert into ComplexJSON ("aList", "aMap") values (cast(? as varchar), cast(? as varchar)); {"aList"=[], "aMap"={"long":1,"double":1.2,"bool":false,"time":"15:10:30","date":"2020-10-25","dt":"2020-10-25T15:10:30","list":[1,2]}} - @id=${complexID2!!.root.id}""")
  }

  @Test fun testReferences() {
    var portugalID: RefTree? = null
    var spainID: RefTree? = null
    var userID: RefTree? = null
    var addressID: RefTree? = null

    init()
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
        email: null,
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

      // expected " Referential integrity constraint violation"
      // delete("Country @id == ?id", portugalID!!)
    }

    check(0,"""insert into Country ("name", "code") values (cast(? as varchar), cast(? as varchar)); {"name"=Portugal, "code"=PT} - @id=${portugalID!!.root.id}""")
    check(1,"""insert into Country ("name", "code") values (cast(? as varchar), cast(? as varchar)); {"name"=Spain, "code"=ES} - @id=${spainID!!.root.id}""")
    check(2,"""insert into User ("name", "email") values (cast(? as varchar), cast(? as varchar)); {"name"=Alex, "email"=null} - @id=${userID!!.root.id}""")
    check(3,"""insert into Address ("city", "@parent") values (cast(? as varchar), cast(? as bigint)); {"city"=Aveiro, "@parent"=${userID!!.root.id}} - @id=${addressID!!.root.id}""")
    check(4,"""insert into Address_country ("@inv", "@ref") values (cast(? as bigint), cast(? as bigint)); [(${addressID!!.root.id}, ${portugalID!!.root.id})]""")
    check(5,"""update Address set "city" = cast(? as varchar) where "@id" = cast(? as bigint); {"city"=Barcelona} - @id=${addressID!!.root.id}""")
    check(6,"""insert into Address_country ("@inv", "@ref") values (cast(? as bigint), cast(? as bigint)); [(${addressID!!.root.id}, ${spainID!!.root.id})]""")
    check(7,"""update Address set "city" = cast(? as varchar) where "@id" = cast(? as bigint); {"city"=None} - @id=${addressID!!.root.id}""")
    check(8,"""delete from Address_country where ("@inv" = cast(? as bigint) and "@ref" in (cast(? as bigint))); - [${addressID!!.root.id}, [${spainID!!.root.id}]]""")
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

    init()
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

    check(0, """insert into Permission ("name", "url") values (cast(? as varchar), cast(? as varchar)); {"name"=perm-1, "url"=http://url-1} - @id=${permID1!!.root.id}""")
    check(1, """insert into Permission ("name", "url") values (cast(? as varchar), cast(? as varchar)); {"name"=perm-2, "url"=http://url-2} - @id=${permID2!!.root.id}""")
    check(2, """insert into Permission ("name", "url") values (cast(? as varchar), cast(? as varchar)); {"name"=perm-3, "url"=http://url-3} - @id=${permID3!!.root.id}""")
    check(3, """insert into Permission ("name", "url") values (cast(? as varchar), cast(? as varchar)); {"name"=perm-4, "url"=http://url-4} - @id=${permID4!!.root.id}""")
    check(4, """insert into Role ("name") values (cast(? as varchar)); {"name"=role-name} - @id=${roleID1!!.root.id}""")
    check(5, """insert into RoleDetail ("name", "active", "@parent") values (cast(? as varchar), cast(? as boolean), cast(? as bigint)); {"name"=role-det-1, "active"=true, "@parent"=${roleID1!!.root.id}} - @id=${role1Det1!!.root.id}""")
    check(6, """insert into RoleDetail_perms ("@inv", "@ref") values (cast(? as bigint), cast(? as bigint)), (cast(? as bigint), cast(? as bigint)); [(${role1Det1!!.root.id}, ${permID1!!.root.id}), (${role1Det1!!.root.id}, ${permID2!!.root.id})]""")
    check(7, """insert into RoleDetail ("name", "active", "@parent") values (cast(? as varchar), cast(? as boolean), cast(? as bigint)); {"name"=role-det-2, "active"=false, "@parent"=${roleID1!!.root.id}} - @id=${role1Det2!!.root.id}""")
    check(8, """insert into RoleDetail_perms ("@inv", "@ref") values (cast(? as bigint), cast(? as bigint)), (cast(? as bigint), cast(? as bigint)); [(${role1Det2!!.root.id}, ${permID3!!.root.id}), (${role1Det2!!.root.id}, ${permID4!!.root.id})]""")

    check(9, """insert into Role ("name") values (cast(? as varchar)); {"name"=role-name} - @id=${roleID2!!.root.id}""")
    check(10, """insert into RoleDetail ("name", "active", "@parent") values (cast(? as varchar), cast(? as boolean), cast(? as bigint)); {"name"=role-det-1, "active"=true, "@parent"=${roleID2!!.root.id}} - @id=${role2Det1!!.root.id}""")
    check(11, """insert into RoleDetail_perms ("@inv", "@ref") values (cast(? as bigint), cast(? as bigint)), (cast(? as bigint), cast(? as bigint)); [(${role2Det1!!.root.id}, ${permID1!!.root.id}), (${role2Det1!!.root.id}, ${permID2!!.root.id})]""")

    check(12, """insert into RoleDetail ("name", "active", "@parent") values (cast(? as varchar), cast(? as boolean), cast(? as bigint)); {"name"=role-det-2, "active"=false, "@parent"=${roleID2!!.root.id}} - @id=${role2Det2!!.root.id}""")
    check(13, """insert into RoleDetail_perms ("@inv", "@ref") values (cast(? as bigint), cast(? as bigint)), (cast(? as bigint), cast(? as bigint)); [(${role2Det2!!.root.id}, ${permID3!!.root.id}), (${role2Det2!!.root.id}, ${permID4!!.root.id})]""")

    check(14, """insert into RoleDetail ("@parent", "name", "active") values (cast(? as bigint), cast(? as varchar), cast(? as boolean)); {"@parent"=${roleID1!!.root.id}, "name"=role-det-5, "active"=true} - @id=${role1AddDet!!.root.id}""")
    check(15, """insert into RoleDetail_perms ("@inv", "@ref") values (cast(? as bigint), cast(? as bigint)), (cast(? as bigint), cast(? as bigint)); [(${role1AddDet!!.root.id}, ${permID1!!.root.id}), (${role1AddDet!!.root.id}, ${permID4!!.root.id})]""")
    check(16, """insert into RoleDetail_perms ("@inv", "@ref") values (cast(? as bigint), cast(? as bigint)), (cast(? as bigint), cast(? as bigint)); [(${role1AddDet!!.root.id}, ${permID2!!.root.id}), (${role1AddDet!!.root.id}, ${permID3!!.root.id})]""")
    check(17, """delete from RoleDetail_perms where ("@inv" = cast(? as bigint) and "@ref" in (cast(? as bigint), cast(? as bigint))); - [${role1AddDet!!.root.id}, [${permID2!!.root.id}, ${permID3!!.root.id}]]""")
  }
}