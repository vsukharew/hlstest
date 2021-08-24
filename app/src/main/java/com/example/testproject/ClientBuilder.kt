package com.example.testproject

import android.app.Activity
import android.content.Context
import android.os.Build
import android.util.Log
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response

class ClientBuilder {

    companion object {
        fun getBuilder(context: Context) = OkHttpClient.Builder()
            .addInterceptor(UserAgentInterceptor(context))
            .build().newBuilder()

        @JvmStatic
        fun getUserAgent(ctx: Context): String {
            val info = ctx.packageManager.getPackageInfo(ctx.packageName, 0)
            val userAgent =
                "${ctx.resources.getString(R.string.app_name)}/" +
                        "${info.versionName} " +
                        "(build ${
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                                info.longVersionCode
                            } else {
                                info.versionCode
                            }
                        } " +
                        "Android ${Build.VERSION.RELEASE}; " +
                        "${Build.MANUFACTURER} ${Build.MODEL})"
            Log.d("UserAgent", userAgent)
            return userAgent
        }

        class UserAgentInterceptor(private val context: Context) : Interceptor {
            override fun intercept(chain: Interceptor.Chain): Response {
                val originalRequest: Request = chain.request()
                val requestWithUserAgent: Request = originalRequest.newBuilder()
                    .header("User-Agent", getUserAgent(context))
                    .build()
                return chain.proceed(requestWithUserAgent)
            }
        }
    }
}