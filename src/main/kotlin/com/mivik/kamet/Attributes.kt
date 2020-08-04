package com.mivik.kamet

enum class Attribute {
	ALWAYS_INLINE, NATIVE;

	companion object {
		private val attributeMap by lazy {
			mutableMapOf<String, Attribute>().apply {
				for (value in values()) this[value.name.toLowerCase()] = value
			}
		}

		fun lookup(name: String): Attribute? = attributeMap[name]

		val empty = emptySet<Attribute>()
	}
}

typealias Attributes = Set<Attribute>
