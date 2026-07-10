package be.saikono.poincare.ui.parent

// POINCARE_REPAIR_V29: parent-localization

import android.speech.tts.Voice
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import be.saikono.poincare.data.settings.VoiceSettings
import be.saikono.poincare.model.AnimationSettings
import be.saikono.poincare.model.ChildProfile
import be.saikono.poincare.model.LearningDomain
import be.saikono.poincare.model.SkillLevel
import be.saikono.poincare.model.isMainCategoryEnabled
import be.saikono.poincare.model.isSubcategoryEnabled
import be.saikono.poincare.ui.components.systemNavigationMargin
import be.saikono.poincare.ui.home.getSubCategoriesForDomain
import be.saikono.poincare.ui.spaceMap.SpaceBackground
import be.saikono.poincare.ui.theme.CockpitGray
import be.saikono.poincare.ui.theme.DeepSpaceBlue
import be.saikono.poincare.ui.theme.PlanetBlue
import be.saikono.poincare.ui.theme.PlanetGold
import be.saikono.poincare.ui.theme.PlanetRed
import be.saikono.poincare.ui.theme.PoincareTheme
import be.saikono.poincare.ui.theme.StarWhite
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flowOf

@Composable
fun ParentDashboardScreen(
    viewModel: ParentViewModel,
    onNavigateBack: () -> Unit,
    onProfileDeleted: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    val availableVoices by viewModel.availableVoices.collectAsState()

    ParentDashboardContent(
        uiState = uiState,
        availableVoices = availableVoices,
        getVoiceSettings = viewModel::getVoiceSettings,
        onUpdateVoiceSettings = viewModel::updateVoiceSettings,
        onTestVoice = viewModel::testVoice,
        onNavigateBack = onNavigateBack,
        onUpdateName = viewModel::updateProfileName,
        onUpdateBirthYear = viewModel::updateBirthYear,
        onUpdateLevel = viewModel::updateSkillLevel,
        onUpdateMainCategoryEnabled = viewModel::updateMainCategoryEnabled,
        onUpdateSubcategoryEnabled = viewModel::updateSubcategoryEnabled,
        onUpdateGiftedMode = viewModel::updateGiftedMode,
        onUpdateShowUnassignedExercises = viewModel::updateShowUnassignedExercises,
        onUpdateAnimationSettings = viewModel::updateAnimationSettings,
        onUpdateLanguage = viewModel::updateLanguage,
        onDeleteProfile = { viewModel.deleteCurrentProfile(onProfileDeleted) }
    )
}

