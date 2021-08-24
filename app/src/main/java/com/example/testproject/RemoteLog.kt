package com.example.testproject

import android.util.Log
import okhttp3.Request
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.atomic.AtomicInteger

class RemoteLog(val tag: String) {

    fun send(message: String) {
        Log.d(tag, message)
    }
}