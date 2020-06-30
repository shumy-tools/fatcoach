package fc.api.spi

import fc.api.RefID
import fc.api.SEntity
import fc.api.SProperty

class InputInstructions {
  val all: List<FcInstruction> = mutableListOf()

  internal fun add(instruction: FcInstruction) {
    (all as MutableList<FcInstruction>).add(instruction)
  }
}

sealed class FcInstruction(val entity: SEntity, val refID: RefID)

class FcCreate(entity: SEntity, refID: RefID, val values: Map<SProperty, Any?>): FcInstruction(entity, refID) {
  override fun toString() = "FcInsert(${entity.name}) @id=$refID - ${values.toText()}"
}

class FcUpdate(entity: SEntity, refID: RefID, val values: Map<SProperty, Any?>): FcInstruction(entity, refID) {
  override fun toString() = "FcUpdate(${entity.name}) @id=$refID - ${values.toText()}"
}

class FcDelete(entity: SEntity, refID: RefID): FcInstruction(entity, refID) {
  override fun toString() = "FcDelete(${entity.name}) @id=$refID"
}

@Suppress("UNCHECKED_CAST")
private fun Map<SProperty, Any?>.toText() = map { (key, value) ->
  val type = value?.let { it.javaClass.kotlin.simpleName }
  val textValue = when(value) {
    is List<*> -> value.text()
    is Map<*, *> -> (value as Map<String, *>).text()
    else -> "($type@${value})"
  }

  key.name to textValue
}.toMap().toString()

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