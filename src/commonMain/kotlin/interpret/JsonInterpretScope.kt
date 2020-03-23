package me.haimgr.jsonvalue.interpret

import me.haimgr.jsonvalue.JsonValue
import me.haimgr.jsonvalue.JsonValueException


@DslMarker
internal annotation class JsonInterpretDsl


@JsonInterpretDsl
interface JsonInterpretScope {

    fun asDouble(): Double
    fun asInt(): Int
    fun asLong(): Long
    fun asString(): String
    fun asBoolean(): Boolean
    fun asJsonValue(): JsonValue

    fun getScopeForArrayElements(): List<JsonInterpretScope>
    fun getScopeForObject(): ObjectScope

    fun failScope(cause: Throwable): Nothing

    @JsonInterpretDsl
    interface ObjectScope {

        fun propertyNames(): Collection<String>

        fun property(name: String): JsonInterpretScope

        fun propertyOrNull(name: String): JsonInterpretScope?

    }
}


inline fun <T> JsonInterpretScope.asArray(parseElement: JsonInterpretScope.(index: Int) -> T): List<T> {
    return this.getScopeForArrayElements()
        .mapIndexed { index, scope -> scope.parse { parseElement(index) } }
}

inline fun <T> JsonInterpretScope.asObject(parseObject: JsonInterpretScope.ObjectScope.() -> T): T {
    val objectScope = this.getScopeForObject()
    return parse { objectScope.parseObject() }
}

inline fun <T> JsonInterpretScope.parse(parseInterpret: JsonInterpretScope.() -> T): T {
    return try {
        parseInterpret()
    } catch (e: Throwable) {
        this.failScope(e)
    }
}

fun JsonValue.interpretScope(): JsonInterpretScope =
    JsonInterpretScopeImpl(this.rawValue, null, null)

private class JsonValueObjectScope(
    private val interpretScope: JsonInterpretScopeImpl,
    private val rawValue: Map<*, *>
) : JsonInterpretScope.ObjectScope {

    @Suppress("UNCHECKED_CAST")
    override fun propertyNames(): Collection<String> = rawValue.keys as Collection<String>

    override fun property(name: String): JsonInterpretScope {
        if (name !in rawValue) interpretScope.fail("Missing property '$name'.")
        if (rawValue[name] == null) interpretScope.fail("Null value for property '$name'.")
        return JsonInterpretScopeImpl(
            rawValue = rawValue[name],
            parent = interpretScope,
            at = name
        )
    }

    override fun propertyOrNull(name: String): JsonInterpretScope? {
        if (rawValue[name] == null) return null
        return JsonInterpretScopeImpl(
            rawValue = rawValue[name],
            parent = interpretScope,
            at = name
        )
    }

}

private class JsonInterpretScopeImpl constructor(
    private val rawValue: Any?,
    private val parent: JsonInterpretScopeImpl?, private val at: Any?
) : JsonInterpretScope {

    private class InterpretException(message: String, cause: Throwable?, val rootId: Any) : JsonValueException(message, cause)

    private val rootId: Any = parent?.rootId ?: Any()

    override fun asDouble(): Double {
        return (rawValue as? Number)?.toDouble()
            ?: asStringOrNull()?.toDoubleOrNull()
            ?: fail("Cannot convert to Double. value: '$rawValue'")
    }

    override fun asInt(): Int {
        return (rawValue as? Number)?.toInt()
            ?: asStringOrNull()?.toIntOrNull()
            ?: fail("Cannot convert to Int. value: '$rawValue'")
    }

    override fun asLong(): Long {
        return (rawValue as? Number)?.toLong()
            ?: asStringOrNull()?.toLongOrNull()
            ?: fail("Cannot convert to Long. value: '$rawValue'")
    }

    override fun asString(): String {
        return asStringOrNull() ?: fail("Cannot convert to String.")
    }

    private fun asStringOrNull(): String? {
        return when (rawValue) {
            null, is String, is Number, is Boolean -> rawValue.toString()
            else -> null
        }
    }

    override fun asBoolean(): Boolean {
        return when (rawValue) {
            is Boolean -> rawValue
            "true" -> true
            "false" -> false
            else -> fail("Cannot convert to Boolean.")
        }
    }

    override fun asJsonValue(): JsonValue =
        JsonValue(rawValue)

    private val pointer: String
        get() {
            return generateSequence(this, JsonInterpretScopeImpl::parent)
                .toList()
                .asReversed()
                .joinToString(prefix = "/", separator = ""){ it.at?.toString() ?: "" }
        }

    override fun failScope(cause: Throwable): Nothing {
        if (cause is InterpretException && cause.rootId == rootId) throw cause
        throw InterpretException(
            "Parsing failed at $pointer: $cause",
            null,
            rootId = rootId
        )
    }

    fun fail(message: String): Nothing {
        throw InterpretException(
            "Parsing failed at $pointer: $message",
            null,
            rootId = rootId
        )
    }

    override fun getScopeForArrayElements(): List<JsonInterpretScope> {

        val listValue = rawValue as? List<*> ?: fail("Not an array.")

        return object : AbstractList<JsonInterpretScope>() {

            override val size: Int
                get() = listValue.size

            override fun get(index: Int): JsonInterpretScope {
                val elementValue = listValue[index]
                return JsonInterpretScopeImpl(
                    elementValue,
                    parent = this@JsonInterpretScopeImpl,
                    at = index
                )
            }

        }
    }

    override fun getScopeForObject(): JsonInterpretScope.ObjectScope {
        if (rawValue !is Map<*,*>) fail("Not an object.")
        return JsonValueObjectScope(this, rawValue)
    }

}
