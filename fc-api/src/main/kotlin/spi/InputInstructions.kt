package fc.api.spi

import fc.api.OType
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

class FcCreate(entity: SEntity, refID: RefID, val values: Map<String, Any?>): FcInstruction(entity, refID) {
  override fun toString() = "FcInsert - { entity=${entity.name}, values=${values} }"
}

class FcUpdate(entity: SEntity, refID: RefID, val values: Map<String, Any?>): FcInstruction(entity, refID) {
  override fun toString() = "FcUpdate - { entity=${entity.name}, id=${refID.id} values=${values} }"
}

class FcUpdateLink(entity: SEntity, refID: RefID, val rel: SProperty, val oper: OType, val ref: RefID): FcInstruction(entity, refID) {
  override fun toString() = "FcUpdateLink - { entity=${entity.name}, id=${refID.id}, rel=${rel.entity!!.name}:${rel.name}, oper=$oper, ref=${ref.id} }"
}

class FcDelete(entity: SEntity, refID: RefID): FcInstruction(entity, refID) {
  override fun toString() = "FcDelete - { entity=${entity.name}, id=${refID.id} }"
}