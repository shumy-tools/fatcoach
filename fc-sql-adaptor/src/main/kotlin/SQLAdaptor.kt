package fc.adaptor.sql

import com.zaxxer.hikari.HikariDataSource
import fc.api.*
import fc.api.input.FcCreate
import fc.api.input.FcDelete
import fc.api.input.FcUpdate
import fc.api.input.Transaction
import fc.api.query.IResult
import fc.api.query.QTree
import fc.api.spi.IAdaptor
import org.jooq.*
import org.jooq.conf.RenderQuotedNames
import org.jooq.conf.Settings
import org.jooq.impl.DSL.*
import org.jooq.impl.SQLDataType
import java.sql.SQLException
import java.sql.Statement

class SQLAdaptor(private val url: String, private val sqlListener: ((String) -> Unit)? = null): IAdaptor {
  private val ds = HikariDataSource().also {
    it.jdbcUrl = url
    it.addDataSourceProperty("cachePrepStmts", true)
    it.addDataSourceProperty("prepStmtCacheSize", 512)
    it.addDataSourceProperty("prepStmtCacheSqlLimit", 2048)
  }

  private val settings = Settings()
    .withRenderQuotedNames(RenderQuotedNames.ALWAYS)
    .withExecuteLogging(true)

  private val db = using(ds, SQLDialect.H2, settings)
  private var cSchema: FcSchema? = null

  init {
    if (db.selectOne().fetch().isEmpty())
      throw SQLException("Database connection failed! - ($url)")
  }

  override val schema: FcSchema
    get() = cSchema!!

  override fun changeSchema(updated: FcSchema) {
    TODO("Not yet implemented")
  }

  override fun getById(sEntity: SEntity, id: Long): FcData {
    TODO("Not yet implemented")
  }

  override fun execTransaction(instructions: Transaction) {
    db.transaction { conf ->
      println("TX-START")
      val tx = using(conf)
      instructions.all.forEach { inst ->
        val tableName = inst.entity.sqlTableName()
        when (inst) {
          is FcCreate -> {
            val allProps = inst.properties

            // ------------------------ main insert ------------------------
            val mainDbFields = allProps.dbFields()
            val dbInsert = tx.insertInto(table(tableName), mainDbFields.keys)
              .values(mainDbFields.values)
              //.returning(idFn())

            // jOOQ is not returning the generated key! Issue: https://github.com/jOOQ/jOOQ/issues/2943
            var seq = 1
            val stmt = conf.connectionProvider().acquire().prepareStatement(dbInsert.sql, Statement.RETURN_GENERATED_KEYS)
            mainDbFields.values.forEach {
              stmt.setObject(seq, it)
              seq++
            }

            print("  ${dbInsert.sql}; $mainDbFields")
            // FIX: val affectedRows = dbInsert.execute()
            val affectedRows = stmt.executeUpdate()
            if (affectedRows != 1)
              throw Exception("Create instruction failed, unexpected number of rows affected!")

            // FIX: inst.refID.id = dbInsert.fetchOne() as Long? ?: throw Exception("Insert failed! No return $SID!")
            val genKeys = stmt.generatedKeys
            inst.refID.id = if (genKeys.next()) genKeys.getLong(1) else throw Exception("Insert failed! No return $SID!")
            println(" - $SID=${inst.refID.id}")
            sqlListener?.invoke("${dbInsert.sql}; $mainDbFields - $SID=${inst.refID.id}")

            // ------------------------ aux-table inserts ------------------------
            allProps.mapRefs().forEach { (rel, refs) ->
              val auxTable = table(rel.sqlAuxTableName())
              tx.link(auxTable, inst.refID, refs, sqlListener)
            }
          }

          is FcUpdate -> {
            val allProps = inst.properties

            // ------------------------ main update ------------------------
            val mainDbFields = allProps.dbFields()
            if (mainDbFields.isNotEmpty()) {
              var dbUpdate = tx.update(table(tableName)) as UpdateSetMoreStep<*>
              mainDbFields.forEach { (field, value) ->
                dbUpdate = dbUpdate.set(field, value)
              }

              val update = dbUpdate.where(idFn().eq(inst.refID.id))

              println("  ${update.sql}; $mainDbFields - @id=${inst.refID.id}")
              val affectedRows = update.execute()
              if (affectedRows != 1)
                throw Exception("Update instruction failed, unexpected number of rows affected!")

              sqlListener?.invoke("${update.sql}; $mainDbFields - @id=${inst.refID.id}")
            }

            // ------------------------ aux-table inserts ------------------------
            allProps.mapLinks().forEach { (rel, refs) ->
              val auxTable = table(rel.sqlAuxTableName())
              val toLink = refs.mapNotNull { if (it.oper == OType.ADD) it.refID else null }
              val toUnlink = refs.mapNotNull { if (it.oper == OType.DEL) it.refID else null }
              if (toLink.isNotEmpty()) tx.link(auxTable, inst.refID, toLink, sqlListener)
              if (toUnlink.isNotEmpty()) tx.unlink(auxTable, inst.refID, toUnlink, sqlListener)
            }
          }

          is FcDelete -> {
            val dbDelete = tx.delete(table(tableName)).where(idFn().eq(inst.refID.id))

            println("  ${dbDelete.sql}; - @id=${inst.refID.id}")
            val affectedRows = dbDelete.execute()
            if (affectedRows != 1)
              throw Exception("Delete instruction failed, unexpected number of rows affected!")

            sqlListener?.invoke("${dbDelete.sql}; - @id=${inst.refID.id}")
          }
        }
      }
      println("TX-COMMIT")
    }
  }

