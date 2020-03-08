package me.haimgr.jsonvalue

import kotlin.test.Test
import kotlin.test.assertEquals


class Tests {

    @Test fun testJson() {
        assertEquals(parseJsonValue("\"\\u0050\"").rawValue, "P")
        assertEquals(parseJsonValue("\"\\n\"").rawValue, "\n")
        assertEquals(
            parseJsonValue("""
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

        assertEquals(123.4e-4.toString(), parseJsonValue("123.4e-4").rawValue.toString())
        assertEquals(Double.NaN.toJsonValue(null).rawValue, "NaN")
        assertEquals(Double.POSITIVE_INFINITY.toJsonValue(null).rawValue, "Infinity")
        assertEquals(Double.NEGATIVE_INFINITY.toJsonValue(null).rawValue, "-Infinity")
    }

}
