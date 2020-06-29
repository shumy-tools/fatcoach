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
  override fun toString() = "FcInsert - { entity=${entity.name}, values=${values.map { it.key.name to it.value }} }"
}

class FcUpdate(entity: SEntity, refID: RefID, val values: Map<SProperty, Any?>): FcInstruction(entity, refID) {
  override fun toString() = "FcUpdate - { entity=${entity.name}, id=${refID.id} values=${values.map { it.key.name to it.value }} }"
}

class FcDelete(entity: SEntity, refID: RefID): FcInstruction(entity, refID) {
  override fun toString() = "FcDelete - { entity=${entity.name}, id=${refID.id} }"
}