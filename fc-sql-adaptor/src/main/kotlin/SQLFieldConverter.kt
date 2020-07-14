package fc.adaptor.sql

import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import fc.api.FType
import fc.api.SField

object SQLFieldConverter {
  private val mapper: ObjectMapper = jacksonObjectMapper()
    .registerModule(JavaTimeModule())
    .configure(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, false)
    .setSerializationInclusion(JsonInclude.Include.NON_NULL)

  fun save(prop: SField, value: Any?): Any? = when (prop.type) {
    FType.LIST -> mapper.writeValueAsString(value)
    FType.MAP -> mapper.writeValueAsString(value)
    else -> value
  }

  fun load(prop: SField, value: Any): Any = when (prop.type) {
    FType.LIST -> mapper.readValue((value as String), List::class.java)
    FType.MAP -> mapper.readValue((value as String), Map::class.java)
    else -> value
  }
}