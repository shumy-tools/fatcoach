package fc.adaptor.sql

import fc.api.*
import fc.api.query.*
import org.jooq.*
import org.jooq.impl.DSL.*
import java.util.*
import kotlin.collections.LinkedHashMap

class SQLQueryExecutor(private val db: DSLContext, private val qTree: QTree, private val args: Map<String, Any>, private val auxRel: SRelation? = null) {
  private val qTable = table(qTree.entity.sqlTableName()).asTable(MAIN)
  private val subQueries = linkedMapOf<String, Pair<Field<Long>, SQLQueryExecutor>>()

  fun exec(): IResult = subExec()

  @Suppress("UNCHECKED_CAST")
  fun subExec(fk: Field<Long>? = null, topIds: Select<*>? = null): IResult {
    val idsQuery = query(true)
    val mainQuery = query(false)

    if (fk != null) {
      mainQuery.addSelect(fk)
      mainQuery.addConditions((fk as Field<Any>).`in`(topIds))
      auxRel?.let { mainQuery.auxJoin(auxRel) }
    }

    // process results
    val result = SQLResult(qTree.entity)

    // add main result
    println(mainQuery.sql)
    mainQuery.fetch().forEach { result.process(it, fk) }

    // add one-to-many and many-to-many results
    subQueries.forEach {
      val subResult = it.value.second.subExec(it.value.first, idsQuery) as SQLResult
      result.rowsWithIds.keys.forEach { pk ->
        val fkKeys = subResult.fkKeys[pk] // join results via "pk <-- fk"
        fkKeys?.forEach { subID ->
          val line = subResult.rowsWithIds[subID]!!
          result.addTo(pk, it.key, line)
        }
      }
    }

    return result
  }

  private fun query(onlyId: Boolean) = db.selectQuery().apply {
    addFrom(qTable)
    select(onlyId, qTree.select, qTree.filter, MAIN, "")
    orderBy(qTree.select)
    limitAndPage(qTree.limit, qTree.page)
  }

  private fun SelectQuery<Record>.select(onlyId: Boolean, selection: QSelect, filter: QExpression?, prefix: String, alias: String) {
    val idField = if (alias.isEmpty()) idFn(prefix) else idFn(prefix).`as`("$alias.$SID")
    addSelect(idField)

    if (!onlyId) {
      val fields = if (alias.isEmpty()) dbFields(selection, prefix) else dbFields(selection, prefix).map { it.`as`("$alias.${it.name}") }
      addSelect(fields)
    }

    if (filter != null)
      addConditions(expression(filter))

    // one-to-one relations (A.id --> B.@parent, , A -->@inv AB @ref--> B)
    val oneToOne = selection.relations.keys.filterIsInstance<SReference>()
    oneToOne.forEach {
      val qRel = selection.relations.getValue(it)
      val sPrefix = "${prefix}_${it.name}"
      val sAlias = "$alias.${it.name}"
      select(onlyId, qRel.select, qRel.filter, sPrefix, sAlias)

      when (it.type) {
        RType.OWNED -> ownedJoin(it, prefix)
        RType.LINKED -> if (it.name == SPARENT) parentJoin(it, prefix) else linkedJoin(it, prefix)
      }
    }

    // one-to-many and many-to-many relations (A.id <-- B.@parent, A <--@inv AB @ref--> B)
    val toMany = selection.relations.keys.filterIsInstance<SCollection>()
    toMany.forEach {
      val qRel = selection.relations.getValue(it)
      val subTree = QTree(it.ref, qRel)

      val auxRel = if (it.type == RType.LINKED) it else null
      val subQuery = SQLQueryExecutor(db, subTree, args, auxRel)

      val sPrefix = "${prefix}_${it.name}"
      subQueries[it.name] = when (it.type) {
        RType.OWNED -> Pair(parentFn(MAIN), subQuery)
        RType.LINKED -> Pair(invFn(sPrefix), subQuery)
      }
    }
  }

  private fun SelectQuery<Record>.orderBy(selection: QSelect) {
    val orderBy = selection.fields.filter { it.value.sort != SortType.NONE }
    if (orderBy.isNotEmpty()) {
      val sorted = TreeMap<Int, SortField<Any>>()
      orderBy.forEach {
        val field = if (it.value.sort == SortType.ASC) it.key.fn().asc() else it.key.fn().desc()
        sorted[it.value.order] = field
      }
      addOrderBy(sorted.values)
    }
  }

  private fun SelectQuery<Record>.limitAndPage(qLimit: Any?, qPage: Any?) {
    when {
      qPage != null -> {
        val limit = (if (qLimit is Int) qLimit else args.getValue((qLimit as QParameter).name)) as Int
        val page = (if (qPage is Int) qPage else args.getValue((qPage as QParameter).name)) as Int

        addLimit(limit)
        addOffset((page - 1) * limit)
      }

      qLimit != null -> {
        val limit = (if (qLimit is Int) qLimit else args.getValue((qLimit as QParameter).name)) as Int
        addLimit(limit)
      }

      else -> Unit
    }
  }

  private fun expression(expr: QExpression): Condition = if (expr.predicate != null) {
    predicate(expr.predicate!!)
  } else {
    val left = expression(expr.left!!)
    val right = expression(expr.right!!)

    when(expr.oper!!) {
      OperType.OR -> left.or(right)
      OperType.AND -> left.and(right)
    }
  }

