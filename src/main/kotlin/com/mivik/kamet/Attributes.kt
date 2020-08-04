package com.mivik.kamet

enum class Attribute {
	PACKED, NATIVE;

	companion object {
		private val attributeMap by lazy {
			mutableMapOf<String, Attribute>().apply {
				for (value in values()) this[value.name.toLowerCase()] = value
			}
		}

		fun lookup(name: String): Attribute? = attributeMap[name]
	}

	@Suppress("NOTHING_TO_INLINE")
	inline fun notApplicableTo(what: String): Nothing = error("Attribute \"$this\" is not applicable to $what")
}

typealias Attributes = Set<Attribute>
