package com.mivik.kamet

enum class Attribute {
	PACKED, NO_MANGLE, EXTERN;

	companion object {
		private val attributeMap by lazy { values().associateBy { it.name.toLowerCase() } }

		fun lookup(name: String): Attribute? = attributeMap[name]
	}

	@Suppress("NOTHING_TO_INLINE")
	inline fun notApplicableTo(what: String): Nothing = error("Attribute \"$this\" is not applicable to $what")
}

class Attributes(val set: Set<Attribute> = emptySet()) : Set<Attribute> by set {
	fun verify(name: String, accepted: Set<Attribute>) = apply {
		forEach {
			if (it !in accepted) it.notApplicableTo(name)
		}
	}

	override fun toString(): String = "#[${set.joinToString(" ") { it.name.toUpperCase() }}]"
}

class AttributesBuilder {
	private val set = mutableSetOf<Attribute>()

	operator fun Attribute.unaryPlus() {
		set  += this
	}

	fun build() = Attributes(set.readOnly())
}

inline fun buildAttributes(block: AttributesBuilder.() -> Unit) =
	AttributesBuilder().apply(block).build()
