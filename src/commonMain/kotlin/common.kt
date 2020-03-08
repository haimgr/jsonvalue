package me.haimgr.jsonvalue

internal typealias Value = Any?

internal fun List<*>.toImmutable() = this

internal  fun Map<*, *>.toImmutable() = this
