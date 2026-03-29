package com.simats.agritrace

import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Multipart
import retrofit2.http.Part
import okhttp3.MultipartBody

interface UserApi {

    @GET("users/me.php")
    suspend fun me(): ProfileResp

    @POST("users/me.php")
    suspend fun updateProfile(@Body req: ProfileUpdateReq): ProfileResp

    @Multipart
    @POST("users/avatar.php")
    suspend fun uploadAvatar(@Part avatar: MultipartBody.Part): ProfileResp
}
