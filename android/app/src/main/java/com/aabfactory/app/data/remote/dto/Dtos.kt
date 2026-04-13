package com.aabfactory.app.data.remote.dto

import com.aabfactory.app.data.model.AppConfig
import com.aabfactory.app.data.model.AppSection
import com.google.gson.annotations.SerializedName

// ── AI Generation ─────────────────────────────────────────────────────────────

data class AiGenerateRequest(
    @SerializedName("prompt") val prompt: String,
    @SerializedName("templateHint") val templateHint: String? = null
)

data class AiGenerateResponse(
    @SerializedName("appName") val appName: String,
    @SerializedName("description") val description: String,
    @SerializedName("config") val config: AppConfig,
    @SerializedName("sections") val sections: List<AppSection>,
    @SerializedName("suggestedTemplate") val suggestedTemplate: String?
)

// ── Build Pipeline ─────────────────────────────────────────────────────────────

data class BuildRequest(
    @SerializedName("projectId") val projectId: String,
    @SerializedName("keystoreType") val keystoreType: String = "system", // "system" | "user"
    @SerializedName("userKeystoreUrl") val userKeystoreUrl: String? = null
)

data class BuildResponse(
    @SerializedName("buildId") val buildId: String,
    @SerializedName("status") val status: String,
    @SerializedName("estimatedMinutes") val estimatedMinutes: Int,
    @SerializedName("queuePosition") val queuePosition: Int
)

data class BuildStatusResponse(
    @SerializedName("buildId") val buildId: String,
    @SerializedName("status") val status: String,   // QUEUED | BUILDING | SUCCESS | FAILED
    @SerializedName("downloadUrl") val downloadUrl: String?,
    @SerializedName("errorMessage") val errorMessage: String?,
    @SerializedName("completedAt") val completedAt: String?
)

// ── Stripe ─────────────────────────────────────────────────────────────────────

data class CheckoutSessionRequest(
    @SerializedName("planId") val planId: String,      // "pro_monthly" | "build_pack_5"
    @SerializedName("successUrl") val successUrl: String = "aabfactory://payment/success",
    @SerializedName("cancelUrl") val cancelUrl: String = "aabfactory://payment/cancel"
)

data class CheckoutSessionResponse(
    @SerializedName("sessionId") val sessionId: String,
    @SerializedName("checkoutUrl") val checkoutUrl: String
)

data class SubscriptionStatusResponse(
    @SerializedName("plan") val plan: String,
    @SerializedName("status") val status: String,
    @SerializedName("buildsUsed") val buildsUsed: Int,
    @SerializedName("billingPeriodEnd") val billingPeriodEnd: String?
)

// ── Error ──────────────────────────────────────────────────────────────────────

data class ApiError(
    @SerializedName("error") val error: String,
    @SerializedName("message") val message: String,
    @SerializedName("code") val code: String? = null
)