  override fun execQuery(query: QTree, args: Map<String, Any>): IResult = SQLQueryExecutor(db, query, args).exec()

  fun createSchema(schema: FcSchema) {
    val constraints = TableConstraints()
    schema.all.values.forEach { entity ->
      val table = table(entity.sqlTableName())
      var dbTable = db.createTable(table)

      // ----------------------------- set @id field -----------------------------
      dbTable = dbTable.column(SID, SQLDataType.BIGINT.nullable(false).identity(true))

      // ----------------------------- set table fields -----------------------------
      entity.fields.values.forEach {
        if (it.unique) dbTable.constraint(unique(it.name))
        dbTable = dbTable.column(it.name, it.type.toSqlType().nullable(it.optional))
      }

      // ----------------------------- set @parent field -----------------------------
      entity.refs.filter { it.name == SPARENT }.forEach {
        dbTable = dbTable.column(it.name, SQLDataType.BIGINT.nullable(false))
        val refDbTable = table(it.ref.sqlTableName())
        val fk = foreignKey(it.name).references(refDbTable)
        constraints.push(table, fk)
      }

      // ----------------------------- set aux tables -----------------------------
      entity.rels.values.filter { it.type == RType.LINKED && it.name != SPARENT }.forEach {
        val auxTable = table(it.sqlAuxTableName())
        var auxDbTable = db.createTable(auxTable)
        auxDbTable = auxDbTable.column(INV, SQLDataType.BIGINT.nullable(false))
        auxDbTable = auxDbTable.column(REF, SQLDataType.BIGINT.nullable(false))

        val refTable = table(it.ref.sqlTableName())
        val invFk = foreignKey(INV).references(table)
        val refFk = foreignKey(REF).references(refTable)
        constraints.push(auxTable, invFk)
        constraints.push(auxTable, refFk)

        // set primary key and execute
        val dbFinal = auxDbTable.constraint(primaryKey(INV, REF))
        println(dbFinal.sql)
        dbFinal.execute()
      }

      // set primary key and execute
      val dbFinal = dbTable.constraint(primaryKey(SID))
      println(dbFinal.sql)
      dbFinal.execute()
    }

    constraints.alterTables(db)
    cSchema = schema
  }

  /*
  override fun compile(query: QTree) = SQLQueryExecutor(db, query)
  }*/
}

class TableConstraints {
  private val allConstraints = linkedMapOf<Table<Record>, MutableList<Constraint>>()

  fun push(table: Table<Record>, constraint: Constraint) {
    val tableConstraits = allConstraints.getOrPut(table) { mutableListOf() }
    tableConstraits.add(constraint)
  }

  fun alterTables(tx: DSLContext) {
    allConstraints.forEach { (tlb, cList) ->
      val dbAlterTable = tx.alterTable(tlb).add(cList)
      println(dbAlterTable.sql)
      dbAlterTable.execute()
    }
  }
}