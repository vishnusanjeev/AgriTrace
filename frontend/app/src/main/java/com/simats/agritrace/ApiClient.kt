package com.simats.agritrace

import android.content.Context
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Response
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

object ApiClient {

    fun init(context: Context) {
        Session.ensureContext(context)
    }

    private val logging = HttpLoggingInterceptor().apply {
        level = HttpLoggingInterceptor.Level.BODY
    }

    private val authInterceptor = Interceptor { chain ->
        val req = chain.request()
        val token = Session.token()

        val newReq = if (!token.isNullOrBlank()) {
            req.newBuilder()
                .addHeader("Authorization", "Bearer $token")
                .build()
        } else req

        chain.proceed(newReq)
    }

    private val http: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .addInterceptor(authInterceptor)
            .addInterceptor(logging)
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .writeTimeout(20, TimeUnit.SECONDS)
            .build()
    }

    private val retrofit: Retrofit by lazy {
        Retrofit.Builder()
            .baseUrl(ApiConfig.BASE_URL) // MUST end with /
            .client(http)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
    }

    val authApi: AuthApi by lazy { retrofit.create(AuthApi::class.java) }
    val farmerApi: FarmerApi by lazy { retrofit.create(FarmerApi::class.java) }
    val distributorApi: DistributorApi by lazy { retrofit.create(DistributorApi::class.java) }
    val retailerApi: RetailerApi by lazy { retrofit.create(RetailerApi::class.java) }
    val qrApi: QrApi by lazy { retrofit.create(QrApi::class.java) }
    val userApi: UserApi by lazy { retrofit.create(UserApi::class.java) }
    val verifyApi: VerifyApi by lazy { retrofit.create(VerifyApi::class.java) }
    val consumerApi: ConsumerApi by lazy { retrofit.create(ConsumerApi::class.java) }
    val httpClient: OkHttpClient get() = http
}
