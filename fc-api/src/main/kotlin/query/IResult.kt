package fc.api.query

typealias FcData = Map<String, Any?>

interface IRowGet {
  fun <R: Any> get(name: String): R?
}

interface IResult: IRowGet, Iterable<IRowGet> {
  val rows: List<FcData>
  fun isEmpty(): Boolean = rows.isEmpty()
}