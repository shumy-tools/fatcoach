package fc.api

const val SID = "@id"
const val SPARENT = "@parent"

enum class OType { ADD, DEL }

typealias FcData = Map<String, Any?>