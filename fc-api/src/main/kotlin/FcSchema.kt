package fc.api

import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

enum class EType { MASTER, DETAIL }
enum class RType { OWNED, LINKED }

class FcSchema(cfg: Builder.() -> Unit) {
  class Builder(private val self: FcSchema) {
    fun master(name: String, cfg: SEntity.Builder.() -> Unit) = self.addEntity(name, EType.MASTER, cfg)
    fun detail(name: String, cfg: SEntity.Builder.() -> Unit) = self.addEntity(name, EType.DETAIL, cfg)
  }

  val all: Map<String, SEntity> = linkedMapOf()
  val masters: Map<String, SEntity> = linkedMapOf()
  val details: Map<String, SEntity> = linkedMapOf()

  init { cfg.invoke(Builder(this)) }

  fun find(entity: String)= all[entity] ?: throw Exception("SEntity '$entity' not found!")

  private fun addEntity(name: String, type: EType, cfg: SEntity.Builder.() -> Unit): SEntity {
    val sEntity = SEntity(name, type)
    val builder = SEntity.Builder(sEntity)
    cfg.invoke(builder)

    if (sEntity.schema != null)
      throw Exception("SEntity '${sEntity.name}' is already being used by a different FcSchema.")

    if (all.containsKey(sEntity.name))
      throw Exception("SEntity '${sEntity.name}' already exists in the schema.")

    sEntity.schema = this
    (all as LinkedHashMap<String, SEntity>)[sEntity.name] = sEntity

    when (sEntity.type) {
      EType.MASTER -> (masters as LinkedHashMap<String, SEntity>)[sEntity.name] = sEntity
      EType.DETAIL -> (details as LinkedHashMap<String, SEntity>)[sEntity.name] = sEntity
    }

    return sEntity
  }
}

class SEntity internal constructor(val name: String, val type: EType) {
  class Builder(private val self: SEntity) {
    fun onCreate(callback: (ctx: InstructionContext) -> Unit) { self.onCreate = callback }
    fun onUpdate(callback: (ctx: InstructionContext) -> Unit) { self.onUpdate = callback }

    fun text(prop: String, cfg: (SField.Builder<String>.() -> Unit)? = null) = self.addField(prop, FType.TEXT, cfg)
    fun int(prop: String, cfg: (SField.Builder<Int>.() -> Unit)? = null) = self.addField(prop, FType.INT, cfg)
    fun long(prop: String, cfg: (SField.Builder<Long>.() -> Unit)? = null) = self.addField(prop, FType.LONG, cfg)
    fun float(prop: String, cfg: (SField.Builder<Float>.() -> Unit)? = null) = self.addField(prop, FType.FLOAT, cfg)
    fun double(prop: String, cfg: (SField.Builder<Double>.() -> Unit)? = null) = self.addField(prop, FType.DOUBLE, cfg)
    fun bool(prop: String, cfg: (SField.Builder<Boolean>.() -> Unit)? = null) = self.addField(prop, FType.BOOL, cfg)

    fun time(prop: String, cfg: (SField.Builder<LocalTime>.() -> Unit)? = null) = self.addField(prop, FType.TIME, cfg)
    fun date(prop: String, cfg: (SField.Builder<LocalDate>.() -> Unit)? = null) = self.addField(prop, FType.DATE, cfg)
    fun datetime(prop: String, cfg: (SField.Builder<LocalDateTime>.() -> Unit)? = null) = self.addField(prop, FType.DATETIME, cfg)

    fun list(prop: String, cfg: (SField.Builder<List<Any>>.() -> Unit)? = null) = self.addField(prop, FType.LIST, cfg)
    fun map(prop: String, cfg: (SField.Builder<Map<String, Any>>.() -> Unit)? = null) = self.addField(prop, FType.MAP, cfg)

    fun linkedRef(prop: String, ref: SEntity, cfg: (SReference.Builder.() -> Unit)? = null) = self.addRef(prop, RType.LINKED, ref, cfg)
    fun ownedRef(prop: String, owned: SEntity, cfg: (SReference.Builder.() -> Unit)? = null) = self.addRef(prop, RType.OWNED, owned, cfg)

    fun linkedCol(prop: String, ref: SEntity, cfg: (SCollection.Builder.() -> Unit)? = null) = self.addCol(prop, RType.LINKED, ref, cfg)
    fun ownedCol(prop: String, owned: SEntity, cfg: (SCollection.Builder.() -> Unit)? = null) = self.addCol(prop, RType.OWNED, owned, cfg)
  }

  internal var schema: FcSchema? = null
  internal var parent: SReference? = null

  internal var onCreate: ((ctx: InstructionContext) -> Unit)? = null
  internal var onUpdate: ((ctx: InstructionContext) -> Unit)? = null

  val all: Map<String, SProperty> = linkedMapOf()
  val fields: Map<String, SField<*>> = linkedMapOf()
  val rels: Map<String, SRelation> = linkedMapOf()

  val id = SField(this, SID, FType.LONG, SField.Builder<Long>().also { it.input = false; it.unique = true })

  val refs: List<SReference>
    get() = rels.values.filterIsInstance<SReference>()

  val cols: List<SCollection>
    get() = rels.values.filterIsInstance<SCollection>()

  internal fun onCreate(ctx: InstructionContext) = onCreate?.invoke(ctx)
  internal fun onUpdate(ctx: InstructionContext) = onUpdate?.invoke(ctx)

