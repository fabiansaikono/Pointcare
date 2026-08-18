package be.saikono.poincare.ui.parent

// POINCARE_REPAIR_V29: parent-localization

import androidx.compose.foundation.layout.PaddingValues
import android.speech.tts.Voice
import androidx.activity.compose.BackHandler
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
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Check
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
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import be.saikono.poincare.data.settings.VoiceSettings
import be.saikono.poincare.domain.curriculum.manifest.CurriculumManifest
import be.saikono.poincare.model.AnimationSettings
import be.saikono.poincare.model.ChildProfile
import be.saikono.poincare.model.LearningDomain
import be.saikono.poincare.model.SkillLevel
import be.saikono.poincare.model.isMainCategoryEnabled
import be.saikono.poincare.model.isSubcategoryEnabled
import be.saikono.poincare.model.selectedGradesFor
import be.saikono.poincare.model.withSelectedGrades
import be.saikono.poincare.ui.components.GradeMultiSelector
import be.saikono.poincare.ui.components.systemNavigationMargin
import be.saikono.poincare.ui.home.getSubCategoriesForDomain
import be.saikono.poincare.ui.spaceMap.SpaceBackground
import be.saikono.poincare.ui.spaceMap.mainCategoryLabel
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
import kotlin.math.roundToInt

