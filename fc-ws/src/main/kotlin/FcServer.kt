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
    val all = adaptor.schema.map()
    val masters = adaptor.schema.masters.keys

    /*val packages = linkedMapOf<String, Any>()
    adaptor.schema.all.keys.forEach {
      packages.addToPackage(it)
    }*/

    val schema = mapOf("all" to all, "masters" to masters)
    mapOf("@type" to "ok").plus("result" to schema)
  }

  private fun create(ctx: Context) = handle(ctx) {
    val req = ctx.bodyAsClass(CodeRequest::class.java)
    // TODO: convert params

    // TODO: check access control
    var tree: RefTree? = null
    val tx = db.tx {
      tree = create(req.code)
    }

    // TODO: report tx instructions to all subscribers?
    mapOf("@type" to "ok").plus("result" to tree!!)
  }

  private fun update(ctx: Context) = handle(ctx) {
    val req = ctx.bodyAsClass(CodeRequest::class.java)
    // TODO: convert params

    // TODO: check access control
    val tx = db.tx { update(req.code) }

    // TODO: report tx instructions to all subscribers?
    mapOf("@type" to "ok")
  }

  private fun delete(ctx: Context) = handle(ctx) {
    val req = ctx.bodyAsClass(CodeRequest::class.java)
    // TODO: convert params

    // TODO: check access control
    val tx = db.tx { delete(req.code) }

    // TODO: report tx instructions to all subscribers?
    mapOf("@type" to "ok")
  }

  private fun query(ctx: Context) = handle(ctx) {
    val req = ctx.bodyAsClass(CodeRequest::class.java)

    // use compiled cache if exists
    val hash = req.code.hash()
    val query = cache.getOrPut(hash) { db.query(req.code) }

    // check and convert input parameters
    val params = if (req.args.isNotEmpty()) {
      query.parameters.map {
        val qParamValue = req.args[it.name] ?: throw Exception("Expecting input parameter $it.")
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
data class CodeRequest(val code: String, val args: Map<String, String>)

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

fun FcSchema.map(): Map<String, Any> = all.values.map { it.name to mapOf(
  "name" to it.name,
  "type" to it.type.name.toLowerCase(),
  "fields" to it.mapFields(),
  "refs" to it.mapRefs(),
  "cols" to it.mapCols()
) }.toMap()

fun SEntity.mapFields(): Map<String, Any> = fields.values.map {
  it.name to mapOf(
    "name" to it.name,
    "type" to it.type.name.toLowerCase(),
    "optional" to it.optional,
    "input" to it.input,
    "unique" to it.unique
  )
}.toMap()

fun SEntity.mapRefs(): Map<String, Any> = refs.map {
  it.name to mapOf(
    "name" to it.name,
    "type" to it.type.name.toLowerCase(),
    "ref" to it.ref.name,
    "optional" to it.optional,
    "input" to it.input
  )
}.toMap()

fun SEntity.mapCols(): Map<String, Any> = cols.map {
  it.name to mapOf(
    "name" to it.name,
    "type" to it.type.name.toLowerCase(),
    "ref" to it.ref.name,
    "input" to it.input
  )
}.toMap()