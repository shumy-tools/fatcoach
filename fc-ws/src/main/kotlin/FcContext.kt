package fc.ws

import fc.api.base.User
import fc.api.input.Transaction

object FcContext {
  private val tUser = ThreadLocal<User>()
  private val tTx = ThreadLocal<Transaction>()

  var user: User
    get() = tUser.get()
    internal set(value) = tUser.set(value)

  var tx: Transaction?
    get() = tTx.get()
    internal set(value) = tTx.set(value)

  fun clear() {
    tUser.set(null)
    tTx.set(null)
  }
}