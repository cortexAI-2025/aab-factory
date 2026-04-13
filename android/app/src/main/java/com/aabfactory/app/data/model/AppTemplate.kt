package com.aabfactory.app.data.model

/**
 * Represents a pre-built app template.
 * Templates are JSON files bundled with the app and also served from the backend.
 */
data class AppTemplate(
    val id: String,
    val name: String,
    val description: String,
    val category: TemplateCategory,
    val thumbnailRes: Int = 0,      // local drawable resource
    val thumbnailUrl: String = "",  // remote URL fallback
    val previewScreens: List<String> = emptyList(),
    val defaultConfig: AppConfig = AppConfig(),
    val defaultSections: List<AppSection> = emptyList(),
    val isPremium: Boolean = false,
    val tags: List<String> = emptyList()
)

enum class TemplateCategory(val displayName: String) {
    BUSINESS("Business Profile"),
    ECOMMERCE("E-commerce"),
    BLOG("Blog & Content"),
    LEAD_GEN("Lead Generation"),
    LOCAL_SERVICES("Local Services")
}

// Built-in template definitions — matches templates/*.json files
object BuiltInTemplates {
    val BUSINESS_PROFILE = "business-profile"
    val ECOMMERCE = "ecommerce"
    val BLOG = "blog"
    val LEAD_GENERATION = "lead-generation"
    val LOCAL_SERVICES = "local-services"

    val freeTemplates = setOf(BUSINESS_PROFILE, BLOG)
    val proTemplates = setOf(ECOMMERCE, LEAD_GENERATION, LOCAL_SERVICES)
}
