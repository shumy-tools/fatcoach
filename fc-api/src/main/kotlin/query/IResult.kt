package fc.api.query

import fc.api.FcData

interface IRowGet {
  fun <R: Any> get(name: String): R?
}

interface IResult: IRowGet, Iterable<IRowGet> {
  val rows: List<FcData>
  fun isEmpty(): Boolean = rows.isEmpty()
}