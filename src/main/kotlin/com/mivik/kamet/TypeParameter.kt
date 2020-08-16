package com.mivik.kamet

open class TypeParameter(val name: String) {
	open fun Context.checkForThis(type: Type) = true
}
