package com.olh.usbrelay

interface LogCallback {
    fun onLog(level: Int, message: String)
}
