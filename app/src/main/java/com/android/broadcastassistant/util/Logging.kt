package com.android.broadcastassistant.util

import android.util.Log

/**
 * Logging utilities for the Auracast app.
 *
 * Supports two usage styles:
 * 1. Extension functions (object-aware): `this.logd("message")` → uses the class's [TAG].
 * 2. Top-level functions (global): `logd("message")` → infers caller info via [callerInfo].
 *
 * Both forms share a common logging implementation for consistency.
 * All log functions support varargs and can take a [Throwable] to log stack traces.
 */

/**
 * Extension property that provides a default log tag based on the class name.
 */
val Any.TAG: String
    get() = this::class.java.simpleName ?: "UnknownClass"

/**
 * Builds a caller info string by inspecting the stack trace.
 * Returns "ClassName.methodName" for the first non-logging class.
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
        "${caller.className}.${caller.methodName}"
    } else {
        "UnknownCaller"
    }
}

/**
 * Shared logging implementation.
 *
 * @param level Android log level (e.g., [Log.DEBUG], [Log.ERROR]).
 * @param tag Log tag (usually class name or caller info).
 * @param parts Message parts and optionally a [Throwable].
 */
private fun log(level: Int, tag: String?, parts: Array<out Any?>) {
    var throwable: Throwable? = null

    // Build log message and extract first throwable if present
    val msg = parts.mapNotNull {
        if (it is Throwable && throwable == null) {
            throwable = it
            null
        } else {
            it
        }
    }.joinToString(" ")

    // Print log with or without throwable
    if (throwable != null) {
        Log.println(level, tag ?: "Auracast", "$msg\n${Log.getStackTraceString(throwable)}")
    } else {
        Log.println(level, tag ?: "Auracast", msg)
    }
}

/**
 * Verbose log with the class name as the tag.
 */
fun Any.logv(vararg parts: Any?) = log(Log.VERBOSE, this.TAG, parts)

/**
 * Debug log with the class name as the tag.
 */
fun Any.logd(vararg parts: Any?) = log(Log.DEBUG, this.TAG, parts)

/**
 * Info log with the class name as the tag.
 */
fun Any.logi(vararg parts: Any?) = log(Log.INFO, this.TAG, parts)

/**
 * Warning log with the class name as the tag.
 */
fun Any.logw(vararg parts: Any?) = log(Log.WARN, this.TAG, parts)

/**
 * Error log with the class name as the tag.
 * Accepts an optional [Throwable] for stack trace logging.
 */
fun Any.loge(vararg parts: Any?) = log(Log.ERROR, this.TAG, parts)

/**
 * Verbose log that derives the tag from the caller info.
 */
fun logv(vararg parts: Any?) = log(Log.VERBOSE, callerInfo(), parts)

/**
 * Debug log that derives the tag from the caller info.
 */
fun logd(vararg parts: Any?) = log(Log.DEBUG, callerInfo(), parts)

/**
 * Info log that derives the tag from the caller info.
 */
fun logi(vararg parts: Any?) = log(Log.INFO, callerInfo(), parts)

/**
 * Warning log that derives the tag from the caller info.
 */
fun logw(vararg parts: Any?) = log(Log.WARN, callerInfo(), parts)

/**
 * Error log that derives the tag from the caller info.
 * Accepts an optional [Throwable] for stack trace logging.
 */
fun loge(vararg parts: Any?) = log(Log.ERROR, callerInfo(), parts)
