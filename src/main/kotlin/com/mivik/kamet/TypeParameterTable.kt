package com.mivik.kamet

internal class TypeParameterTable private constructor(private val delegate: MutableMap<TypeParameter, Type> = mutableMapOf()) :
	MutableMap<TypeParameter, Type> by delegate {
	companion object {
		private val threadLocal =
			object : ThreadLocal<TypeParameterTable>() {
				override fun initialValue(): TypeParameterTable = TypeParameterTable()
			}

		fun set(typeParameters: List<TypeParameter>, typeArguments: List<Type>) {
			get().apply {
				clear()
				for (i in typeParameters.indices) put(typeParameters[i], typeArguments[i])
			}
		}

		fun get(): TypeParameterTable = threadLocal.get()

		fun clear() {
			get().clear()
		}
	}

	fun equals(typeParameter: TypeParameter, other: Type): Boolean {
		this[typeParameter]?.let { return it == other }
		this[typeParameter] = other
		return true
	}

	fun map(typeParameters: List<TypeParameter>): List<Type> =
		typeParameters.map { this[it] ?: error("Cannot infer type for $it") }
}