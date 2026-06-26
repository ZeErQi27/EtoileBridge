package com.zeerqi27.etoilebridge.core

fun interface ConvertLogger {
    fun log(message: String)

    companion object {
        val NONE = ConvertLogger { }
    }
}

class LogCollector(private val downstream: ConvertLogger) {
    private val mutableLogs = mutableListOf<String>()
    val logs: List<String> get() = mutableLogs.toList()

    fun log(message: String) {
        mutableLogs += message
        downstream.log(message)
    }
}
