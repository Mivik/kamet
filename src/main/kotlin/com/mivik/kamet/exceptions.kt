package com.mivik.kamet

class IllegalCastException(val type1: Type, val type2: Type) : IllegalArgumentException() {
	override val message: String?
		get() = "Illegal cast from $type1 to $type2"
}

class IllegalEscapeException(private val char: Char) : IllegalArgumentException() {
	override val message: String?
		get() = "Illegal escape char: ${char.description()}"
}