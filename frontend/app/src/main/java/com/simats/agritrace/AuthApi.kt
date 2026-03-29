package com.simats.agritrace

import retrofit2.http.Body
import retrofit2.http.POST

interface AuthApi {

    @POST("auth/register.php")
    suspend fun register(@Body req: RegisterReq): AuthResp

    @POST("auth/login.php")
    suspend fun login(@Body req: LoginReq): AuthResp

    @POST("auth/request_reset_otp.php")
    suspend fun requestVerifyEmailOtp(@Body req: OtpRequestReq): BasicResp

    // ✅ CHANGED: verify must return AuthResp (token + user) after successful OTP verification
    @POST("auth/verify_otp.php")
    suspend fun verifyOtp(@Body req: OtpVerifyReq): AuthResp

    @POST("auth/request_reset_otp.php")
    suspend fun requestResetOtp(@Body req: OtpRequestReq): BasicResp

    @POST("auth/reset_password.php")
    suspend fun resetPassword(@Body req: ResetPasswordReq): BasicResp
}
