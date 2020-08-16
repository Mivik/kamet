package com.mivik.kamet

interface Resolvable {
	val resolved: Boolean
		get() = true

	fun Context.resolveForThis(): Resolvable = this@Resolvable
}