package me.haimgr.jsonvalue


///////////////////////
// Parser
///////////////////////

open class JsonValueParseException internal constructor(message: String, cause: Throwable? = null) : Exception(message, cause)

private class Parser(private val text: String, position: Int = 0) {

    var position: Int = position
        private set

    private val remainingCount
        get() = text.length - position

    fun read(count: Int) {
        require (count >= 1) { "count must be >= 1. count: $count" }
        check(count <= remainingCount) { "Cannot read $count characters. remainingCount: $remainingCount" }
        position += count
    }

    fun charAt(offset: Int): Char {
        require(offset in 0 until remainingCount) { throw IndexOutOfBoundsException("offset: $offset, remainingCount: $remainingCount") }
        return text[position + offset]
    }

    fun endsAt(offset: Int): Boolean = offset !in 0 until remainingCount

    fun throwExpected(vararg names: String): Nothing {
        var lineCount = 1
        var lastNewLinePosition = 0
        val position = this.position
        for (i in 0 until position) {
            if (text[i] == '\n') {
                lineCount += 1
                lastNewLinePosition = i
            }
        }
        val linePosition = position - lastNewLinePosition
        throw JsonValueParseException("Expected ${names.joinToString(" or ")} at line $lineCount position $linePosition")
    }

    fun clone(): Parser = Parser(text, position)

}


private fun Parser.read(): Char {
    val char = charAt(0)
    read(1)
    return char
}

private fun Parser.peek(text: String): Boolean {
    if (endsAt(text.length)) return false
    for (i in text.indices) if (text[i] != charAt(i)) return false
    return true
}

private fun Parser.peek(char: Char): Boolean = !endsAt(0) && charAt(0) == char

private typealias CharPredicate = (Char) -> Boolean

private inline fun Parser.peek(predicate: CharPredicate): Boolean = !endsAt(0) && predicate(charAt(0))


///////////////////////
// Utils
///////////////////////


private fun Parser.checkEndOfFile() {
    if (!endsAt(0)) {
        throwExpected("end-of-file")
    }
}

private fun Parser.readText(text: Char) {
    if (peek(text)) {
        read()
    } else {
        throwExpected("'$text'")
    }
}

private fun Parser.readLengthOf(text: String) {
    read(text.length)
}

/**
 *
 * A read function for grammar of the following template:
 *
 * ```
 * multiple
 *   prefix ws postfix
 *   prefix elements postfix
 *
 * elements
 *  element
 *  element separator elements
 *  ```
 *
 *  `element` is specified by [readElement] and [isElementFirst] parameter.
 *
 */
private fun <T> Parser.readMultiple(
    prefix: Char,
    postfix: Char,
    separator: Char,
    elementsName: String,
    isElementFirst: CharPredicate,
    readElement: Parser.() -> T
): List<T> {
    readText(prefix)
    readWS()
    return when {
        peek(postfix) -> {
            readText(postfix)
            emptyList()
        }
        peek(isElementFirst) -> {
            val elements = readMultipleElements(readElement, separator, postfix)
            readText(postfix)
            elements
        }
        else -> throwExpected("'$postfix'", elementsName)
    }
}

private fun <T> Parser.readMultipleElements(
    readElement: Parser.() -> T,
    separator: Char,
    postfix: Char
): List<T> {
    val firstElement = readElement()
    val elements = arrayListOf(firstElement)
    while (true) {
        when {
            peek(separator) -> {
                readText(separator)
                elements += readElement()
            }
            peek(postfix) -> return elements
            else -> throwExpected("'$postfix'", "'$separator'")
        }
    }
}


///////////////////////
// Grammar
///////////////////////

private const val ESCAPE_CHARACTERS: String = "\"\\/bnrtfu"
private const val ESCAPE_CHARACTERS_RESOLUTION: String = "\"\\/\b\n\r\t"

private fun Parser.readValue(): Value {
    return when {
        peek('{') -> readObject()
        peek('[') -> readArray()
        peekNumber() -> readNumber()
        peek('"') -> readString()
        peek("true") -> true.also { readLengthOf("true") }
        peek("false") -> false.also { readLengthOf("false") }
        peek("null") -> null.also { readLengthOf("null") }
        else -> throwExpected("value")
    }
}

private fun Parser.readObject(): Value {
    return readMultiple(
        prefix = '{',
        postfix = '}',
        separator = ',',
        elementsName = "member",
        isElementFirst = { it == '"' },
        readElement = { readMember() }
    ).toMapDistinct().toImmutable()
}

