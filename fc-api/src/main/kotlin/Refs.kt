package fc.api

class RefID(id: Long? = null) {
  var id: Long? = id

  override fun toString() = hashCode().toString()
}

class RefLink(val oper: OType, val refID: RefID) {
  override fun toString() = "(@${oper.name.toLowerCase()} -> $refID)"
}

class RefTree {
  val root = RefID()

  private val refs = linkedMapOf<String, RefTree>()
  private val cols = linkedMapOf<String, MutableList<RefTree>>()

  fun find(path: String): RefTree? {
    val splits = path.split(".")
    var tree: RefTree? = this
    for (level in splits) {
      if (tree == null) return null
      tree = tree.refs[level]
    }

    return tree!!
  }

  fun find(path: String, index: Int): RefTree? {
    val splits = path.split(".")
    var tree: RefTree? = this
    for (level in splits.dropLast(1)) {
      if (tree == null) return null
      tree = tree.refs[level]
    }

    val last = tree!!.cols[splits.last()] ?: return null
    return last[index]
  }

  internal fun pushRef(prop: String): RefTree {
    val ref = RefTree()
    refs[prop] = ref
    return ref
  }

  internal fun pushCol(prop: String): RefTree {
    val ref = RefTree()
    val list = cols.getOrPut(prop) { mutableListOf() }
    list.add(ref)
    return ref
  }

  override fun toString() = root.toString()
}