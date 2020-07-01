package fc.api

const val ID = "@id"
const val PARENT = "@parent"

enum class OType { ADD, DEL }

typealias FcData = Map<String, Any?>