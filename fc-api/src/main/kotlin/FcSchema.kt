package fc.api

enum class EType { MASTER, DETAIL, TRAIT }
enum class RType { OWNED, LINKED }

class FcSchema {
  val all: Map<String, SEntity> = linkedMapOf()
  val masters: Map<String, SEntity> = linkedMapOf()
  val details: Map<String, SEntity> = linkedMapOf()
  val traits: Map<String, SEntity> = linkedMapOf()

  private val entityListeners = mutableListOf<(oper: OType, sEntity: SEntity) -> Unit>()
  internal val propertyListeners = mutableListOf<(oper: OType, sEntity: SEntity, sProperty: SProperty) -> Unit>()

  infix fun add(sEntity: SEntity) {
    if (sEntity.schema != null)
      throw Exception("SEntity '${sEntity.name}' is already being used by different FcSchema")

    if (all.containsKey(sEntity.name))
      throw Exception("SEntity '${sEntity.name}' already exists in the FcSchema")

    sEntity.schema = this
    (all as LinkedHashMap<String, SEntity>)[sEntity.name] = sEntity

    when (sEntity.type) {
      EType.MASTER -> (masters as LinkedHashMap<String, SEntity>)[sEntity.name] = sEntity
      EType.DETAIL -> (details as LinkedHashMap<String, SEntity>)[sEntity.name] = sEntity
      EType.TRAIT -> (traits as LinkedHashMap<String, SEntity>)[sEntity.name] = sEntity
    }

    entityListeners.forEach { it(OType.ADD, sEntity) }
  }

  infix fun rmv(name: String) {
    (masters as LinkedHashMap<String, SEntity>).remove(name)
    (details as LinkedHashMap<String, SEntity>).remove(name)
    (traits as LinkedHashMap<String, SEntity>).remove(name)

    val sEntity = (all as LinkedHashMap<String, SEntity>).remove(name)
    sEntity?.schema = null

    if (sEntity != null)
      entityListeners.forEach { it(OType.REMOVE, sEntity) }
  }

  fun onEntity(listener: (oper: OType, sEntity: SEntity) -> Unit) {
    entityListeners.add(listener)
  }

  fun onProperty(listener: (oper: OType, sEntity: SEntity, sProperty: SProperty) -> Unit) {
    propertyListeners.add(listener)
  }
}

class SMultiplicity(val start: String, val end: String)

class SEntity(val name: String, val type: EType) {
  init {
    this add SField(ID, FType.REFID, isInput = false, isUnique = true) // set the default @id property
  }

  internal var schema: FcSchema? = null
  internal var ownedBy: SEntity? = null

  val all: Map<String, SProperty> = linkedMapOf()
  val fields: Map<String, SField> = linkedMapOf()
  val refs: Map<String, SReference> = linkedMapOf()
  val cols: Map<String, SCollection> = linkedMapOf()

  /* ------------------------- owned/linked -------------------------*/
  val ownedRefs: List<SReference>
    get() = refs.values.filter { it.type == RType.OWNED }

  val linkedRefs: List<SReference>
    get() = refs.values.filter { it.type == RType.LINKED }

  val ownedCols: List<SCollection>
    get() = cols.values.filter { it.type == RType.OWNED }

  val linkedCols: List<SCollection>
    get() = cols.values.filter { it.type == RType.LINKED }

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


  infix fun add(sProperty: SProperty) {
    if (sProperty.entity != null)
      throw Exception("SProperty '${sProperty.name}' is already being used by a different SEntity '${sProperty.entity!!.name}'")

    if (all.containsKey(sProperty.name))
      throw Exception("SProperty '${sProperty.name}' already exists in the SEntity '$name'")

    sProperty.entity = this
    (all as LinkedHashMap<String, SProperty>)[sProperty.name] = sProperty

    when (sProperty) {
      is SField -> (fields as LinkedHashMap<String, SField>)[sProperty.name] = sProperty

      is SReference -> {
        if (sProperty.type == RType.OWNED) {
          own(sProperty.name, sProperty.ref)
          sProperty.ref add SField(PARENT, FType.REFID) // set the @parent property for owned entities
        }

        (refs as LinkedHashMap<String, SReference>)[sProperty.name] = sProperty
      }

      is SCollection -> {
        if (sProperty.type == RType.OWNED) {
          own(sProperty.name, sProperty.ref)
          sProperty.ref add SField(PARENT, FType.REFID) // set the @parent property for owned entities
        }

        (cols as LinkedHashMap<String, SCollection>)[sProperty.name] = sProperty
      }
    }

    schema!!.propertyListeners.forEach { it(OType.ADD, this, sProperty) }
  }

  infix fun rmv(name: String) {
    if (name == ID)
      throw Exception("Cannot remove '$ID' from a SEntity")

    (fields as LinkedHashMap<String, SField>).remove(name)
    (refs as LinkedHashMap<String, SReference>).remove(name)
    (cols as LinkedHashMap<String, SCollection>).remove(name)

    val sProperty = (all as LinkedHashMap<String, SProperty>).remove(name)
    sProperty?.entity = null

    if (sProperty != null)
      schema!!.propertyListeners.forEach { it(OType.REMOVE, this, sProperty) }
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

class SReference(
  name: String,
  val type: RType,
  val ref: SEntity,
  val isOptional: Boolean = false,
  isInput: Boolean = true,
  isUnique: Boolean = false
): SProperty(name, isInput, isUnique)

class SCollection(
  name: String,
  val type: RType,
  val ref: SEntity,
  val multiplicity: SMultiplicity = SMultiplicity("0", "*"),
  isInput: Boolean = true,
  isUnique: Boolean = false
): SProperty(name, isInput, isUnique)

/* ------------------------- helpers -------------------------*/
fun SEntity.own(prop: String, ref: SEntity) {
  if (ref.type != EType.DETAIL)
    throw Exception("Only entities of type DETAIL can be owned. '${name}:$prop' trying to own '${ref.name}'")

  if (ref.ownedBy != null)
    throw Exception("SEntity '${ref.name}' already owned by '$name'")

  ref.ownedBy = this
}