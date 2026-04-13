package com.aabfactory.app.data.model

import com.google.firebase.Timestamp

/**
 * Core domain model for a user's app project.
 * Stored in Firestore under users/{uid}/projects/{projectId}
 */
data class AppProject(
    val id: String = "",
    val userId: String = "",
    val name: String = "",
    val description: String = "",
    val templateId: String = "",           // null if AI-generated
    val aiPrompt: String = "",             // original user prompt
    val config: AppConfig = AppConfig(),
    val sections: List<AppSection> = emptyList(),
    val status: ProjectStatus = ProjectStatus.DRAFT,
    val buildStatus: BuildStatus = BuildStatus.NONE,
    val lastBuildUrl: String = "",         // Firebase Storage URL
    val createdAt: Timestamp? = null,
    val updatedAt: Timestamp? = null,
    val hasWatermark: Boolean = true       // false for Pro users
)

data class AppConfig(
    val appName: String = "",
    val packageName: String = "",
    val primaryColor: String = "#6C63FF",
    val accentColor: String = "#00D4AA",
    val backgroundColor: String = "#0A0A0F",
    val logoUrl: String = "",
    val versionName: String = "1.0.0",
    val versionCode: Int = 1,
    val minSdkVersion: Int = 26,
    val targetSdkVersion: Int = 34
)

data class AppSection(
    val id: String = "",
    val type: SectionType = SectionType.HERO,
    val title: String = "",
    val subtitle: String = "",
    val content: String = "",
    val imageUrl: String = "",
    val ctaText: String = "",
    val ctaAction: String = "",
    val isVisible: Boolean = true,
    val order: Int = 0
)

enum class SectionType {
    HERO,
    FEATURES,
    GALLERY,
    TESTIMONIALS,
    CONTACT,
    PRODUCTS,
    BLOG,
    MAP,
    FORM,
    FOOTER,
    CUSTOM
}

enum class ProjectStatus {
    DRAFT,
    PUBLISHED,
    ARCHIVED
}

enum class BuildStatus {
    NONE,
    QUEUED,
    BUILDING,
    SUCCESS,
    FAILED
}
