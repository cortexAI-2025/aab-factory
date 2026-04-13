package com.aabfactory.app.data.remote

import com.aabfactory.app.data.remote.dto.AiGenerateRequest
import com.aabfactory.app.data.remote.dto.AiGenerateResponse
import com.aabfactory.app.data.remote.dto.BuildRequest
import com.aabfactory.app.data.remote.dto.BuildResponse
import com.aabfactory.app.data.remote.dto.BuildStatusResponse
import com.aabfactory.app.data.remote.dto.CheckoutSessionRequest
import com.aabfactory.app.data.remote.dto.CheckoutSessionResponse
import com.aabfactory.app.data.remote.dto.SubscriptionStatusResponse
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.POST
import retrofit2.http.Path

interface ApiService {

    // ── AI Generation ────────────────────────────────────────────────────────

    @POST("ai/generate")
    suspend fun generateApp(
        @Header("Authorization") bearerToken: String,
        @Body request: AiGenerateRequest
    ): Response<AiGenerateResponse>

    // ── Build Pipeline ────────────────────────────────────────────────────────

    @POST("builds/trigger")
    suspend fun triggerBuild(
        @Header("Authorization") bearerToken: String,
        @Body request: BuildRequest
    ): Response<BuildResponse>

    @GET("builds/{buildId}/status")
    suspend fun getBuildStatus(
        @Header("Authorization") bearerToken: String,
        @Path("buildId") buildId: String
    ): Response<BuildStatusResponse>

    // ── Stripe ────────────────────────────────────────────────────────────────

    @POST("stripe/create-checkout-session")
    suspend fun createCheckoutSession(
        @Header("Authorization") bearerToken: String,
        @Body request: CheckoutSessionRequest
    ): Response<CheckoutSessionResponse>

    @GET("stripe/subscription-status")
    suspend fun getSubscriptionStatus(
        @Header("Authorization") bearerToken: String
    ): Response<SubscriptionStatusResponse>
}