@Composable
fun ParentDashboardContent(
    uiState: ParentDashboardUiState,
    availableVoices: List<Voice>,
    getVoiceSettings: (String) -> Flow<VoiceSettings>,
    onUpdateVoiceSettings: (String, VoiceSettings) -> Unit,
    onTestVoice: (String, String, VoiceSettings) -> Unit,
    onNavigateBack: () -> Unit,
    onUpdateName: (String) -> Unit,
    onUpdateBirthYear: (Int) -> Unit,
    onUpdateLevel: (LearningDomain, SkillLevel) -> Unit,
    onUpdateMainCategoryEnabled: (LearningDomain, Boolean) -> Unit,
    onUpdateSubcategoryEnabled: (String, Boolean) -> Unit,
    onUpdateGiftedMode: (Boolean) -> Unit,
    onUpdateShowUnassignedExercises: (Boolean) -> Unit,
    onUpdateAnimationSettings: (AnimationSettings) -> Unit,
    onUpdateLanguage: (LanguageLayer, String) -> Unit,
    onDeleteProfile: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(DeepSpaceBlue)
    ) {
        SpaceBackground()

        Column(
            modifier = Modifier
                .fillMaxSize()
                .widthIn(max = 800.dp)
                .align(Alignment.TopCenter)
                .systemNavigationMargin()
                .padding(horizontal = 24.dp, vertical = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = onNavigateBack,
                    modifier = Modifier.size(48.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.ArrowBack,
                        contentDescription = "Terug naar kaart",
                        tint = StarWhite
                    )
                }
                Spacer(modifier = Modifier.width(16.dp))
                Text(
                    text = parentText("dashboard"),
                    style = MaterialTheme.typography.titleLarge,
                    color = StarWhite
                )
            }

            if (uiState.isLoading) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(color = PlanetGold)
                }
            } else {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(24.dp)
                ) {
                    ProfileSettingsSection(
                        profile = uiState.currentProfile,
                        onUpdateName = onUpdateName,
                        onUpdateBirthYear = onUpdateBirthYear,
                        onUpdateLevel = onUpdateLevel,
                        onUpdateMainCategoryEnabled = onUpdateMainCategoryEnabled,
                        onUpdateSubcategoryEnabled = onUpdateSubcategoryEnabled,
                        onUpdateGiftedMode = onUpdateGiftedMode,
                        onUpdateShowUnassignedExercises = onUpdateShowUnassignedExercises,
                        onDeleteProfile = onDeleteProfile
                    )

                    LanguageSettingsSection(
                        profile = uiState.currentProfile,
                        onUpdateLanguage = onUpdateLanguage
                    )

                    /* Voice settings hidden as requested.
                    VoiceSettingsSection(
                        availableVoices = availableVoices,
                        getVoiceSettings = getVoiceSettings,
                        onUpdateVoiceSettings = onUpdateVoiceSettings,
                        onTestVoice = onTestVoice
                    )
                    */

                    AnimationSettingsSection(
                        settings = uiState.animationSettings,
                        onUpdateSettings = onUpdateAnimationSettings
                    )

                    SyncStatusSection(pendingCount = uiState.pendingSyncCount)

                    Spacer(modifier = Modifier.height(40.dp))
                }
            }
        }
    }
}

