package fc.api.security

import fc.api.SProperty
import fc.api.spi.FcInstruction

interface IAuthorizer {
  fun canInput(instruction: FcInstruction)
  fun canQuery(props: Set<SProperty>)
}