package me.haimgr.jsonvalue

//////////////////////
// JSON Value
//////////////////////

class JsonValue internal constructor(val rawValue: Value) {

    companion object {

        fun parse(string: String): JsonValue {
            return JsonValue(parseJsonValueRaw(string))
        }

    }

    override fun equals(other: Any?): Boolean = other === this || other is JsonValue && other.rawValue == this.rawValue
    override fun hashCode(): Int = rawValue.hashCode()
    override fun toString(): String = rawValue.toJsonText(spacing = false)

}


interface JsonValueConverter {

    fun convertToJsonValue(value: Any?): JsonValue?

}

fun Value.toJsonValue(converter: JsonValueConverter?): JsonValue {
    val value = this
    if (value is JsonValue) return value
    converter?.convertToJsonValue(value)?.let { return it }
    val rawValue: Any? = when (value) {
        null -> value
        is String -> value
        is Number -> value.toJsonCanonized() ?: throw IllegalArgumentException("Cannot convert Number to json value: $value")
        is Collection<*> -> value.map { it.toJsonValue(converter).rawValue }.toImmutable()
        is Map<*, *> -> value.entries.associateBy(
            keySelector = { it.key as? String ?: throw IllegalArgumentException("Only String keys are supported for converting map to json value. Accepted key: ${it.key}")},
            valueTransform = { it.value.toJsonValue(converter).rawValue }
        ).toImmutable()
        is Boolean -> value
        else -> throw IllegalArgumentException("Cannot convert value to json element: $value")
    }
    return JsonValue(rawValue)
}


//////////////////////
// To String
//////////////////////

private fun StringBuilder.appendJson(json: Value, spacing: Boolean) {
    fun appendJson(json: Value) = appendJson(json, spacing)
    when (json) {
        null -> append("null")
        is JsonValue -> appendJson(json.rawValue)
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
                if (i != 0) append(if (spacing) ", " else ",")
                appendJson(element)
            }
            append(']')
        }
        is Map<*, *> -> {
            append('{')
            json.entries.forEachIndexed { i, (k, v) ->
                if (i != 0) append(if (spacing) ", " else ",")
                appendQuote(k.toString())
                append(if (spacing) ": " else ":")
                appendJson(v)
            }
            append('}')
        }
        is Boolean -> append(json.toString())
        else -> throw IllegalArgumentException("Cannot convert to json: $json")
    }
}

private fun Value.toJsonText(spacing: Boolean): String = buildString { appendJson(this@toJsonText, spacing = spacing) }