@Composable
fun ProfileSettingsSection(
    profile: ChildProfile?,
    onUpdateName: (String) -> Unit,
    onUpdateBirthYear: (Int) -> Unit,
    onUpdateLevel: (LearningDomain, SkillLevel) -> Unit,
    onUpdateMainCategoryEnabled: (LearningDomain, Boolean) -> Unit,
    onUpdateSubcategoryEnabled: (String, Boolean) -> Unit,
    onUpdateGiftedMode: (Boolean) -> Unit,
    onUpdateShowUnassignedExercises: (Boolean) -> Unit,
    onDeleteProfile: () -> Unit
) {
    if (profile == null) return

    var showDeleteConfirmation by remember { mutableStateOf(false) }
    var nameText by remember(profile.id) { mutableStateOf(profile.name) }
    var birthYearText by remember(profile.id) { mutableStateOf(profile.birthYear.toString()) }

    if (showDeleteConfirmation) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirmation = false },
            containerColor = PlanetGold.copy(alpha = 0.85f),
            titleContentColor = DeepSpaceBlue,
            textContentColor = DeepSpaceBlue,
            title = { Text(parentText("deleteProfileTitle")) },
            text = {
                Text(
                    parentDeleteConfirmation(profile.name)
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showDeleteConfirmation = false
                        onDeleteProfile()
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = DeepSpaceBlue,
                        contentColor = PlanetGold
                    )
                ) {
                    Text(parentText("delete"))
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showDeleteConfirmation = false },
                    colors = ButtonDefaults.textButtonColors(contentColor = DeepSpaceBlue)
                ) {
                    Text(parentText("cancel"))
                }
            }
        )
    }

    DashboardCard(title = parentText("profileSettings")) {
        EditableSettingTextField(
            value = nameText,
            onValueChange = { nameText = it },
            onSave = { if (nameText.isNotBlank()) onUpdateName(nameText.trim()) },
            label = parentText("astronautName"),
            contentDescription = "Naam van de kind-astronaut"
        )

        Spacer(modifier = Modifier.height(16.dp))

        EditableSettingTextField(
            value = birthYearText,
            onValueChange = { value -> birthYearText = value.filter { it.isDigit() }.take(4) },
            onSave = { birthYearText.toIntOrNull()?.let(onUpdateBirthYear) },
            label = parentText("birthYear"),
            contentDescription = "Geboortejaar van de astronaut"
        )

        Spacer(modifier = Modifier.height(24.dp))

        Text(
            text = parentText("educationLevels"),
            style = MaterialTheme.typography.titleSmall,
            color = StarWhite
        )

        DomainLevelSelector(parentText("reading"), LearningDomain.READING, profile.levelFor(LearningDomain.READING), onUpdateLevel)
        DomainLevelSelector(parentText("writing"), LearningDomain.WRITING, profile.levelFor(LearningDomain.WRITING), onUpdateLevel)
        DomainLevelSelector(parentText("math"), LearningDomain.MATH, profile.levelFor(LearningDomain.MATH), onUpdateLevel)
        DomainLevelSelector(parentText("geometry"), LearningDomain.GEOMETRY, profile.levelFor(LearningDomain.GEOMETRY), onUpdateLevel)
        DomainLevelSelector(parentText("worldKnowledge"), LearningDomain.WORLD_KNOWLEDGE, profile.levelFor(LearningDomain.WORLD_KNOWLEDGE), onUpdateLevel)
        DomainLevelSelector(parentText("puzzles"), LearningDomain.PUZZLE, profile.levelFor(LearningDomain.PUZZLE), onUpdateLevel)

        Spacer(modifier = Modifier.height(24.dp))

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)
        ) {
            Text(
                text = parentText("showMixedExercises"),
                color = StarWhite,
                modifier = Modifier.weight(1f).padding(end = 12.dp)
            )
            Checkbox(
                checked = profile.showUnassignedExercises,
                onCheckedChange = onUpdateShowUnassignedExercises,
                colors = CheckboxDefaults.colors(
                    checkedColor = PlanetBlue,
                    checkmarkColor = DeepSpaceBlue
                )
            )
        }

        Text(
            text = parentText("mixedExerciseHint"),
            color = StarWhite.copy(alpha = 0.66f),
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(24.dp))

        CategoryAccessSection(
            profile = profile,
            onUpdateMainCategoryEnabled = onUpdateMainCategoryEnabled,
            onUpdateSubcategoryEnabled = onUpdateSubcategoryEnabled
        )

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 16.dp)
                .heightIn(min = 48.dp)
        ) {
            Text(text = parentText("giftedMode"), color = StarWhite)
            Switch(
                checked = profile.giftedModeEnabled,
                onCheckedChange = onUpdateGiftedMode,
                colors = SwitchDefaults.colors(
                    checkedTrackColor = PlanetBlue,
                    checkedThumbColor = StarWhite
                ),
                modifier = Modifier.semantics {
                    contentDescription = "Schakel Gifted Mode in voor meer uitdagende puzzels"
                }
            )
        }

        Spacer(modifier = Modifier.height(24.dp))

        OutlinedButton(
            onClick = { showDeleteConfirmation = true },
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.outlinedButtonColors(contentColor = PlanetRed)
        ) {
            Icon(Icons.Default.Delete, contentDescription = null)
            Spacer(modifier = Modifier.width(8.dp))
            Text(parentText("deleteAccount"))
        }
    }
}

