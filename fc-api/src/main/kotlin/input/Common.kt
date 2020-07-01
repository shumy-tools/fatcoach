package fc.api.input

import fc.api.*
import fc.dsl.input.CreateParser
import fc.dsl.input.UpdateParser
import org.antlr.v4.runtime.tree.TerminalNode
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

class ValueProxy {
  private val ctx: Any
  private val _isUpdate: Boolean
  private val _args: Map<String, Any>

  private val aNull: TerminalNode?
  private val aText: TerminalNode?
  private val aLong: TerminalNode?
  private val aDouble: TerminalNode?
  private val aBool: TerminalNode?
  private val aTime: TerminalNode?
  private val aDate: TerminalNode?
  private val aDateTime: TerminalNode?
  private val aParam: TerminalNode?

  constructor(value: CreateParser.ValueContext, args: Map<String, Any>, isUpdate: Boolean) {
    ctx = value; _args = args; _isUpdate = isUpdate
    aNull = ctx.NULL()
    aText = ctx.TEXT()
    aLong = ctx.LONG()
    aDouble = ctx.DOUBLE()
    aBool = ctx.BOOL()
    aTime = ctx.TIME()
    aDate = ctx.DATE()
    aDateTime = ctx.DATETIME()
    aParam = ctx.PARAM()
  }

  constructor(value: UpdateParser.ValueContext, args: Map<String, Any>, isUpdate: Boolean) {
    ctx = value; _args = args; _isUpdate = isUpdate
    aNull = ctx.NULL()
    aText = ctx.TEXT()
    aLong = ctx.LONG()
    aDouble = ctx.DOUBLE()
    aBool = ctx.BOOL()
    aTime = ctx.TIME()
    aDate = ctx.DATE()
    aDateTime = ctx.DATETIME()
    aParam = ctx.PARAM()
  }

  fun process(prop: SProperty): Any? {
    return when (prop) {
      is SField -> processField(prop)
      is SReference -> processRefID(prop, prop.isOptional)
      is SCollection -> {
        if (aParam != null) {
          val key = aParam.text.substring(1)
          _args.getRefIDList(key)
        } else
          throw Exception("Expecting a collection or a parameter for '${prop.entity!!.name}.${prop.name}'.")
      }
    }
  }

  fun processField(field: SField): Any? = when {
    aNull != null -> {
      if (!field.isOptional)
        throw Exception("Expecting an input value for non-optional field '${field.entity!!.name}.${field.name}'.")
      null
    }

    aText != null -> {
      field.tryType(FType.TEXT)
      aText.text.substring(1, aText.text.length-1)
    }

    aLong != null -> {
      field.tryType(FType.LONG)
      val value = aLong.text
      if (field.type == FType.INT) value.toInt() else value.toLong()
    }

    aDouble != null -> {
      field.tryType(FType.DOUBLE)
      val value = aDouble.text
      if (field.type == FType.FLOAT) value.toFloat() else value.toDouble()
    }

    aBool != null -> {
      field.tryType(FType.BOOL)
      aBool.text!!.toBoolean()
    }

    aTime != null -> {
      field.tryType(FType.TIME)
      LocalTime.parse(aTime.text.substring(1))
    }

    aDate != null -> {
      field.tryType(FType.DATE)
      LocalDate.parse(aDate.text.substring(1))
    }

    aDateTime != null -> {
      field.tryType(FType.DATETIME)
      LocalDateTime.parse(aDateTime.text.substring(1))
    }

    aParam != null -> {
      val key = aParam.text.substring(1)
      val value = _args[key] ?: throw Exception("Expecting an argument value for '${field.entity!!.name}.${field.name}'.")
      field.tryType(TypeEngine.convert(value.javaClass.kotlin))
      value
    }

    else -> throw Exception("Expecting typeOf (null, text, long, double, bool, time, data, datetime, param) for '${field.entity!!.name}.${field.name}'.")
  }

  fun processRefID(ref: SRelation, isOptional: Boolean): RefID {
    if (ref.type == RType.OWNED) {
      if (_isUpdate)
        throw Exception("Updating an owned reference/collection is not supported. Update '${ref.entity!!.name}.${ref.name}' using the @parent field.")
      else
        throw Exception("Expecting an object for '${ref.entity!!.name}.${ref.name}'.")
    }

    return when {
      aNull != null -> {
        if (!isOptional)
          throw Exception("Expecting an input value for a non-optional reference '${ref.entity!!.name}.${ref.name}'.")
        RefID()
      }

      aLong != null -> RefID(aLong.text.toLong())

      aParam != null -> {
        val key = aParam.text.substring(1)
        _args.getRefID(key)
      }

      else -> throw Exception("Expecting typeOf (null, long, param=RefID) for '${ref.entity!!.name}.${ref.name}'.")
    }
  }
}

fun Map<String, Any>.getRefID(key: String): RefID {
  val value = this[key] ?: throw Exception("Expecting an argument value for '$key'.")
  return when (value) {
    is RefID -> value
    is RefTree -> value.root
    else -> throw Exception("Expecting an argument typeOf RefID or RefTree for '$key', found '${value.javaClass.kotlin.simpleName}'.")
  }
}

fun Map<String, Any>.getRefIDList(key: String): List<RefID> {
  val value = this[key] ?: throw Exception("Expecting an argument value for '$key'.")
  if (value !is List<*>)
    throw Exception("Expecting a collection from arg '$key'.")

  return value.map {
    when (it) {
      is RefID -> it
      is RefTree -> it.root
      else -> throw Exception("Expecting an argument typeOf List<RefID or RefTree> for '$key', found '${value.javaClass.kotlin.simpleName}'.")
    }
  }
}