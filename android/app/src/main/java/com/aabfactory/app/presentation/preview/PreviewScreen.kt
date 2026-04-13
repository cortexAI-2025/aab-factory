package com.aabfactory.app.presentation.preview

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.aabfactory.app.data.model.AppProject
import com.aabfactory.app.data.model.AppSection
import com.aabfactory.app.data.model.SectionType
import com.aabfactory.app.presentation.theme.BrandPurple
import com.aabfactory.app.presentation.theme.BrandTeal

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PreviewScreen(
    projectId: String,
    onBack: () -> Unit,
    onBuild: () -> Unit,
    viewModel: PreviewViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsState()

    LaunchedEffect(projectId) {
        viewModel.loadProject(projectId)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Live Preview", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    Icon(Icons.Default.PhoneAndroid, contentDescription = null,
                        tint = BrandPurple, modifier = Modifier.padding(end = 16.dp))
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                ),
                modifier = Modifier.statusBarsPadding()
            )
        },
        bottomBar = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surface)
                    .navigationBarsPadding()
                    .padding(16.dp)
            ) {
                Button(
                    onClick = onBuild,
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = BrandPurple)
                ) {
                    Icon(Icons.Default.Build, null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Generate AAB", fontWeight = FontWeight.SemiBold)
                }
            }
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        when {
            state.isLoading -> {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = BrandPurple)
                }
            }
            state.project != null -> {
                PhoneFrame(
                    project = state.project!!,
                    modifier = Modifier.padding(padding)
                )
            }
        }
    }
}

/**
 * Simulated phone frame that renders app sections as a live preview.
 * This is a compositional preview — a real implementation would use
 * a WebView or a custom renderer for full fidelity.
 */
@Composable
private fun PhoneFrame(project: AppProject, modifier: Modifier = Modifier) {
    val primaryColor = try {
        Color(android.graphics.Color.parseColor(project.config.primaryColor))
    } catch (e: Exception) { BrandPurple }

    val accentColor = try {
        Color(android.graphics.Color.parseColor(project.config.accentColor))
    } catch (e: Exception) { BrandTeal }

    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        // Phone shell
        Box(
            modifier = Modifier
                .width(280.dp)
                .height(560.dp)
                .clip(RoundedCornerShape(36.dp))
                .border(3.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(36.dp))
                .background(Color(0xFF1A1A2E))
        ) {
            // Screen content
            Column(modifier = Modifier.fillMaxSize()) {
                // Status bar
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(24.dp)
                        .background(primaryColor.copy(alpha = 0.8f))
                )

                // App content
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color(0xFFF8F8FF)),
                    contentPadding = PaddingValues(bottom = 16.dp)
                ) {
                    // App bar
                    item {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp)
                                .background(primaryColor),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = project.name,
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.Bold,
                                color = Color.White,
                                maxLines = 1
                            )
                        }
                    }

                    // Visible sections
                    items(project.sections.filter { it.isVisible }.sortedBy { it.order }) { section ->
                        PreviewSection(section = section, primaryColor = primaryColor, accentColor = accentColor)
                    }

                    // Watermark for free plan
                    if (project.hasWatermark) {
                        item {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(Color(0x22000000))
                                    .padding(4.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    "Built with AAB Factory — Free Plan",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = Color.Gray,
                                    textAlign = TextAlign.Center
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PreviewSection(
    section: AppSection,
    primaryColor: Color,
    accentColor: Color
) {
    when (section.type) {
        SectionType.HERO -> HeroSection(section, primaryColor, accentColor)
        SectionType.FEATURES -> FeaturesSection(section, primaryColor)
        SectionType.CONTACT -> ContactSection(section, accentColor)
        SectionType.FOOTER -> FooterSection(section, primaryColor)
        else -> GenericSection(section, primaryColor)
    }
}

@Composable
private fun HeroSection(section: AppSection, primary: Color, accent: Color) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(140.dp)
            .background(Brush.verticalGradient(listOf(primary, accent))),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(16.dp)) {
            Text(section.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = Color.White, textAlign = TextAlign.Center, maxLines = 2)
            if (section.subtitle.isNotEmpty()) {
                Spacer(Modifier.height(4.dp))
                Text(section.subtitle, style = MaterialTheme.typography.bodySmall, color = Color.White.copy(alpha = 0.85f), textAlign = TextAlign.Center, maxLines = 2)
            }
            if (section.ctaText.isNotEmpty()) {
                Spacer(Modifier.height(8.dp))
                Surface(shape = RoundedCornerShape(20.dp), color = Color.White.copy(alpha = 0.2f)) {
                    Text(section.ctaText, modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                        style = MaterialTheme.typography.labelSmall, color = Color.White)
                }
            }
        }
    }
}

@Composable
private fun FeaturesSection(section: AppSection, primary: Color) {
    Column(modifier = Modifier.fillMaxWidth().padding(12.dp)) {
        Text(section.title, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold, color = Color(0xFF1A1A2E))
        Spacer(Modifier.height(8.dp))
        repeat(3) { index ->
            Row(modifier = Modifier.padding(bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(modifier = Modifier.size(6.dp).clip(RoundedCornerShape(3.dp)).background(primary))
                Spacer(Modifier.width(8.dp))
                Text("Feature ${index + 1}", style = MaterialTheme.typography.bodySmall, color = Color(0xFF444466))
            }
        }
    }
}

@Composable
private fun ContactSection(section: AppSection, accent: Color) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(8.dp),
        shape = RoundedCornerShape(10.dp),
        colors = CardDefaults.cardColors(containerColor = accent.copy(alpha = 0.1f))
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(section.title.ifEmpty { "Contact Us" }, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold, color = Color(0xFF1A1A2E))
            Text(section.content.ifEmpty { "Get in touch with us" }, style = MaterialTheme.typography.bodySmall, color = Color.Gray)
        }
    }
}

@Composable
private fun FooterSection(section: AppSection, primary: Color) {
    Box(
        modifier = Modifier.fillMaxWidth().background(primary.copy(alpha = 0.1f)).padding(12.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(section.content.ifEmpty { "© 2025 All rights reserved" }, style = MaterialTheme.typography.bodySmall, color = Color.Gray)
    }
}

@Composable
private fun GenericSection(section: AppSection, primary: Color) {
    Column(modifier = Modifier.fillMaxWidth().padding(12.dp)) {
        if (section.title.isNotEmpty()) {
            Text(section.title, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold, color = Color(0xFF1A1A2E))
        }
        if (section.subtitle.isNotEmpty()) {
            Spacer(Modifier.height(4.dp))
            Text(section.subtitle, style = MaterialTheme.typography.bodySmall, color = Color.Gray)
        }
    }
}
