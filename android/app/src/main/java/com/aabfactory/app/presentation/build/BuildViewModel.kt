package com.aabfactory.app.presentation.build

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aabfactory.app.data.model.AppProject
import com.aabfactory.app.data.model.BuildStatus
import com.aabfactory.app.data.model.Plan
import com.aabfactory.app.data.model.PlanLimits
import com.aabfactory.app.data.model.UserPlan
import com.aabfactory.app.data.repository.AuthRepository
import com.aabfactory.app.data.repository.BuildRepository
import com.aabfactory.app.data.repository.ProjectRepository
import com.aabfactory.app.data.repository.SubscriptionRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class BuildState(
    val project: AppProject? = null,
    val userPlan: UserPlan = UserPlan(),
    val isLoading: Boolean = true,
    val isBuildTriggered: Boolean = false,
    val buildId: String = "",
    val buildStatus: BuildStatus = BuildStatus.NONE,
    val downloadUrl: String = "",
    val errorMessage: String? = null,
    val canBuild: Boolean = false,
    val estimatedMinutes: Int = 0,
    val queuePosition: Int = 0
)

@HiltViewModel
class BuildViewModel @Inject constructor(
    private val authRepository: AuthRepository,
    private val projectRepository: ProjectRepository,
    private val buildRepository: BuildRepository,
    private val subscriptionRepository: SubscriptionRepository
) : ViewModel() {

    private val _state = MutableStateFlow(BuildState())
    val state: StateFlow<BuildState> = _state.asStateFlow()

    private val uid get() = authRepository.currentUser?.uid ?: ""

    fun loadProject(projectId: String) {
        viewModelScope.launch {
            _state.value = _state.value.copy(isLoading = true)
            val projectResult = projectRepository.getProject(uid, projectId)
            val planResult = subscriptionRepository.getUserPlan(uid)

            val project = projectResult.getOrNull()
            val plan = planResult.getOrDefault(UserPlan())

            _state.value = _state.value.copy(
                project = project,
                userPlan = plan,
                isLoading = false,
                canBuild = PlanLimits.canBuild(plan)
            )
        }
    }

    fun triggerBuild() {
        val project = _state.value.project ?: return
        if (!_state.value.canBuild) return

        viewModelScope.launch {
            _state.value = _state.value.copy(buildStatus = BuildStatus.QUEUED, errorMessage = null)
            val result = buildRepository.triggerBuild(project.id)
            result.fold(
                onSuccess = { response ->
                    _state.value = _state.value.copy(
                        isBuildTriggered = true,
                        buildId = response.buildId,
                        buildStatus = BuildStatus.QUEUED,
                        estimatedMinutes = response.estimatedMinutes,
                        queuePosition = response.queuePosition
                    )
                    // Update Firestore
                    projectRepository.updateBuildStatus(uid, project.id, BuildStatus.QUEUED)
                    // Start polling
                    pollBuildStatus(response.buildId, project.id)
                },
                onFailure = { e ->
                    _state.value = _state.value.copy(
                        buildStatus = BuildStatus.FAILED,
                        errorMessage = e.message
                    )
                }
            )
        }
    }

    private fun pollBuildStatus(buildId: String, projectId: String) {
        viewModelScope.launch {
            var attempts = 0
            val maxAttempts = 60 // 5 minutes with 5s intervals
            while (attempts < maxAttempts) {
                delay(5_000L)
                val result = buildRepository.getBuildStatus(buildId)
                result.fold(
                    onSuccess = { status ->
                        val buildStatus = when (status.status) {
                            "BUILDING" -> BuildStatus.BUILDING
                            "SUCCESS" -> BuildStatus.SUCCESS
                            "FAILED" -> BuildStatus.FAILED
                            else -> BuildStatus.QUEUED
                        }
                        _state.value = _state.value.copy(
                            buildStatus = buildStatus,
                            downloadUrl = status.downloadUrl ?: "",
                            errorMessage = status.errorMessage
                        )
                        // Sync to Firestore
                        projectRepository.updateBuildStatus(
                            uid, projectId, buildStatus,
                            downloadUrl = status.downloadUrl ?: ""
                        )
                        if (buildStatus == BuildStatus.SUCCESS || buildStatus == BuildStatus.FAILED) {
                            return@launch
                        }
                    },
                    onFailure = { /* Continue polling on network error */ }
                )
                attempts++
            }
        }
    }
}
