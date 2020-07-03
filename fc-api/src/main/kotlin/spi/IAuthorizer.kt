package fc.api.spi

import fc.api.SProperty
import fc.api.base.User
import fc.api.input.FcInstruction

interface IAuthorizer {
  fun canInput(instruction: FcInstruction)
  fun canQuery(props: Set<SProperty>)
}

class UnauthorizedException(val user: User, msg: String): Exception(msg)