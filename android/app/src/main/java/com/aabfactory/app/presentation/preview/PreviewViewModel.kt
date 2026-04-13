package com.aabfactory.app.presentation.preview

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aabfactory.app.data.model.AppProject
import com.aabfactory.app.data.repository.AuthRepository
import com.aabfactory.app.data.repository.ProjectRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class PreviewState(
    val project: AppProject? = null,
    val isLoading: Boolean = true,
    val error: String? = null
)

@HiltViewModel
class PreviewViewModel @Inject constructor(
    private val authRepository: AuthRepository,
    private val projectRepository: ProjectRepository
) : ViewModel() {

    private val _state = MutableStateFlow(PreviewState())
    val state: StateFlow<PreviewState> = _state.asStateFlow()

    fun loadProject(projectId: String) {
        val uid = authRepository.currentUser?.uid ?: return
        viewModelScope.launch {
            _state.value = _state.value.copy(isLoading = true)
            val result = projectRepository.getProject(uid, projectId)
            result.fold(
                onSuccess = { project ->
                    _state.value = PreviewState(project = project, isLoading = false)
                },
                onFailure = { e ->
                    _state.value = PreviewState(isLoading = false, error = e.message)
                }
            )
        }
    }
}
