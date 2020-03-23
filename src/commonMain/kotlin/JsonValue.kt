package me.haimgr.jsonvalue

//////////////////////
// JSON Value
//////////////////////

class JsonValue internal constructor(val rawValue: Any?) {

    companion object {

        fun parse(string: String): JsonValue {
            return JsonValue(parseJsonValueRaw(string))
        }

        fun from(rawValue: Any?): JsonValue {
            return rawValue.toJsonValue(null)
        }

    }

    override fun equals(other: Any?): Boolean = other === this || other is JsonValue && this.rawValue == other.rawValue

    override fun hashCode(): Int = rawValue.hashCode()

    override fun toString(): String = buildString { appendJson(rawValue) }

}


interface JsonValueConverter {

    fun convertToJsonValue(value: Any?): JsonValue?

}

fun Any?.toJsonValue(converter: JsonValueConverter?): JsonValue {
    val result = this.toJsonValueOrRaw(converter)
    return if (result is JsonValue) result else JsonValue(result)
}

private fun Any?.toJsonValueRaw(converter: JsonValueConverter?): Any? {
    val result = this.toJsonValueOrRaw(converter)
    return if (result is JsonValue) result.rawValue else result
}

private fun Any?.toJsonValueOrRaw(converter: JsonValueConverter?): Any? {
    val value = this
    if (value is JsonValue) return value
    converter?.convertToJsonValue(value)?.let { return it }
    return when (value) {
        null -> value
        is String -> value
        is Number -> value.toJsonCanonized() ?: throw IllegalArgumentException("Cannot convert Number to json value: $value")
        is Collection<*> -> value.map { it.toJsonValueRaw(converter) }.toImmutable()
        is Map<*, *> -> value.entries.associateBy(
            keySelector = { it.key as? String ?: throw IllegalArgumentException("Only String keys are supported for converting map to json value. Accepted key: ${it.key}")},
            valueTransform = { it.value.toJsonValueRaw(converter) }
        ).toImmutable()
        is Boolean -> value
        else -> throw IllegalArgumentException("Cannot convert value to json element: $value")
    }
}

fun jsonObjectOf(vararg pairs: Pair<String, Any?>): JsonValue {
    return jsonObjectOfPairs(pairs, null)
}

fun jsonObjectOf(vararg pairs: Pair<String, Any?>, converter: JsonValueConverter?): JsonValue {
    return jsonObjectOfPairs(pairs, converter)
}

private fun jsonObjectOfPairs(
    pairs: Array<out Pair<String, Any?>>,
    converter: JsonValueConverter?
): JsonValue {
    val map = pairs.associateBy(keySelector = { it.first }, valueTransform = { it.second.toJsonValueRaw(converter) })
    return JsonValue(map.toImmutable())
}

//////////////////////
// To String
//////////////////////

private fun StringBuilder.appendJson(json: Any?) {
    when (json) {
        null -> append("null")
        is String -> appendQuote(json)
        is Number -> {
            val isNotFinite = json is Double && !json.isFinite() || json is Float && !json.isFinite()
            if (isNotFinite) {
                appendQuote(json.toString())
            } else {
                val numberString = json.toString()
                append(numberString)
            }
        }
        is Collection<*> -> {
            append('[')
            json.forEachIndexed { i, element ->
                if (i != 0) append(',')
                appendJson(element)
            }
            append(']')
        }
        is Map<*, *> -> {
            append('{')
            json.entries.forEachIndexed { i, (k, v) ->
                if (i != 0) append(',')
                appendQuote(k.toString())
                append(':')
                appendJson(v)
            }
            append('}')
        }
        is Boolean -> append(json.toString())
        else -> throw IllegalArgumentException("Cannot convert to json: $json")
    }
}