private fun <K, V> Iterable<Pair<K, V>>.toMapDistinct(): Map<K, V> {
    if (this is Collection<*> && this.isEmpty()) return emptyMap()
    val keySet = linkedSetOf<K>()
    for ((k, _) in this) if (!keySet.add(k)) throw JsonValueParseException("Multiple entries for property '$k'.")
    return toMap()
}

private fun Parser.readMember(): Pair<String, Value> {
    readWS()
    val string = readString()
    readWS()
    readText(':')
    val element = readElement()
    return string to element
}

private fun Parser.readArray(): Value {
    return readMultiple(
        prefix = '[',
        postfix = ']',
        separator = ',',
        elementsName = "element",
        isElementFirst = { it in "[{0123456789\"tfn" },
        readElement = { readElement() }
    ).toImmutable()
}

private fun Parser.readElement(): Value {
    readWS()
    val value = readValue()
    readWS()
    return value
}

private fun Parser.readString(): String {
    readText('"')
    val string = readStringCharacters()
    readText('"')
    return string
}

private fun Parser.readStringCharacters(): String {
    val sb = StringBuilder()
    while (true) when {
        peek { it.isRegularCharacter() } -> sb.append(read())
        peek('"') -> return sb.toString()
        peek('\\') -> sb.append(readEscape())
        else -> throwExpected("'\"'", "character")
    }
}

private fun Char.isRegularCharacter(): Boolean = this >= '\u0020' && this != '"' && this != '\\'

private fun Parser.readEscape(): Char {
    readText('\\')
    when {
        peek('u') -> {
            read()
            var charValue = 0
            repeat(4) {
                val hex = readHex()
                charValue = charValue shl 4 or hex
            }
            return charValue.toChar()
        }
        peek { it in ESCAPE_CHARACTERS } -> {
            val original = read()
            val resolution = ESCAPE_CHARACTERS_RESOLUTION[ESCAPE_CHARACTERS.indexOf(original)]
            return resolution
        }
        else -> throwExpected("escape")
    }
}

private fun Parser.readHex(): Int {
    return when {
        peek { it.toHex() != -1 } -> read().toHex()
        else -> throwExpected("hex")
    }
}

private fun Parser.readDigit(): Int {
    return when {
        peekDigit() -> read() - '0'
        else -> throwExpected("digit")
    }
}

private fun Char.toHex(): Int {
    return when (this) {
        in '0'..'9' -> this - '0'
        in 'a'..'z' -> 10 + (this - 'a')
        in 'A'..'Z' -> 10 + (this - 'A')
        else -> -1
    }
}

private fun Parser.readNumber(): Value {
    val p = clone()
    p.readInteger()
    p.readFraction()
    p.readExponent()
    val numberSize = p.position - position
    val numberString = String(CharArray(numberSize){ read() })
    val convertedNumber = numberString.toIntOrNull()
        ?: numberString.toLongOrNull()
        ?: numberString.toDouble()
    return (convertedNumber as Number).toJsonCanonized()
}

private fun Parser.readExponent() {
    when {
        peek('E') || peek('e') -> {
            read()
            when {
                peek('+') -> read()
                peek('-') -> read()
                else -> Unit
            }
            readDigits()
        }
        else -> Unit
    }
}

private fun Parser.readFraction() {
    when {
        peek('.') -> {
            read()
            readDigit()
            while (peekDigit()) {
                read()
            }
        }
        else -> Unit
    }
}

private fun Parser.readInteger() {
    if (peek('-')) {
        read()
    }
    readDigits()
}

private fun Parser.readDigits() {
    val first = readDigit()
    if (first == 0) {
        return
    } else {
        while (peekDigit()) {
            read()
        }
        return
    }
}

private fun Parser.peekDigit() = peek { it in '0'..'9' }

private fun Parser.peekNumber() = peek { it in '0'..'9' || it == '-' }

private fun Parser.readWS() {
    while(peek { it in "\u0020\u000D\u000A\u0009" }) {
        read()
    }
}


////////////////////////
// API
////////////////////////

internal fun parseJsonValueRaw(string: String): Value {
    Parser(string).run {
        val value = readElement()
        checkEndOfFile()
        return value
    }
}

internal fun StringBuilder.appendQuote(string: String) {
    append('"')
    for (c in string) {
        if (c in ESCAPE_CHARACTERS_RESOLUTION) {
            append('\\')
            append(ESCAPE_CHARACTERS[ESCAPE_CHARACTERS_RESOLUTION.indexOf(c)])
        } else {
            append(c)
        }
    }
    append('"')
}


