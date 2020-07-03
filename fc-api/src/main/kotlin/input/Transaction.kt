package fc.api.input

import fc.api.RefID
import fc.api.SEntity
import fc.api.SProperty
import fc.api.SRelation

class Transaction {
  val all: List<FcInstruction> = mutableListOf()

  fun find(refID: RefID) = all.filter { it.refID == refID }

  internal fun add(instruction: FcInstruction) {
    (all as MutableList<FcInstruction>).add(instruction)
  }
}

sealed class FcInstruction(val entity: SEntity, val refID: RefID) {
  val accessed = mutableSetOf<SProperty>()
}

class FcCreate internal constructor(entity: SEntity, refID: RefID): FcInstruction(entity, refID) {
  internal lateinit var values: Map<String, Any?>
  override fun toString() = "FcCreate(${entity.name}) @id=$refID - ${values.text()}"
}

class FcUpdate internal constructor(entity: SEntity, refID: RefID): FcInstruction(entity, refID) {
  internal lateinit var values: Map<String, Any?>
  override fun toString() = "FcUpdate(${entity.name}) @id=$refID - ${values.text()}"
}

class FcDelete internal constructor(entity: SEntity, refID: RefID): FcInstruction(entity, refID) {
  override fun toString() = "FcDelete(${entity.name}) @id=$refID"
}

@Suppress("UNCHECKED_CAST")
private fun List<*>.text(): String = map { value ->
  val type = value?.let { it.javaClass.kotlin.simpleName }
  when(value) {
    is List<*> -> value.text()
    is Map<*, *> -> (value as Map<String, *>).text()
    else -> "($type@${value})"
  }
}.toString()

@Suppress("UNCHECKED_CAST")
private fun Map<String, *>.text(): String = map { (key, value) ->
  val type = value?.let { it.javaClass.kotlin.simpleName }
  val textValue = when(value) {
    is List<*> -> value.text()
    is Map<*, *> -> (value as Map<String, *>).text()
    else -> "($type@${value})"
  }

  key to textValue
}.toMap().toString()