package fc.api

enum class EType { MASTER, DETAIL }
enum class RType { OWNED, LINKED }

class FcSchema(onChange: (FcSchema.() -> Unit)? = null) {
  var committed = false
    internal set

  val all: Map<String, SEntity> = linkedMapOf()
  val masters: Map<String, SEntity> = linkedMapOf()
  val details: Map<String, SEntity> = linkedMapOf()

  init {
    if (onChange != null) {
      onChange()
      commit()
    }
  }

  fun find(entity: String): SEntity {
    return all[entity] ?: throw Exception("SEntity '$entity' not found!")
  }

  inline fun entity(name: String, type: EType, onChange: SEntity.() -> Unit) {
    val newEntity = SEntity(name, type)
    add(newEntity)
    newEntity.onChange()
  }

  fun add(sEntity: SEntity) {
    if (committed)
      throw Exception("Cannot change a committed schema. Invoke 'change' and set the desired changes.")

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
  }

  fun del(name: String) {
    if (committed)
      throw Exception("Cannot change a committed schema. Invoke 'change' and set the desired changes.")

    (masters as LinkedHashMap<String, SEntity>).remove(name)
    (details as LinkedHashMap<String, SEntity>).remove(name)

    val sEntity = (all as LinkedHashMap<String, SEntity>).remove(name)
    sEntity?.schema = null
  }

  // deep clone the schema and return
  fun change(): FcSchema {
    val newSchema = FcSchema()
    all.values.forEach { newSchema.add(it.clone()) }
    return newSchema
  }

  fun commit() { committed = true }
}

class SEntity(val name: String, val type: EType) {
  internal var schema: FcSchema? = null
  internal var ownedBy: SEntity? = null

  val all: Map<String, SProperty> = linkedMapOf()
  val fields: Map<String, SField> = linkedMapOf()
  val rels: Map<String, SRelation> = linkedMapOf()

  /* ------------------------- owned/linked -------------------------*/
  val ownedRefs: List<SReference>
    get() = rels.values.filterIsInstance<SReference>().filter { it.type == RType.OWNED }

  val linkedRefs: List<SReference>
    get() = rels.values.filterIsInstance<SReference>().filter { it.type == RType.LINKED }

  val ownedCols: List<SCollection>
    get() = rels.values.filterIsInstance<SCollection>().filter { it.type == RType.OWNED }

  val linkedCols: List<SCollection>
    get() = rels.values.filterIsInstance<SCollection>().filter { it.type == RType.LINKED }

  private fun checkChange() {
    if (schema == null)
      throw Exception("Cannot change an orphan entity. Add the entity to a FcSchema.")

    if (schema!!.committed)
      throw Exception("Cannot change a committed schema. Invoke 'change' and set the desired changes.")
  }

  fun field(name: String, type: FType, isOptional: Boolean = false, isInput: Boolean = true, isUnique: Boolean = false) {
    val newProperty = SField(name, type, isOptional, isInput, isUnique)
    add(newProperty)
  }

  fun ref(name: String, type: RType, ref: SEntity, isOptional: Boolean = false, isInput: Boolean = true, isUnique: Boolean = false) {
    val newProperty = SReference(name, type, ref, isOptional, isInput, isUnique)
    add(newProperty)
  }

  fun col(name: String, type: RType, ref: SEntity, isInput: Boolean = true, isUnique: Boolean = false) {
    val newProperty = SCollection(name, type, ref, isInput, isUnique)
    add(newProperty)
  }

  fun add(sProperty: SProperty) {
    checkChange()

    if (sProperty.entity != null)
      throw Exception("SProperty '${sProperty.name}' is already being used by a different SEntity '${sProperty.entity!!.name}'.")

    if (all.containsKey(sProperty.name))
      throw Exception("SProperty '${sProperty.name}' already exists in the SEntity '$name'.")

    sProperty.entity = this
    (all as LinkedHashMap<String, SProperty>)[sProperty.name] = sProperty

    when (sProperty) {
      is SField -> (fields as LinkedHashMap<String, SField>)[sProperty.name] = sProperty

      is SReference -> {
        if (sProperty.type == RType.OWNED) {
          own(sProperty.name, sProperty.ref)
          // set the @parent property for owned entities
          sProperty.ref.add(SReference(PARENT, RType.LINKED, ref = this, isOptional = false, isInput = true, isUnique = true))
        }

        (rels as LinkedHashMap<String, SRelation>)[sProperty.name] = sProperty
      }

      is SCollection -> {
        if (sProperty.type == RType.OWNED) {
          own(sProperty.name, sProperty.ref)
          // set the @parent property for owned entities
          sProperty.ref.add(SReference(PARENT, RType.LINKED, ref = this, isOptional = false, isInput = true, isUnique = false))
        }

        (rels as LinkedHashMap<String, SRelation>)[sProperty.name] = sProperty
      }
    }
  }

  fun del(name: String) {
    checkChange()

    if (name == PARENT)
      throw Exception("Cannot remove '$PARENT' from a SEntity.")

    (fields as LinkedHashMap<String, SField>).remove(name)
    (rels as LinkedHashMap<String, SRelation>).remove(name)

    val sProperty = (all as LinkedHashMap<String, SProperty>).remove(name)
    sProperty?.entity = null
  }
}

sealed class SProperty(val name: String, val isInput: Boolean, val isUnique: Boolean) {
  internal var entity: SEntity? = null
}

  class SField(
    name: String,
    val type: FType,
    val isOptional: Boolean,
    isInput: Boolean,
    isUnique: Boolean
  ): SProperty(name, isInput, isUnique)

  sealed class SRelation(
    name: String,
    val type: RType,
    val ref: SEntity,
    isInput: Boolean,
    isUnique: Boolean
  ): SProperty(name, isInput, isUnique)

    class SReference(
      name: String,
      type: RType,
      ref: SEntity,
      val isOptional: Boolean,
      isInput: Boolean,
      isUnique: Boolean
    ): SRelation(name, type, ref, isInput, isUnique)

    class SCollection(
      name: String,
      type: RType,
      ref: SEntity,
      isInput: Boolean,
      isUnique: Boolean
    ): SRelation(name, type, ref, isInput, isUnique)

/* ------------------------- helpers -------------------------*/
fun SEntity.own(prop: String, ref: SEntity) {
  if (ref.type != EType.DETAIL)
    throw Exception("Only entities of type DETAIL can be owned. '${name}:$prop' trying to own '${ref.name}'.")

  if (ref.ownedBy != null)
    throw Exception("SEntity '${ref.name}' already owned by '$name'.")

  ref.ownedBy = this
}

fun SEntity.clone(): SEntity = SEntity(name, type).also {
  for (prop in all.values) {
    when (prop) {
      is SField -> it.add(prop)
      is SRelation -> {
        // clone only entities from the original schema
        val ref = if (prop.ref.schema != null) prop.ref.clone() else prop.ref
        when (prop) {
          is SReference -> it.add(SReference(prop.name, prop.type, ref, prop.isOptional, prop.isInput, prop.isUnique))
          is SCollection -> it.add(SCollection(prop.name, prop.type, ref, prop.isInput, prop.isUnique))
        }
      }
    }
  }
}