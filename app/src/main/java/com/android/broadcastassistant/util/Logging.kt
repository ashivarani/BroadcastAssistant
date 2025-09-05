package com.android.broadcastassistant.util

import android.util.Log

/**
 * Logging utilities for the Auracast app.
 *
 * Supports two usage styles:
 * 1. Extension functions (object-aware): `this.logd("message")` → uses the class's [TAG].
 * 2. Top-level functions (global): `logd("message")` → infers caller info via [callerInfo].
 *
 * All logs now include the app package name `com.android.broadcastassistant` in the tag.
 * Supports varargs and optional Throwable to log exceptions with stack traces.
 */

private const val APP_PACKAGE = "com.android.broadcastassistant" // App package prefix for all logs

/**
 * Extension property to generate a default log tag based on the class name.
 *
 * Example: com.android.broadcastassistant.MyClass
 */
val Any.TAG: String
    get() = "$APP_PACKAGE.${this::class.java.simpleName ?: "UnknownClass"}"

/**
 * Builds a caller info string by inspecting the current thread's stack trace.
 * Returns "com.android.broadcastassistant.ClassName.methodName" for the first non-logging class.
 *
 * This is used by top-level log functions to provide a meaningful tag automatically.
 */
private fun callerInfo(): String {
    val stack = Thread.currentThread().stackTrace
    val caller = stack.firstOrNull { element ->
        val cls = element.className
        // Ignore internal Java threads and logging classes
        cls != null &&
                !cls.startsWith("java.lang.Thread") &&
                !cls.contains("LoggingKt") &&
                !cls.contains("Logging")
    }
    return if (caller != null) {
        "$APP_PACKAGE.${caller.className}.${caller.methodName}"
    } else {
        "$APP_PACKAGE.UnknownCaller"
    }
}

/**
 * Shared logging implementation used by all log functions.
 *
 * @param level Android log level (Log.VERBOSE, Log.DEBUG, etc.)
 * @param tag Log tag (usually class name or caller info)
 * @param parts Message parts (vararg) including optional Throwable for stack trace
 */
private fun log(level: Int, tag: String?, parts: Array<out Any?>) {
    var throwable: Throwable? = null

    // Combine all message parts into a single string, extract first Throwable if present
    val msg = parts.mapNotNull {
        if (it is Throwable && throwable == null) {
            throwable = it // capture the throwable
            null
        } else it
    }.joinToString(" ")

    // Print log with or without Throwable
    if (throwable != null) {
        Log.println(level, tag ?: APP_PACKAGE, "$msg\n${Log.getStackTraceString(throwable)}")
    } else {
        Log.println(level, tag ?: APP_PACKAGE, msg)
    }
}

/* -------------------------- Extension Log Functions -------------------------- */

/** Verbose log with class name + package as tag */
fun Any.logv(vararg parts: Any?) = log(Log.VERBOSE, this.TAG, parts)

/** Debug log with class name + package as tag */
fun Any.logd(vararg parts: Any?) = log(Log.DEBUG, this.TAG, parts)

/** Info log with class name + package as tag */
fun Any.logi(vararg parts: Any?) = log(Log.INFO, this.TAG, parts)

/** Warning log with class name + package as tag */
fun Any.logw(vararg parts: Any?) = log(Log.WARN, this.TAG, parts)

/** Error log with class name + package as tag, supports optional Throwable */
fun Any.loge(vararg parts: Any?) = log(Log.ERROR, this.TAG, parts)

/* -------------------------- Top-Level Log Functions -------------------------- */

/** Verbose log with caller info + package as tag */
fun logv(vararg parts: Any?) = log(Log.VERBOSE, callerInfo(), parts)

/** Debug log with caller info + package as tag */
fun logd(vararg parts: Any?) = log(Log.DEBUG, callerInfo(), parts)

/** Info log with caller info + package as tag */
fun logi(vararg parts: Any?) = log(Log.INFO, callerInfo(), parts)

/** Warning log with caller info + package as tag */
fun logw(vararg parts: Any?) = log(Log.WARN, callerInfo(), parts)

/** Error log with caller info + package as tag, supports optional Throwable */
fun loge(vararg parts: Any?) = log(Log.ERROR, callerInfo(), parts)
