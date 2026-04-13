package com.aabfactory.app.presentation.onboarding

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aabfactory.app.data.model.AppConfig
import com.aabfactory.app.data.model.AppProject
import com.aabfactory.app.data.model.AppTemplate
import com.aabfactory.app.data.model.BuildStatus
import com.aabfactory.app.data.model.ProjectStatus
import com.aabfactory.app.data.model.TemplateCategory
import com.aabfactory.app.data.remote.ApiService
import com.aabfactory.app.data.remote.dto.AiGenerateRequest
import com.aabfactory.app.data.repository.AuthRepository
import com.aabfactory.app.data.repository.ProjectRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

enum class OnboardingStep { CHOOSE_METHOD, AI_PROMPT, TEMPLATE_SELECT }

data class OnboardingState(
    val step: OnboardingStep = OnboardingStep.CHOOSE_METHOD,
    val prompt: String = "",
    val selectedTemplate: AppTemplate? = null,
    val availableTemplates: List<AppTemplate> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null
)

@HiltViewModel
class OnboardingViewModel @Inject constructor(
    private val authRepository: AuthRepository,
    private val projectRepository: ProjectRepository,
    private val apiService: ApiService
) : ViewModel() {

    private val _state = MutableStateFlow(OnboardingState())
    val state: StateFlow<OnboardingState> = _state.asStateFlow()

    private val uid get() = authRepository.currentUser?.uid ?: ""

    init {
        loadTemplates()
    }

    private fun loadTemplates() {
        // In a real app, fetch from backend or load from assets
        _state.value = _state.value.copy(availableTemplates = sampleTemplates())
    }

    fun selectAIMethod() {
        _state.value = _state.value.copy(step = OnboardingStep.AI_PROMPT)
    }

    fun selectTemplateMethod() {
        _state.value = _state.value.copy(step = OnboardingStep.TEMPLATE_SELECT)
    }

    fun onPromptChange(prompt: String) {
        _state.value = _state.value.copy(prompt = prompt, error = null)
    }

    fun onSelectTemplate(template: AppTemplate) {
        _state.value = _state.value.copy(selectedTemplate = template)
    }

    fun generateWithAI(onSuccess: (String) -> Unit) {
        val prompt = _state.value.prompt.trim()
        if (prompt.isBlank()) {
            _state.value = _state.value.copy(error = "Please describe your app first")
            return
        }
        viewModelScope.launch {
            _state.value = _state.value.copy(isLoading = true, error = null)
            try {
                val token = authRepository.getIdToken()
                    ?: throw Exception("Authentication required")
                val response = apiService.generateApp(
                    bearerToken = "Bearer $token",
                    request = AiGenerateRequest(prompt = prompt)
                )
                if (response.isSuccessful) {
                    val body = response.body()!!
                    val project = AppProject(
                        userId = uid,
                        name = body.appName,
                        description = body.description,
                        aiPrompt = prompt,
                        config = body.config,
                        sections = body.sections,
                        status = ProjectStatus.DRAFT
                    )
                    val result = projectRepository.createProject(uid, project)
                    result.fold(
                        onSuccess = { projectId ->
                            _state.value = _state.value.copy(isLoading = false)
                            onSuccess(projectId)
                        },
                        onFailure = { e ->
                            _state.value = _state.value.copy(isLoading = false, error = e.message)
                        }
                    )
                } else {
                    _state.value = _state.value.copy(
                        isLoading = false,
                        error = "AI generation failed. Please try again."
                    )
                }
            } catch (e: Exception) {
                _state.value = _state.value.copy(isLoading = false, error = e.message)
            }
        }
    }

    fun createFromTemplate(onSuccess: (String) -> Unit) {
        val template = _state.value.selectedTemplate ?: return
        viewModelScope.launch {
            _state.value = _state.value.copy(isLoading = true)
            val project = AppProject(
                userId = uid,
                name = template.name,
                description = template.description,
                templateId = template.id,
                config = template.defaultConfig,
                sections = template.defaultSections,
                status = ProjectStatus.DRAFT
            )
            val result = projectRepository.createProject(uid, project)
            result.fold(
                onSuccess = { projectId ->
                    _state.value = _state.value.copy(isLoading = false)
                    onSuccess(projectId)
                },
                onFailure = { e ->
                    _state.value = _state.value.copy(isLoading = false, error = e.message)
                }
            )
        }
    }

    private fun sampleTemplates(): List<AppTemplate> = listOf(
        AppTemplate(
            id = "business-profile",
            name = "Business Profile",
            description = "Professional business showcase",
            category = TemplateCategory.BUSINESS,
            isPremium = false,
            tags = listOf("professional", "services", "contact")
        ),
        AppTemplate(
            id = "ecommerce",
            name = "E-commerce",
            description = "Product catalog with cart",
            category = TemplateCategory.ECOMMERCE,
            isPremium = true,
            tags = listOf("shop", "products", "cart")
        ),
        AppTemplate(
            id = "blog",
            name = "Blog & Content",
            description = "Articles and media platform",
            category = TemplateCategory.BLOG,
            isPremium = false,
            tags = listOf("blog", "articles", "media")
        ),
        AppTemplate(
            id = "lead-generation",
            name = "Lead Generation",
            description = "Capture leads with smart forms",
            category = TemplateCategory.LEAD_GEN,
            isPremium = true,
            tags = listOf("leads", "forms", "cta")
        ),
        AppTemplate(
            id = "local-services",
            name = "Local Services",
            description = "Location-based service booking",
            category = TemplateCategory.LOCAL_SERVICES,
            isPremium = true,
            tags = listOf("local", "booking", "map")
        )
    )
}
