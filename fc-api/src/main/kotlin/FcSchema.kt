package fc.api

enum class EType { MASTER, DETAIL, TRAIT }
enum class RType { OWNED, LINKED }

class FcSchema {
  var committed = false
    internal set

  val all: Map<String, SEntity> = linkedMapOf()
  val masters: Map<String, SEntity> = linkedMapOf()
  val details: Map<String, SEntity> = linkedMapOf()
  val traits: Map<String, SEntity> = linkedMapOf()

  fun find(entity: String): SEntity {
    return all[entity] ?: throw Exception("SEntity '$entity' not found!")
  }

  infix fun add(sEntity: SEntity) {
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
      EType.TRAIT -> (traits as LinkedHashMap<String, SEntity>)[sEntity.name] = sEntity
    }
  }

  infix fun del(name: String) {
    if (committed)
      throw Exception("Cannot change a committed schema. Invoke 'change' and set the desired changes.")

    (masters as LinkedHashMap<String, SEntity>).remove(name)
    (details as LinkedHashMap<String, SEntity>).remove(name)
    (traits as LinkedHashMap<String, SEntity>).remove(name)

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

class SMultiplicity(val start: String, val end: String)

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

  /* ------------------------- inputs -------------------------*/
  val inputFields: List<SField>
    get() = fields.values.filter { it.isInput }

  val inputOwnedRefs: List<SReference>
    get() = ownedRefs.filter { it.isInput }

  val inputLinkedRefs: List<SReference>
    get() = linkedRefs.filter { it.isInput }

  val inputOwnedCols: List<SCollection>
    get() = ownedCols.filter { it.isInput }

  val inputLinkedCols: List<SCollection>
    get() = linkedCols.filter { it.isInput }

  private fun checkCommitted() {
    if (schema == null)
      throw Exception("Cannot change an orphan entity. Add the entity to a FcSchema.")

    if (schema!!.committed)
      throw Exception("Cannot change a committed schema. Invoke 'change' and set the desired changes.")
  }

  infix fun add(sProperty: SProperty) {
    checkCommitted()

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
          sProperty.ref add SReference(PARENT, RType.LINKED, this) // set the @parent property for owned entities
        }

        (rels as LinkedHashMap<String, SRelation>)[sProperty.name] = sProperty
      }

      is SCollection -> {
        if (sProperty.type == RType.OWNED) {
          own(sProperty.name, sProperty.ref)
          sProperty.ref add SReference(PARENT, RType.LINKED, this) // set the @parent property for owned entities
        }

        (rels as LinkedHashMap<String, SRelation>)[sProperty.name] = sProperty
      }
    }
  }

  infix fun del(name: String) {
    checkCommitted()

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
    val isOptional: Boolean = false,
    isInput: Boolean = true,
    isUnique: Boolean = false
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
      val isOptional: Boolean = false,
      isInput: Boolean = true,
      isUnique: Boolean = false
    ): SRelation(name, type, ref, isInput, isUnique)

    class SCollection(
      name: String,
      type: RType,
      ref: SEntity,
      val multiplicity: SMultiplicity = SMultiplicity("0", "*"),
      isInput: Boolean = true,
      isUnique: Boolean = false
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
      is SField -> it add prop
      is SRelation -> {
        // clone only entities from the original schema
        val ref = if (prop.ref.schema != null) prop.ref.clone() else prop.ref
        when (prop) {
          is SReference -> it add SReference(prop.name, prop.type, ref, prop.isOptional, prop.isInput, prop.isUnique)
          is SCollection -> it add SCollection(prop.name, prop.type, ref, prop.multiplicity, prop.isInput, prop.isUnique)
        }
      }
    }
  }
}