  @Suppress("UNCHECKED_CAST")
  private fun predicate(pred: QPredicate): Condition {
    /*var sPrefix = sRelation?.name ?: MAIN // detect if it's an auxTable
    var sName = "_null_"
    for ((i, qDeref) in pred.path.withIndex()) {
      when (qDeref.deref) {
        DerefType.FIELD -> sName = qDeref.name

        DerefType.ONE -> {
          when (qDeref.name) {
            SUPER -> qDeref.table.superJoin(sPrefix)
            PARENT -> qDeref.table.parentJoin(sPrefix)
            else -> {
              val dRef = qDeref.table.oneToOne.getValue(qDeref.name)
              qDeref.table.directJoin(dRef, sPrefix)
            }
          }

          sPrefix = qDeref.name
        }

        DerefType.MANY -> {
          val inPredicate = QPredicate(pred.path.drop(i + 1), pred.comp, pred.param)

          qDeref.table.oneToMany[qDeref.name]?.let {
            val refTable = tables.get(it.rel.ref)
            val inSelect = db.select(it.fn(it.refTable))
              .from(table(refTable.sqlName()).`as`(MAIN))
              .where(refTable.predicate(inPredicate, params))
            return idFn(sPrefix).`in`(inSelect)
          }

          qDeref.table.manyToMany[qDeref.name]?.let {
            val auxTable = it.first
            val refTable = tables.get(it.second)
            val inSelect = db.select(invFn(MAIN))
              .from(table(auxTable.sqlName()).`as`(MAIN))
              .join(table(refTable.sqlName()).`as`(qDeref.name)).on(refFn(MAIN).eq(idFn(qDeref.name)))
              .where(auxTable.predicate(inPredicate, params))
            return idFn(sPrefix).`in`(inSelect)
          }
        }
      }
    }*/

    var sPrefix = MAIN
    for (rel in pred.path.dropLast(1)) {
      sPrefix += "_${rel.name}"
    }

    val field = pred.end.fn(sPrefix)
    val value = (if (pred.param is QParameter) args.getValue((pred.param as QParameter).name) else pred.param)
    return when(pred.comp) {
      CompType.EQUAL -> field.eq(value)
      CompType.DIFFERENT -> field.ne(value)
      CompType.MORE -> field.greaterThan(value)
      CompType.LESS -> field.lessThan(value)
      CompType.MORE_EQ -> field.greaterOrEqual(value)
      CompType.LESS_EQ -> field.lessOrEqual(value)
      CompType.IN -> field.`in`(value)
    }
  }
}


/* ------------------------- helpers -------------------------*/
class RowGet(private val row: FcData): IRowGet {
  @Suppress("UNCHECKED_CAST")
  override fun <R : Any> get(name: String) = row[name] as R?
}

class ResultIterator(rows: List<FcData>): Iterator<IRowGet> {
  private val iter = rows.iterator()
  override fun hasNext() = iter.hasNext()
  override fun next() = RowGet(iter.next())
}

class SQLResult(private val entity: SEntity): IResult {
  internal val rowsWithIds = linkedMapOf<Long, LinkedHashMap<String, Any?>>()
  internal val fkKeys = linkedMapOf<Long, MutableList<Long>>()

  override val rows
    get() = rowsWithIds.values.toList()

  @Suppress("UNCHECKED_CAST")
  override fun <R: Any> get(name: String): R? {
    if (rows.size != 1)
      throw Exception("Expecting a single result to use 'get' function!")

    return rows.first()[name] as R?
  }

  override fun iterator(): Iterator<IRowGet> = ResultIterator(rows)

  @Suppress("UNCHECKED_CAST")
  fun addTo(pk: Long, name: String, row: FcData) {
    val main = rowsWithIds.getValue(pk)
    val list = main.getOrPut(name) { mutableListOf<FcData>() } as MutableList<FcData>
    list.add(row)
  }

  fun process(record: Record, fk: Field<Long>?) {
    val fieldID = record.fields().find { it.name == SID }!!
    val rowID = fieldID.getValue(record) as Long
    val row = linkedMapOf<String, Any?>()
    rowsWithIds[rowID] = row

    record.fields().forEach {
      val value = it.getValue(record)

      // process foreignKeys
      if (fk != null && it.name == fk.name) {
        val fkRows = fkKeys.getOrPut(value as Long) { mutableListOf() }
        fkRows.add(rowID)
      } else {
        if (it.name.startsWith('.')) {
          row.processJoin(it.name, value)
        } else {
          val cValue = if (value != null) convert(entity, it.name, value) else null
          row[it.name] = cValue
        }
      }
    }
  }

  @Suppress("UNCHECKED_CAST")
  private fun LinkedHashMap<String, Any?>.processJoin(name: String, value: Any?) {
    val splits = name.split('.').drop(1)

    var sEntity = entity
    var place = this
    for (position in splits.dropLast(1)) {
      sEntity = sEntity.rels.getValue(position).ref
      place = place.getOrPut(position) { linkedMapOf<String, Any?>() } as LinkedHashMap<String, Any?>
    }

    val cValue = if (value != null) convert(sEntity, splits.last(), value) else null
    place[splits.last()] = cValue
  }

  private fun convert(sEntity: SEntity, field: String, value: Any): Any {
    println("(${sEntity.name}::$field, $value)")
    return if (field != SID) {
      val sField = sEntity.fields.getValue(field)
      SQLFieldConverter.load(sField, value)
    } else value
  }
}