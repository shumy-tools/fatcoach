package fc.adaptor.sql

import com.google.gson.*
import com.google.gson.stream.JsonReader
import com.google.gson.stream.JsonWriter
import fc.api.FType
import fc.api.SField
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.format.DateTimeFormatter

private class LocalDateTypeAdapter : TypeAdapter<LocalDate>() {
  override fun write(out: JsonWriter, value: LocalDate) {
    out.value(DateTimeFormatter.ISO_LOCAL_DATE.format(value))
  }

  override fun read(input: JsonReader): LocalDate = LocalDate.parse(input.nextString())
}

private class LocalTimeTypeAdapter : TypeAdapter<LocalTime>() {
  override fun write(out: JsonWriter, value: LocalTime) {
    out.value(DateTimeFormatter.ISO_LOCAL_TIME.format(value))
  }

  override fun read(input: JsonReader): LocalTime = LocalTime.parse(input.nextString())
}

private class LocalDateTimeTypeAdapter : TypeAdapter<LocalDateTime>() {
  override fun write(out: JsonWriter, value: LocalDateTime) {
    out.value(DateTimeFormatter.ISO_LOCAL_DATE_TIME.format(value))
  }

  override fun read(input: JsonReader): LocalDateTime = LocalDateTime.parse(input.nextString())
}

object SQLFieldConverter {
  private val gson = GsonBuilder()
    .registerTypeAdapter(LocalDate::class.java, LocalDateTypeAdapter().nullSafe())
    .registerTypeAdapter(LocalTime::class.java, LocalTimeTypeAdapter().nullSafe())
    .registerTypeAdapter(LocalDateTime::class.java, LocalDateTimeTypeAdapter().nullSafe())
    .enableComplexMapKeySerialization()
    .create()

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