package com.aabfactory.app.presentation.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aabfactory.app.data.model.AppProject
import com.aabfactory.app.data.model.UserPlan
import com.aabfactory.app.data.repository.AuthRepository
import com.aabfactory.app.data.repository.ProjectRepository
import com.aabfactory.app.data.repository.SubscriptionRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import javax.inject.Inject

data class HomeState(
    val projects: List<AppProject> = emptyList(),
    val userPlan: UserPlan = UserPlan(),
    val isLoading: Boolean = true,
    val error: String? = null
)

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val authRepository: AuthRepository,
    private val projectRepository: ProjectRepository,
    private val subscriptionRepository: SubscriptionRepository
) : ViewModel() {

    private val _state = MutableStateFlow(HomeState())
    val state: StateFlow<HomeState> = _state.asStateFlow()

    private val uid get() = authRepository.currentUser?.uid ?: ""

    init {
        loadData()
    }

    private fun loadData() {
        val userId = uid
        if (userId.isEmpty()) return

        // Observe projects in real-time
        projectRepository.observeProjects(userId)
            .onEach { projects ->
                _state.value = _state.value.copy(projects = projects, isLoading = false)
            }
            .launchIn(viewModelScope)

        // Observe user plan in real-time
        subscriptionRepository.observeUserPlan(userId)
            .onEach { plan ->
                _state.value = _state.value.copy(userPlan = plan)
            }
            .launchIn(viewModelScope)
    }

    fun deleteProject(projectId: String) {
        viewModelScope.launch {
            projectRepository.deleteProject(uid, projectId)
        }
    }

    fun duplicateProject(projectId: String) {
        viewModelScope.launch {
            projectRepository.duplicateProject(uid, projectId)
        }
    }

    fun signOut() {
        authRepository.signOut()
    }
}
