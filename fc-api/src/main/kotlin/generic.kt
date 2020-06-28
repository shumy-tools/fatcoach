package fc.api

const val ID = "@id"
const val PARENT = "@parent"

enum class OType {
  ADD, REMOVE
}

class RefID {
  var id: Long? = null
    internal set

  override fun toString() = "RefID(id=$id)"
}

data class RefLink(val oper: OType, val refID: RefID)