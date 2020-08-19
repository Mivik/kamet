package com.mivik.kamet

interface Disposable {
	fun dispose()
}

inline fun <T : Disposable, R> T.use(block: T.() -> R): R =
	try {
		block()
	} finally {
		dispose()
	}
