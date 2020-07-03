package fc.ws

import fc.api.*
import fc.api.base.User
import fc.api.query.Query
import fc.api.spi.IAdaptor
import fc.api.spi.IAuthorizer
import fc.api.spi.UnauthorizedException
import io.javalin.Javalin
import io.javalin.apibuilder.ApiBuilder.*
import io.javalin.http.Context
import java.security.MessageDigest
import java.util.*
import java.util.concurrent.ConcurrentHashMap

class FcServer(val adaptor: IAdaptor, val authorizer: IAuthorizer? = null) {
  private val cache = ConcurrentHashMap<String, Query>()
  private val db = FcDatabase(adaptor, authorizer)

  fun start(port: Int) {
    val app = Javalin.create().start(port)

    app.get("/") { ctx -> ctx.result("Hello FcServer") }

    app.before("/api/*") { ctx ->
      // TODO: authenticate and set the correct user
      FcContext.user = User("admin", "admin@mail.com", listOf())
    }

    app.before("/api/*") { FcContext.clear() }

    app.routes {
      path("api") {
        get("schema", this::schema)
        //post("check/:entity", this::check)

        post("create", this::create)
        post("update", this::update)
        post("delete", this::delete)
        post("query", this::query)
      }
    }
  }

  private fun schema(ctx: Context) = handle(ctx) {
    val schema = adaptor.schema.map()
    mapOf("@type" to "ok").plus("result" to schema)
  }

  private fun create(ctx: Context) = handle(ctx) {
    // TODO: check access control
    var tree: RefTree? = null
    val tx = db.tx {
      tree = create(ctx.body())
    }

    // TODO: report tx instructions to all subscribers?
    mapOf("@type" to "ok").plus("result" to tree!!)
  }

  private fun update(ctx: Context) = handle(ctx) {
    // TODO: check access control
    val tx = db.tx { update(ctx.body()) }

    // TODO: report tx instructions to all subscribers?
    mapOf("@type" to "ok")
  }

  private fun delete(ctx: Context) = handle(ctx) {
    // TODO: check access control
    val tx = db.tx { delete(ctx.body()) }

    // TODO: report tx instructions to all subscribers?
    mapOf("@type" to "ok")
  }

  private fun query(ctx: Context) = handle(ctx) {
    // use compiled cache if exists
    val hash = ctx.body().hash()
    val query = cache.getOrPut(hash) { db.query(ctx.body()) }

    // check and convert input parameters
    val params = if (query.parameters.isNotEmpty()) {
      val qParams = ctx.queryParamMap()
      query.parameters.map {
        val qParamValue = qParams[it.name]?.firstOrNull() ?: throw Exception("Expecting input parameter $it.")
        val value = TypeEngine.tryConvertParam(it.type, qParamValue)
        it.name to value
      }.toMap()
    } else emptyMap()

    // execute and send results
    val res = query.exec(params)
    mutableMapOf<String, Any>("@type" to "ok").also { it["result"] = res.rows }
  }
}

/* ------------------------- helpers -------------------------*/
private fun handle(ctx: Context, handler: (Context) -> Map<String, Any>) {
  val res = try {
    handler(ctx)
  } catch (ex: UnauthorizedException) {
    ctx.status(401).json("Unauthorized: ${ex.message}")
    return
  } catch (ex: Exception) {
    ex.printStackTrace()
    val message = (if (ex.cause != null) ex.cause!!.message else ex.message) ?: "Unrecognized error!"
    mapOf("@type" to "error", "msg" to message.replace('"', '\''))
  }

  ctx.json(res)
}

private fun String.hash(): String {
  // TODO: should not replace spaces inside strings !!
  val compact = replace(Regex("\\s"), "")
  val digest = MessageDigest.getInstance("SHA-256")
  val bytes = digest.digest(compact.toByteArray())
  return Base64.getUrlEncoder().encodeToString(bytes)
}