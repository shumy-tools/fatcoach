package fc.api

import fc.api.input.CreateCompiler
import fc.api.input.DeleteCompiler
import fc.api.input.UpdateCompiler
import fc.api.spi.InputInstructions

class FcTxData internal constructor(private val schema: FcSchema) {
  internal val tx = InputInstructions()

  fun create(dsl: String, vararg args: Pair<String, Any>) = create(dsl, args.toMap())
  fun create(dsl: String, args: Map<String, Any>): RefID {
    val compiled = CreateCompiler(dsl, schema, tx, args)
    if (compiled.errors.isNotEmpty())
      throw Exception("Failed to compile create! ${compiled.errors}")

    // TODO: check security on accessed properties?
    // TODO: compute derived values

    return compiled.refID
  }

  fun update(dsl: String, vararg args: Pair<String, Any>) = update(dsl, args.toMap())
  fun update(dsl: String, args: Map<String, Any>) {
    val compiled = UpdateCompiler(dsl, schema, tx, args)
    if (compiled.errors.isNotEmpty())
      throw Exception("Failed to compile update! ${compiled.errors}")

    // TODO: check security on accessed properties?
    // TODO: compute derived values
  }

  fun delete(dsl: String, arg: RefID? = null) {
    val compiled = DeleteCompiler(dsl, schema, tx, arg)
    if (compiled.errors.isNotEmpty())
      throw Exception("Failed to compile delete! ${compiled.errors}")

    // TODO: check security on accessed properties?
  }
}