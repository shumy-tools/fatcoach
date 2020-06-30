package fc.api

import fc.api.input.CreateCompiler
import fc.api.input.DeleteCompiler
import fc.api.input.UpdateCompiler
import fc.api.spi.InputInstructions

class FcTxData internal constructor(private val schema: FcSchema) {
  internal val tx = InputInstructions()

  fun create(dsl: String, args: Map<String, Any>): RefID {
    val compiled = CreateCompiler(dsl, schema, tx, args)
    if (compiled.errors.isNotEmpty())
      throw Exception("Failed to compile create! ${compiled.errors}")

    // TODO: check security on accessed properties?
    // TODO: compute derived values

    return compiled.refID
  }

  fun update(dsl: String) {
    val compiled = UpdateCompiler(dsl, schema)
    if (compiled.errors.isNotEmpty())
      throw Exception("Failed to compile update! ${compiled.errors}")

    // TODO: check security on accessed properties?
    // TODO: compute derived values

    TODO()
  }

  fun delete(dsl: String) {
    val compiled = DeleteCompiler(dsl, schema)
    if (compiled.errors.isNotEmpty())
      throw Exception("Failed to compile delete! ${compiled.errors}")

    // TODO: check security on accessed properties?

    TODO()
  }
}