  private fun <T> addField(prop: String, type: FType, cfg: (SField.Builder<T>.() -> Unit)?): SField<T> {
    val builder = SField.Builder<T>()
    cfg?.invoke(builder)

    val sField = SField(this, prop, type, builder)
    addProperty(sField)
    return sField
  }

  private fun addRef(prop: String, type: RType, ref: SEntity, cfg: (SReference.Builder.() -> Unit)?): SReference {
    val builder = SReference.Builder()
    cfg?.invoke(builder)

    val sRef = SReference(this, prop, type, ref, builder)
    addProperty(sRef)
    return sRef
  }

  private fun addCol(prop: String, type: RType, ref: SEntity, cfg: (SCollection.Builder.() -> Unit)?): SCollection {
    val builder = SCollection.Builder()
    cfg?.invoke(builder)

    val sCol = SCollection(this, prop, type, ref, builder)
    addProperty(sCol)
    return sCol
  }

  private fun addProperty(sProperty: SProperty) {
    if (all.containsKey(sProperty.name))
      throw Exception("SProperty '${sProperty.name}' already exists in the SEntity '$name'.")

    (all as LinkedHashMap<String, SProperty>)[sProperty.name] = sProperty
    when (sProperty) {
      is SField<*> -> (fields as LinkedHashMap<String, SField<*>>)[sProperty.name] = sProperty

      is SRelation -> {
        // set the @parent property for owned entities
        if (sProperty.type == RType.OWNED) {
          if (sProperty.ref.type != EType.DETAIL)
            throw Exception("Only entities of type DETAIL can be owned. '${name}:${sProperty.name}' trying to own '${sProperty.ref.name}'.")

          if (sProperty.ref.parent != null)
            throw Exception("SEntity '${sProperty.ref.name}' already owned by '$name'.")

          sProperty.ref.parent = sProperty.ref.addRef(SPARENT, RType.LINKED, this, null)
        }
        (rels as LinkedHashMap<String, SRelation>)[sProperty.name] = sProperty
      }
    }
  }

  override fun toString() = """SEntity(${name}):
    |  ${all.values.joinToString("\n|  ")}
  """.trimMargin()
}


sealed class SProperty(val entity: SEntity, val name: String, val input: Boolean) {
  abstract fun simpleString(): String
}

  class SField<T> internal constructor(entity: SEntity, name: String, val type: FType, private val builder: Builder<T>): SProperty(entity, name, builder.input) {
    class Builder<T> {
      var input = true
      var optional = false
      var unique = false

      internal val checks = mutableListOf<ICheck<T>>()
      fun checkIf(onCheck: (T) -> Boolean) = checks.add(SimpleCheck(onCheck))
      fun checkIf(message: String, onCheck: (T) -> Boolean) = checks.add(SimpleCheck(onCheck, message))
      fun checkIf(onCheck: ICheck<T>) = checks.add(onCheck)

      internal var derived: (() -> T)? = null
      fun deriveFrom(onDerive: () -> T) {
        input = false
        derived = onDerive
      }
    }

    val optional: Boolean
      get() = builder.optional

    val unique: Boolean
      get() = builder.unique

    internal fun check(value: T?) = value?.let { cValue ->
      builder.checks.forEach {
        if (!it.check(cValue)) {
          if (it.message != null)
            throw Exception(it.message)
          else
            throw Exception("Check constraint failed for '${entity.name}::$name'.")
        }
      }
    }

    internal fun derive() = builder.derived?.invoke()

    override fun simpleString() = "(${type.name.toLowerCase()}@$name)"
    override fun toString() = "SField(${entity.name}::${simpleString()}, optional=$optional, input=$input, unique=$unique)"
  }

  sealed class SRelation(entity: SEntity, name: String, val type: RType, val ref: SEntity, input: Boolean): SProperty(entity, name, input) {
    override fun simpleString() = "(${ref.name}@$name)"
  }

    class SReference internal constructor(entity: SEntity, name: String, type: RType, ref: SEntity, private val builder: Builder): SRelation(entity, name, type, ref, builder.input) {
      class Builder {
        var input = true
        var optional = false
      }

      val optional: Boolean
        get() = builder.optional

      override fun toString() = "SReference(${entity.name}::${simpleString()}, type=${type.name.toLowerCase()}, optional=$optional, input=$input)"
    }

    class SCollection internal constructor(entity: SEntity, name: String, type: RType, ref: SEntity, private val builder: Builder): SRelation(entity, name, type, ref, builder.input) {
      class Builder {
        var input = true
      }

      override fun toString() = "SCollection(${entity.name}::${simpleString()}, type=${type.name.toLowerCase()}, input=$input)"
    }

interface ICheck<T> {
  val message: String?
  fun check(value: T): Boolean
}

class InstructionContext(val tx: FcTransaction, val selfID: RefID, private val values: MutableMap<String, Any?>) {
  private val ctxVars = ThreadLocal<MutableMap<String, Any>>()
  fun vars(): MutableMap<String, Any> {
    var inVars = ctxVars.get()
    if (inVars == null) {
      inVars = linkedMapOf()
      ctxVars.set(inVars)
    }

    return inVars
  }

  fun contains(field: SField<*>) = values.contains(field.name)
  fun <T> get(field: SField<T>) = values[field.name]
  fun <T> set(field: SField<T>, value: T) { values[field.name] = value }
}

/* ------------------------- helpers -------------------------*/
private class SimpleCheck<T>(private val checkFun: (T) -> Boolean, override val message: String? = null): ICheck<T> {
  override fun check(value: T) = checkFun.invoke(value)
}