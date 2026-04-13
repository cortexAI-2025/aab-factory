package com.aabfactory.app.data.repository

import com.aabfactory.app.data.model.AppProject
import com.aabfactory.app.data.model.AppSection
import com.aabfactory.app.data.model.BuildStatus
import com.google.firebase.Timestamp
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import timber.log.Timber
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ProjectRepository @Inject constructor(
    private val firestore: FirebaseFirestore
) {

    private fun projectsCollection(uid: String) =
        firestore.collection("users").document(uid).collection("projects")

    fun observeProjects(uid: String): Flow<List<AppProject>> = callbackFlow {
        val listener = projectsCollection(uid)
            .orderBy("updatedAt", Query.Direction.DESCENDING)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Timber.e(error, "Error observing projects")
                    return@addSnapshotListener
                }
                val projects = snapshot?.documents?.mapNotNull { doc ->
                    doc.toObject(AppProject::class.java)?.copy(id = doc.id)
                } ?: emptyList()
                trySend(projects)
            }
        awaitClose { listener.remove() }
    }

    suspend fun getProject(uid: String, projectId: String): Result<AppProject> {
        return try {
            val doc = projectsCollection(uid).document(projectId).get().await()
            val project = doc.toObject(AppProject::class.java)?.copy(id = doc.id)
                ?: return Result.failure(Exception("Project not found"))
            Result.success(project)
        } catch (e: Exception) {
            Timber.e(e, "Failed to get project $projectId")
            Result.failure(e)
        }
    }

    suspend fun createProject(uid: String, project: AppProject): Result<String> {
        return try {
            val newProject = project.copy(
                userId = uid,
                createdAt = Timestamp.now(),
                updatedAt = Timestamp.now()
            )
            val docRef = projectsCollection(uid).add(newProject).await()
            Result.success(docRef.id)
        } catch (e: Exception) {
            Timber.e(e, "Failed to create project")
            Result.failure(e)
        }
    }

    suspend fun updateProject(uid: String, project: AppProject): Result<Unit> {
        return try {
            val updated = project.copy(updatedAt = Timestamp.now())
            projectsCollection(uid).document(project.id).set(updated).await()
            Result.success(Unit)
        } catch (e: Exception) {
            Timber.e(e, "Failed to update project ${project.id}")
            Result.failure(e)
        }
    }

    suspend fun updateBuildStatus(
        uid: String,
        projectId: String,
        buildStatus: BuildStatus,
        downloadUrl: String = ""
    ): Result<Unit> {
        return try {
            val updates = mutableMapOf<String, Any>(
                "buildStatus" to buildStatus.name,
                "updatedAt" to Timestamp.now()
            )
            if (downloadUrl.isNotEmpty()) updates["lastBuildUrl"] = downloadUrl
            projectsCollection(uid).document(projectId).update(updates).await()
            Result.success(Unit)
        } catch (e: Exception) {
            Timber.e(e, "Failed to update build status for $projectId")
            Result.failure(e)
        }
    }

    suspend fun deleteProject(uid: String, projectId: String): Result<Unit> {
        return try {
            projectsCollection(uid).document(projectId).delete().await()
            Result.success(Unit)
        } catch (e: Exception) {
            Timber.e(e, "Failed to delete project $projectId")
            Result.failure(e)
        }
    }

    suspend fun duplicateProject(uid: String, projectId: String): Result<String> {
        val original = getProject(uid, projectId).getOrElse { return Result.failure(it) }
        val copy = original.copy(
            id = "",
            name = "${original.name} (Copy)",
            createdAt = null,
            updatedAt = null,
            buildStatus = BuildStatus.NONE,
            lastBuildUrl = ""
        )
        return createProject(uid, copy)
    }
}