@Composable
fun CategoryAccessSection(
    profile: ChildProfile,
    onUpdateMainCategoryEnabled: (LearningDomain, Boolean) -> Unit,
    onUpdateSubcategoryEnabled: (String, Boolean) -> Unit
) {
    Text(
        text = parentText("categories"),
        style = MaterialTheme.typography.titleSmall,
        color = StarWhite
    )
    Spacer(modifier = Modifier.height(8.dp))

    val categories: List<Pair<LearningDomain, String>> = listOf(
        LearningDomain.READING to parentText("communication"),
        LearningDomain.WRITING to parentText("writing"),
        LearningDomain.MATH to parentText("numbers"),
        LearningDomain.GEOMETRY to parentText("geometry"),
        LearningDomain.WORLD_KNOWLEDGE to parentText("worldKnowledge"),
        LearningDomain.PUZZLE to parentText("puzzles")
    )

    categories.forEach { (domain, label) ->
        val domainEnabled = profile.isMainCategoryEnabled(domain)
        var expanded by remember(domain) { mutableStateOf(false) }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 6.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 48.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .weight(1f)
                        .clickable { expanded = !expanded }
                ) {
                    Text(
                        text = if (expanded) "▾" else "▸",
                        color = StarWhite.copy(alpha = 0.75f),
                        style = MaterialTheme.typography.titleMedium
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = label,
                        color = StarWhite,
                        style = MaterialTheme.typography.titleMedium
                    )
                }
                Checkbox(
                    checked = domainEnabled,
                    onCheckedChange = { checked -> onUpdateMainCategoryEnabled(domain, checked) },
                    colors = CheckboxDefaults.colors(
                        checkedColor = PlanetBlue,
                        checkmarkColor = DeepSpaceBlue
                    )
                )
            }

            if (domainEnabled && expanded) {
                getSubCategoriesForDomain(domain.name).forEach { subcategory ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 16.dp)
                            .heightIn(min = 40.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = parentSubcategoryText(subcategory.id, subcategory.nameNl),
                            color = StarWhite.copy(alpha = 0.78f),
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier
                                .weight(1f)
                                .padding(end = 12.dp)
                        )
                        Checkbox(
                            checked = profile.isSubcategoryEnabled(subcategory.id),
                            onCheckedChange = { checked -> onUpdateSubcategoryEnabled(subcategory.id, checked) },
                            colors = CheckboxDefaults.colors(
                                checkedColor = PlanetBlue,
                                checkmarkColor = StarWhite
                            )
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun DomainLevelSelector(
    label: String,
    domain: LearningDomain,
    currentLevel: SkillLevel,
    onUpdateLevel: (LearningDomain, SkillLevel) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp)
    ) {
        Text(
            text = label,
            color = StarWhite.copy(alpha = 0.7f),
            style = MaterialTheme.typography.labelMedium
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            SkillLevel.entries
                .filterNot { level -> level == SkillLevel.NOT_APPLICABLE }
                .forEach { level ->
                    val isSelected = level == currentLevel
                    Surface(
                        onClick = { onUpdateLevel(domain, level) },
                        shape = RoundedCornerShape(8.dp),
                        color = if (isSelected) PlanetBlue else Color.White.copy(alpha = 0.05f),
                        contentColor = if (isSelected) StarWhite else StarWhite,
                        modifier = Modifier
                            .heightIn(min = 40.dp)
                            .weight(1f)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text(
                                text = level.displayLabel(),
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = if (isSelected) FontWeight.Medium else FontWeight.Normal
                            )
                        }
                    }
                }
        }
    }
}

private fun SkillLevel.displayLabel(): String = (ordinal + 1).toString()

private fun ChildProfile.levelFor(domain: LearningDomain): SkillLevel = when (domain) {
    LearningDomain.READING -> readingLevel
    LearningDomain.WRITING -> writingLevel
    LearningDomain.MATH -> mathLevel
    LearningDomain.PUZZLE -> puzzleLevel
    LearningDomain.GEOMETRY -> geometryLevel
    LearningDomain.WORLD_KNOWLEDGE -> worldKnowledgeLevel
}

@Composable
fun EditableSettingTextField(
    value: String,
    onValueChange: (String) -> Unit,
    onSave: () -> Unit,
    label: String,
    contentDescription: String,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            label = { Text(label) },
            singleLine = true,
            modifier = Modifier
                .weight(1f)
                .semantics { this.contentDescription = contentDescription },
            colors = OutlinedTextFieldDefaults.colors(
                focusedTextColor = StarWhite,
                unfocusedTextColor = StarWhite
            )
        )
        IconButton(
            onClick = onSave,
            modifier = Modifier.size(48.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Check,
                contentDescription = "$label opslaan",
                tint = PlanetGold
            )
        }
    }
}

@Composable
fun LanguageSettingsSection(
    profile: ChildProfile?,
    onUpdateLanguage: (LanguageLayer, String) -> Unit
) {
    if (profile == null) return

    DashboardCard(title = parentText("languageSettings")) {
        LanguageRow(parentText("language"), profile.appLanguage) { selectedLanguage ->
            onUpdateLanguage(LanguageLayer.ALL, selectedLanguage)
        }
    }
}

