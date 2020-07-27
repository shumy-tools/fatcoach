package fc.api

import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import kotlin.reflect.KClass

enum class FType {
  TEXT, INT, LONG, FLOAT, DOUBLE, BOOL,
  TIME, DATE, DATETIME,
  LIST, MAP
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
  }

  private val oneOf = classToType.values.map { it.name.toLowerCase() }

  fun tryConvertParam(type: FType, value: String): Any = when (type) {
    FType.TEXT -> value
    FType.INT -> value.toInt()
    FType.LONG -> value.toLong()
    FType.FLOAT -> value.toFloat()
    FType.DOUBLE -> value.toDouble()
    FType.BOOL -> value.toBoolean()
    FType.TIME -> LocalTime.parse(value)
    FType.DATE -> LocalDate.parse(value)
    FType.DATETIME -> LocalDateTime.parse(value)
    FType.LIST -> throw Exception("Unsupported type parameter: list") // TODO: add support
    FType.MAP -> throw Exception("Unsupported type parameter: map") // TODO: add support
  }

  fun convert(vType: KClass<Any>): FType = classToType[vType] ?:
    throw Exception("Expecting type one of $oneOf. Found ${vType.simpleName}.")

  fun check(fType: FType, vType: KClass<Any>): Boolean {
    return fType == classToType[vType]
  }
}

fun SField<*>.tryType(tryType: FType) {
  // list and map can contain any value
  if (type == FType.LIST || type == FType.MAP)
    return

  val implicitConvert = when (type) {
    FType.INT -> FType.LONG
    FType.FLOAT -> FType.DOUBLE
    else -> type
  }

  if (implicitConvert != tryType)
    throw Exception("Expecting typeOf ${type.name.toLowerCase()} for '${entity.name}.$name'. Trying to assign a ${tryType.name.toLowerCase()}.")
}