@Composable
fun ParentDashboardScreen(
    viewModel: ParentViewModel,
    onNavigateBack: () -> Unit,
    onProfileDeleted: () -> Unit,
    curriculumManifest: CurriculumManifest? = null
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
        onSaveChanges = viewModel::saveDashboard,
        onUpdateName = viewModel::updateProfileName,
        onUpdateBirthYear = viewModel::updateBirthYear,
        onUpdateGrades = viewModel::updateSelectedGrades,
        onUpdateMainCategoryEnabled = viewModel::updateMainCategoryEnabled,
        onUpdateSubcategoryEnabled = viewModel::updateSubcategoryEnabled,
        onUpdateGiftedMode = viewModel::updateGiftedMode,
        onUpdateShowUnassignedExercises = viewModel::updateShowUnassignedExercises,
        onUpdateAnimationSettings = viewModel::updateAnimationSettings,
        onUpdateLessonMasteryThreshold = viewModel::updateLessonMasteryThreshold,
        onUpdateLessonMasteryThresholdOverrideEnabled = viewModel::updateLessonMasteryThresholdOverrideEnabled,
        onUpdateLessonMasteryThresholdForDomain = viewModel::updateLessonMasteryThresholdForDomain,
        onUpdateLanguage = viewModel::updateLanguage,
        onValidateParentPin = viewModel::isParentPinCorrect,
        onDeleteProfile = { viewModel.deleteCurrentProfile(onProfileDeleted) },
        curriculumManifest = curriculumManifest
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
    onSaveChanges: (ParentDashboardDraft, (Boolean) -> Unit) -> Unit,
    onUpdateName: (String) -> Unit,
    onUpdateBirthYear: (Int) -> Unit,
    onUpdateGrades: (LearningDomain, Set<Int>) -> Unit,
    onUpdateMainCategoryEnabled: (LearningDomain, Boolean) -> Unit,
    onUpdateSubcategoryEnabled: (String, Boolean) -> Unit,
    onUpdateGiftedMode: (Boolean) -> Unit,
    onUpdateShowUnassignedExercises: (Boolean) -> Unit,
    onUpdateAnimationSettings: (AnimationSettings) -> Unit,
    onUpdateLessonMasteryThreshold: (Int) -> Unit,
    onUpdateLessonMasteryThresholdOverrideEnabled: (LearningDomain, Boolean) -> Unit,
    onUpdateLessonMasteryThresholdForDomain: (LearningDomain, Int) -> Unit,
    onUpdateLanguage: (LanguageLayer, String) -> Unit,
    onValidateParentPin: (String) -> Boolean,
    onDeleteProfile: () -> Unit,
    curriculumManifest: CurriculumManifest? = null
) {
    val sourceDraft = uiState.toDraft()
    var draft by remember(uiState.currentProfile?.id) { mutableStateOf(sourceDraft) }
    var savedDraft by remember(uiState.currentProfile?.id) { mutableStateOf(sourceDraft) }
    var showUnsavedChangesDialog by remember { mutableStateOf(false) }
    var isSaving by remember { mutableStateOf(false) }
    val hasUnsavedChanges = draft != null && draft != savedDraft

    fun saveCurrentDraft(navigateAfterSave: Boolean) {
        val currentDraft = draft ?: return
        if (!currentDraft.isValid || isSaving) return
        isSaving = true
        onSaveChanges(currentDraft) { success ->
            isSaving = false
            if (success) {
                val persisted = currentDraft.persistedProfile() ?: currentDraft.profile
                val normalized = currentDraft.copy(
                    profile = persisted,
                    profileName = persisted.name,
                    birthYearText = persisted.birthYear.toString(),
                    teacherIdText = currentDraft.persistedTeacherId()
                )
                draft = normalized
                savedDraft = normalized
                showUnsavedChangesDialog = false
                if (navigateAfterSave) onNavigateBack()
            }
        }
    }

    fun requestNavigateBack() {
        if (hasUnsavedChanges) showUnsavedChangesDialog = true else onNavigateBack()
    }

    BackHandler(onBack = ::requestNavigateBack)

    if (showUnsavedChangesDialog) {
        AlertDialog(
            onDismissRequest = { showUnsavedChangesDialog = false },
            containerColor = DeepSpaceBlue.copy(alpha = 0.96f),
            title = { Text(parentText("unsavedChangesTitle"), color = PlanetGold) },
            text = { Text(parentText("unsavedChangesMessage"), color = StarWhite) },
            confirmButton = {
                Button(
                    onClick = { saveCurrentDraft(navigateAfterSave = true) },
                    enabled = draft?.isValid == true && !isSaving
                ) { Text(parentText("save")) }
            },
            dismissButton = {
                TextButton(onClick = {
                    draft = savedDraft
                    showUnsavedChangesDialog = false
                    onNavigateBack()
                }) { Text(parentText("discardChanges")) }
            }
        )
    }

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
                    onClick = ::requestNavigateBack,
                    modifier = Modifier.size(48.dp)
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
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
                Spacer(modifier = Modifier.weight(1f))
                Button(
                    onClick = { saveCurrentDraft(navigateAfterSave = false) },
                    enabled = hasUnsavedChanges && draft?.isValid == true && !isSaving,
                    shape = RoundedCornerShape(999.dp),
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp)
                ) {
                    if (isSaving) {
                        CircularProgressIndicator(
                            color = StarWhite,
                            strokeWidth = 2.dp,
                            modifier = Modifier.size(18.dp)
                        )
                    } else {
                        Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(18.dp))
                    }
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(parentText("save"))
                }
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
                    val currentDraft = draft
                    ProfileSettingsSection(
                        profile = currentDraft?.profile,
                        nameText = currentDraft?.profileName.orEmpty(),
                        birthYearText = currentDraft?.birthYearText.orEmpty(),
                        teacherIdText = currentDraft?.teacherIdText.orEmpty(),
                        onUpdateName = { value -> draft = draft?.copy(profileName = value) },
                        onUpdateBirthYearText = { value -> draft = draft?.copy(birthYearText = value) },
                        onUpdateTeacherIdText = { value -> draft = draft?.copy(teacherIdText = value.take(64)) },
                        onUpdateGrades = { domain, grades ->
                            draft = draft?.let { it.copy(profile = it.profile.withSelectedGrades(domain, grades)) }
                        },
                        onUpdateMainCategoryEnabled = { domain, enabled ->
                            draft = draft?.let { current ->
                                val profile = current.profile
                                val hidden = if (enabled) profile.hiddenMainCategories - domain.name
                                else (profile.hiddenMainCategories + domain.name).distinct()
                                current.copy(profile = profile.copy(hiddenMainCategories = hidden))
                            }
                        },
                        onUpdateSubcategoryEnabled = { id, enabled ->
                            draft = draft?.let { current ->
                                val profile = current.profile
                                val hidden = if (enabled) profile.hiddenSubcategories - id
                                else (profile.hiddenSubcategories + id).distinct()
                                current.copy(profile = profile.copy(hiddenSubcategories = hidden))
                            }
                        },
                        onUpdateGiftedMode = { enabled ->
                            draft = draft?.let { it.copy(profile = it.profile.copy(giftedModeEnabled = enabled)) }
                        },
                        onUpdateShowUnassignedExercises = { enabled ->
                            draft = draft?.let { it.copy(profile = it.profile.copy(showUnassignedExercises = enabled)) }
                        },
                        onValidateParentPin = onValidateParentPin,
                        onDeleteProfile = onDeleteProfile,
                        curriculumManifest = curriculumManifest
                    )

                    LanguageSettingsSection(
                        profile = currentDraft?.profile,
                        onUpdateLanguage = { type, languageCode ->
                            draft = draft?.let { current ->
                                val profile = current.profile
                                val updated = when (type) {
                                    LanguageLayer.APP -> profile.copy(appLanguage = languageCode)
                                    LanguageLayer.EXERCISE -> profile.copy(exerciseLanguage = languageCode)
                                    LanguageLayer.SUPPORT -> profile.copy(supportLanguage = languageCode)
                                    LanguageLayer.SPEECH -> profile.copy(speechLanguage = languageCode)
                                    LanguageLayer.ALL -> profile.copy(
                                        appLanguage = languageCode,
                                        exerciseLanguage = languageCode,
                                        supportLanguage = languageCode,
                                        speechLanguage = languageCode
                                    )
                                }
                                current.copy(profile = updated)
                            }
                        }
                    )

                    LessonSettingsSection(
                        masteryThresholdPercent = currentDraft?.lessonMasteryThresholdPercent
                            ?: uiState.lessonMasteryThresholdPercent,
                        masteryThresholdOverrides = currentDraft?.lessonMasteryThresholdOverrides.orEmpty(),
                        onUpdateMasteryThreshold = { percent ->
                            draft = draft?.copy(lessonMasteryThresholdPercent = percent)
                        },
                        onUpdateOverrideEnabled = { domain, enabled ->
                            draft = draft?.let { current ->
                                val overrides = if (enabled) {
                                    current.lessonMasteryThresholdOverrides +
                                        (domain to current.lessonMasteryThresholdPercent)
                                } else current.lessonMasteryThresholdOverrides - domain
                                current.copy(lessonMasteryThresholdOverrides = overrides)
                            }
                        },
                        onUpdateDomainThreshold = { domain, percent ->
                            draft = draft?.let { current ->
                                current.copy(
                                    lessonMasteryThresholdOverrides =
                                        current.lessonMasteryThresholdOverrides + (domain to percent)
                                )
                            }
                        }
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
                        settings = currentDraft?.animationSettings ?: uiState.animationSettings,
                        onUpdateSettings = { settings -> draft = draft?.copy(animationSettings = settings) }
                    )

                    LocalStorageStatusSection()

                    Spacer(modifier = Modifier.height(40.dp))
                }
            }
        }
    }
}

