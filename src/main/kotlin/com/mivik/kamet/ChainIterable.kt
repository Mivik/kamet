package com.mivik.kamet

class ChainIterable<T>(private val elements: List<Iterable<T>>) : Iterable<T> {
	override fun iterator(): Iterator<T> =
		object : Iterator<T> {
			var index = 0
			var current: Iterator<T>? = null

			init {
				getNext()
			}

			private fun getNext() {
				while (index < elements.size)
					elements[index++].iterator().let {
						if (it.hasNext()) {
							current = it
							return
						}
					}
				current = null
			}

			override fun hasNext(): Boolean = current != null

			override fun next(): T {
				val current = current!!
				return current.next().also { if (!current.hasNext()) getNext() }
			}
		}
}

@Suppress("NOTHING_TO_INLINE")
inline fun <T> concat(vararg elements: Iterable<T>) = ChainIterable(elements.asList())