@Composable
fun LanguageRow(
    label: String,
    currentCode: String,
    onLanguageSelected: (String) -> Unit
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .heightIn(min = 48.dp)
    ) {
        Text(text = label, color = StarWhite.copy(alpha = 0.7f))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("nl", "fr", "en").forEach { code ->
                val isSelected = normalizeLanguageCode(code) == normalizeLanguageCode(currentCode)
                Surface(
                    onClick = { onLanguageSelected(code) },
                    shape = RoundedCornerShape(8.dp),
                    color = if (isSelected) PlanetBlue else Color.White.copy(alpha = 0.05f),
                    contentColor = if (isSelected) DeepSpaceBlue else StarWhite,
                    modifier = Modifier.heightIn(min = 40.dp)
                ) {
                    Text(
                        text = code.uppercase(),
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = if (isSelected) FontWeight.Medium else FontWeight.Normal
                    )
                }
            }
        }
    }
}

private fun normalizeLanguageCode(code: String): String = when (code) {
    "nl", "nl-BE", "nl_BE" -> "nl"
    "fr", "fr-BE", "fr_BE", "fr-FR", "fr_FR" -> "fr"
    "en", "en-US", "en_US", "en-GB", "en_GB" -> "en"
    else -> code
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VoiceSettingsSection(
    availableVoices: List<Voice>,
    getVoiceSettings: (String) -> Flow<VoiceSettings>,
    onUpdateVoiceSettings: (String, VoiceSettings) -> Unit,
    onTestVoice: (String, String, VoiceSettings) -> Unit
) {
    var selectedLocale by remember { mutableStateOf("nl-BE") }
    val settings by getVoiceSettings(selectedLocale).collectAsState(
        initial = VoiceSettings(null, 1.0f, 1.0f)
    )

    DashboardCard(title = "Stem Instellingen") {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("nl-BE", "fr-BE", "fr-FR", "en").forEach { locale ->
                FilterChip(
                    selected = selectedLocale == locale,
                    onClick = { selectedLocale = locale },
                    label = { Text(locale) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = PlanetBlue,
                        selectedLabelColor = DeepSpaceBlue
                    )
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = "Kies Stem",
            style = MaterialTheme.typography.labelMedium,
            color = StarWhite
        )

        val filteredVoices = availableVoices.filter { voice ->
            voice.locale.language == selectedLocale.take(2)
        }

        if (filteredVoices.isEmpty()) {
            Text(
                text = "Geen extra stemmen gevonden op dit apparaat.",
                color = StarWhite.copy(alpha = 0.5f)
            )
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                filteredVoices.forEach { voice ->
                    Surface(
                        onClick = {
                            onUpdateVoiceSettings(selectedLocale, settings.copy(voiceId = voice.name))
                        },
                        color = if (settings.voiceId == voice.name) PlanetBlue else Color.White.copy(alpha = 0.05f),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = voice.name,
                            modifier = Modifier.padding(8.dp),
                            color = StarWhite
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        SliderWithLabel("Toonhoogte (Pitch)", settings.pitch, 0.5f..2.0f) {
            onUpdateVoiceSettings(selectedLocale, settings.copy(pitch = it))
        }
        SliderWithLabel("Snelheid (Rate)", settings.rate, 0.5f..2.0f) {
            onUpdateVoiceSettings(selectedLocale, settings.copy(rate = it))
        }

        Spacer(modifier = Modifier.height(16.dp))

        Button(
            onClick = {
                onTestVoice("Hallo, ik ben de stem van Poincaré.", selectedLocale, settings)
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Stem Testen")
        }
    }
}

@Composable
fun SliderWithLabel(
    label: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    onValueChange: (Float) -> Unit
) {
    Column {
        Text(
            text = "$label: ${"%.1f".format(value)}",
            color = StarWhite,
            style = MaterialTheme.typography.labelMedium
        )
        Slider(
            value = value,
            onValueChange = onValueChange,
            valueRange = range,
            colors = SliderDefaults.colors(
                thumbColor = PlanetGold,
                activeTrackColor = PlanetGold
            )
        )
    }
}

@Composable
fun AnimationSettingsSection(
    settings: AnimationSettings,
    onUpdateSettings: (AnimationSettings) -> Unit
) {
    DashboardCard(title = parentText("appSettings")) {
        SettingsToggle(parentText("animations"), settings.animationsEnabled) {
            onUpdateSettings(settings.copy(animationsEnabled = it))
        }
        SettingsToggle(parentText("reducedMotion"), settings.reducedMotionEnabled) {
            onUpdateSettings(settings.copy(reducedMotionEnabled = it))
        }
        SettingsToggle(parentText("soundEffects"), settings.soundEffectsEnabled) {
            onUpdateSettings(settings.copy(soundEffectsEnabled = it))
        }
        SliderWithLabel(parentText("soundVolume"), settings.soundEffectsVolume, 0f..1f) {
            onUpdateSettings(settings.copy(soundEffectsVolume = it))
        }
        SliderWithLabel(parentText("voiceVolume"), settings.voiceVolume, 0f..1f) {
            onUpdateSettings(settings.copy(voiceVolume = it))
        }
        SettingsToggle(parentText("haptics"), settings.hapticFeedbackEnabled) {
            onUpdateSettings(settings.copy(hapticFeedbackEnabled = it))
        }
    }
}

@Composable
fun SettingsToggle(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .heightIn(min = 48.dp)
    ) {
        Text(text = label, color = StarWhite)
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedTrackColor = PlanetBlue,
                checkedThumbColor = StarWhite
            ),
            modifier = Modifier.semantics {
                contentDescription = "Zet $label aan of uit"
            }
        )
    }
}

@Composable
fun SyncStatusSection(pendingCount: Int) {
    DashboardCard(title = parentText("systemStatus")) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 48.dp)
        ) {
            Text(text = parentText("unsyncedMissions"), color = StarWhite)
            Text(
                text = pendingCount.toString(),
                color = if (pendingCount > 0) PlanetRed else Color.Green,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.semantics {
                    contentDescription = "Er zijn $pendingCount missies die nog gesynchroniseerd moeten worden"
                }
            )
        }
    }
}

