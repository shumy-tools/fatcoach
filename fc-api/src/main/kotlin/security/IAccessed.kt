package fc.api.security

import fc.api.SProperty

enum class InstructionType {
  CREATE, UPDATE, DELETE, QUERY
}

interface IAccessed {
  fun canAccess(type: InstructionType, props: Set<SProperty>)
}