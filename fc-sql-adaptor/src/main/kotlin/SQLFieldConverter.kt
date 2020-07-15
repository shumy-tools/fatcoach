package fc.adaptor.sql

import com.google.gson.GsonBuilder
import fc.api.FType
import fc.api.SField

object SQLFieldConverter {
  val gson = GsonBuilder().enableComplexMapKeySerialization().create()

  fun save(prop: SField, value: Any?): Any? = when (prop.type) {
    FType.LIST -> gson.toJson(value)
    FType.MAP -> gson.toJson(value)
    else -> value
  }

  fun load(prop: SField, value: Any): Any = when (prop.type) {
    FType.LIST -> gson.fromJson(value.parse(), List::class.java)
    FType.MAP -> gson.fromJson(value.parse(), Map::class.java)
    else -> value
  }

  private fun Any.parse(): String {
    val res = (this as String)
    return if (res.startsWith("\"")) res.substring(1, res.length - 1) else res
  }
}