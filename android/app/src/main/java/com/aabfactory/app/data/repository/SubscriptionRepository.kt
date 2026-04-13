package com.aabfactory.app.data.repository

import com.aabfactory.app.data.model.Plan
import com.aabfactory.app.data.model.SubscriptionStatus
import com.aabfactory.app.data.model.UserPlan
import com.aabfactory.app.data.remote.ApiService
import com.aabfactory.app.data.remote.dto.CheckoutSessionRequest
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SubscriptionRepository @Inject constructor(
    private val firestore: FirebaseFirestore,
    private val apiService: ApiService,
    private val authRepository: AuthRepository
) {

    private fun userDoc(uid: String) = firestore.collection("users").document(uid)

    fun observeUserPlan(uid: String): Flow<UserPlan> = callbackFlow {
        val listener = userDoc(uid).addSnapshotListener { snapshot, error ->
            if (error != null) {
                Timber.e(error, "Error observing user plan")
                return@addSnapshotListener
            }
            val plan = snapshot?.toObject(UserPlan::class.java) ?: UserPlan(uid = uid)
            trySend(plan)
        }
        awaitClose { listener.remove() }
    }

    suspend fun getUserPlan(uid: String): Result<UserPlan> {
        return try {
            val doc = userDoc(uid).get().await()
            val plan = doc.toObject(UserPlan::class.java) ?: UserPlan(uid = uid)
            Result.success(plan)
        } catch (e: Exception) {
            Timber.e(e, "Failed to get user plan")
            Result.failure(e)
        }
    }

    suspend fun createCheckoutSession(planId: String): Result<String> {
        return try {
            val token = authRepository.getIdToken()
                ?: return Result.failure(Exception("Not authenticated"))
            val response = apiService.createCheckoutSession(
                bearerToken = "Bearer $token",
                request = CheckoutSessionRequest(planId = planId)
            )
            if (response.isSuccessful) {
                Result.success(response.body()!!.checkoutUrl)
            } else {
                Result.failure(Exception("Failed to create checkout session: ${response.code()}"))
            }
        } catch (e: Exception) {
            Timber.e(e, "Checkout session error")
            Result.failure(e)
        }
    }

    suspend fun refreshSubscriptionStatus(): Result<UserPlan> {
        return try {
            val token = authRepository.getIdToken()
                ?: return Result.failure(Exception("Not authenticated"))
            val response = apiService.getSubscriptionStatus("Bearer $token")
            if (response.isSuccessful) {
                val body = response.body()!!
                val plan = UserPlan(
                    plan = if (body.plan == "pro") Plan.PRO else Plan.FREE,
                    subscriptionStatus = SubscriptionStatus.valueOf(
                        body.status.uppercase().replace("-", "_")
                    ),
                    buildsUsedThisMonth = body.buildsUsed
                )
                Result.success(plan)
            } else {
                Result.failure(Exception("Failed to fetch subscription: ${response.code()}"))
            }
        } catch (e: Exception) {
            Timber.e(e, "Subscription refresh error")
            Result.failure(e)
        }
    }
}
