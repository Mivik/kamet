package com.mivik.kamet

import com.mivik.kot.escape

internal class Context(
	val parent: Context? = null,
	private val map: MutableMap<String, ValueRef> = mutableMapOf()
) {
	fun lookupOrNull(name: String): ValueRef? {
		var current = this
		while (true) {
			current.map[name]?.let { return it }
			current = current.parent ?: return null
		}
	}

	fun lookup(name: String): ValueRef = lookupOrNull(name) ?: error("Unknown identifier ${name.escape()}")
}