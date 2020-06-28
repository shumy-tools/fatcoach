package fc.api

import fc.api.query.FcData
import fc.api.spi.FcCreate
import fc.api.spi.FcDelete
import fc.api.spi.FcUpdate
import fc.api.spi.InputInstructions

class FcTxData(private val schema: FcSchema) {
  internal val instructions = InputInstructions()

  fun create(entity: String, data: FcData): RefID {
    val sEntity = schema.all[entity] ?: throw Exception("SEntity '$entity' not found!")
    return create(sEntity, data)
  }

  fun create(sEntity: SEntity, data: FcData): RefID {
    // check and create all input (fields / linkedRefs / linkedCols)
    val inputFields = sEntity.inputFields.map { it.name to it.check(data[it.name]) }.toMap()
    val inputLinkedRefs = sEntity.inputLinkedRefs.map { it.name to it.checkRef(data[it.name]) }.toMap()
    val inputLinkedCols = sEntity.inputLinkedCols.map { it.name to it.checkRefs(data[it.name]) }.toMap()

    // check and create recursively all (input ownedRefs / ownedCols)
    val inputOwnedRefs = sEntity.inputOwnedRefs.map { it.name to it.convert(data[it.name]) }.toMap()
    val inputOwnedCols = sEntity.inputOwnedCols.map { it.name to it.convert(data[it.name]) }.toMap()

    val refID = RefID()
    val inputs = inputFields.plus(inputLinkedRefs).plus(inputLinkedCols).plus(inputOwnedRefs).plus(inputOwnedCols)
    instructions.add(FcCreate(sEntity, refID, inputs))
    return refID
  }

  fun update(entity: String, refID: RefID, data: FcData) {
    val sEntity = schema.all[entity] ?: throw Exception("SEntity '$entity' not found!")
    return update(sEntity, refID, data)
  }

  fun update(sEntity: SEntity, refID: RefID, data: FcData) {
    // filtering existing input properties
    val inputProperties = data.keys.mapNotNull {
      val sProperty = sEntity.all[it]
      if (sProperty != null && sProperty.isInput) sProperty else null
    }

    val owned = inputProperties.find { it is SReference && it.type == RType.OWNED || it is SCollection && it.type == RType.OWNED }
    if (owned != null)
      throw Exception("Cannot add/remove owned references/collections. Use update with @parent")

    // check and create all input (fields / linkedRefs / linkedCols)
    val inputFields = inputProperties.filterIsInstance<SField>().map { it.name to it.check(data[it.name]) }.toMap()
    val inputLinkedRefs = inputProperties.filterIsInstance<SReference>().map { it.name to it.checkRef(data[it.name], true) }.toMap()
    val inputLinkedCols = inputProperties.filterIsInstance<SCollection>().map { it.name to it.checkRefs(data[it.name], true) }.toMap()

    val inputs = inputFields.plus(inputLinkedRefs).plus(inputLinkedCols)
    instructions.add(FcUpdate(sEntity, refID, inputs))
  }

  fun delete(entity: String, refID: RefID) {
    val sEntity = schema.all[entity] ?: throw Exception("SEntity '$entity' not found!")
    return delete(sEntity, refID)
  }

  fun delete(sEntity: SEntity, refID: RefID) {
    instructions.add(FcDelete(sEntity, refID))
  }

  @Suppress("UNCHECKED_CAST")
  private fun SReference.convert(value: Any?): RefID? {
    value ?: if (isOptional)
      throw Exception("Expecting owned value for '${entity!!.name}:$name'")

    if (value == null) return null

    val data = try {
      value as FcData
    } catch (ex: Exception) {
      throw Exception("Expecting map of 'FcData' for '${entity!!.name}:$name', found ${value.javaClass.kotlin.simpleName}")
    }

    return create(ref, data)
  }

  @Suppress("UNCHECKED_CAST")
  private fun SCollection.convert(value: Any?): List<RefID> {
    value ?: throw Exception("Expecting owned collection for '${entity!!.name}:$name'")

    val data = try {
      (value as List<*>).forEach { it as FcData }
      value as List<FcData>
    } catch (ex: Exception) {
      throw Exception("Expecting collection of 'FcData' for '${entity!!.name}:$name', found ${value.javaClass.kotlin.simpleName}")
    }

    return data.map { create(ref, it) }
  }
}

/* ------------------------- helpers -------------------------*/
private fun SField.check(value: Any?): Any? {
  value ?: if (isOptional)
    throw Exception("Expecting field value for '${entity!!.name}:$name'")

  if (value != null && !TypeEngine.check(type, value.javaClass.kotlin))
    throw Exception("Expecting field type '$type' for '${entity!!.name}:$name', found ${value.javaClass.kotlin.simpleName}")

  return value
}

private fun SReference.checkRef(value: Any?, isUpdate: Boolean = false): Any? {
  value ?: if (isOptional)
    throw Exception("Expecting reference value for '${entity!!.name}:$name'")

  if (isUpdate) {
    if (value != null && value !is RefLink)
      throw Exception("On update, expecting reference type 'RefLink' for '${entity!!.name}:$name', found ${value.javaClass.kotlin.simpleName}")
  } else {
    if (value != null && value !is RefID)
      throw Exception("On create, expecting reference type 'RefID' for '${entity!!.name}:$name', found ${value.javaClass.kotlin.simpleName}")
  }

  return value
}

@Suppress("UNCHECKED_CAST")
private fun SCollection.checkRefs(value: Any?, isUpdate: Boolean = false): List<Any> {
  value ?: throw Exception("Expecting collection for '${entity!!.name}:$name'")

  return if (isUpdate) {
    try {
      (value as List<*>).forEach { it as RefLink }
      value as List<Any>
    } catch (ex: Exception) {
      throw Exception("On update, expecting collection of 'RefLink' for '${entity!!.name}:$name', found ${value.javaClass.kotlin.simpleName}")
    }
  } else {
    try {
      (value as List<*>).forEach { it as RefID }
      value as List<Any>
    } catch (ex: Exception) {
      throw Exception("On create, expecting collection of 'RefID' for '${entity!!.name}:$name', found ${value.javaClass.kotlin.simpleName}")
    }
  }
}