package me.haimgr.jsonvalue

internal fun List<*>.toImmutable() = this

internal  fun Map<*, *>.toImmutable() = this

internal fun Number.toJsonCanonized(): Any? {
    return when (this) {
        is Int -> this
        is Long -> when {
            this.toInt().toLong() == this -> this.toInt()
            else -> this
        }
        is Double -> when {
            !this.isFinite() -> this.toString()
            this.toInt().toDouble() == this -> this.toInt()
            this.toLong().toDouble() == this -> this.toLong()
            else -> this
        }
        is Float -> when {
            !this.isFinite() -> this.toString()
            this.toInt().toDouble() == this.toDouble() -> this.toInt()
            this.toLong().toDouble() == this.toDouble() -> this.toLong()
            else -> this
        }
        is Byte, is Short -> this.toInt()
        else -> null
    }
}

