package fc.api

import fc.api.input.CreateCompiler
import fc.api.input.DeleteCompiler
import fc.api.input.UpdateCompiler
import fc.api.input.Transaction

class FcTxData internal constructor(private val schema: FcSchema) {
  internal val tx = Transaction()

  fun create(dsl: String, vararg args: Pair<String, Any>) = create(dsl, args.toMap())
  fun create(dsl: String, args: Map<String, Any>): RefTree {
    val compiled = CreateCompiler(dsl, schema, tx, args)
    if (compiled.errors.isNotEmpty())
      throw Exception("Failed to compile create! ${compiled.errors}")

    return compiled.tree
  }

  fun update(dsl: String, vararg args: Pair<String, Any>) = update(dsl, args.toMap())
  fun update(dsl: String, args: Map<String, Any>) {
    val compiled = UpdateCompiler(dsl, schema, tx, args)
    if (compiled.errors.isNotEmpty())
      throw Exception("Failed to compile update! ${compiled.errors}")
  }

  fun delete(dsl: String, arg: RefTree) = delete(dsl, arg.root)
  fun delete(dsl: String, arg: RefID? = null) {
    val compiled = DeleteCompiler(dsl, schema, tx, arg)
    if (compiled.errors.isNotEmpty())
      throw Exception("Failed to compile delete! ${compiled.errors}")
  }
}