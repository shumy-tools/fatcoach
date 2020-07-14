package fc.adaptor.sql

import fc.api.*
import fc.api.FType.*
import fc.api.query.QSelect
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

fun SRelation.sqlAuxTableName() = "${entity.sqlTableName()}_${name}"

fun Map<SProperty, Any?>.dbFields(): Map<Field<Any>, Any?> {
  val filtered = filter { (prop, _) -> prop is SField || prop is SReference && prop.name == SPARENT }
  return filtered.map { (prop, value) ->
    when (prop) {
      is SField -> prop.fn() to SQLFieldConverter.save(prop, value)
      is SRelation -> parentFn() as Field<Any> to (value as RefID).id
    }
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
fun parentFn(prefix: String? = null): Field<Long> = if (prefix != null) {
  DSL.field(DSL.name(prefix, SPARENT), java.lang.Long::class.java) as Field<Long>
} else {
  DSL.field(DSL.name(SPARENT), java.lang.Long::class.java) as Field<Long>
}

@Suppress("UNCHECKED_CAST")
fun SField.fn(prefix: String? = null): Field<Any> = if (prefix != null) {
  DSL.field(DSL.name(prefix, name), jType()) as Field<Any>
} else {
  DSL.field(DSL.name(name), jType()) as Field<Any>
}

fun DSLContext.link(auxTable: Table<Record>, inv: RefID, refs: List<RefID>, sqlListener: ((String) -> Unit)? = null) {
  var dbInsert = insertInto(auxTable, listOf(invFn(), refFn()))
  val values = refs.map { inv.id to it.id }
  values.forEach {
    dbInsert = dbInsert.values(it.first, it.second)
  }

  println("  ${dbInsert.sql}; $values")
  val affectedRows = dbInsert.execute()
  if (affectedRows != refs.size)
    throw Exception("Create aux-table instruction failed, unexpected number of rows affected!")

  sqlListener?.invoke("${dbInsert.sql}; $values")
}

fun DSLContext.unlink(auxTable: Table<Record>, inv: RefID, refs: List<RefID>, sqlListener: ((String) -> Unit)? = null) {
  val values = refs.map { it.id }
  val dbDelete = delete(auxTable).where(invFn().eq(inv.id).and(refFn().`in`(values)))

  println("  ${dbDelete.sql}; - [${inv.id}, ${values}]")
  val affectedRows = dbDelete.execute()
  if (affectedRows != refs.size)
    throw Exception("Delete instruction failed, unexpected number of rows affected!")

  sqlListener?.invoke("${dbDelete.sql}; - [${inv.id}, ${values}]")
}
// --------------------------------------------------- query ---------------------------------------------------
fun dbFields(selection: QSelect, prefix: String = MAIN) =
  selection.fields.keys.filter { it.name != SID }.map { it.fn(prefix) }


fun SelectQuery<Record>.parentJoin(rel: SRelation, prefix: String) {
  val joinWith = DSL.table(rel.ref.sqlTableName()).asTable(rel.name)
  val joinWhere = parentFn(prefix).eq(idFn(rel.name))
  addJoin(joinWith, JoinType.LEFT_OUTER_JOIN, joinWhere)
}

fun SelectQuery<Record>.ownedJoin(rel: SRelation, prefix: String) {
  val joinWith = DSL.table(rel.ref.sqlTableName()).asTable(rel.name)
  val joinWhere = idFn(prefix).eq(parentFn(rel.name))
  addJoin(joinWith, JoinType.LEFT_OUTER_JOIN, joinWhere)
}

fun SelectQuery<Record>.linkedJoin(rel: SRelation, prefix: String) {
  val auxPrefix = "${prefix}_${rel.name}"

  val auxJoinWith = DSL.table(rel.sqlAuxTableName()).asTable(auxPrefix)
  val auxJoinWhere = idFn(prefix).eq(invFn(auxPrefix))
  addJoin(auxJoinWith, JoinType.LEFT_OUTER_JOIN, auxJoinWhere)

  val joinWith = DSL.table(rel.ref.sqlTableName()).asTable(rel.name)
  val joinWhere = refFn(auxPrefix).eq(idFn(rel.name))
  addJoin(joinWith, JoinType.LEFT_OUTER_JOIN, joinWhere)
}

/*
fun dbOneToOne(selection: QSelect) = selection.relations.filter {
  it.key is SReference && it.key.type == RType.OWNED
}

fun dbOneToMany(selection: QSelect) = selection.relations.filter {
  it.key is SCollection && it.key.type == RType.OWNED
}

fun dbManyToMany(selection: QSelect) = selection.relations.mapNotNull {
  val value = manyToMany[it.name]
  if (value != null) it to value else null
}.toMap()
*/

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
  MAP -> SQLDataType.JSON
  LIST -> SQLDataType.JSON
}