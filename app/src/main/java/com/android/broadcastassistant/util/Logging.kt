package com.android.broadcastassistant.util

import android.util.Log

/**
 * Extension property to generate a default log tag for any object.
 * Uses the class's simple name prefixed by its package.
 */
val Any.TAG: String
    get() {
        val pkg = this::class.java.`package`?.name ?: "UnknownPackage"
        val simpleName = this::class.java.simpleName ?: "UnknownClass"
        return "$pkg.$simpleName"
    }

/**
 * Builds a caller info string by inspecting the stack trace.
 * Returns "ClassName.methodName".
 */
private fun callerInfo(): String {
    val stack = Thread.currentThread().stackTrace
    val caller = stack.firstOrNull { element ->
        val cls = element.className
        cls != null &&
                !cls.startsWith("java.lang.Thread") &&
                !cls.contains("LoggingKt") &&
                !cls.contains("Logging")
    }
    return if (caller != null) {
        "${caller.className}"
    } else {
        "UnknownCaller"
    }
}

/**
 * Verbose log — tries to use this.TAG if available, else fallback.
 */
fun Any.logv(vararg parts: Any?) {
    val tag = this.TAG.takeIf { it.isNotBlank() } ?: callerInfo()
    val msg = parts.joinToString(" ")
    Log.v(tag, msg)
}

fun Any.logd(vararg parts: Any?) {
    val tag = this.TAG.takeIf { it.isNotBlank() } ?: callerInfo()
    val msg = parts.joinToString(" ")
    Log.d(tag, msg)
}

fun Any.logi(vararg parts: Any?) {
    val tag = this.TAG.takeIf { it.isNotBlank() } ?: callerInfo()
    val msg = parts.joinToString(" ")
    Log.i(tag, msg)
}

fun Any.logw(vararg parts: Any?) {
    val tag = this.TAG.takeIf { it.isNotBlank() } ?: callerInfo()
    val msg = parts.joinToString(" ")
    Log.w(tag, msg)
}

fun Any.loge(vararg parts: Any?) {
    val tag = this.TAG.takeIf { it.isNotBlank() } ?: callerInfo()
    var throwable: Throwable? = null
    val msg = parts.mapNotNull {
        if (it is Throwable && throwable == null) {
            throwable = it
            null
        } else {
            it
        }
    }.joinToString(" ")
    if (throwable != null) {
        Log.e(tag, msg, throwable)
    } else {
        Log.e(tag, msg)
    }
}
