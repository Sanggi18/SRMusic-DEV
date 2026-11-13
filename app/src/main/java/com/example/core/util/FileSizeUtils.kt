package com.example.core.util

fun formatBytes(bytes: Long): String {
    val mb = bytes / (1024.0 * 1024.0)

    return if (mb >= 1024.0) String.format(java.util.Locale.US, "%.2f GB", mb / 1024.0)
    else String.format(java.util.Locale.US, "%.1f MB", mb)
}
