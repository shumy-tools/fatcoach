package fc.adaptor.sql

import fc.api.*
import fc.api.FType.*
import org.jooq.*
import org.jooq.impl.DSL
import org.jooq.impl.SQLDataType
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

const val MAIN = "main"
const val REF = "@ref"
const val INV = "@inv"

fun SEntity.sqlTableName() = name.replace('.', '_')

fun SRelation.sqlAuxTableName() = "${entity.sqlTableName()}_${name}_${ref.sqlTableName()}"

fun Map<SProperty, Any?>.dbFields(): Map<Field<Any>, Any?> {
  val filtered = filter { (prop, _) -> prop is SField || prop is SReference && prop.name == SPARENT }
  return filtered.map { (prop, value) ->
    val field = when (prop) {
      is SField -> prop.fn()
      is SRelation -> parentFn()
    }
    field to value
  }.toMap()
}

@Suppress("UNCHECKED_CAST")
fun Map<SProperty, Any?>.mapRefs(): Map<SRelation, List<RefID>> {
  val filtered = filter { (prop, _) -> prop is SRelation && prop.type == RType.LINKED && prop.name != SPARENT } as Map<SRelation, Any?>
  return filtered.map { (prop, value) ->
    val refs = when (prop) {
      is SReference -> listOf(value as RefID)
      is SCollection -> value as List<RefID>
    }
    prop to refs
  }.toMap()
}

@Suppress("UNCHECKED_CAST")
fun Map<SProperty, Any?>.mapLinks(): Map<SRelation, List<RefLink>> {
  val filtered = filter { (prop, _) -> prop is SRelation && prop.type == RType.LINKED && prop.name != SPARENT } as Map<SRelation, Any?>
  return filtered.map { (prop, value) ->
    val refs = when (prop) {
      is SReference -> listOf(value as RefLink)
      is SCollection -> value as List<RefLink>
    }
    prop to refs
  }.toMap()
}

@Suppress("UNCHECKED_CAST")
fun idFn(prefix: String? = null): Field<Long> = if (prefix != null) {
  DSL.field(DSL.name(prefix, SID), java.lang.Long::class.java) as Field<Long>
} else {
  DSL.field(DSL.name(SID), java.lang.Long::class.java) as Field<Long>
}

@Suppress("UNCHECKED_CAST")
fun refFn(prefix: String? = null): Field<Long> = if (prefix != null) {
  DSL.field(DSL.name(prefix, REF), java.lang.Long::class.java) as Field<Long>
} else {
  DSL.field(DSL.name(REF), java.lang.Long::class.java) as Field<Long>
}

@Suppress("UNCHECKED_CAST")
fun invFn(prefix: String? = null): Field<Long> = if (prefix != null) {
  DSL.field(DSL.name(prefix, INV), java.lang.Long::class.java) as Field<Long>
} else {
  DSL.field(DSL.name(INV), java.lang.Long::class.java) as Field<Long>
}

@Suppress("UNCHECKED_CAST")
fun parentFn(prefix: String? = null): Field<Any> = if (prefix != null) {
  DSL.field(DSL.name(prefix, SPARENT), java.lang.Long::class.java) as Field<Any>
} else {
  DSL.field(DSL.name(SPARENT), java.lang.Long::class.java) as Field<Any>
}

@Suppress("UNCHECKED_CAST")
fun SField.fn(prefix: String? = null): Field<Any> = if (prefix != null) {
  DSL.field(DSL.name(prefix, name), jType()) as Field<Any>
} else {
  DSL.field(DSL.name(name), jType()) as Field<Any>
}

fun DSLContext.link(auxTable: Table<Record>, inv: RefID, refs: List<RefID>) {
  var dbInsert = insertInto(auxTable, listOf(invFn(), refFn()))
  val values = refs.map { inv.id to it.id }
  values.forEach {
    dbInsert = dbInsert.values(it.first, it.second)
  }

  print("  ${dbInsert.sql}; $values")
  val affectedRows = dbInsert.execute()
  if (affectedRows != refs.size)
    throw Exception("Create aux-table instruction failed, unexpected number of rows affected!")
}

fun DSLContext.unlink(auxTable: Table<Record>, inv: RefID, refs: List<RefID>) {
  val values = refs.map { it.id }
  val dbDelete = delete(auxTable).where(invFn().eq(inv.id).and(refFn().`in`(values)))

  println("  ${dbDelete.sql}; - [${inv.id}, ${values}]")
  val affectedRows = dbDelete.execute()
  if (affectedRows != refs.size)
    throw Exception("Delete instruction failed, unexpected number of rows affected!")
}

private fun SField.jType(): Class<out Any> = when (type) {
  TEXT -> java.lang.String::class.java
  INT -> java.lang.Integer::class.java
  LONG -> java.lang.Long::class.java
  FLOAT -> java.lang.Float::class.java
  DOUBLE -> java.lang.Double::class.java
  BOOL -> java.lang.Boolean::class.java

  TIME -> LocalTime::class.java
  DATE -> LocalDate::class.java
  DATETIME -> LocalDateTime::class.java
  MAP -> java.lang.String::class.java
  LIST -> java.lang.String::class.java
}

fun FType.toSqlType(): DataType<out Any> = when (this) {
  TEXT -> SQLDataType.VARCHAR
  INT -> SQLDataType.INTEGER
  LONG -> SQLDataType.BIGINT
  FLOAT -> SQLDataType.FLOAT
  DOUBLE -> SQLDataType.DOUBLE
  BOOL -> SQLDataType.BOOLEAN
  TIME -> SQLDataType.LOCALTIME
  DATE -> SQLDataType.LOCALDATE
  DATETIME -> SQLDataType.LOCALDATETIME
  MAP -> SQLDataType.VARCHAR
  LIST -> SQLDataType.VARCHAR
}