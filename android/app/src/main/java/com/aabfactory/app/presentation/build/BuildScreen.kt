package com.aabfactory.app.presentation.build

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.HourglassEmpty
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.aabfactory.app.data.model.BuildStatus
import com.aabfactory.app.data.model.Plan
import com.aabfactory.app.presentation.theme.BrandPurple
import com.aabfactory.app.presentation.theme.BrandTeal
import com.aabfactory.app.presentation.theme.Error
import com.aabfactory.app.presentation.theme.Success
import com.aabfactory.app.presentation.theme.Warning

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BuildScreen(
    projectId: String,
    onBack: () -> Unit,
    onUpgrade: () -> Unit,
    viewModel: BuildViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsState()
    val uriHandler = LocalUriHandler.current

    LaunchedEffect(projectId) {
        viewModel.loadProject(projectId)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Generate AAB", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, "Back") }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
                modifier = Modifier.statusBarsPadding()
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(24.dp)
                .navigationBarsPadding(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            if (state.isLoading) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = BrandPurple)
                }
                return@Scaffold
            }

            // Project info card
            state.project?.let { project ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("App", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(project.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.height(8.dp))
                        Row {
                            InfoChip("${project.sections.count { it.isVisible }} sections")
                            Spacer(Modifier.width(8.dp))
                            InfoChip("v${project.config.versionName}")
                            if (project.hasWatermark) {
                                Spacer(Modifier.width(8.dp))
                                InfoChip("Watermark", color = Warning)
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.height(24.dp))

            // Build status visualization
            AnimatedContent(targetState = state.buildStatus, label = "BuildStatus") { status ->
                when (status) {
                    BuildStatus.NONE -> BuildReadyPanel(
                        canBuild = state.canBuild,
                        isPro = state.userPlan.plan == Plan.PRO,
                        buildsUsed = state.userPlan.buildsUsedThisMonth,
                        onBuild = viewModel::triggerBuild,
                        onUpgrade = onUpgrade
                    )
                    BuildStatus.QUEUED -> BuildQueuedPanel(
                        queuePosition = state.queuePosition,
                        estimatedMinutes = state.estimatedMinutes
                    )
                    BuildStatus.BUILDING -> BuildInProgressPanel()
                    BuildStatus.SUCCESS -> BuildSuccessPanel(
                        downloadUrl = state.downloadUrl,
                        onDownload = { uriHandler.openUri(state.downloadUrl) }
                    )
                    BuildStatus.FAILED -> BuildFailedPanel(
                        errorMessage = state.errorMessage,
                        onRetry = viewModel::triggerBuild
                    )
                }
            }
        }
    }
}

@Composable
private fun BuildReadyPanel(
    canBuild: Boolean,
    isPro: Boolean,
    buildsUsed: Int,
    onBuild: () -> Unit,
    onUpgrade: () -> Unit
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            modifier = Modifier
                .size(120.dp)
                .clip(CircleShape)
                .background(Brush.linearGradient(listOf(BrandPurple, BrandTeal))),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Default.PlayArrow, null, tint = Color.White, modifier = Modifier.size(56.dp))
        }

        Spacer(Modifier.height(24.dp))
        Text("Ready to Build", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))
        Text(
            if (isPro) "Unlimited builds available"
            else "$buildsUsed/2 free builds used this month",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(Modifier.height(32.dp))

        if (canBuild) {
            Button(
                onClick = onBuild,
                modifier = Modifier.fillMaxWidth().height(56.dp),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(containerColor = BrandPurple)
            ) {
                Icon(Icons.Default.PlayArrow, null)
                Spacer(Modifier.width(8.dp))
                Text("Start Build", fontWeight = FontWeight.SemiBold)
            }
        } else {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(Icons.Default.Lock, null, tint = Warning, modifier = Modifier.size(32.dp))
                Spacer(Modifier.height(8.dp))
                Text("Build limit reached", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                Text("Free plan: 2 builds/month", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(16.dp))
                Button(
                    onClick = onUpgrade,
                    modifier = Modifier.fillMaxWidth().height(56.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = BrandPurple)
                ) {
                    Text("Upgrade to Pro — \$29/mo", fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}

@Composable
private fun BuildQueuedPanel(queuePosition: Int, estimatedMinutes: Int) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(Icons.Default.HourglassEmpty, null, tint = BrandTeal, modifier = Modifier.size(80.dp))
        Spacer(Modifier.height(16.dp))
        Text("Build Queued", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Text("Position #$queuePosition · ~$estimatedMinutes min",
            style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(24.dp))
        LinearProgressIndicator(modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(4.dp)), color = BrandTeal)
    }
}

@Composable
private fun BuildInProgressPanel() {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        CircularProgressIndicator(color = BrandPurple, modifier = Modifier.size(80.dp), strokeWidth = 6.dp)
        Spacer(Modifier.height(24.dp))
        Text("Building your app…", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))
        Text("Running Gradle bundleRelease", style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
        Spacer(Modifier.height(8.dp))
        Text("You'll be notified when it's ready", style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun BuildSuccessPanel(downloadUrl: String, onDownload: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(Icons.Default.CheckCircle, null, tint = Success, modifier = Modifier.size(80.dp))
        Spacer(Modifier.height(16.dp))
        Text("Build Complete!", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Text("Your .aab file is ready to download", style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
        Spacer(Modifier.height(24.dp))
        Button(
            onClick = onDownload,
            modifier = Modifier.fillMaxWidth().height(56.dp),
            shape = RoundedCornerShape(16.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Success),
            enabled = downloadUrl.isNotEmpty()
        ) {
            Icon(Icons.Default.CloudDownload, null)
            Spacer(Modifier.width(8.dp))
            Text("Download AAB", fontWeight = FontWeight.SemiBold)
        }
        Spacer(Modifier.height(12.dp))
        Text("Upload this file directly to Google Play Console",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center)
    }
}

@Composable
private fun BuildFailedPanel(errorMessage: String?, onRetry: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(Icons.Default.Error, null, tint = Error, modifier = Modifier.size(80.dp))
        Spacer(Modifier.height(16.dp))
        Text("Build Failed", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        errorMessage?.let {
            Spacer(Modifier.height(8.dp))
            Text(it, style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
        }
        Spacer(Modifier.height(24.dp))
        OutlinedButton(
            onClick = onRetry,
            modifier = Modifier.fillMaxWidth().height(52.dp),
            shape = RoundedCornerShape(14.dp)
        ) {
            Text("Retry Build")
        }
    }
}

@Composable
private fun InfoChip(text: String, color: Color = MaterialTheme.colorScheme.onSurfaceVariant) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(20.dp))
            .background(color.copy(alpha = 0.1f))
            .padding(horizontal = 10.dp, vertical = 4.dp)
    ) {
        Text(text, style = MaterialTheme.typography.labelSmall, color = color)
    }
}
