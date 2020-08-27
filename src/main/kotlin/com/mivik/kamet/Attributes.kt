package com.mivik.kamet

enum class AttributeType {
	PACKED, NO_MANGLE, EXTERN, INLINE;

	companion object {
		private val attributeMap by lazy { values().associateBy { it.name.toLowerCase() } }

		fun lookup(name: String): AttributeType? = attributeMap[name]
	}

	@Suppress("NOTHING_TO_INLINE")
	inline fun notApplicableTo(what: String): Nothing = error("Attribute \"$this\" is not applicable to $what")
}

@Suppress("NOTHING_TO_INLINE")
data class Attribute(val type: AttributeType, val arguments: List<String>? = null) {
	constructor(name: String, arguments: List<String>? = null) : this(AttributeType.lookup(name) ?: error("Unknown attribute type: $name"), arguments)

	fun expectNoArguments() = apply {
		require(arguments.isNullOrEmpty()) { "Attribute $type does not have parameter" }
	}
}

class Attributes(val set: Set<Attribute> = emptySet()) : Set<Attribute> by set {
	override fun toString(): String =
		set.isNotEmpty().ifThat {
			"#[${
				set.joinToString(" ") {
					it.type.name.toLowerCase() + it.arguments.ifNotNull {
						it.arguments!!.joinToString(" ", "(", ")")
					}
				}
			}] "
		}
}