@Composable
fun DashboardCard(
    title: String,
    content: @Composable ColumnScope.() -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.05f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = PlanetGold,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(16.dp))
            content()
        }
    }
}

@Preview(showBackground = true)
@Composable
fun ParentDashboardPreview() {
    PoincareTheme {
        ParentDashboardContent(
            uiState = ParentDashboardUiState(
                currentProfile = ChildProfile(
                    id = "1",
                    name = "Henri",
                    avatarId = "1",
                    rocketColorId = "1",
                    birthYear = 2021,
                    readingLevel = SkillLevel.FIRST_GRADE,
                    writingLevel = SkillLevel.FIRST_GRADE,
                    mathLevel = SkillLevel.FIRST_GRADE,
                    geometryLevel = SkillLevel.FIRST_GRADE,
                    worldKnowledgeLevel = SkillLevel.FIRST_GRADE,
                    puzzleLevel = SkillLevel.FIRST_GRADE
                ),
                pendingSyncCount = 5,
                isLoading = false
            ),
            availableVoices = emptyList(),
            getVoiceSettings = { flowOf(VoiceSettings(null, 1.0f, 1.0f)) },
            onUpdateVoiceSettings = { _, _ -> },
            onTestVoice = { _, _, _ -> },
            onNavigateBack = {},
            onUpdateName = {},
            onUpdateBirthYear = {},
            onUpdateLevel = { _, _ -> },
            onUpdateMainCategoryEnabled = { _, _ -> },
            onUpdateSubcategoryEnabled = { _, _ -> },
            onUpdateGiftedMode = {},
            onUpdateShowUnassignedExercises = {},
            onUpdateAnimationSettings = {},
            onUpdateLanguage = { _, _ -> },
            onDeleteProfile = {}
        )
    }
}
