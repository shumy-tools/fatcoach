package fc.test

import fc.api.FcData
import fc.api.FcDatabase
import fc.api.FcSchema
import fc.api.SProperty
import fc.api.query.IResult
import fc.api.query.IRowGet
import fc.api.query.QTree
import fc.api.spi.IAuthorizer
import fc.api.input.FcInstruction
import org.junit.Test

private class EmptyResult(override val rows: List<FcData> = emptyList()) : IResult {
  override fun <R : Any> get(name: String): R? = throw Exception("Not used!")
  override fun iterator(): Iterator<IRowGet> = throw Exception("Not used!")
}

private class QuerySecurity: IAuthorizer {
  private val session = ThreadLocal<Set<SProperty>>()

  override fun canInput(instruction: FcInstruction) { throw Exception("Not used!") }
  override fun canQuery(props: Set<SProperty>) = session.set(props)

  fun check(props: String) = assert(session.get().text() == props)
  fun print() = println("Access: ${session.get().text()}")
  private fun Set<SProperty>.text() = map{"${it.entity!!.name}::${it.simpleString()}"}.toString()
}

private class TestQueryAdaptor(override val schema: FcSchema) : TestAdaptor(schema) {
  private val sessionTree = ThreadLocal<QTree>()
  private val sessionArgs = ThreadLocal<Map<String, Any>>()

  override fun execQuery(query: QTree, args: Map<String, Any>): IResult {
    sessionTree.set(query)
    sessionArgs.set(args)
    return EmptyResult()
  }

  fun check(query: String, vararg args: Pair<String, Any>) {
    val tree = sessionTree.get()
    val sArgs = sessionArgs.get()
    assert(tree.toString() == query)
    assert(sArgs == args.toMap())
  }

  fun print() {
    val tree = sessionTree.get()
    println("Args: ${sessionArgs.get()}" )
    println(tree)
  }
}

class TestQuery {
  private val schema = createCorrectSchema()
  private val adaptor = TestQueryAdaptor(schema)
  private val security = QuerySecurity()
  private val db = FcDatabase(adaptor, security)

  @Test fun testSimpleQuery() {
    db.query("""Simple | @id == ?id | { * }""").exec("id" to 10L)
    security.check("[Simple::(long@@id), Simple::(text@aText), Simple::(int@aInt), Simple::(long@aLong), Simple::(float@aFloat), Simple::(double@aDouble), Simple::(bool@aBool), Simple::(time@aTime), Simple::(date@aDate), Simple::(datetime@aDateTime), Simple::(list@aList), Simple::(map@aMap)]")
    adaptor.check("""QTree(Simple) | Simple::[(long@@id)] equal ?(long@id) | {
    |  (none 0) (text@aText)
    |  (none 0) (int@aInt)
    |  (none 0) (long@aLong)
    |  (none 0) (float@aFloat)
    |  (none 0) (double@aDouble)
    |  (none 0) (bool@aBool)
    |  (none 0) (time@aTime)
    |  (none 0) (date@aDate)
    |  (none 0) (datetime@aDateTime)
    |  (none 0) (list@aList)
    |  (none 0) (map@aMap)
    |}""".trimMargin(), "id" to 10L)

    db.query("""Simple | @id == 10 | limit 10 page 1 { (asc 1) aText, (dsc 2) aFloat, aDate } """).exec()
    security.check("[Simple::(long@@id), Simple::(text@aText), Simple::(float@aFloat), Simple::(date@aDate)]")
    adaptor.check("""QTree(Simple) | Simple::[(long@@id)] equal (long@10) | limit 10 page 1 {
    |  (asc 1) (text@aText)
    |  (dsc 2) (float@aFloat)
    |  (none 0) (date@aDate)
    |}""".trimMargin())
  }

  @Test fun testReferences() {
    db.query("""User | name == "Alex" | {
      name,
      address {
        (asc 1) city,
        country { * }
      }
    }""").exec()
    security.check("[User::(long@@id), User::(text@name), User::(Address@address), Address::(long@@id), Address::(text@city), Address::(Country@country), Country::(long@@id), Country::(text@name), Country::(text@code)]")
    adaptor.check("""QTree(User) | User::[(text@name)] equal (text@Alex) | {
    |  (none 0) (text@name)
    |  User::address -> Address {
    |    (asc 1) (text@city)
    |    Address::country -> Country {
    |      (none 0) (text@name)
    |      (none 0) (text@code)
    |    }
    |  }
    |}""".trimMargin())

    db.query("""User | name == "Alex" and (address.@id == ?id or address.country.name == "Portugal") | {
      name,
      address { * }
    }""").exec("id" to 10L)
    security.check("[User::(long@@id), User::(text@name), User::(Address@address), Address::(long@@id), Address::(text@city), Address::(Country@country), Country::(text@name)]")
    adaptor.check("""QTree(User) | User::[(text@name)] equal (text@Alex) and (User::[(Address@address), (long@@id)] equal ?(long@id) or User::[(Address@address), (Country@country), (text@name)] equal (text@Portugal)) | {
    |  (none 0) (text@name)
    |  User::address -> Address {
    |    (none 0) (text@city)
    |  }
    |}""".trimMargin(),"id" to 10L)

    db.query("""Address | country.name == "Portugal" | {
      city,
      @parent { * }
    }""").exec()

    security.check("[Address::(long@@id), Address::(text@city), Address::(User@@parent), User::(long@@id), User::(text@name), Address::(Country@country), Country::(text@name)]")
    adaptor.check("""QTree(Address) | Address::[(Country@country), (text@name)] equal (text@Portugal) | {
    |  (none 0) (text@city)
    |  Address::@parent -> User {
    |    (none 0) (text@name)
    |  }
    |}""".trimMargin())
  }

  @Test fun testCollections() {
    db.query("""Role | name == "Admin" or details.name == "AdminProxy" | {
      @id,
      details {
        *,
        perms | name == "Table1" | {
          (dsc 1) url
        }
      }
    }""").exec()
    security.check("[Role::(long@@id), Role::(RoleDetail@details), RoleDetail::(long@@id), RoleDetail::(text@name), RoleDetail::(bool@active), RoleDetail::(Permission@perms), Permission::(long@@id), Permission::(text@url), Permission::(text@name), Role::(text@name)]")
    adaptor.check("""QTree(Role) | Role::[(text@name)] equal (text@Admin) or Role::[(RoleDetail@details), (text@name)] equal (text@AdminProxy) | {
    |  (none 0) (long@@id)
    |  Role::details -< RoleDetail {
    |    (none 0) (text@name)
    |    (none 0) (bool@active)
    |    RoleDetail::perms -< Permission | Permission::[(text@name)] equal (text@Table1) | {
    |      (dsc 1) (text@url)
    |    }
    |  }
    |}""".trimMargin())

    db.query("""RoleDetail limit 10 {
      @id, (dsc 1) active,
      @parent { * }
    }""").exec()
    security.check("[RoleDetail::(long@@id), RoleDetail::(bool@active), RoleDetail::(Role@@parent), Role::(long@@id), Role::(text@name)]")
    adaptor.check("""QTree(RoleDetail) limit 10 {
    |  (none 0) (long@@id)
    |  (dsc 1) (bool@active)
    |  RoleDetail::@parent -> Role {
    |    (none 0) (text@name)
    |  }
    |}""".trimMargin())
  }
}