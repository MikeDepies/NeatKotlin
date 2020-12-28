package util

inline fun <reified T> Any.safeCast(): T? = this as? T