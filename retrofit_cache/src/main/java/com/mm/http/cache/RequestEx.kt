package com.mm.http.cache

import okhttp3.Headers

/**
 *
 */

fun Headers.Builder.addWithLine(line: String) = apply {
    val index = line.indexOf(':', 1)
    when {
        index != -1 -> {
            add(line.substring(0, index), line.substring(index + 1))
        }
        line[0] == ':' -> {
            // Work around empty header names and header names that start with a colon (created by old
            // broken SPDY versions of the response cache).
            add("", line.substring(1)) // Empty header name.
        }
        else -> {
            // No header name.
            add("", line)
        }
    }
}