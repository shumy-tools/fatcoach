package fc.api

import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import kotlin.reflect.KClass

enum class FType {
  TEXT, INT, LONG, FLOAT, DOUBLE, BOOL,
  TIME, DATE, DATETIME,
  MAP, LIST, SET,
}

object TypeEngine {
  private val classToType = mutableMapOf<KClass<*>, FType>().apply {
    put(String::class, FType.TEXT)
    put(Int::class, FType.INT)
    put(Long::class, FType.LONG)
    put(Float::class, FType.FLOAT)
    put(Double::class, FType.DOUBLE)
    put(Boolean::class, FType.BOOL)

    put(LocalTime::class, FType.TIME)
    put(LocalDate::class, FType.DATE)
    put(LocalDateTime::class, FType.DATETIME)

    put(Map::class, FType.MAP)
    put(List::class, FType.LIST)
    put(Set::class, FType.SET)
  }

  fun check(fType: FType, vType: KClass<Any>): Boolean {
    return fType == classToType[vType]
  }
}