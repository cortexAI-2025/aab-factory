package com.aabfactory.app.presentation.editor

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Preview
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.aabfactory.app.data.model.AppSection
import com.aabfactory.app.data.model.SectionType
import com.aabfactory.app.presentation.theme.BrandPurple
import com.aabfactory.app.presentation.theme.BrandTeal
import com.aabfactory.app.presentation.theme.Success

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditorScreen(
    projectId: String,
    onPreview: () -> Unit,
    onBuild: () -> Unit,
    onBack: () -> Unit,
    viewModel: EditorViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsState()

    LaunchedEffect(projectId) {
        viewModel.loadProject(projectId)
    }

    var selectedTab by remember { mutableIntStateOf(0) }
    var showAddSectionSheet by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = state.project?.name ?: "Editor",
                        fontWeight = FontWeight.Bold,
                        maxLines = 1
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    // Save indicator
                    AnimatedVisibility(visible = state.saveSuccess) {
                        Icon(Icons.Default.Check, contentDescription = null, tint = Success, modifier = Modifier.padding(end = 4.dp))
                    }
                    IconButton(onClick = viewModel::saveProject, enabled = !state.isSaving) {
                        if (state.isSaving) {
                            CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                        } else {
                            Icon(Icons.Default.Save, contentDescription = "Save")
                        }
                    }
                    IconButton(onClick = onPreview) {
                        Icon(Icons.Default.Preview, contentDescription = "Preview")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                ),
                modifier = Modifier.statusBarsPadding()
            )
        },
        bottomBar = {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surface)
                    .navigationBarsPadding()
                    .padding(16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Button(
                    onClick = onBuild,
                    modifier = Modifier.fillMaxWidth(),
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
        if (state.isLoading) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = BrandPurple)
            }
            return@Scaffold
        }

        val project = state.project ?: return@Scaffold

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // Tab navigation
            val tabs = listOf("General", "Colors", "Sections")
            ScrollableTabRow(
                selectedTabIndex = selectedTab,
                containerColor = MaterialTheme.colorScheme.background,
                edgePadding = 0.dp
            ) {
                tabs.forEachIndexed { index, title ->
                    Tab(
                        selected = selectedTab == index,
                        onClick = { selectedTab = index },
                        text = { Text(title) }
                    )
                }
            }

            when (selectedTab) {
                0 -> GeneralTab(
                    appName = project.name,
                    packageName = project.config.packageName,
                    versionName = project.config.versionName,
                    onAppNameChange = viewModel::updateAppName,
                    onPackageNameChange = { viewModel.updateConfig(project.config.copy(packageName = it)) },
                    onVersionNameChange = { viewModel.updateConfig(project.config.copy(versionName = it)) }
                )
                1 -> ColorsTab(
                    primaryColor = project.config.primaryColor,
                    accentColor = project.config.accentColor,
                    onPrimaryChange = viewModel::updatePrimaryColor,
                    onAccentChange = viewModel::updateAccentColor
                )
                2 -> SectionsTab(
                    sections = project.sections,
                    selectedSectionId = state.selectedSectionId,
                    onSelectSection = viewModel::selectSection,
                    onUpdateSection = viewModel::updateSection,
                    onToggleVisibility = viewModel::toggleSectionVisibility,
                    onRemoveSection = viewModel::removeSection,
                    onAddSection = { showAddSectionSheet = true }
                )
            }
        }

        // Add section bottom sheet
        if (showAddSectionSheet) {
            val sheetState = rememberModalBottomSheetState()
            ModalBottomSheet(
                onDismissRequest = { showAddSectionSheet = false },
                sheetState = sheetState
            ) {
                AddSectionSheet(
                    onAddSection = { type ->
                        viewModel.addSection(type)
                        showAddSectionSheet = false
                    }
                )
            }
        }
    }
}

@Composable
private fun GeneralTab(
    appName: String,
    packageName: String,
    versionName: String,
    onAppNameChange: (String) -> Unit,
    onPackageNameChange: (String) -> Unit,
    onVersionNameChange: (String) -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            EditorSection(title = "App Identity") {
                EditorTextField("App Name", appName, onAppNameChange)
                Spacer(Modifier.height(12.dp))
                EditorTextField(
                    label = "Package Name",
                    value = packageName,
                    onValueChange = onPackageNameChange,
                    hint = "com.yourcompany.appname"
                )
                Spacer(Modifier.height(12.dp))
                EditorTextField("Version Name", versionName, onVersionNameChange, hint = "1.0.0")
            }
        }
    }
}

@Composable
private fun ColorsTab(
    primaryColor: String,
    accentColor: String,
    onPrimaryChange: (String) -> Unit,
    onAccentChange: (String) -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            EditorSection(title = "Brand Colors") {
                ColorPickerRow(
                    label = "Primary Color",
                    colorHex = primaryColor,
                    onColorChange = onPrimaryChange
                )
                Spacer(Modifier.height(12.dp))
                ColorPickerRow(
                    label = "Accent Color",
                    colorHex = accentColor,
                    onColorChange = onAccentChange
                )
            }
        }

        // Quick color presets
        item {
            EditorSection(title = "Presets") {
                val presets = listOf(
                    "#6C63FF" to "#00D4AA",
                    "#FF4757" to "#FFA502",
                    "#2ED573" to "#1E90FF",
                    "#FF6B81" to "#ECCC68",
                    "#3D5A80" to "#98C1D9"
                )
                presets.forEach { (primary, accent) ->
                    ColorPresetRow(primary = primary, accent = accent, onSelect = {
                        onPrimaryChange(primary)
                        onAccentChange(accent)
                    })
                    Spacer(Modifier.height(8.dp))
                }
            }
        }
    }
}