@Composable
fun ProfileSettingsSection(
    profile: ChildProfile?,
    nameText: String,
    birthYearText: String,
    teacherIdText: String,
    onUpdateName: (String) -> Unit,
    onUpdateBirthYearText: (String) -> Unit,
    onUpdateTeacherIdText: (String) -> Unit,
    onUpdateGrades: (LearningDomain, Set<Int>) -> Unit,
    onUpdateMainCategoryEnabled: (LearningDomain, Boolean) -> Unit,
    onUpdateSubcategoryEnabled: (String, Boolean) -> Unit,
    onUpdateGiftedMode: (Boolean) -> Unit,
    onUpdateShowUnassignedExercises: (Boolean) -> Unit,
    onValidateParentPin: (String) -> Boolean,
    onDeleteProfile: () -> Unit,
    curriculumManifest: CurriculumManifest? = null
) {
    if (profile == null) return

    var showDeleteConfirmation by remember { mutableStateOf(false) }
    var deletePin by remember { mutableStateOf("") }
    var deletePinError by remember { mutableStateOf(false) }
    val hardModeDescription = parentText("hardModeDescription")

    if (showDeleteConfirmation) {
        AlertDialog(
            onDismissRequest = {
                showDeleteConfirmation = false
                deletePin = ""
                deletePinError = false
            },
            containerColor = DeepSpaceBlue.copy(alpha = 0.92f),
            titleContentColor = PlanetGold,
            textContentColor = PlanetGold,
            title = { Text(parentText("deleteProfileTitle")) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(parentDeleteConfirmation(profile.name))
                    OutlinedTextField(
                        value = deletePin,
                        onValueChange = {
                            deletePin = it.filter(Char::isDigit).take(8)
                            deletePinError = false
                        },
                        label = { Text(parentText("confirmParentPin")) },
                        singleLine = true,
                        isError = deletePinError,
                        supportingText = if (deletePinError) {
                            { Text(parentText("incorrectPin"), color = PlanetRed) }
                        } else null,
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = StarWhite,
                            unfocusedTextColor = StarWhite,
                            focusedBorderColor = PlanetGold,
                            unfocusedBorderColor = PlanetGold.copy(alpha = .55f),
                            focusedLabelColor = PlanetGold,
                            unfocusedLabelColor = PlanetGold.copy(alpha = .72f),
                            cursorColor = PlanetGold
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    shape = RoundedCornerShape(12.dp),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
                    onClick = {
                        if (onValidateParentPin(deletePin)) {
                            showDeleteConfirmation = false
                            deletePin = ""
                            deletePinError = false
                            onDeleteProfile()
                        } else {
                            deletePinError = true
                        }
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
                    shape = RoundedCornerShape(12.dp),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
                    onClick = {
                        showDeleteConfirmation = false
                        deletePin = ""
                        deletePinError = false
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = PlanetGold)
                ) {
                    Text(parentText("cancel"))
                }
            }
        )
    }

    DashboardCard(title = parentText("profileSettings")) {
        EditableSettingTextField(
            value = nameText,
            onValueChange = onUpdateName,
            label = parentText("astronautName"),
            contentDescription = "Naam van de kind-astronaut"
        )

        Spacer(modifier = Modifier.height(16.dp))

        EditableSettingTextField(
            value = birthYearText,
            onValueChange = { value -> onUpdateBirthYearText(value.filter { it.isDigit() }.take(4)) },
            label = parentText("birthYear"),
            contentDescription = "Geboortejaar van de astronaut"
        )

        Spacer(modifier = Modifier.height(16.dp))

        EditableSettingTextField(
            value = teacherIdText,
            onValueChange = onUpdateTeacherIdText,
            label = parentText("teacherId"),
            contentDescription = parentText("teacherIdDescription")
        )

        Spacer(modifier = Modifier.height(24.dp))

        Text(
            text = parentText("educationLevels"),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = StarWhite
        )

        val gradeHint = parentText("gradeSelectionHint")
        Text(
            text = gradeHint,
            style = MaterialTheme.typography.bodySmall,
            color = StarWhite.copy(alpha = 0.65f),
            modifier = Modifier.fillMaxWidth().padding(top = 4.dp, bottom = 4.dp)
        )
        GradeMultiSelector(mainCategoryLabel(LearningDomain.READING, profile.appLanguage, curriculumManifest), profile.selectedGradesFor(LearningDomain.READING).toSet(), { onUpdateGrades(LearningDomain.READING, it) })
        GradeMultiSelector(mainCategoryLabel(LearningDomain.WRITING, profile.appLanguage, curriculumManifest), profile.selectedGradesFor(LearningDomain.WRITING).toSet(), { onUpdateGrades(LearningDomain.WRITING, it) })
        GradeMultiSelector(mainCategoryLabel(LearningDomain.MATH, profile.appLanguage, curriculumManifest), profile.selectedGradesFor(LearningDomain.MATH).toSet(), { onUpdateGrades(LearningDomain.MATH, it) })
        GradeMultiSelector(mainCategoryLabel(LearningDomain.GEOMETRY, profile.appLanguage, curriculumManifest), profile.selectedGradesFor(LearningDomain.GEOMETRY).toSet(), { onUpdateGrades(LearningDomain.GEOMETRY, it) })
        GradeMultiSelector(mainCategoryLabel(LearningDomain.WORLD_KNOWLEDGE, profile.appLanguage, curriculumManifest), profile.selectedGradesFor(LearningDomain.WORLD_KNOWLEDGE).toSet(), { onUpdateGrades(LearningDomain.WORLD_KNOWLEDGE, it) })

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
            onUpdateSubcategoryEnabled = onUpdateSubcategoryEnabled,
            curriculumManifest = curriculumManifest
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
                    contentDescription = hardModeDescription
                }
            )
        }

        Spacer(modifier = Modifier.height(24.dp))

        OutlinedButton(
            shape = RoundedCornerShape(12.dp),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
            onClick = {
                deletePin = ""
                deletePinError = false
                showDeleteConfirmation = true
            },
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
    onUpdateSubcategoryEnabled: (String, Boolean) -> Unit,
    curriculumManifest: CurriculumManifest? = null
) {
    Text(
        text = parentText("categories"),
        style = MaterialTheme.typography.titleSmall,
        color = StarWhite
    )
    Spacer(modifier = Modifier.height(8.dp))

    val categories: List<Pair<LearningDomain, String>> = LearningDomain.entries.map { domain ->
        domain to mainCategoryLabel(domain, profile.appLanguage, curriculumManifest)
    }

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
                getSubCategoriesForDomain(
                    domain.name,
                    curriculumManifest,
                    profile.appLanguage,
                    profile.selectedGradesFor(domain)
                ).forEach { subcategory ->
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
fun EditableSettingTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    contentDescription: String,
    modifier: Modifier = Modifier
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        singleLine = true,
        modifier = modifier
            .fillMaxWidth()
            .semantics { this.contentDescription = contentDescription },
        colors = OutlinedTextFieldDefaults.colors(
            focusedTextColor = StarWhite,
            unfocusedTextColor = StarWhite
        )
    )
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
                    shape = RoundedCornerShape(12.dp),
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
            shape = RoundedCornerShape(12.dp),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
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
fun LessonSettingsSection(
    masteryThresholdPercent: Int,
    masteryThresholdOverrides: Map<LearningDomain, Int>,
    onUpdateMasteryThreshold: (Int) -> Unit,
    onUpdateOverrideEnabled: (LearningDomain, Boolean) -> Unit,
    onUpdateDomainThreshold: (LearningDomain, Int) -> Unit
) {
    DashboardCard(title = parentText("lessonSettings")) {
        Text(
            text = parentText("masteryThresholdDescription"),
            color = StarWhite.copy(alpha = 0.75f),
            style = MaterialTheme.typography.bodyMedium
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "${parentText("generalMasteryThreshold")}: $masteryThresholdPercent%",
            color = StarWhite,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold
        )
        Slider(
            value = masteryThresholdPercent.toFloat(),
            onValueChange = { value ->
                val rounded = (value / 5f).roundToInt() * 5
                if (rounded != masteryThresholdPercent) onUpdateMasteryThreshold(rounded)
            },
            valueRange = 50f..100f,
            steps = 9,
            colors = SliderDefaults.colors(
                thumbColor = PlanetGold,
                activeTrackColor = PlanetGold
            )
        )

        Spacer(modifier = Modifier.height(12.dp))
        Text(
            text = parentText("categoryThresholds"),
            color = PlanetGold,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(6.dp))

        val categories = listOf(
            LearningDomain.READING to parentText("communication"),
            LearningDomain.WRITING to parentText("writing"),
            LearningDomain.MATH to parentText("numbers"),
            LearningDomain.GEOMETRY to parentText("geometry"),
            LearningDomain.WORLD_KNOWLEDGE to parentText("worldKnowledge"),
            LearningDomain.PUZZLE to parentText("puzzles")
        )
        categories.forEachIndexed { index, (domain, label) ->
            val overridePercent = masteryThresholdOverrides[domain]
            val overrideEnabled = overridePercent != null
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 10.dp)
            ) {
                Text(
                    text = label,
                    color = StarWhite,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Row(
                    modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = parentText("differentThreshold"),
                        color = StarWhite.copy(alpha = .82f),
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.weight(1f)
                    )
                    Switch(
                        checked = overrideEnabled,
                        onCheckedChange = { onUpdateOverrideEnabled(domain, it) },
                        colors = SwitchDefaults.colors(
                            checkedTrackColor = PlanetBlue,
                            checkedThumbColor = StarWhite
                        )
                    )
                }
                if (overrideEnabled) {
                    Text(
                        text = "${parentText("masteryThreshold")}: $overridePercent%",
                        color = PlanetGold,
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold
                    )
                    Slider(
                        value = overridePercent.toFloat(),
                        onValueChange = { value ->
                            val rounded = (value / 5f).roundToInt() * 5
                            if (rounded != overridePercent) onUpdateDomainThreshold(domain, rounded)
                        },
                        valueRange = 50f..100f,
                        steps = 9,
                        colors = SliderDefaults.colors(
                            thumbColor = PlanetGold,
                            activeTrackColor = PlanetGold
                        )
                    )
                } else {
                    Text(
                        text = "${parentText("usesGeneralThreshold")} ($masteryThresholdPercent%)",
                        color = StarWhite.copy(alpha = .60f),
                        style = MaterialTheme.typography.labelMedium
                    )
                }
            }
            if (index < categories.lastIndex) {
                HorizontalDivider(color = Color.White.copy(alpha = .10f))
            }
        }
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
fun LocalStorageStatusSection() {
    val localStorageDescription = parentText("localStorageDescription")
    DashboardCard(title = parentText("systemStatus")) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 48.dp)
        ) {
            Text(text = parentText("progressStorage"), color = StarWhite)
            Text(
                text = parentText("localOnly"),
                color = Color.Green,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.semantics {
                    contentDescription = localStorageDescription
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
            onSaveChanges = { _, onComplete -> onComplete(true) },
            onUpdateName = {},
            onUpdateBirthYear = {},
            onUpdateGrades = { _, _ -> },
            onUpdateMainCategoryEnabled = { _, _ -> },
            onUpdateSubcategoryEnabled = { _, _ -> },
            onUpdateGiftedMode = {},
            onUpdateShowUnassignedExercises = {},
            onUpdateAnimationSettings = {},
            onUpdateLessonMasteryThreshold = {},
            onUpdateLessonMasteryThresholdOverrideEnabled = { _, _ -> },
            onUpdateLessonMasteryThresholdForDomain = { _, _ -> },
            onUpdateLanguage = { _, _ -> },
            onValidateParentPin = { it == "1234" },
            onDeleteProfile = {}
        )
    }
}
