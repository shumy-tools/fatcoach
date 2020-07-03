package fc.api.base

import fc.api.RefID

const val ANY = "@any" // defines any role
val ANONYMOUS = User("anonymous", "no-email", emptyList()) // defines default anonymous user

data class User(val name: String, val email: String, val roles: List<RefID>)

data class Role(val name: String)

// TODO: User >--< Role >--< Permission