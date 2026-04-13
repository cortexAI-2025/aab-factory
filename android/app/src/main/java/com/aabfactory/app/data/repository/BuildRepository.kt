package com.aabfactory.app.data.repository

import com.aabfactory.app.data.remote.ApiService
import com.aabfactory.app.data.remote.dto.BuildRequest
import com.aabfactory.app.data.remote.dto.BuildResponse
import com.aabfactory.app.data.remote.dto.BuildStatusResponse
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class BuildRepository @Inject constructor(
    private val apiService: ApiService,
    private val authRepository: AuthRepository
) {

    suspend fun triggerBuild(
        projectId: String,
        keystoreType: String = "system"
    ): Result<BuildResponse> {
        return try {
            val token = authRepository.getIdToken()
                ?: return Result.failure(Exception("Not authenticated"))
            val response = apiService.triggerBuild(
                bearerToken = "Bearer $token",
                request = BuildRequest(
                    projectId = projectId,
                    keystoreType = keystoreType
                )
            )
            if (response.isSuccessful) {
                Result.success(response.body()!!)
            } else {
                Result.failure(Exception("Build trigger failed: ${response.code()}"))
            }
        } catch (e: Exception) {
            Timber.e(e, "Build trigger error for project $projectId")
            Result.failure(e)
        }
    }

    suspend fun getBuildStatus(buildId: String): Result<BuildStatusResponse> {
        return try {
            val token = authRepository.getIdToken()
                ?: return Result.failure(Exception("Not authenticated"))
            val response = apiService.getBuildStatus("Bearer $token", buildId)
            if (response.isSuccessful) {
                Result.success(response.body()!!)
            } else {
                Result.failure(Exception("Status fetch failed: ${response.code()}"))
            }
        } catch (e: Exception) {
            Timber.e(e, "Build status error for build $buildId")
            Result.failure(e)
        }
    }
}
