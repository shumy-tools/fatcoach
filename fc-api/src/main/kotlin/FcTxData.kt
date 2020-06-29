package fc.api

import fc.api.input.CreateCompiler
import fc.api.input.DeleteCompiler
import fc.api.input.UpdateCompiler
import fc.api.spi.FcCreate
import fc.api.spi.FcDelete
import fc.api.spi.FcUpdate
import fc.api.spi.InputInstructions

class FcTxData internal constructor(private val schema: FcSchema) {
  internal val instructions = InputInstructions()

  fun create(dsl: String): RefID {
    val compiled = CreateCompiler(dsl, schema)
    if (compiled.errors.isNotEmpty())
      throw Exception("Failed to compile create! ${compiled.errors}")

    return create(compiled.entity, compiled.data)
  }

  fun create(sEntity: SEntity, data: FcData): RefID {
    // check and create all input (fields / linkedRefs / linkedCols)
    val inputFields = sEntity.inputFields.map { it to it.check(data[it.name]) }.toMap()
    val inputLinkedRefs = sEntity.inputLinkedRefs.map { it to it.checkRef(data[it.name]) }.toMap()
    val inputLinkedCols = sEntity.inputLinkedCols.map { it to it.checkRefs(data[it.name]) }.toMap()

    // check and create recursively all (input ownedRefs / ownedCols)
    val inputOwnedRefs = sEntity.inputOwnedRefs.map { it to it.convert(data[it.name]) }.toMap()
    val inputOwnedCols = sEntity.inputOwnedCols.map { it to it.convert(data[it.name]) }.toMap()

    val refID = RefID()
    val inputs = inputFields.plus(inputLinkedRefs).plus(inputLinkedCols).plus(inputOwnedRefs).plus(inputOwnedCols)
    instructions.add(FcCreate(sEntity, refID, inputs))
    return refID
  }

  fun update(dsl: String) {
    val compiled = UpdateCompiler(dsl, schema)
    if (compiled.errors.isNotEmpty())
      throw Exception("Failed to compile update! ${compiled.errors}")

    return update(compiled.entity, compiled.refID, compiled.data)
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
    val inputFields = inputProperties.filterIsInstance<SField>().map { it to it.check(data[it.name]) }.toMap()
    val inputLinkedRefs = inputProperties.filterIsInstance<SReference>().map { it to it.checkRef(data[it.name], true) }.toMap()
    val inputLinkedCols = inputProperties.filterIsInstance<SCollection>().map { it to it.checkRefs(data[it.name], true) }.toMap()

    val inputs = inputFields.plus(inputLinkedRefs).plus(inputLinkedCols)
    instructions.add(FcUpdate(sEntity, refID, inputs))
  }

  fun delete(dsl: String) {
    val compiled = DeleteCompiler(dsl, schema)
    if (compiled.errors.isNotEmpty())
      throw Exception("Failed to compile delete! ${compiled.errors}")

    return delete(compiled.entity, compiled.refID)
  }

  fun delete(sEntity: SEntity, refID: RefID) {
    instructions.add(FcDelete(sEntity, refID))
  }

  @Suppress("UNCHECKED_CAST")
  private fun SReference.convert(value: Any?): RefID? {
    value ?: if (isOptional)
      throw Exception("Expecting owned value for '${entity!!.name}:$name'")

    if (value == null) return null

    if (value !is Map<*, *>)
      throw Exception("Expecting map of 'FcData' for '${entity!!.name}:$name', found ${value.javaClass.kotlin.simpleName}")

    return create(ref, value as FcData)
  }

  @Suppress("UNCHECKED_CAST")
  private fun SCollection.convert(value: Any?): List<RefID> {
    value ?: throw Exception("Expecting owned collection for '${entity!!.name}:$name'")

    if (value !is List<*>)
      throw Exception("Expecting collection for '${entity!!.name}:$name', found ${value.javaClass.kotlin.simpleName}")

    (value).forEach {
      if (it !is Map<*, *>)
        throw Exception("Expecting collection of 'FcData' for '${entity!!.name}:$name', found ${value.javaClass.kotlin.simpleName}")
    }

    return value.map { create(ref, it as FcData) }
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

  if (value !is List<*>)
    throw Exception("On update, expecting collection for '${entity!!.name}:$name', found ${value.javaClass.kotlin.simpleName}")

  if (isUpdate) {
    value.forEach {
      if (it !is RefLink)
        throw Exception("On update, expecting collection of 'RefLink' for '${entity!!.name}:$name', found ${value.javaClass.kotlin.simpleName}")
    }
  } else {
    value.forEach {
      if (it !is RefID)
        throw Exception("On create, expecting collection of 'RefID' for '${entity!!.name}:$name', found ${value.javaClass.kotlin.simpleName}")
    }
  }

  return value as List<Any>
}