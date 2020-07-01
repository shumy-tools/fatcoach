package fc.api.input

import fc.api.*

fun Map<String, Any>.getRefID(key: String): RefID {
  val value = this[key] ?: throw Exception("Expecting an argument value for '$key'.")
  return when (value) {
    is RefID -> value
    is RefTree -> value.root
    else -> throw Exception("Expecting an argument typeOf RefID or RefTree for '$key'.")
  }
}

fun Map<String, Any>.getRefIDList(key: String): List<RefID> {
  val value = this[key] ?: throw Exception("Expecting an argument value for '$key'.")
  if (value !is List<*>)
    throw Exception("Expecting a collection from arg '$key'.")

  return value.map {
    when (it) {
      is RefID -> it
      is RefTree -> it.root
      else -> throw Exception("Expecting an argument typeOf List<RefID or RefTree> for '$key'.")
    }
  }
}