@Composable
private fun SectionsTab(
    sections: List<AppSection>,
    selectedSectionId: String?,
    onSelectSection: (String?) -> Unit,
    onUpdateSection: (AppSection) -> Unit,
    onToggleVisibility: (String) -> Unit,
    onRemoveSection: (String) -> Unit,
    onAddSection: () -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("App Sections", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                IconButton(onClick = onAddSection) {
                    Icon(Icons.Default.Add, contentDescription = "Add section", tint = BrandPurple)
                }
            }
        }

        items(sections, key = { it.id }) { section ->
            SectionItem(
                section = section,
                isSelected = section.id == selectedSectionId,
                onSelect = { onSelectSection(section.id) },
                onToggleVisibility = { onToggleVisibility(section.id) },
                onRemove = { onRemoveSection(section.id) },
                onUpdate = onUpdateSection
            )
        }
    }
}

@Composable
private fun SectionItem(
    section: AppSection,
    isSelected: Boolean,
    onSelect: () -> Unit,
    onToggleVisibility: () -> Unit,
    onRemove: () -> Unit,
    onUpdate: (AppSection) -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .then(
                if (isSelected) Modifier.border(2.dp, BrandPurple, RoundedCornerShape(12.dp))
                else Modifier
            ),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        onClick = onSelect
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(section.type.name.lowercase().replaceFirstChar { it.uppercase() },
                        style = MaterialTheme.typography.labelMedium, color = BrandPurple)
                    Text(section.title.ifEmpty { "Untitled" },
                        style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                }
                IconButton(onClick = onToggleVisibility, modifier = Modifier.size(36.dp)) {
                    Icon(
                        if (section.isVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                }
                IconButton(onClick = onRemove, modifier = Modifier.size(36.dp)) {
                    Icon(Icons.Default.Delete, contentDescription = null,
                        tint = com.aabfactory.app.presentation.theme.Error, modifier = Modifier.size(18.dp))
                }
            }

            if (isSelected) {
                Spacer(Modifier.height(12.dp))
                EditorTextField("Title", section.title, { onUpdate(section.copy(title = it)) })
                Spacer(Modifier.height(8.dp))
                EditorTextField("Subtitle", section.subtitle, { onUpdate(section.copy(subtitle = it)) })
                Spacer(Modifier.height(8.dp))
                EditorTextField("CTA Text", section.ctaText, { onUpdate(section.copy(ctaText = it)) })
            }
        }
    }
}

@Composable
private fun EditorSection(title: String, content: @Composable () -> Unit) {
    Column {
        Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(bottom = 12.dp))
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(modifier = Modifier.padding(16.dp)) { content() }
        }
    }
}

@Composable
private fun EditorTextField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    hint: String = ""
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        placeholder = if (hint.isNotEmpty()) ({ Text(hint, color = MaterialTheme.colorScheme.onSurfaceVariant) }) else null,
        modifier = Modifier.fillMaxWidth(),
        singleLine = true,
        shape = RoundedCornerShape(10.dp),
        colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = BrandPurple)
    )
}

@Composable
private fun ColorPickerRow(label: String, colorHex: String, onColorChange: (String) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(
                    try { Color(android.graphics.Color.parseColor(colorHex)) }
                    catch (e: Exception) { BrandPurple }
                )
                .border(2.dp, MaterialTheme.colorScheme.outline, CircleShape)
        )
        Spacer(Modifier.width(12.dp))
        OutlinedTextField(
            value = colorHex,
            onValueChange = onColorChange,
            label = { Text(label) },
            modifier = Modifier.weight(1f),
            singleLine = true,
            shape = RoundedCornerShape(10.dp),
            colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = BrandPurple)
        )
    }
}

@Composable
private fun ColorPresetRow(primary: String, accent: String, onSelect: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .clickable(onClick = onSelect)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(modifier = Modifier.size(28.dp).clip(CircleShape).background(
            try { Color(android.graphics.Color.parseColor(primary)) } catch (e: Exception) { BrandPurple }
        ))
        Spacer(Modifier.width(8.dp))
        Box(modifier = Modifier.size(28.dp).clip(CircleShape).background(
            try { Color(android.graphics.Color.parseColor(accent)) } catch (e: Exception) { BrandTeal }
        ))
        Spacer(Modifier.width(12.dp))
        Text("$primary · $accent", style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun AddSectionSheet(onAddSection: (SectionType) -> Unit) {
    Column(modifier = Modifier.padding(24.dp)) {
        Text("Add Section", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(16.dp))
        val types = SectionType.entries.filter { it != SectionType.CUSTOM }
        types.forEach { type ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .clickable { onAddSection(type) }
                    .padding(14.dp)
            ) {
                Text(type.name.lowercase().replaceFirstChar { it.uppercase() },
                    style = MaterialTheme.typography.bodyMedium)
            }
        }
        Spacer(Modifier.height(32.dp))
    }
}
