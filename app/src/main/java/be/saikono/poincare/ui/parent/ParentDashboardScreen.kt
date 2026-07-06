package be.saikono.poincare.ui.parent

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import be.saikono.poincare.data.settings.VoiceSettings
import be.saikono.poincare.model.*
import be.saikono.poincare.ui.components.systemNavigationMargin
import be.saikono.poincare.ui.home.getSubCategoriesForDomain
import be.saikono.poincare.ui.spaceMap.SpaceBackground
import be.saikono.poincare.ui.theme.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flowOf

@Composable
fun ParentDashboardScreen(
    viewModel: ParentViewModel,
    onNavigateBack: () -> Unit,
    onProfileDeleted: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()

    ParentDashboardContent(
        uiState = uiState,
        availableVoices = viewModel.availableVoices,
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
        onUpdateAnimationSettings = viewModel::updateAnimationSettings,
        onUpdateLanguage = viewModel::updateLanguage,
        onDeleteProfile = { viewModel.deleteCurrentProfile(onProfileDeleted) }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ParentDashboardContent(
    uiState: ParentDashboardUiState,
    availableVoices: StateFlow<List<android.speech.tts.Voice>>,
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
                .widthIn(max = 800.dp) // Optimized for tablets
                .align(Alignment.TopCenter)
                .systemNavigationMargin()
                .padding(horizontal = 24.dp, vertical = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Top Bar
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = onNavigateBack,
                    modifier = Modifier.size(48.dp)
                ) {
                    Icon(Icons.Default.ArrowBack, contentDescription = "Terug naar kaart", tint = StarWhite)
                }
                Spacer(modifier = Modifier.width(16.dp))
                Text(
                    text = "Ouder Dashboard",
                    style = MaterialTheme.typography.titleLarge,
                    color = StarWhite
                )
            }

            if (uiState.isLoading) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
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
                        onDeleteProfile = onDeleteProfile
                    )

                    LanguageSettingsSection(
                        profile = uiState.currentProfile,
                        onUpdateLanguage = onUpdateLanguage
                    )

                    /* Voice settings hidden as per user request
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

                    SyncStatusSection(
                        pendingCount = uiState.pendingSyncCount
                    )
                    
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
            title = { Text("Profiel verwijderen") },
            text = { Text("Weet je zeker dat je het account van ${profile.name} wilt verwijderen? Dit kan niet ongedaan worden gemaakt.") },
            confirmButton = {
                Button(
                    onClick = {
                        showDeleteConfirmation = false
                        onDeleteProfile()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = DeepSpaceBlue, contentColor = PlanetGold)
                ) {
                    Text("Verwijderen")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirmation = false }, colors = ButtonDefaults.textButtonColors(contentColor = DeepSpaceBlue)) {
                    Text("Annuleren")
                }
            }
        )
    }

    DashboardCard(title = "Profiel Instellingen") {
        EditableSettingTextField(
            value = nameText,
            onValueChange = { nameText = it },
            onSave = { if (nameText.isNotBlank()) onUpdateName(nameText.trim()) },
            label = "Naam van de astronaut",
            contentDescription = "Naam van de kind-astronaut"
        )

        Spacer(modifier = Modifier.height(16.dp))

        EditableSettingTextField(
            value = birthYearText,
            onValueChange = { value -> birthYearText = value.filter { it.isDigit() }.take(4) },
            onSave = { birthYearText.toIntOrNull()?.let(onUpdateBirthYear) },
            label = "Geboortejaar",
            contentDescription = "Geboortejaar van de astronaut"
        )

        Spacer(modifier = Modifier.height(24.dp))

        Text(text = "Opleidings-niveaus", style = MaterialTheme.typography.titleSmall, color = StarWhite)
        
        DomainLevelSelector("Lezen", LearningDomain.READING, profile.readingLevel, onUpdateLevel)
        DomainLevelSelector("Schrijven", LearningDomain.WRITING, profile.writingLevel, onUpdateLevel)
        DomainLevelSelector("Wiskunde", LearningDomain.MATH, profile.mathLevel, onUpdateLevel)
        DomainLevelSelector("Meetkunde", LearningDomain.GEOMETRY, profile.geometryLevel, onUpdateLevel)
        DomainLevelSelector("Wereldkennis", LearningDomain.WORLD_KNOWLEDGE, profile.worldKnowledgeLevel, onUpdateLevel)
        DomainLevelSelector("Puzzels", LearningDomain.PUZZLE, profile.puzzleLevel, onUpdateLevel)

        Spacer(modifier = Modifier.height(24.dp))

        CategoryAccessSection(
            profile = profile,
            onUpdateMainCategoryEnabled = onUpdateMainCategoryEnabled,
            onUpdateSubcategoryEnabled = onUpdateSubcategoryEnabled
        )

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier.fillMaxWidth().padding(top = 16.dp).heightIn(min = 48.dp)
        ) {
            Text(text = "Gifted Mode (Adaptief)", color = StarWhite)
            Switch(
                checked = profile.giftedModeEnabled, 
                onCheckedChange = onUpdateGiftedMode,
                modifier = Modifier.semantics { contentDescription = "Schakel Gifted Mode in voor meer uitdagende puzzels" }
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
            Text("Account verwijderen")
        }
    }
}

@Composable
fun CategoryAccessSection(
    profile: ChildProfile,
    onUpdateMainCategoryEnabled: (LearningDomain, Boolean) -> Unit,
    onUpdateSubcategoryEnabled: (String, Boolean) -> Unit
) {
    Text(text = "Categorieen", style = MaterialTheme.typography.titleSmall, color = StarWhite)
    Spacer(modifier = Modifier.height(8.dp))

    val categories = listOf(
        LearningDomain.READING to "Communicatie",
        LearningDomain.WRITING to "Schrijven",
        LearningDomain.MATH to "Getallen",
        LearningDomain.GEOMETRY to "Meetkunde",
        LearningDomain.WORLD_KNOWLEDGE to "Wereldkennis",
        LearningDomain.PUZZLE to "Puzzels"
    )

    categories.forEach { (domain, label) ->
        val domainEnabled = profile.isMainCategoryEnabled(domain)
        var expanded by remember(domain) { mutableStateOf(false) }
        Column(modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f).clickable { expanded = !expanded }) {
                    Text(
                        text = if (expanded) "▾" else "▸",
                        color = StarWhite.copy(alpha = 0.75f),
                        style = MaterialTheme.typography.titleMedium
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(label, color = StarWhite, style = MaterialTheme.typography.titleMedium)
                }
                Checkbox(
                    checked = domainEnabled,
                    onCheckedChange = { onUpdateMainCategoryEnabled(domain, it) },
                    colors = CheckboxDefaults.colors(checkedColor = PlanetGold, checkmarkColor = CockpitGray)
                )
            }

            if (domainEnabled && expanded) {
                getSubCategoriesForDomain(domain.name).forEach { subcategory ->
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(start = 16.dp).heightIn(min = 40.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = subcategory.nameNl,
                            color = StarWhite.copy(alpha = 0.78f),
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.weight(1f).padding(end = 12.dp)
                        )
                        Checkbox(
                            checked = profile.isSubcategoryEnabled(subcategory.id),
                            onCheckedChange = { onUpdateSubcategoryEnabled(subcategory.id, it) },
                            colors = CheckboxDefaults.colors(checkedColor = PlanetBlue, checkmarkColor = StarWhite)
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
    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
        Text(text = label, color = StarWhite.copy(alpha = 0.7f), style = MaterialTheme.typography.labelMedium)
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            SkillLevel.entries.forEach { level ->
                val isSelected = level == currentLevel
                Surface(
                    onClick = { onUpdateLevel(domain, level) },
                    shape = RoundedCornerShape(8.dp),
                    color = if (isSelected) PlanetGold else Color.White.copy(alpha = 0.05f),
                    contentColor = if (isSelected) CockpitGray else StarWhite,
                    modifier = Modifier.heightIn(min = 40.dp).weight(1f)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(
                            text = level.displayLabel(),
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                        )
                    }
                }
            }
        }
    }
}

private fun SkillLevel.displayLabel(): String = if (this == SkillLevel.NOT_APPLICABLE) "N/A" else (ordinal + 1).toString()

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
            modifier = Modifier.weight(1f).semantics { this.contentDescription = contentDescription },
            colors = OutlinedTextFieldDefaults.colors(focusedTextColor = StarWhite, unfocusedTextColor = StarWhite)
        )
        IconButton(onClick = onSave, modifier = Modifier.size(48.dp)) {
            Icon(Icons.Default.Check, contentDescription = "$label opslaan", tint = PlanetGold)
        }
    }
}

@Composable
fun LanguageSettingsSection(
    profile: ChildProfile?,
    onUpdateLanguage: (LanguageLayer, String) -> Unit
) {
    if (profile == null) return

    DashboardCard(title = "Taal Instellingen") {
        LanguageRow("Taal", profile.appLanguage) { onUpdateLanguage(LanguageLayer.ALL, it) }
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
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp).heightIn(min = 48.dp)
    ) {
        Text(text = label, color = StarWhite.copy(alpha = 0.7f))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("nl", "fr", "en").forEach { code ->
                val isSelected = normalizeLanguageCode(code) == normalizeLanguageCode(currentCode)
                Surface(
                    onClick = { onLanguageSelected(code) },
                    shape = RoundedCornerShape(8.dp),
                    color = if (isSelected) PlanetBlue else Color.White.copy(alpha = 0.05f),
                    contentColor = StarWhite,
                    modifier = Modifier.heightIn(min = 40.dp)
                ) {
                    Text(
                        text = code.uppercase(),
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
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

@Composable
fun VoiceSettingsSection(
    availableVoices: StateFlow<List<android.speech.tts.Voice>>,
    getVoiceSettings: (String) -> Flow<VoiceSettings>,
    onUpdateVoiceSettings: (String, VoiceSettings) -> Unit,
    onTestVoice: (String, String, VoiceSettings) -> Unit
) {
    val voices by availableVoices.collectAsState()
    var selectedLocale by remember { mutableStateOf("nl-BE") }
    val settings by getVoiceSettings(selectedLocale).collectAsState(initial = VoiceSettings(null, 1.0f, 1.0f))

    DashboardCard(title = "Stem Instellingen") {
        // Locale Selector
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("nl-BE", "fr-BE", "fr-FR", "en").forEach { locale ->
                FilterChip(
                    selected = selectedLocale == locale,
                    onClick = { selectedLocale = locale },
                    label = { Text(locale) }
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Voice Dropdown
        Text(text = "Kies Stem", style = MaterialTheme.typography.labelMedium, color = StarWhite)
        voices.filter { voice -> voice.locale.language == selectedLocale.take(2) }.let { filteredVoices ->
            if (filteredVoices.isEmpty()) {
                Text("Geen extra stemmen gevonden op dit apparaat.", color = StarWhite.copy(alpha = 0.5f))
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    filteredVoices.forEach { voice ->
                        Surface(
                            onClick = { onUpdateVoiceSettings(selectedLocale, settings.copy(voiceId = voice.name)) },
                            color = if (settings.voiceId == voice.name) PlanetBlue else Color.White.copy(alpha = 0.05f),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(text = voice.name, modifier = Modifier.padding(8.dp), color = StarWhite)
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Pitch & Rate
        SliderWithLabel("Toonhoogte (Pitch)", settings.pitch, 0.5f..2.0f) {
            onUpdateVoiceSettings(selectedLocale, settings.copy(pitch = it))
        }
        SliderWithLabel("Snelheid (Rate)", settings.rate, 0.5f..2.0f) {
            onUpdateVoiceSettings(selectedLocale, settings.copy(rate = it))
        }

        Spacer(modifier = Modifier.height(16.dp))

        Button(
            onClick = { onTestVoice("Hallo, ik ben de stem van Poincaré.", selectedLocale, settings) },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Stem Testen")
        }
    }
}

@Composable
fun SliderWithLabel(label: String, value: Float, range: ClosedFloatingPointRange<Float>, onValueChange: (Float) -> Unit) {
    Column {
        Text(text = "$label: ${"%.1f".format(value)}", color = StarWhite, style = MaterialTheme.typography.labelMedium)
        Slider(value = value, onValueChange = onValueChange, valueRange = range, colors = SliderDefaults.colors(thumbColor = PlanetGold, activeTrackColor = PlanetGold))
    }
}

@Composable
fun AnimationSettingsSection(
    settings: AnimationSettings,
    onUpdateSettings: (AnimationSettings) -> Unit
) {
    DashboardCard(title = "App Instellingen") {
        SettingsToggle("Animaties inschakelen", settings.animationsEnabled) {
            onUpdateSettings(settings.copy(animationsEnabled = it))
        }
        SettingsToggle("Reduced Motion", settings.reducedMotionEnabled) {
            onUpdateSettings(settings.copy(reducedMotionEnabled = it))
        }
        SettingsToggle("Geluidseffecten", settings.soundEffectsEnabled) {
            onUpdateSettings(settings.copy(soundEffectsEnabled = it))
        }
        SliderWithLabel("Volume geluidseffecten", settings.soundEffectsVolume, 0f..1f) {
            onUpdateSettings(settings.copy(soundEffectsVolume = it))
        }
        SliderWithLabel("Volume stem", settings.voiceVolume, 0f..1f) {
            onUpdateSettings(settings.copy(voiceVolume = it))
        }
        SettingsToggle("Haptische feedback", settings.hapticFeedbackEnabled) {
            onUpdateSettings(settings.copy(hapticFeedbackEnabled = it))
        }
    }
}

@Composable
fun SettingsToggle(label: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp).heightIn(min = 48.dp)
    ) {
        Text(text = label, color = StarWhite)
        Switch(
            checked = checked, 
            onCheckedChange = onCheckedChange,
            modifier = Modifier.semantics { contentDescription = "Zet $label aan of uit" }
        )
    }
}

@Composable
fun SyncStatusSection(pendingCount: Int) {
    DashboardCard(title = "Systeem Status") {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)
        ) {
            Text(text = "Niet gesyncte missies", color = StarWhite)
            Text(
                text = pendingCount.toString(),
                color = if (pendingCount > 0) PlanetRed else Color.Green,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.semantics { contentDescription = "Er zijn $pendingCount missies die nog gesynchroniseerd moeten worden" }
            )
        }
    }
}

@Composable
fun DashboardCard(title: String, content: @Composable ColumnScope.() -> Unit) {
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
                    birthYear = 2018,
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
            availableVoices = flowOf(emptyList<android.speech.tts.Voice>()).collectAsState(initial = emptyList()) as StateFlow<List<android.speech.tts.Voice>>,
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
            onUpdateAnimationSettings = {},
            onUpdateLanguage = { _, _ -> },
            onDeleteProfile = {}
        )
    }
}
