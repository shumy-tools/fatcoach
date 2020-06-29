package fc.api

const val ID = "@id"
const val PARENT = "@parent"

typealias FcData = Map<String, Any?>

enum class OType { ADD, DEL }

class RefID(id: Long? = null) {
  var id: Long? = id
    internal set

  override fun toString() = "RefID(id=$id)"
}

data class RefLink(val oper: OType, val refID: RefID)