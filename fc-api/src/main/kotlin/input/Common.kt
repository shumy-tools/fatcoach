package fc.api.input

import fc.api.*
import fc.dsl.input.CreateParser
import fc.dsl.input.UpdateParser
import org.antlr.v4.runtime.tree.TerminalNode
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

class FieldProxy {
  private val ctx: Any
  private val _isUpdate: Boolean
  private val _args: Map<String, Any>
  private val tValue: ValueProxy

  constructor(value: CreateParser.ValueContext, args: Map<String, Any>, isUpdate: Boolean) {
    ctx = value; _args = args; _isUpdate = isUpdate
    tValue = ValueProxy(value)
  }

  constructor(value: UpdateParser.ValueContext, args: Map<String, Any>, isUpdate: Boolean) {
    ctx = value; _args = args; _isUpdate = isUpdate
    tValue = ValueProxy(value)
  }

  fun process(prop: SProperty): Any? {
    return when (prop) {
      is SField -> processField(prop)
      is SReference -> processRefID(prop, prop.isOptional)
      is SCollection -> {
        if (tValue.aParam != null) {
          val key = tValue.aParam.text.substring(1)
          _args.getRefIDList(key)
        } else
          throw Exception("Expecting a collection or parameters for '${prop.entity!!.name}.${prop.name}'.")
      }
    }
  }

  fun processRefID(ref: SRelation, isOptional: Boolean): RefID {
    if (ref.type == RType.OWNED) {
      if (_isUpdate)
        throw Exception("Updating an owned reference/collection is not supported. Update '${ref.entity!!.name}.${ref.name}' using the @parent field.")
      else
        throw Exception("Expecting an object for '${ref.entity!!.name}.${ref.name}'.")
    }

    return when {
      tValue.aNull != null -> {
        if (!isOptional)
          throw Exception("Expecting an input value for a non-optional reference '${ref.entity!!.name}.${ref.name}'.")
        RefID()
      }

      tValue.aLong != null -> RefID(tValue.aLong.text.toLong())

      tValue.aParam != null -> {
        val key = tValue.aParam.text.substring(1)
        _args.getRefID(key)
      }

      else -> throw Exception("Expecting typeOf (null, long, param=RefID) for '${ref.entity!!.name}.${ref.name}'.")
    }
  }

  private fun processField(field: SField): Any? = when {
    tValue.aNull != null -> {
      if (!field.isOptional)
        throw Exception("Expecting an input value for non-optional field '${field.entity!!.name}.${field.name}'.")
      null
    }

    tValue.aText != null -> {
      field.tryType(FType.TEXT)
      tValue.aText.text.substring(1, tValue.aText.text.length-1)
    }

    tValue.aLong != null -> {
      field.tryType(FType.LONG)
      val value = tValue.aLong.text
      if (field.type == FType.INT) value.toInt() else value.toLong()
    }

    tValue.aDouble != null -> {
      field.tryType(FType.DOUBLE)
      val value = tValue.aDouble.text
      if (field.type == FType.FLOAT) value.toFloat() else value.toDouble()
    }

    tValue.aBool != null -> {
      field.tryType(FType.BOOL)
      tValue.aBool.text!!.toBoolean()
    }

    tValue.aTime != null -> {
      field.tryType(FType.TIME)
      LocalTime.parse(tValue.aTime.text.substring(1))
    }

    tValue.aDate != null -> {
      field.tryType(FType.DATE)
      LocalDate.parse(tValue.aDate.text.substring(1))
    }

    tValue.aDateTime != null -> {
      field.tryType(FType.DATETIME)
      LocalDateTime.parse(tValue.aDateTime.text.substring(1))
    }

    tValue.aParam != null -> {
      val key = tValue.aParam.text.substring(1)
      val value = _args[key] ?: throw Exception("Expecting an argument value for '${field.entity!!.name}.${field.name}'.")
      field.tryType(TypeEngine.convert(value.javaClass.kotlin))
      value
    }

    else -> throw Exception("Expecting typeOf (null, text, long, double, bool, time, data, datetime, param) for '${field.entity!!.name}.${field.name}'.")
  }
}

class ValueProxy {
  val aNull: TerminalNode?
  val aText: TerminalNode?
  val aLong: TerminalNode?
  val aDouble: TerminalNode?
  val aBool: TerminalNode?
  val aTime: TerminalNode?
  val aDate: TerminalNode?
  val aDateTime: TerminalNode?
  val aParam: TerminalNode?

  constructor(ctx: CreateParser.ValueContext) {
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

  constructor(ctx: UpdateParser.ValueContext) {
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

  fun parse(): Any = when {
    aText != null -> aText.text.substring(1, aText.text.length-1)
    aLong != null -> aLong.text.toLong()
    aDouble != null -> aDouble.text.toDouble()
    aBool != null -> aBool.text!!.toBoolean()
    aTime != null -> LocalTime.parse(aTime.text.substring(1))
    aDate != null -> LocalDate.parse(aDate.text.substring(1))
    aDateTime != null -> LocalDateTime.parse(aDateTime.text.substring(1))

    aNull != null -> throw Exception("Unsupported null inside a map!")
    aParam != null -> throw Exception("Unsupported parameter inside a map!")
    else -> throw Exception("Unrecognized parameter type!")
  }
}

class ListProxy {
  private val items: List<Any>?

  constructor(ctx: CreateParser.ListContext) {
    items = ctx.item()?.map { if (it.value() != null) ValueProxy(it.value()).parse() else MapProxy(it.data()).parse() }
  }

  constructor(ctx: UpdateParser.ListContext) {
    items = ctx.item()?.map { if (it.value() != null) ValueProxy(it.value()).parse() else MapProxy(it.data()).parse() }
  }

  fun parse(): List<Any> = items ?: emptyList()
}

class EntryProxy {
  val key: String
  val data: Any?
  val list: List<Any>?
  val value: Any?

  constructor(ctx: CreateParser.EntryContext) {
    key = ctx.ID().text
    data = ctx.data()?.let { MapProxy(it).parse() }
    list = ctx.list()?.item()?.map { if (it.value() != null) ValueProxy(it.value()).parse() else MapProxy(it.data()).parse() }
    value = ctx.value()?.let { ValueProxy(it).parse() }
  }

  constructor(ctx: UpdateParser.EntryContext) {
    key = ctx.ID().text
    data = ctx.data()?.let { MapProxy(it).parse() }
    list = ctx.list()?.item()?.map { if (it.value() != null) ValueProxy(it.value()).parse() else MapProxy(it.data()).parse() }
    value = ctx.value()?.let { ValueProxy(it).parse() }
  }

  fun parse(): Pair<String, Any> {
    val eValue = when {
      data != null -> data
      list != null -> list
      value != null -> value
      else -> throw Exception("Bug - EntryProxy.parse() - 1. Unrecognized dsl branch!")
    }

    return Pair(key, eValue)
  }
}

class MapProxy {
  val entries: Map<String, Any>?
  constructor(ctx: CreateParser.DataContext) {
    entries = ctx.entry()?.map { EntryProxy(it).parse() }?.toMap()
  }

  constructor(ctx: UpdateParser.DataContext) {
    entries = ctx.entry()?.map { EntryProxy(it).parse() }?.toMap()
  }

  fun parse(): Map<String, Any> = entries ?: emptyMap()
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