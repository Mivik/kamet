package com.mivik.kamet

internal object CastManager {
	fun cast(from: Value, to: Type): Value {
		if (from.type == to) return from
		if (!from.type.isSubtypeOf(to)) error("Attempt to cast a ${from.type} to $to")
		return Value(from.llvm, to)
	}
}