package fc.admin

import fc.adaptor.sql.SQLAdaptor
import fc.api.FcSchema
import fc.ws.FcServer
import java.time.LocalDateTime

fun main() {
  val schema = FcSchema {
    val Country = master("Country") {
      text("name")
      text("code")
    }

    val Role = master("Role") {
      text("name")
      datetime("createdAt") {
        deriveFrom { LocalDateTime.now() }
      }

      ownedCol("perms", detail("Permission"){
        text("name")
        text("script")
      })
    }

    master("User") {
      text("name")
      text("email") {
        checkIf { it.contains('@') }
      }

      linkedCol("roles", Role)

      ownedRef("address", detail("Address") {
        text("city")
        text("local")
        linkedRef("country", Country)
      })
    }
  }

  val adaptor = SQLAdaptor("jdbc:h2:mem:AdminTest").also {
    it.createSchema(schema)
  }

  FcServer(adaptor).start(9090)
}
