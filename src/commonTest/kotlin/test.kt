package me.haimgr.jsonvalue

import kotlin.test.Test
import kotlin.test.assertEquals


class Tests {

    @Test fun testJson() {
        assertEquals(JsonValue.parse("\"\\u0050\"").rawValue, "P")
        assertEquals(JsonValue.parse("\"\\n\"").rawValue, "\n")
        assertEquals(
            JsonValue.parse("""
                {
                  "name": "Haim",
                  "numbers": ["1", "3", "6\"", [{"":[]}], null, false, 23.4e-1, 7878,{ }],
                  "address": { "x":1, "y" : 2, "z": 3 }
                }
            """).rawValue,
            mapOf(
                "name" to "Haim",
                "numbers" to listOf("1", "3",  "6\"", listOf(mapOf("" to listOf<Any>())), null, false, 2.34, 7878, mapOf<String, Any>()),
                "address" to mapOf("x" to 1, "y" to 2, "z" to 3)
            )
        )

        assertEquals(123.4e-4.toString(), JsonValue.parse("123.4e-4").rawValue.toString())
        assertEquals(Double.NaN.toJsonValue(null).rawValue, "NaN")
        assertEquals(Double.POSITIVE_INFINITY.toJsonValue(null).rawValue, "Infinity")
        assertEquals(Double.NEGATIVE_INFINITY.toJsonValue(null).rawValue, "-Infinity")
    }
    
    @Test fun converter() {
        val converter = object : JsonValueConverter {
            override fun convertToJsonValue(value: Any?): JsonValue? = when (value) {
                is Triple<*, *, *> -> listOf(value.first,  value.second, value.third).toJsonValue(this)
                is Map<*, *> -> value.entries.map { (k, v) -> listOf(k, v) }.toJsonValue(this)
                else -> null
            }
        }
        val jsonValue = jsonObjectOf(
            "name" to "James",
            "grades" to mapOf("math" to 90.0, "art" to 85.0),
            "position" to Triple(10, 20, 30),
            "address" to jsonObjectOf("country" to "USA", "street" to "eleven", converter = converter),
            converter = converter
        )
        assertEquals(jsonValue.rawValue, mapOf(
            "name" to "James",
            "grades" to listOf(listOf("math", 90), listOf("art", 85)),
            "position" to listOf(10, 20, 30),
            "address" to mapOf("country" to "USA", "street" to "eleven")
        ))

    }

}

private fun jsonObjectOf(vararg pairs: Pair<String, Any?>, converter: JsonValueConverter?): JsonValue {
    val map = pairs.associateBy(keySelector = { it.first }, valueTransform = { it.second.toJsonValue(converter) })
    return map.toJsonValue(null)
}


