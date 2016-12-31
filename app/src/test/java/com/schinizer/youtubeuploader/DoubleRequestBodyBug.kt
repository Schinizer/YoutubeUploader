package com.schinizer.youtubeuploader

import okhttp3.MediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.logging.HttpLoggingInterceptor
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okio.*
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

/**
 * Sample unit test to demonstrate RequestBody is instantiated twice when HttpLoggingInterceptor is used
 */

class DoubleRequestBodyBug {

    @Rule @JvmField
    val mockWebServer = MockWebServer()

    @Test
    @Throws(Exception::class)
    fun doubleRequestBodyBugTest() {

        val okhttpClient = OkHttpClient.Builder()
                .addNetworkInterceptor(HttpLoggingInterceptor().setLevel(HttpLoggingInterceptor.Level.BODY))
                .build()

        var callCount = 0

        val byteString = ByteString.decodeHex("89504e470d0a1a0a")
        val requestBody = object : RequestBody() {

            override fun contentType(): MediaType = MediaType.parse("video/*")
            override fun contentLength(): Long = byteString.size().toLong()
            override fun writeTo(sink: BufferedSink) {
                callCount++
                val countingSink = Okio.buffer(object : ForwardingSink(sink) {
                    var total: Long = 0
                    override fun write(source: Buffer?, byteCount: Long) {
                        total += byteCount
                        super.write(source, byteCount)
                    }
                })

                countingSink.write(byteString)
                countingSink.flush()
            }
        }

        val request = Request.Builder()
                .url(mockWebServer.url("."))
                .put(requestBody)
                .build()

        mockWebServer.enqueue(MockResponse())
        okhttpClient.newCall(request).execute()

        assertEquals(2, callCount)
    }

    @Test
    @Throws(Exception::class)
    fun doubleRequestBodyBugNoLoggerTest() {

        val okhttpClient = OkHttpClient.Builder()
                .build()

        var callCount = 0

        val byteString = ByteString.decodeHex("89504e470d0a1a0a")
        val requestBody = object : RequestBody() {

            override fun contentType(): MediaType = MediaType.parse("video/*")
            override fun contentLength(): Long = byteString.size().toLong()
            override fun writeTo(sink: BufferedSink) {
                callCount++
                val countingSink = Okio.buffer(object : ForwardingSink(sink) {
                    var total: Long = 0
                    override fun write(source: Buffer?, byteCount: Long) {
                        total += byteCount
                        super.write(source, byteCount)
                    }
                })

                countingSink.write(byteString)
                countingSink.flush()
            }
        }

        val request = Request.Builder()
                .url(mockWebServer.url("."))
                .put(requestBody)
                .build()

        mockWebServer.enqueue(MockResponse())
        okhttpClient.newCall(request).execute()

        assertEquals(1, callCount)
    }
}