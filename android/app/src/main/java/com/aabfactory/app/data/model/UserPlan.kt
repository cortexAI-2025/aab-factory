package com.aabfactory.app.data.model

import com.google.firebase.Timestamp

data class UserPlan(
    val uid: String = "",
    val email: String = "",
    val displayName: String = "",
    val photoUrl: String = "",
    val plan: Plan = Plan.FREE,
    val stripeCustomerId: String = "",
    val stripeSubscriptionId: String = "",
    val subscriptionStatus: SubscriptionStatus = SubscriptionStatus.NONE,
    val buildsUsedThisMonth: Int = 0,
    val billingPeriodEnd: Timestamp? = null,
    val fcmToken: String = ""
)

enum class Plan(val displayName: String, val monthlyPrice: Double) {
    FREE("Free", 0.0),
    PRO("Pro", 29.0)
}

enum class SubscriptionStatus {
    NONE,
    ACTIVE,
    PAST_DUE,
    CANCELED,
    TRIALING
}

// Feature access gates
object PlanLimits {
    const val FREE_MAX_BUILDS_PER_MONTH = 2
    const val FREE_MAX_PROJECTS = 3
    const val PRO_MAX_BUILDS_PER_MONTH = Int.MAX_VALUE
    const val PRO_MAX_PROJECTS = Int.MAX_VALUE

    fun canBuild(plan: UserPlan): Boolean {
        return when (plan.plan) {
            Plan.FREE -> plan.buildsUsedThisMonth < FREE_MAX_BUILDS_PER_MONTH
            Plan.PRO -> true
        }
    }

    fun canUseTemplate(templateId: String, plan: UserPlan): Boolean {
        return when (plan.plan) {
            Plan.FREE -> templateId !in BuiltInTemplates.proTemplates
            Plan.PRO -> true
        }
    }

    fun hasWatermark(plan: UserPlan): Boolean = plan.plan == Plan.FREE
}
