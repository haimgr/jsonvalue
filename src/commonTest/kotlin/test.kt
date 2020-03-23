package me.haimgr.jsonvalue

import me.haimgr.jsonvalue.interpret.JsonInterpretScope
import me.haimgr.jsonvalue.interpret.asArray
import me.haimgr.jsonvalue.interpret.asObject
import me.haimgr.jsonvalue.interpret.interpretScope
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith


class Tests {

    @Test fun testJson() {
        assertEquals(JsonValue.parse("\"\\u0050\"").rawValue, "P")
        assertEquals(JsonValue.parse("\"\\n\"").rawValue, "\n")
        assertEquals(
            JsonValue.parse(
                """
                    {
                      "name": "Haim",
                      "numbers": ["1", "3", "6\"", [{"":[]}], null, false, 23.4e-1, 7878,{ }],
                      "address": { "x":1, "y" : 2, "z": 3 }
                    }
                """
            ).rawValue,
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
    
    @Test fun converterWithJsonObjectOf() {
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
            "address" to jsonObjectOf("country" to "USA", "street" to "eleven"),
            converter = converter
        )
        assertEquals(mapOf(
            "name" to "James",
            "grades" to listOf(listOf("math", 90), listOf("art", 85)),
            "position" to listOf(10, 20, 30),
            "address" to mapOf("country" to "USA", "street" to "eleven")
        ), jsonValue.rawValue)

    }

    @Test fun throwOnDuplicateProperty() {
        assertFailsWith<JsonValueException> {
            JsonValue.parse("""{"x":1,"x":2}""")
        }
    }

}


class ScopeTests {

    data class User(val name: String, val age: Double, val children: List<String>)

    @Test fun scopeTest() {

        fun JsonInterpretScope.asUser(): User = asObject {
            User(
                name = property("name").asString(),
                age = property("age").asDouble(),
                children = propertyOrNull("children")?.asArray { asString() }.orEmpty()
            )
        }

        val user = JsonValue.parse(
            """
                { "name": "Haim", "age": 12.3, "children": ["A", "B"] }
            """.trimIndent()
        ).interpretScope().asUser()

        assertEquals(User("Haim", 12.3, listOf("A", "B")), user)

    }

    private sealed class Place {
        data class Location(val lat: Double, val lon: Double) : Place()
        data class Description(val text: String) : Place()
    }

    @Test fun polymorphismTest() {
        fun JsonInterpretScope.asPlace() = asObject {
            when (val type = property("type").asString()) {
                "Location" -> Place.Location(lat = property("lat").asDouble(), lon = property("lon").asDouble())
                "Description" -> Place.Description(text = property("text").asString())
                else -> throw IllegalArgumentException("Unexpected type '$type'")
            }
        }
        val place = JsonValue.parse("""{"type":"Description", "text":"abc"}""").interpretScope().asPlace()
        assertEquals(Place.Description(text = "abc"), place)
    }

}
