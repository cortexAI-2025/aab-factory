package com.aabfactory.app.presentation.editor

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aabfactory.app.data.model.AppConfig
import com.aabfactory.app.data.model.AppProject
import com.aabfactory.app.data.model.AppSection
import com.aabfactory.app.data.model.SectionType
import com.aabfactory.app.data.repository.AuthRepository
import com.aabfactory.app.data.repository.ProjectRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject

data class EditorState(
    val project: AppProject? = null,
    val isLoading: Boolean = true,
    val isSaving: Boolean = false,
    val selectedSectionId: String? = null,
    val error: String? = null,
    val saveSuccess: Boolean = false
)

@HiltViewModel
class EditorViewModel @Inject constructor(
    private val authRepository: AuthRepository,
    private val projectRepository: ProjectRepository
) : ViewModel() {

    private val _state = MutableStateFlow(EditorState())
    val state: StateFlow<EditorState> = _state.asStateFlow()

    private val uid get() = authRepository.currentUser?.uid ?: ""

    fun loadProject(projectId: String) {
        viewModelScope.launch {
            _state.value = _state.value.copy(isLoading = true)
            val result = projectRepository.getProject(uid, projectId)
            result.fold(
                onSuccess = { project ->
                    _state.value = _state.value.copy(project = project, isLoading = false)
                },
                onFailure = { e ->
                    _state.value = _state.value.copy(isLoading = false, error = e.message)
                }
            )
        }
    }

    fun updateAppName(name: String) {
        val project = _state.value.project ?: return
        _state.value = _state.value.copy(
            project = project.copy(name = name),
            saveSuccess = false
        )
    }

    fun updateConfig(config: AppConfig) {
        val project = _state.value.project ?: return
        _state.value = _state.value.copy(
            project = project.copy(config = config),
            saveSuccess = false
        )
    }

    fun updatePrimaryColor(hex: String) {
        val project = _state.value.project ?: return
        _state.value = _state.value.copy(
            project = project.copy(config = project.config.copy(primaryColor = hex))
        )
    }

    fun updateAccentColor(hex: String) {
        val project = _state.value.project ?: return
        _state.value = _state.value.copy(
            project = project.copy(config = project.config.copy(accentColor = hex))
        )
    }

    fun updateSection(updated: AppSection) {
        val project = _state.value.project ?: return
        val sections = project.sections.map {
            if (it.id == updated.id) updated else it
        }
        _state.value = _state.value.copy(project = project.copy(sections = sections))
    }

    fun addSection(type: SectionType) {
        val project = _state.value.project ?: return
        val newSection = AppSection(
            id = UUID.randomUUID().toString(),
            type = type,
            title = type.name.lowercase().replaceFirstChar { it.uppercase() },
            order = project.sections.size
        )
        _state.value = _state.value.copy(
            project = project.copy(sections = project.sections + newSection)
        )
    }

    fun removeSection(sectionId: String) {
        val project = _state.value.project ?: return
        val sections = project.sections.filter { it.id != sectionId }
        _state.value = _state.value.copy(project = project.copy(sections = sections))
    }

    fun toggleSectionVisibility(sectionId: String) {
        val project = _state.value.project ?: return
        val sections = project.sections.map {
            if (it.id == sectionId) it.copy(isVisible = !it.isVisible) else it
        }
        _state.value = _state.value.copy(project = project.copy(sections = sections))
    }

    fun selectSection(sectionId: String?) {
        _state.value = _state.value.copy(selectedSectionId = sectionId)
    }

    fun saveProject() {
        val project = _state.value.project ?: return
        viewModelScope.launch {
            _state.value = _state.value.copy(isSaving = true, saveSuccess = false, error = null)
            val result = projectRepository.updateProject(uid, project)
            result.fold(
                onSuccess = {
                    _state.value = _state.value.copy(isSaving = false, saveSuccess = true)
                },
                onFailure = { e ->
                    _state.value = _state.value.copy(isSaving = false, error = e.message)
                }
            )
        }
    }
}
