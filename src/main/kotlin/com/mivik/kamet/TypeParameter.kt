package com.mivik.kamet

open class TypeParameter(val name: String) {
	open fun check(type: Type) = true
}
