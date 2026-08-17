package be.saikono.poincare.ui.lesson

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import be.saikono.poincare.domain.curriculum.ExerciseCapabilityRegistry
import be.saikono.poincare.domain.curriculum.ExerciseCapabilityStatus
import be.saikono.poincare.domain.curriculum.ExerciseRuntime
import be.saikono.poincare.domain.curriculum.ParentSupportLevel
import be.saikono.poincare.domain.curriculum.v2.LessonFeedbackPolicyV2
import be.saikono.poincare.domain.curriculum.v2.LessonStageTypeV2
import be.saikono.poincare.domain.curriculum.v2.LessonVisualKindV2
import be.saikono.poincare.domain.curriculum.v2.LessonVisualV2
import be.saikono.poincare.domain.curriculum.v2.isExercises
import be.saikono.poincare.domain.curriculum.v2.isTheory
import be.saikono.poincare.model.ExerciseContent
import be.saikono.poincare.model.InputMode
import be.saikono.poincare.model.localizedOptions
import be.saikono.poincare.model.localizedAnswer
import be.saikono.poincare.ui.components.LetterTracingComponent
import be.saikono.poincare.ui.components.AzertyBeKeyboard
import be.saikono.poincare.ui.components.systemNavigationMargin
import be.saikono.poincare.ui.spaceMap.SpaceBackground
import be.saikono.poincare.ui.theme.DeepSpaceBlue
import be.saikono.poincare.ui.theme.PlanetBlue
import be.saikono.poincare.ui.theme.PlanetGold
import be.saikono.poincare.ui.theme.PlanetGreen
import be.saikono.poincare.ui.theme.PlanetRed
import be.saikono.poincare.ui.theme.StarWhite
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

internal const val LESSON_SCREEN_TOP_PADDING_DP = 20
internal const val LESSON_SCREEN_BOTTOM_PADDING_DP = 10
internal const val LESSON_HUB_FIRST_ROW_TOP_PADDING_DP = 20
internal const val LESSON_HUB_BOTTOM_PADDING_DP = 16
internal const val LESSON_EXERCISE_FIRST_ROW_TOP_PADDING_DP = 12
internal const val LESSON_ROW_ICON_WEIGHT = .14f
internal const val LESSON_ROW_META_WEIGHT = .18f

internal fun lessonExerciseRequiresAudio(exercise: ExerciseContent): Boolean =
    exercise.data["audioRequired"]?.equals("true", ignoreCase = true) == true ||
        exercise.inputMode == InputMode.LISTEN_AND_SELECT

internal fun lessonAudioButtonLabel(locale: String): String = when (
    locale.substringBefore('-').substringBefore('_').lowercase()
) {
    "fr" -> "Réécouter"
    "en" -> "Listen again"
    else -> "Luister opnieuw"
}

internal fun highlightPhonemeReferences(value: String): AnnotatedString {
    val pattern = Regex("""/([\p{L}]{1,3})/""")
    return buildAnnotatedString {
        var cursor = 0
        pattern.findAll(value).forEach { match ->
            append(value.substring(cursor, match.range.first))
            pushStyle(SpanStyle(color = PlanetGold, fontWeight = FontWeight.Bold))
            append(match.groupValues[1])
            pop()
            cursor = match.range.last + 1
        }
        append(value.substring(cursor))
    }
}

internal enum class LessonPhaseIconKind {
    COMPLETED,
    LOCKED,
    RETRY,
    START
}

internal data class LessonPartPreviewGroupUi(
    val number: Int,
    val title: String,
    val phases: List<LessonPhasePreviewUi>
)

internal fun lessonPartOrdinalLabel(number: Int, locale: String): String = when (
    locale.substringBefore('-').substringBefore('_').lowercase()
) {
    "fr" -> "Partie $number"
    "en" -> "Part $number"
    else -> "Deel $number"
}

internal fun lessonPartCountLabel(count: Int, locale: String): String = when {
    count == 1 -> lessonPartOrdinalLabel(1, locale)
    locale.substringBefore('-').substringBefore('_').lowercase() == "fr" -> "$count parties"
    locale.substringBefore('-').substringBefore('_').lowercase() == "en" -> "$count parts"
    else -> "$count delen"
}

internal fun lessonPartTitle(type: LessonStageTypeV2, locale: String): String = when {
    type.isTheory() -> stageShortLabel(LessonStageTypeV2.THEORY, locale)
    type.isExercises() -> stageShortLabel(LessonStageTypeV2.EXERCISES, locale)
    else -> when (locale.substringBefore('-').substringBefore('_').lowercase()) {
        "fr", "en" -> "Tests"
        else -> "Toetsen"
    }
}

internal fun lessonSeriesLabel(index: Int, locale: String): String =
    "${lessonUiText("series", locale)} ${index + 1}"

internal fun lessonPartGroups(
    phases: List<LessonPhasePreviewUi>,
    locale: String
): List<LessonPartPreviewGroupUi> {
    val ordered = phases.sortedBy { it.sequence }
    val groups = listOf(
        ordered.filter { it.type.isTheory() },
        ordered.filter { it.type.isExercises() },
        ordered.filter { it.type == LessonStageTypeV2.MASTERY_TEST }
    ).filter { it.isNotEmpty() }
    return groups.mapIndexed { index, group ->
        LessonPartPreviewGroupUi(
            number = index + 1,
            title = lessonPartTitle(group.first().type, locale),
            phases = group
        )
    }
}

internal fun lessonPhaseIconKind(
    type: LessonStageTypeV2,
    available: Boolean,
    completed: Boolean
): LessonPhaseIconKind = when {
    !available -> LessonPhaseIconKind.LOCKED
    type.isExercises() -> LessonPhaseIconKind.RETRY
    completed -> LessonPhaseIconKind.COMPLETED
    else -> LessonPhaseIconKind.START
}

@Composable
fun LessonHubScreen(
    domain: String,
    legacySubcategoryId: String,
    viewModel: LessonViewModel,
    onLessonSelected: (String) -> Unit,
    onNavigateBack: () -> Unit
) {
    val state by viewModel.hubState.collectAsState()
    LaunchedEffect(domain, legacySubcategoryId) { viewModel.showDomain(domain, legacySubcategoryId) }

    Box(modifier = Modifier.fillMaxSize().background(DeepSpaceBlue)) {
        SpaceBackground()
        Column(
            modifier = Modifier
                .fillMaxSize()
                .systemNavigationMargin()
                .padding(
                    start = 20.dp,
                    top = LESSON_SCREEN_TOP_PADDING_DP.dp,
                    end = 20.dp,
                    bottom = LESSON_SCREEN_BOTTOM_PADDING_DP.dp
                ),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onNavigateBack) {
                    Icon(
                        Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = lessonUiText("back", state.appLocale),
                        tint = StarWhite
                    )
                }
                Spacer(Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        state.subcategoryTitle.ifBlank { lessonUiText("lessons", state.appLocale) },
                        color = StarWhite,
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            when {
                state.isLoading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = PlanetGold)
                }
                state.error != null -> LessonError(state.error.orEmpty(), state.appLocale, onNavigateBack)
                else -> LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                    contentPadding = PaddingValues(
                        top = LESSON_HUB_FIRST_ROW_TOP_PADDING_DP.dp,
                        bottom = LESSON_HUB_BOTTOM_PADDING_DP.dp
                    )
                ) {
                    items(state.units, key = { "${it.id}-${it.grade}" }) { unit ->
                        LessonUnitCard(
                            unit = unit,
                            locale = state.appLocale,
                            showGrade = state.selectedGrades.distinct().size > 1,
                            onLessonSelected = onLessonSelected
                        )
                    }
                    if (state.units.isEmpty()) {
                        item(key = "empty-lessons") {
                            Text(
                                lessonUiText("noLessons", state.appLocale),
                                color = StarWhite.copy(alpha = .72f),
                                modifier = Modifier.fillMaxWidth().padding(18.dp),
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun LessonUnitCard(
    unit: LessonUnitUi,
    locale: String,
    showGrade: Boolean,
    onLessonSelected: (String) -> Unit
) {
    Surface(
        color = DeepSpaceBlue.copy(alpha = .88f),
        shape = RoundedCornerShape(20.dp),
        tonalElevation = 6.dp,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            val repeatsSingleLessonTitle = unit.lessons.singleOrNull()
                ?.title
                ?.trim()
                ?.equals(unit.title.trim(), ignoreCase = true) == true
            if (!repeatsSingleLessonTitle || showGrade) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    if (repeatsSingleLessonTitle) {
                        Spacer(Modifier.weight(1f))
                    } else {
                        Text(
                            unit.title,
                            color = StarWhite,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.weight(1f)
                        )
                    }
                    if (showGrade) GradePill(unit.grade, locale)
                }
            }
            if (unit.description.isNotBlank()) {
                Text(unit.description, color = StarWhite.copy(alpha = .78f), style = MaterialTheme.typography.bodyMedium)
            }
            val ratio = if (unit.totalLessons == 0) 0f else unit.completedLessons.toFloat() / unit.totalLessons
            LinearProgressIndicator(
                progress = { ratio },
                color = PlanetGold,
                trackColor = Color.White.copy(alpha = .12f),
                modifier = Modifier.fillMaxWidth().height(8.dp)
            )
            unit.lessons.forEach { lesson ->
                LessonRow(lesson, locale) { onLessonSelected(lesson.id) }
            }
        }
    }
}

@Composable
private fun GradePill(grade: Int, locale: String) {
    val gradeShape = RoundedCornerShape(12.dp)
    Surface(
        color = DeepSpaceBlue.copy(alpha = .88f),
        shape = gradeShape,
        modifier = Modifier.border(1.dp, PlanetGold.copy(alpha = .34f), gradeShape)
    ) {
        Text(
            "${lessonUiText("grade", locale)} $grade",
            color = PlanetGold,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
        )
    }
}

@Composable
private fun LessonRow(lesson: LessonCardUi, locale: String, onClick: () -> Unit) {
    val completed = lesson.state == be.saikono.poincare.model.lesson.LessonProgressState.COMPLETED
    Surface(
        color = when {
            completed -> PlanetBlue.copy(alpha = .22f)
            lesson.available -> Color.White.copy(alpha = .08f)
            else -> Color.Black.copy(alpha = .18f)
        },
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = lesson.available) { onClick() }
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier.weight(LESSON_ROW_ICON_WEIGHT),
                contentAlignment = Alignment.CenterStart
            ) {
                Icon(
                    imageVector = when {
                        completed -> Icons.Default.CheckCircle
                        lesson.available -> Icons.Default.PlayArrow
                        else -> Icons.Default.Lock
                    },
                    contentDescription = null,
                    tint = if (lesson.available) PlanetGold else StarWhite.copy(alpha = .35f),
                    modifier = Modifier.size(28.dp)
                )
            }
            Text(
                text = if (lesson.isDraft) "${lesson.title} · Draft" else lesson.title,
                color = if (lesson.available) StarWhite else StarWhite.copy(alpha = .45f),
                fontWeight = if (lesson.isDraft) FontWeight.Normal else FontWeight.Bold,
                fontStyle = if (lesson.isDraft) FontStyle.Italic else FontStyle.Normal,
                modifier = Modifier.weight(1f)
            )
            if (lesson.parentSupport != ParentSupportLevel.NONE) {
                Box(
                    modifier = Modifier.weight(LESSON_ROW_META_WEIGHT),
                    contentAlignment = Alignment.CenterEnd
                ) {
                    ParentAidIcon(
                        contentDescription = lessonUiText("parentHelp", locale),
                        modifier = Modifier.size(24.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun LessonIntroScreen(
    lessonId: String,
    viewModel: LessonViewModel,
    onStartLesson: (String?) -> Unit,
    onNavigateBack: () -> Unit
) {
    val state by viewModel.introState.collectAsState()
    LaunchedEffect(lessonId) { viewModel.showLessonIntro(lessonId) }

    Box(modifier = Modifier.fillMaxSize().background(DeepSpaceBlue)) {
        SpaceBackground()
        Column(
            modifier = Modifier
                .fillMaxSize()
                .widthIn(max = 900.dp)
                .systemNavigationMargin()
                .padding(
                    start = 20.dp,
                    top = LESSON_SCREEN_TOP_PADDING_DP.dp,
                    end = 20.dp,
                    bottom = LESSON_SCREEN_BOTTOM_PADDING_DP.dp
                )
        ) {
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onNavigateBack) {
                    Icon(
                        Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = lessonUiText("back", state.appLocale),
                        tint = StarWhite
                    )
                }
                Spacer(Modifier.width(12.dp))
                Text(
                    lessonUiText("lessonOverview", state.appLocale),
                    color = StarWhite,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold
                )
            }

            when {
                state.isLoading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = PlanetGold)
                }
                state.error != null -> LessonError(state.error.orEmpty(), state.appLocale, onNavigateBack)
                else -> {
                    LazyColumn(
                        modifier = Modifier.weight(1f).fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(14.dp),
                        contentPadding = PaddingValues(top = 18.dp, bottom = 18.dp)
                    ) {
                        item(key = "lesson-summary") {
                            Surface(
                                color = Color.White.copy(alpha = .08f),
                                shape = RoundedCornerShape(20.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(
                                    modifier = Modifier.padding(18.dp),
                                    verticalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    Text(
                                        state.title,
                                        color = StarWhite,
                                        style = MaterialTheme.typography.headlineSmall,
                                        fontWeight = FontWeight.Bold
                                    )
                                    if (state.summary.isNotBlank()) {
                                        Text(
                                            state.summary,
                                            color = StarWhite.copy(alpha = .78f),
                                            style = MaterialTheme.typography.bodyLarge
                                        )
                                    }
                                }
                            }
                        }

                        lessonPartGroups(state.phases, state.appLocale).forEach { part ->
                            item(key = "part-${part.number}") {
                                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                    Text(
                                        part.title,
                                        color = PlanetGold,
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 10.dp)
                                    )
                                    part.phases.forEachIndexed { index, phase ->
                                        PhasePreviewRow(
                                            phase = phase,
                                            locale = state.appLocale,
                                            displayTitle = lessonSeriesLabel(phase.seriesIndex ?: index, state.appLocale),
                                            onClick = { onStartLesson(phase.id) }
                                        )
                                    }
                                }
                            }
                        }
                    }
                    if (state.architectureVersion != 2) {
                        Button(
                            onClick = { onStartLesson(null) },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = PlanetGold,
                                contentColor = DeepSpaceBlue
                            ),
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)
                        ) {
                            Text(
                                when {
                                    state.completed -> lessonUiText("viewResult", state.appLocale)
                                    state.hasSavedDraft -> lessonUiText("resume", state.appLocale)
                                    else -> lessonUiText("startLesson", state.appLocale)
                                },
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PhasePreviewRow(
    phase: LessonPhasePreviewUi,
    locale: String,
    displayTitle: String? = null,
    onClick: () -> Unit
) {
    val contentAlpha = if (phase.available) 1f else .36f
    val iconKind = lessonPhaseIconKind(phase.type, phase.available, phase.completed)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = phase.available) { onClick() }
            .padding(horizontal = 4.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = when (iconKind) {
                    LessonPhaseIconKind.COMPLETED -> Icons.Default.CheckCircle
                    LessonPhaseIconKind.LOCKED -> Icons.Default.Lock
                    LessonPhaseIconKind.RETRY -> Icons.Default.Refresh
                    LessonPhaseIconKind.START -> Icons.Default.PlayArrow
                },
                contentDescription = when (iconKind) {
                    LessonPhaseIconKind.COMPLETED -> lessonUiText("done", locale)
                    LessonPhaseIconKind.LOCKED -> lessonUiText("locked", locale)
                    LessonPhaseIconKind.RETRY -> lessonUiText("retry", locale)
                    LessonPhaseIconKind.START -> stageShortLabel(phase.type, locale)
                },
                tint = if (phase.completed) PlanetGreen else PlanetGold.copy(alpha = contentAlpha),
                modifier = Modifier.size(30.dp)
            )
            Spacer(Modifier.width(14.dp))
            Text(
                displayTitle ?: buildString {
                    append(stageShortLabel(phase.type, locale))
                    if (phase.type == LessonStageTypeV2.EXERCISES && phase.seriesIndex != null) {
                        append(", ")
                        append(lessonUiText("series", locale).lowercase())
                        append(" ")
                        append(phase.seriesIndex + 1)
                    }
                },
                color = StarWhite.copy(alpha = contentAlpha),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.weight(1f))
            Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    when {
                        phase.type in setOf(LessonStageTypeV2.THEORY, LessonStageTypeV2.ACQUIRE_UNDERSTAND) && phase.completed -> lessonUiText("done", locale)
                        phase.type == LessonStageTypeV2.MASTERY_TEST && phase.correctCount != null ->
                            "${phase.correctCount}/${phase.questionCount ?: phase.itemCount} ${lessonUiText("correctShort", locale)}"
                        phase.itemCount > 0 -> "${phase.itemCount} ${lessonUiText("exercises", locale)}"
                        else -> ""
                    },
                    color = StarWhite.copy(alpha = contentAlpha),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold
                )
                if (phase.resumable && phase.hasSavedDraft) {
                    Text(
                        lessonUiText("resume", locale),
                        color = PlanetGold.copy(alpha = contentAlpha),
                        style = MaterialTheme.typography.labelSmall
                    )
                }
            }
        }
        if (phase.progressPercent > 0 || phase.available) {
            LinearProgressIndicator(
                progress = { phase.progressPercent / 100f },
                color = if (phase.completed) PlanetGreen else PlanetGold,
                trackColor = Color.White.copy(alpha = .10f),
                modifier = Modifier.fillMaxWidth().height(5.dp)
            )
        }
        HorizontalDivider(color = Color.White.copy(alpha = .10f))
    }
}

@Composable
fun LessonPlayerScreen(
    lessonId: String,
    stageIdV2: String? = null,
    viewModel: LessonViewModel,
    onOpenLetterPractice: (String) -> Unit = {},
    onNavigateBack: () -> Unit
) {
    val state by viewModel.playerState.collectAsState()
    LaunchedEffect(lessonId, stageIdV2) { viewModel.openLesson(lessonId, stageIdV2 = stageIdV2) }
    DisposableEffect(Unit) { onDispose { viewModel.pauseLesson() } }

    Box(modifier = Modifier.fillMaxSize().background(DeepSpaceBlue)) {
        SpaceBackground()
        Column(
            modifier = Modifier
                .fillMaxSize()
                .widthIn(max = 900.dp)
                .systemNavigationMargin()
                .padding(
                    start = 20.dp,
                    top = LESSON_SCREEN_TOP_PADDING_DP.dp,
                    end = 20.dp,
                    bottom = LESSON_SCREEN_BOTTOM_PADDING_DP.dp
                ),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            val hasParentSupport = state.parentSupport != ParentSupportLevel.NONE
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Box(modifier = Modifier.weight(.10f), contentAlignment = Alignment.CenterStart) {
                    IconButton(onClick = { viewModel.pauseLesson(); onNavigateBack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = lessonUiText("back", state.appLocale), tint = StarWhite)
                    }
                }
                Box(
                    modifier = Modifier.weight(if (hasParentSupport) .80f else .90f),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        state.lessonTitle.ifBlank { lessonUiText("lesson", state.appLocale) },
                        color = StarWhite,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center
                    )
                }
                if (hasParentSupport) {
                    Box(modifier = Modifier.weight(.10f), contentAlignment = Alignment.CenterEnd) {
                        ParentAidIcon(
                            contentDescription = lessonUiText("parentHelp", state.appLocale),
                            modifier = Modifier.size(42.dp)
                        )
                    }
                }
            }

            if (state.totalSteps > 0 && state.result == null) {
                val progress = (state.currentStepIndex + 1).toFloat() / state.totalSteps
                LinearProgressIndicator(
                    progress = { progress },
                    color = PlanetGold,
                    trackColor = Color.White.copy(alpha = .12f),
                    modifier = Modifier.fillMaxWidth().height(8.dp)
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    "${state.currentStepIndex + 1} ${lessonUiText("of", state.appLocale)} ${state.totalSteps}",
                    color = StarWhite.copy(alpha = .68f),
                    style = MaterialTheme.typography.labelMedium
                )
            }

            when {
                state.isLoading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = PlanetGold)
                }
                state.error != null -> LessonError(state.error.orEmpty(), state.appLocale, onNavigateBack)
                state.result != null -> LessonResultCard(
                    result = requireNotNull(state.result),
                    stageType = state.stageTypeV2,
                    locale = state.appLocale,
                    onReplay = viewModel::replayLesson,
                    onBack = onNavigateBack
                )
                state.step != null -> LessonStepContent(state, viewModel, onOpenLetterPractice)
            }
        }
    }
}

@Composable
private fun LessonStepContent(
    state: LessonPlayerUiState,
    viewModel: LessonViewModel,
    onOpenLetterPractice: (String) -> Unit
) {
    val step = state.step ?: return
    Column(
        modifier = Modifier.fillMaxSize().padding(top = LESSON_EXERCISE_FIRST_ROW_TOP_PADDING_DP.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Surface(
            color = Color.White.copy(alpha = .08f),
            shape = RoundedCornerShape(20.dp),
            modifier = Modifier.fillMaxWidth().weight(1f)
        ) {
            when (step) {
                is LessonStep.Explanation -> ExplanationStep(step, state.exerciseLocale, viewModel::playCurrentStep)
                is LessonStep.Exercise -> ExerciseStep(step, state, viewModel)
                is LessonStep.Practical -> PracticalStep(step, state, viewModel, onOpenLetterPractice)
            }
        }
        Spacer(Modifier.height(12.dp))
        val enabled = when (step) {
            is LessonStep.Explanation -> true
            is LessonStep.Exercise -> state.answerFinalized
            is LessonStep.Practical -> state.practicalConfirmed
        }
        val continueShape = RoundedCornerShape(14.dp)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            if (step is LessonStep.Explanation && state.currentStepIndex > 0) {
                OutlinedButton(
                    onClick = viewModel::previousTheoryPage,
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = StarWhite),
                    modifier = Modifier.weight(1f).height(54.dp)
                ) {
                    Text(lessonUiText("previous", state.appLocale), fontWeight = FontWeight.Bold)
                }
            }
            Button(
                onClick = viewModel::continueLesson,
                enabled = enabled,
                colors = ButtonDefaults.buttonColors(containerColor = DeepSpaceBlue.copy(alpha = .88f), contentColor = PlanetGold),
                shape = continueShape,
                modifier = Modifier.weight(1f).height(54.dp).border(1.dp, PlanetGold.copy(alpha = .34f), continueShape)
            ) {
                Text(lessonUiText("continue", state.appLocale), fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun ExplanationStep(step: LessonStep.Explanation, locale: String, onPlay: () -> Unit) {
    Column(
        Modifier.padding(24.dp).verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(18.dp)
    ) {
        Text(step.title?.visibleFor(locale) ?: lessonUiText("explanation", locale), color = PlanetGold, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        if (step.visualsV2.isNotEmpty()) {
            Row(
                modifier = Modifier.fillMaxWidth().height(156.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                step.visualsV2.forEach { visual -> LessonVisualV2(visual, locale, Modifier.weight(1f)) }
            }
        }
        Text(
            highlightPhonemeReferences(step.content.visibleFor(locale)),
            color = StarWhite,
            style = MaterialTheme.typography.titleMedium,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth().clickable(onClick = onPlay).padding(vertical = 12.dp)
        )
    }
}

@Composable
private fun ExerciseStep(step: LessonStep.Exercise, state: LessonPlayerUiState, viewModel: LessonViewModel) {
    val exercise = step.exercise
    val requiresAudio = lessonExerciseRequiresAudio(exercise)
    LaunchedEffect(exercise.id) {
        if (requiresAudio) viewModel.playCurrentStep()
    }
    val capability = ExerciseCapabilityRegistry.capability(exercise.inputMode, ExerciseRuntime.LESSON_V2)
    if (capability.status != ExerciseCapabilityStatus.ACTIVE) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(
                "Deze oefening kan nog niet veilig worden weergegeven (${exercise.inputMode}).",
                color = PlanetRed,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(24.dp)
            )
        }
        return
    }
    if (exercise.inputMode in setOf(
            InputMode.TAP_CHOICE,
            InputMode.LISTEN_AND_SELECT,
            InputMode.CUSTOM_TEXT_KEYBOARD,
            InputMode.CUSTOM_NUMBER_PAD
        )
    ) {
        BottomAlignedExerciseStep(step, state, requiresAudio, viewModel)
        return
    }
    val options = remember(exercise.id, state.exerciseLocale) { exercise.localizedOptions(state.exerciseLocale) }
    Box(modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 22.dp, top = 22.dp, end = 22.dp, bottom = 82.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            item {
                Text(
                    highlightPhonemeReferences(exercise.prompt.get(state.exerciseLocale)),
                    color = StarWhite,
                    style = MaterialTheme.typography.headlineSmall,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().clickable(onClick = viewModel::playCurrentStep).padding(vertical = 12.dp)
                )
            }
            if (requiresAudio) {
                item {
                    Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                        IconButton(onClick = viewModel::playCurrentStep) {
                            Icon(
                                imageVector = Icons.Default.PlayArrow,
                                contentDescription = lessonAudioButtonLabel(state.appLocale),
                                tint = PlanetGold,
                                modifier = Modifier.size(34.dp)
                            )
                        }
                    }
                }
            }
            clockMomentsFor(exercise).takeIf { it.isNotEmpty() }?.let { moments ->
                item { ClockExerciseVisual(moments) }
            }
            if (exercise.inputMode in setOf(InputMode.TAP_CHOICE, InputMode.LISTEN_AND_SELECT)) {
                items(options) { option ->
                    val selected = state.answerInput == option || state.previewedChoice == option
                    val wrong = step.feedbackPolicyV2 != LessonFeedbackPolicyV2.AFTER_TEST && option in state.wrongAnswers
                    val correct = state.correctAnswerRevealed && option == exercise.localizedAnswer(state.exerciseLocale)
                    Surface(
                        color = when {
                            wrong -> PlanetRed.copy(alpha = .38f)
                            correct -> PlanetGreen.copy(alpha = .38f)
                            selected -> PlanetBlue.copy(alpha = .40f)
                            else -> Color.White.copy(alpha = .08f)
                        },
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .graphicsLayer {
                                if (state.previewedChoice == option) {
                                    scaleX = .98f
                                    scaleY = .94f
                                    translationY = 4.dp.toPx()
                                }
                            }
                            .clickable(
                            enabled = !state.answerFinalized && option !in state.wrongAnswers
                        ) { viewModel.selectChoice(option) }
                    ) {
                        Text(
                            option,
                            color = when {
                                wrong -> PlanetRed
                                correct -> PlanetGreen
                                else -> StarWhite
                            },
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(18.dp),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = if (wrong || correct) FontWeight.Bold else FontWeight.Normal
                        )
                    }
                }
            } else if (exercise.inputMode == InputMode.CUSTOM_NUMBER_PAD) {
                item {
                    Text(
                        state.answerInput.ifBlank { "…" },
                        color = PlanetGold,
                        style = MaterialTheme.typography.displaySmall,
                        modifier = Modifier.padding(8.dp)
                    )
                }
                state.wrongAnswers.lastOrNull()
                    ?.takeIf { step.feedbackPolicyV2 != LessonFeedbackPolicyV2.AFTER_TEST }
                    ?.let { wrong ->
                    item {
                        Text(
                            "${lessonUiText("yourAnswer", state.appLocale)} $wrong",
                            color = PlanetRed,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
                if (state.correctAnswerRevealed) {
                    item {
                        Text(
                            "${lessonUiText("correctAnswer", state.appLocale)} ${exercise.localizedAnswer(state.exerciseLocale)}",
                            color = PlanetGreen,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
                items(NUMBER_PAD_ROWS) { row ->
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        row.forEach { key ->
                            Button(
                                onClick = {
                                    when (key) {
                                        "⌫" -> viewModel.backspaceNumber()
                                        "✓" -> viewModel.submitNumber()
                                        else -> viewModel.appendNumber(key)
                                    }
                                },
                                enabled = !state.answerFinalized && (key != "✓" || state.answerInput.isNotBlank()),
                                modifier = Modifier.weight(1f).height(52.dp),
                                shape = RoundedCornerShape(12.dp),
                                contentPadding = PaddingValues(4.dp)
                            ) { Text(key, fontWeight = FontWeight.Bold) }
                        }
                    }
                }
            } else if (exercise.inputMode == InputMode.CUSTOM_TEXT_KEYBOARD) {
                val maskedText = exercise.data["maskedText"]
                    ?: exercise.data["maskedText_${state.exerciseLocale}"]
                val displayedAnswer = fillDictationMask(maskedText, state.answerInput)
                item {
                    Text(
                        text = displayedAnswer.ifBlank { "…" }.replace('_', '＿'),
                        color = when {
                            state.feedbackCorrect == true -> PlanetGreen
                            state.feedbackCorrect == false -> PlanetRed
                            else -> PlanetGold
                        },
                        style = MaterialTheme.typography.headlineMedium,
                        textAlign = TextAlign.Center,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)
                    )
                }
                if (state.correctAnswerRevealed) {
                    item {
                        Text(
                            "${lessonUiText("correctAnswer", state.appLocale)} ${exercise.localizedAnswer(state.exerciseLocale)}",
                            color = PlanetGreen,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
                item {
                    AzertyBeKeyboard(
                        locale = state.exerciseLocale,
                        onCharacter = viewModel::appendText,
                        onBackspace = viewModel::backspaceText,
                        onSubmit = viewModel::submitText,
                        enabled = !state.answerFinalized,
                        modifier = Modifier.widthIn(max = 760.dp).fillMaxWidth()
                    )
                }
            } else if (exercise.inputMode == InputMode.TRACE_LETTER) {
                item {
                    LetterTracingComponent(
                        targetLetter = exercise.data["letter"].orEmpty(),
                        onPathUpdate = {},
                        onComplete = viewModel::completeTracing,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(360.dp)
                    )
                }
            }
        }
        state.feedbackCorrect?.takeIf { step.feedbackPolicyV2 != LessonFeedbackPolicyV2.AFTER_TEST }?.let { correct ->
            LessonFeedbackText(
                correct = correct,
                canTryAgain = !state.answerFinalized,
                locale = state.appLocale,
                modifier = Modifier.align(Alignment.BottomCenter)
            )
        }
    }
}

@Composable
private fun BottomAlignedExerciseStep(
    step: LessonStep.Exercise,
    state: LessonPlayerUiState,
    requiresAudio: Boolean,
    viewModel: LessonViewModel
) {
    val exercise = step.exercise
    val options = remember(exercise.id, state.exerciseLocale) { exercise.localizedOptions(state.exerciseLocale) }
    val feedback = state.feedbackCorrect?.takeIf { step.feedbackPolicyV2 != LessonFeedbackPolicyV2.AFTER_TEST }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(start = 22.dp, top = 18.dp, end = 22.dp, bottom = 14.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        when (exercise.inputMode) {
            InputMode.TAP_CHOICE, InputMode.LISTEN_AND_SELECT -> {
                ExerciseQuestionArea(exercise, state, requiresAudio, viewModel::playCurrentStep, viewModel::selectCountingIcon, Modifier.weight(1f))
                feedback?.let { LessonFeedbackText(it, !state.answerFinalized, state.appLocale) }
                Spacer(Modifier.height(10.dp))
                Column(
                    modifier = Modifier.widthIn(max = 760.dp).fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    options.forEach { option ->
                        val selected = state.answerInput == option || state.previewedChoice == option
                        val wrong = step.feedbackPolicyV2 != LessonFeedbackPolicyV2.AFTER_TEST && option in state.wrongAnswers
                        val correct = state.correctAnswerRevealed && option == exercise.localizedAnswer(state.exerciseLocale)
                        Surface(
                            color = when {
                                wrong -> PlanetRed.copy(alpha = .38f)
                                correct -> PlanetGreen.copy(alpha = .38f)
                                selected -> PlanetBlue.copy(alpha = .40f)
                                else -> Color.White.copy(alpha = .08f)
                            },
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .graphicsLayer {
                                    if (state.previewedChoice == option) {
                                        scaleX = .98f
                                        scaleY = .94f
                                        translationY = 4.dp.toPx()
                                    }
                                }
                                .clickable(
                                enabled = !state.answerFinalized && option !in state.wrongAnswers
                            ) { viewModel.selectChoice(option) }
                        ) {
                            Text(
                                option,
                                color = when {
                                    wrong -> PlanetRed
                                    correct -> PlanetGreen
                                    else -> StarWhite
                                },
                                textAlign = TextAlign.Center,
                                modifier = Modifier.padding(horizontal = 18.dp, vertical = 15.dp),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = if (wrong || correct) FontWeight.Bold else FontWeight.Normal
                            )
                        }
                    }
                }
            }
            InputMode.CUSTOM_TEXT_KEYBOARD -> {
                val maskedText = exercise.data["maskedText"] ?: exercise.data["maskedText_${state.exerciseLocale}"]
                val displayedAnswer = fillDictationMask(maskedText, state.answerInput)
                ExerciseQuestionArea(exercise, state, requiresAudio, viewModel::playCurrentStep, viewModel::selectCountingIcon, Modifier.weight(1f)) {
                    Text(
                        text = displayedAnswer.ifBlank { "…" }.replace('_', '＿'),
                        color = when {
                            state.feedbackCorrect == true -> PlanetGreen
                            state.feedbackCorrect == false -> PlanetRed
                            else -> PlanetGold
                        },
                        style = MaterialTheme.typography.headlineMedium,
                        textAlign = TextAlign.Center,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)
                    )
                    if (state.correctAnswerRevealed) {
                        Text(
                            "${lessonUiText("correctAnswer", state.appLocale)} ${exercise.localizedAnswer(state.exerciseLocale)}",
                            color = PlanetGreen,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
                feedback?.let { LessonFeedbackText(it, !state.answerFinalized, state.appLocale) }
                Spacer(Modifier.height(10.dp))
                AzertyBeKeyboard(
                    locale = state.exerciseLocale,
                    onCharacter = viewModel::appendText,
                    onBackspace = viewModel::backspaceText,
                    onSubmit = viewModel::submitText,
                    enabled = !state.answerFinalized,
                    modifier = Modifier.widthIn(max = 760.dp).fillMaxWidth()
                )
            }
            InputMode.CUSTOM_NUMBER_PAD -> {
                ExerciseQuestionArea(exercise, state, requiresAudio, viewModel::playCurrentStep, viewModel::selectCountingIcon, Modifier.weight(1f)) {
                    Text(
                        state.answerInput.ifBlank { "…" },
                        color = PlanetGold,
                        style = MaterialTheme.typography.displaySmall,
                        modifier = Modifier.padding(8.dp)
                    )
                    state.wrongAnswers.lastOrNull()
                        ?.takeIf { step.feedbackPolicyV2 != LessonFeedbackPolicyV2.AFTER_TEST }
                        ?.let { wrong -> Text("${lessonUiText("yourAnswer", state.appLocale)} $wrong", color = PlanetRed, fontWeight = FontWeight.Bold) }
                    if (state.correctAnswerRevealed) {
                        Text(
                            "${lessonUiText("correctAnswer", state.appLocale)} ${exercise.localizedAnswer(state.exerciseLocale)}",
                            color = PlanetGreen,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
                feedback?.let { LessonFeedbackText(it, !state.answerFinalized, state.appLocale) }
                Spacer(Modifier.height(10.dp))
                Column(
                    modifier = Modifier.widthIn(max = 760.dp).fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    NUMBER_PAD_ROWS.forEach { row ->
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            row.forEach { key ->
                                Button(
                                    onClick = {
                                        when (key) {
                                            "⌫" -> viewModel.backspaceNumber()
                                            "✓" -> viewModel.submitNumber()
                                            else -> viewModel.appendNumber(key)
                                        }
                                    },
                                    enabled = !state.answerFinalized &&
                                        (exercise.data["objectChoiceEnabled"] != "true" || state.countingIconChoice != null) &&
                                        (key != "✓" || state.answerInput.isNotBlank()),
                                    modifier = Modifier.weight(1f).height(52.dp),
                                    shape = RoundedCornerShape(12.dp),
                                    contentPadding = PaddingValues(4.dp)
                                ) { Text(key, fontWeight = FontWeight.Bold) }
                            }
                        }
                    }
                }
            }
            else -> Unit
        }
    }
}

@Composable
private fun ExerciseQuestionArea(
    exercise: ExerciseContent,
    state: LessonPlayerUiState,
    requiresAudio: Boolean,
    onPlay: () -> Unit,
    onCountingIconSelected: (String) -> Unit,
    modifier: Modifier = Modifier,
    extraContent: (@Composable () -> Unit)? = null
) {
    LazyColumn(
        modifier = modifier.fillMaxWidth(),
        contentPadding = PaddingValues(vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(14.dp, Alignment.CenterVertically)
    ) {
        item {
            Text(
                highlightPhonemeReferences(exercise.prompt.get(state.exerciseLocale)),
                color = StarWhite,
                style = MaterialTheme.typography.headlineSmall,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().clickable(onClick = onPlay).padding(vertical = 12.dp)
            )
        }
        if (requiresAudio) {
            item {
                IconButton(onClick = onPlay) {
                    Icon(
                        Icons.Default.PlayArrow,
                        contentDescription = lessonAudioButtonLabel(state.appLocale),
                        tint = PlanetGold,
                        modifier = Modifier.size(34.dp)
                    )
                }
            }
        }
        if (exercise.data["objectChoiceEnabled"] == "true") {
            item {
                CountingIconPicker(
                    selected = state.countingIconChoice,
                    locale = state.appLocale,
                    onSelect = onCountingIconSelected
                )
            }
            item {
                if (state.countingIconChoice == null) {
                    Text(
                        lessonUiText("chooseCountingIcon", state.appLocale),
                        color = PlanetGold,
                        textAlign = TextAlign.Center,
                        fontWeight = FontWeight.Bold
                    )
                } else {
                    CountingObjectsExerciseVisual(exercise, state.countingIconChoice)
                }
            }
        }
        clockMomentsFor(exercise).takeIf { it.isNotEmpty() }?.let { moments ->
            item { ClockExerciseVisual(moments) }
        }
        extraContent?.let { content ->
            item {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) { content() }
            }
        }
    }
}

@Composable
private fun CountingIconPicker(selected: String?, locale: String, onSelect: (String) -> Unit) {
    val choices = listOf(
        "astronaut" to "🧑‍🚀",
        "yellow_car" to "🚕",
        "flower" to "🌼",
        "chick" to "🐥"
    )
    Row(
        modifier = Modifier.widthIn(max = 520.dp).fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        choices.forEach { (id, emoji) ->
            Surface(
                color = if (selected == id) PlanetGold else Color.White.copy(alpha = .09f),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.weight(1f).clickable { onSelect(id) }
            ) {
                Text(
                    emoji,
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.headlineMedium,
                    modifier = Modifier
                        .padding(vertical = 10.dp)
                        .semantics { contentDescription = countingIconLabel(id, locale) }
                )
            }
        }
    }
}

@Composable
private fun CountingObjectsExerciseVisual(exercise: ExerciseContent, selected: String) {
    val emoji = when (selected) {
        "yellow_car" -> "🚕"
        "flower" -> "🌼"
        "chick" -> "🐥"
        else -> "🧑‍🚀"
    }
    val left = exercise.data["left"]?.toIntOrNull()?.coerceIn(0, 20) ?: 0
    val right = exercise.data["right"]?.toIntOrNull()?.coerceIn(0, 20) ?: 0
    val operation = exercise.data["operation"]
    val operator = if (operation == "subtraction") "−" else "+"
    fun group(count: Int): String = List(count) { emoji }
        .chunked(5)
        .joinToString("\n") { it.joinToString(" ") }
    if (operation == "counting") {
        Text(
            group(left),
            color = StarWhite,
            textAlign = TextAlign.Center,
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.widthIn(max = 640.dp).fillMaxWidth()
        )
    } else {
        Row(
            modifier = Modifier.widthIn(max = 640.dp).fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            Text(group(left), color = StarWhite, textAlign = TextAlign.Center, style = MaterialTheme.typography.titleLarge)
            Text(operator, color = PlanetGold, style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.Bold)
            Text(group(right), color = StarWhite, textAlign = TextAlign.Center, style = MaterialTheme.typography.titleLarge)
        }
    }
}

private fun countingIconLabel(id: String, locale: String): String = when (
    locale.substringBefore('-').substringBefore('_')
) {
    "fr" -> when (id) { "yellow_car" -> "voiture jaune"; "flower" -> "fleur"; "chick" -> "poussin"; else -> "astronaute" }
    "en" -> when (id) { "yellow_car" -> "yellow car"; "flower" -> "flower"; "chick" -> "chick"; else -> "astronaut" }
    else -> when (id) { "yellow_car" -> "gele auto"; "flower" -> "bloem"; "chick" -> "kuiken"; else -> "astronaut" }
}

@Composable
private fun LessonStageRail(stages: List<LessonStageNavUi>, currentStageId: String, locale: String) {
    val current = stages.firstOrNull { it.id == currentStageId }
    val groupedStages = listOf(
        stages.filter { it.type.isTheory() },
        stages.filter { it.type.isExercises() },
        stages.filter { it.type == LessonStageTypeV2.MASTERY_TEST }
    ).filter { it.isNotEmpty() }
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        current?.sectionTitle?.let { sectionTitle ->
            Text(
                sectionTitle,
                color = PlanetGold,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold
            )
        }
        LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            items(groupedStages, key = { it.first().type.name }) { group ->
                val stage = group.first()
                val selected = when {
                    current?.type?.isTheory() == true -> stage.type.isTheory()
                    current?.type?.isExercises() == true -> stage.type.isExercises()
                    else -> stage.type == current?.type
                }
                Surface(
                    color = if (selected) PlanetGold else Color.White.copy(alpha = .08f),
                    shape = RoundedCornerShape(10.dp),
                ) {
                    Text(
                        text = "${groupedStages.indexOf(group) + 1}. ${stageShortLabel(stage.type, locale)}",
                        color = if (selected) DeepSpaceBlue else StarWhite.copy(alpha = .72f),
                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                        style = MaterialTheme.typography.labelSmall,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp)
                    )
                }
            }
        }
    }
}

private fun stageShortLabel(stage: LessonStageTypeV2, locale: String): String {
    val language = locale.substringBefore('-').substringBefore('_').lowercase()
    return when (language) {
        "fr" -> when (stage) {
            LessonStageTypeV2.THEORY -> "Théorie"
            LessonStageTypeV2.EXERCISES -> "Exercices"
            LessonStageTypeV2.ACQUIRE_UNDERSTAND -> "Comprendre"
            LessonStageTypeV2.PRACTISE -> "Exercer"
            LessonStageTypeV2.APPLY_DEMONSTRATE -> "Appliquer"
            LessonStageTypeV2.MASTERY_TEST -> "Test"
        }
        "en" -> when (stage) {
            LessonStageTypeV2.THEORY -> "Theory"
            LessonStageTypeV2.EXERCISES -> "Exercises"
            LessonStageTypeV2.ACQUIRE_UNDERSTAND -> "Learn"
            LessonStageTypeV2.PRACTISE -> "Practise"
            LessonStageTypeV2.APPLY_DEMONSTRATE -> "Apply"
            LessonStageTypeV2.MASTERY_TEST -> "Test"
        }
        else -> when (stage) {
            LessonStageTypeV2.THEORY -> "Theorie"
            LessonStageTypeV2.EXERCISES -> "Oefeningen"
            LessonStageTypeV2.ACQUIRE_UNDERSTAND -> "Begrijp"
            LessonStageTypeV2.PRACTISE -> "Oefen"
            LessonStageTypeV2.APPLY_DEMONSTRATE -> "Pas toe"
            LessonStageTypeV2.MASTERY_TEST -> "Toets"
        }
    }
}

@Composable
private fun LessonVisualV2(visual: LessonVisualV2, locale: String, modifier: Modifier = Modifier) {
    val description = visual.altText[locale] ?: visual.altText["nl-BE"].orEmpty()
    val additionCounts = when (visual.representationId) {
        "math.addition.counters.3_plus_2.v1" -> 3 to 2
        "math.addition.counters.4_plus_3.v1" -> 4 to 3
        else -> null
    }
    if (additionCounts != null) {
        AdditionTheoryDiagram(
            goldCount = additionCounts.first,
            blueCount = additionCounts.second,
            description = description,
            modifier = modifier
        )
        return
    }
    if (!visual.assetPathPng.isNullOrBlank()) {
        val context = LocalContext.current
        val image = remember(visual.assetPathPng) {
            runCatching {
                context.assets.open(requireNotNull(visual.assetPathPng)).use { stream ->
                    BitmapFactory.decodeStream(stream)?.asImageBitmap()
                }
            }.getOrNull()
        }
        if (image != null) {
            Image(
                bitmap = image,
                contentDescription = description,
                contentScale = ContentScale.Fit,
                modifier = modifier.fillMaxSize()
            )
        }
        return
    }
    Canvas(
        modifier = modifier
            .fillMaxSize()
            .semantics { contentDescription = description }
    ) {
        val gold = PlanetGold
        val white = StarWhite
        val w = size.width
        val h = size.height
        when (visual.representationId) {
            "math.ten_frame.v1" -> {
                val left = w * .08f; val top = h * .18f; val cellW = w * .168f; val cellH = h * .30f
                repeat(2) { row -> repeat(5) { column ->
                    drawRect(white, Offset(left + column * cellW, top + row * cellH), androidx.compose.ui.geometry.Size(cellW, cellH), style = androidx.compose.ui.graphics.drawscope.Stroke(width = 3f))
                } }
                repeat(7) { index ->
                    val row = index / 5; val column = index % 5
                    drawCircle(gold, radius = minOf(cellW, cellH) * .28f, center = Offset(left + column * cellW + cellW / 2, top + row * cellH + cellH / 2))
                }
            }
            "math.part_whole.v1" -> {
                drawCircle(gold, w * .16f, Offset(w * .5f, h * .28f), style = androidx.compose.ui.graphics.drawscope.Stroke(width = 8f))
                drawCircle(white, w * .13f, Offset(w * .28f, h * .72f), style = androidx.compose.ui.graphics.drawscope.Stroke(width = 6f))
                drawCircle(white, w * .13f, Offset(w * .72f, h * .72f), style = androidx.compose.ui.graphics.drawscope.Stroke(width = 6f))
                drawLine(gold, Offset(w * .44f, h * .42f), Offset(w * .32f, h * .58f), strokeWidth = 7f)
                drawLine(gold, Offset(w * .56f, h * .42f), Offset(w * .68f, h * .58f), strokeWidth = 7f)
            }
            "reading.elkonin_boxes.v1" -> repeat(3) { index ->
                val cellW = w * .25f; val left = w * .09f + index * cellW
                drawRect(white, Offset(left, h * .30f), androidx.compose.ui.geometry.Size(cellW, h * .42f), style = androidx.compose.ui.graphics.drawscope.Stroke(width = 6f))
                drawCircle(gold, radius = minOf(cellW, h * .42f) * .22f, center = Offset(left + cellW / 2, h * .51f))
            }
            "measurement.ruler_compare.v1" -> {
                val start = w * .12f
                drawLine(white, Offset(start, h * .82f), Offset(w * .9f, h * .82f), strokeWidth = 5f)
                repeat(9) { index ->
                    val x = start + index * (w * .78f / 8)
                    drawLine(white, Offset(x, h * .72f), Offset(x, h * .88f), strokeWidth = 3f)
                }
                drawLine(gold, Offset(start, h * .30f), Offset(w * .82f, h * .30f), strokeWidth = 14f, cap = StrokeCap.Round)
                drawLine(white, Offset(start, h * .55f), Offset(w * .58f, h * .55f), strokeWidth = 14f, cap = StrokeCap.Round)
            }
            "world.season_cycle.v1" -> {
                drawCircle(gold, radius = minOf(w, h) * .36f, center = center, style = androidx.compose.ui.graphics.drawscope.Stroke(width = 10f))
                drawCircle(white, radius = 14f, center = Offset(w * .5f, h * .18f))
                drawCircle(gold, radius = 22f, center = Offset(w * .82f, h * .5f))
                drawCircle(white, radius = 18f, center = Offset(w * .5f, h * .82f), style = androidx.compose.ui.graphics.drawscope.Stroke(width = 6f))
                drawLine(white, Offset(w * .15f, h * .5f), Offset(w * .29f, h * .5f), strokeWidth = 8f)
                drawLine(white, Offset(w * .22f, h * .43f), Offset(w * .22f, h * .57f), strokeWidth = 8f)
            }
        }
    }
}

@Composable
private fun AdditionTheoryDiagram(
    goldCount: Int,
    blueCount: Int,
    description: String,
    modifier: Modifier = Modifier
) {
    Canvas(
        modifier = modifier
            .fillMaxSize()
            .semantics { contentDescription = description }
    ) {
        val gold = PlanetGold
        val blue = Color(0xFF4FC3F7)
        val white = StarWhite
        val w = size.width
        val h = size.height
        val topRadius = minOf(w * .037f, h * .075f)

        repeat(goldCount) { index ->
            drawCircle(gold, topRadius, Offset(w * (.10f + index * .085f), h * .23f))
        }
        val plusX = w * .47f
        drawLine(white, Offset(plusX - w * .025f, h * .23f), Offset(plusX + w * .025f, h * .23f), 5f, StrokeCap.Round)
        drawLine(white, Offset(plusX, h * .18f), Offset(plusX, h * .28f), 5f, StrokeCap.Round)
        repeat(blueCount) { index ->
            drawCircle(blue, topRadius, Offset(w * (.58f + index * .085f), h * .23f))
        }

        drawLine(white, Offset(w * .50f, h * .36f), Offset(w * .50f, h * .49f), 7f, StrokeCap.Round)
        drawLine(white, Offset(w * .50f, h * .49f), Offset(w * .46f, h * .44f), 7f, StrokeCap.Round)
        drawLine(white, Offset(w * .50f, h * .49f), Offset(w * .54f, h * .44f), 7f, StrokeCap.Round)

        val left = w * .08f
        val top = h * .59f
        val cellW = w * .168f
        val cellH = h * .16f
        repeat(2) { row ->
            repeat(5) { column ->
                drawRect(
                    white,
                    Offset(left + column * cellW, top + row * cellH),
                    androidx.compose.ui.geometry.Size(cellW, cellH),
                    style = androidx.compose.ui.graphics.drawscope.Stroke(width = 4f)
                )
            }
        }
        repeat(goldCount + blueCount) { index ->
            val row = index / 5
            val column = index % 5
            drawCircle(
                color = if (index < goldCount) gold else blue,
                radius = minOf(cellW, cellH) * .27f,
                center = Offset(left + column * cellW + cellW / 2f, top + row * cellH + cellH / 2f)
            )
        }
    }
}

@Composable
private fun PracticalStep(
    step: LessonStep.Practical,
    state: LessonPlayerUiState,
    viewModel: LessonViewModel,
    onOpenLetterPractice: (String) -> Unit
) {
    Column(Modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(18.dp)) {
        Text(step.title?.visibleFor(state.exerciseLocale) ?: lessonUiText("practical", state.appLocale), color = PlanetGold, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Text(
            step.instructions.visibleFor(state.exerciseLocale),
            color = StarWhite,
            style = MaterialTheme.typography.titleMedium,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth().clickable(onClick = viewModel::playCurrentStep).padding(vertical = 12.dp)
        )
        if (step.requiresAdultVerification) {
            Text(lessonUiText("adultVerification", state.appLocale), color = StarWhite.copy(alpha = .72f), textAlign = TextAlign.Center)
        }
        if (step.activityId.startsWith("writing.letter_tracing.")) {
            val practiceShape = RoundedCornerShape(14.dp)
            Button(
                onClick = { onOpenLetterPractice(step.activityId) },
                colors = ButtonDefaults.buttonColors(
                    containerColor = PlanetGold,
                    contentColor = DeepSpaceBlue
                ),
                shape = practiceShape,
                modifier = Modifier.fillMaxWidth().heightIn(min = 54.dp)
            ) {
                Text(lessonUiText("openLetterPractice", state.appLocale), fontWeight = FontWeight.Bold)
            }
        }
        val confirmationShape = RoundedCornerShape(14.dp)
        Button(
            onClick = viewModel::confirmPractical,
            colors = ButtonDefaults.buttonColors(
                containerColor = if (state.practicalConfirmed) PlanetBlue else DeepSpaceBlue.copy(alpha = .88f),
                contentColor = if (state.practicalConfirmed) DeepSpaceBlue else PlanetGold
            ),
            shape = confirmationShape,
            modifier = Modifier.fillMaxWidth().heightIn(min = 54.dp).border(1.dp, PlanetGold.copy(alpha = .34f), confirmationShape)
        ) {
            if (state.practicalConfirmed) Icon(Icons.Default.CheckCircle, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text(lessonUiText(if (state.practicalConfirmed) "confirmed" else "confirmAdult", state.appLocale), fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun LessonFeedbackText(correct: Boolean, canTryAgain: Boolean, locale: String, modifier: Modifier = Modifier) {
    Text(
        text = lessonUiText(if (correct) "correct" else if (canTryAgain) "tryAgain" else "tryNext", locale),
        color = if (correct) PlanetGreen else PlanetRed,
        textAlign = TextAlign.Center,
        fontWeight = FontWeight.Bold,
        style = MaterialTheme.typography.titleMedium,
        modifier = modifier
            .fillMaxWidth()
            .background(DeepSpaceBlue.copy(alpha = .72f))
            .padding(horizontal = 12.dp, vertical = 14.dp)
    )
}

@Composable
private fun ParentAidIcon(contentDescription: String, modifier: Modifier = Modifier) {
    val description = contentDescription
    Canvas(modifier = modifier.semantics { this.contentDescription = description }) {
        val adult = Offset(size.width * .34f, size.height * .25f)
        val child = Offset(size.width * .70f, size.height * .43f)
        val stroke = size.minDimension * .075f
        drawCircle(color = PlanetGold, radius = size.minDimension * .12f, center = adult)
        drawCircle(color = PlanetGold, radius = size.minDimension * .09f, center = child)
        drawLine(PlanetGold, Offset(adult.x, size.height * .40f), Offset(adult.x, size.height * .78f), stroke, StrokeCap.Round)
        drawLine(PlanetGold, Offset(child.x, size.height * .55f), Offset(child.x, size.height * .80f), stroke, StrokeCap.Round)
        drawLine(PlanetGold, Offset(adult.x, size.height * .52f), Offset(size.width * .53f, size.height * .62f), stroke, StrokeCap.Round)
        drawLine(PlanetGold, Offset(size.width * .53f, size.height * .62f), Offset(child.x, size.height * .62f), stroke, StrokeCap.Round)
        drawLine(PlanetGold, Offset(adult.x, size.height * .76f), Offset(size.width * .23f, size.height * .96f), stroke, StrokeCap.Round)
        drawLine(PlanetGold, Offset(adult.x, size.height * .76f), Offset(size.width * .45f, size.height * .96f), stroke, StrokeCap.Round)
        drawLine(PlanetGold, Offset(child.x, size.height * .79f), Offset(size.width * .62f, size.height * .96f), stroke, StrokeCap.Round)
        drawLine(PlanetGold, Offset(child.x, size.height * .79f), Offset(size.width * .78f, size.height * .96f), stroke, StrokeCap.Round)
    }
}

@Composable
private fun LessonResultCard(
    result: LessonResultUi,
    stageType: LessonStageTypeV2?,
    locale: String,
    onReplay: () -> Unit,
    onBack: () -> Unit
) {
    val isTheory = stageType in setOf(LessonStageTypeV2.THEORY, LessonStageTypeV2.ACQUIRE_UNDERSTAND)
    val isTest = stageType == LessonStageTypeV2.MASTERY_TEST
    var showAnswerReview by remember(result) { mutableStateOf(false) }
    if (showAnswerReview) {
        LessonAnswerReviewScreen(
            answers = result.answerReviews,
            locale = locale,
            onBack = { showAnswerReview = false },
            onReturnToLesson = onBack
        )
        return
    }
    Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        Surface(color = Color.White.copy(alpha = .10f), shape = RoundedCornerShape(24.dp), modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(28.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Icon(
                    if (result.requiresRetry) Icons.Default.Refresh else Icons.Default.CheckCircle,
                    contentDescription = null,
                    tint = if (result.requiresRetry) PlanetRed else PlanetGold,
                    modifier = Modifier.size(72.dp)
                )
                Text(
                    lessonUiText(
                        when {
                            isTheory && result.requiresRetry -> "theoryRetry"
                            isTheory -> "theoryFinished"
                            isTest -> "testFinished"
                            else -> "exercisesFinished"
                        },
                        locale
                    ),
                    color = StarWhite,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center
                )
                if (isTheory) {
                    Text(
                        lessonUiText(if (result.requiresRetry) "theoryRetryBody" else "theoryFinishedBody", locale),
                        color = StarWhite.copy(alpha = .78f),
                        style = MaterialTheme.typography.bodyLarge,
                        textAlign = TextAlign.Center
                    )
                } else if (isTest) {
                    Text(
                        "${result.correctCount}/${result.questionCount}",
                        color = PlanetGold,
                        style = MaterialTheme.typography.displayMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(lessonUiText("correctAnswers", locale), color = StarWhite.copy(alpha = .78f))
                } else {
                    Text(
                        lessonUiText("exercisesFinishedBody", locale),
                        color = StarWhite.copy(alpha = .78f),
                        style = MaterialTheme.typography.bodyLarge,
                        textAlign = TextAlign.Center
                    )
                }
                if (!isTheory && result.questionCount > 0) {
                    Button(
                        onClick = { showAnswerReview = true },
                        modifier = Modifier.fillMaxWidth().height(54.dp),
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = PlanetGold,
                            contentColor = DeepSpaceBlue
                        )
                    ) {
                        Text(lessonUiText("reviewAnswers", locale), fontWeight = FontWeight.Bold)
                    }
                }
                Button(
                    onClick = onBack,
                    modifier = Modifier.fillMaxWidth().height(54.dp),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = PlanetGold, contentColor = DeepSpaceBlue)
                ) {
                    Text(lessonUiText("backToLessons", locale), fontWeight = FontWeight.Bold)
                }
                TextButton(
                    onClick = onReplay,
                    colors = ButtonDefaults.textButtonColors(contentColor = StarWhite)
                ) {
                    Icon(Icons.Default.Refresh, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text(
                        lessonUiText(
                            when {
                                isTheory && result.requiresRetry -> "retryTheory"
                                isTheory -> "viewAgain"
                                else -> "replay"
                            },
                            locale
                        ),
                        color = StarWhite
                    )
                }
            }
        }
    }
}

@Composable
private fun LessonAnswerReviewScreen(
    answers: List<LessonAnswerReview>,
    locale: String,
    onBack: () -> Unit,
    onReturnToLesson: () -> Unit
) {
    Column(Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(
                    Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = lessonUiText("back", locale),
                    tint = StarWhite
                )
            }
            Text(
                lessonUiText("answerReview", locale),
                color = StarWhite,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f),
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.size(48.dp))
        }
        if (answers.isEmpty()) {
            Column(Modifier.fillMaxSize()) {
                Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                    Text(
                        lessonUiText("reviewUnavailable", locale),
                        color = StarWhite.copy(alpha = .72f),
                        textAlign = TextAlign.Center
                    )
                }
                Button(
                    onClick = onReturnToLesson,
                    modifier = Modifier.fillMaxWidth().height(54.dp),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Text(lessonUiText("backToLesson", locale), fontWeight = FontWeight.Bold)
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(answers, key = { "${it.exerciseId}-${it.questionNumber}" }) { answer ->
                    Surface(
                        color = Color.White.copy(alpha = .08f),
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(16.dp),
                            verticalAlignment = Alignment.Top
                        ) {
                            if (answer.correct) {
                                Icon(
                                    imageVector = Icons.Default.CheckCircle,
                                    contentDescription = null,
                                    tint = PlanetGreen,
                                    modifier = Modifier.size(28.dp)
                                )
                            } else {
                                Text(
                                    text = "✕",
                                    color = PlanetRed,
                                    style = MaterialTheme.typography.headlineSmall,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.width(28.dp),
                                    textAlign = TextAlign.Center
                                )
                            }
                            Spacer(Modifier.width(12.dp))
                            Column(
                                modifier = Modifier.weight(1f),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Text(
                                    "${answer.questionNumber}. ${answer.question}",
                                    color = StarWhite,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    "${lessonUiText("yourAnswer", locale)} ${answer.givenAnswer}",
                                    color = if (answer.correct) PlanetGreen else PlanetRed
                                )
                                if (!answer.correct) {
                                    Text(
                                        "${lessonUiText("correctAnswer", locale)} ${answer.correctAnswer}",
                                        color = PlanetGreen
                                    )
                                }
                            }
                        }
                    }
                }
                item(key = "return-to-lesson") {
                    Button(
                        onClick = onReturnToLesson,
                        modifier = Modifier.fillMaxWidth().height(54.dp),
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        Text(lessonUiText("backToLesson", locale), fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

internal data class ClockMoment(val hour: Int, val minute: Int)

internal fun clockMomentsFor(exercise: ExerciseContent): List<ClockMoment> {
    val kind = exercise.data["promptKind"].orEmpty()
    val values = exercise.data["values"]
        ?.split(',')
        ?.mapNotNull { it.toIntOrNull() }
        .orEmpty()
    return when (kind) {
        "hour_after", "hour_before" ->
            values.getOrNull(0)?.let { listOf(ClockMoment(it, 0)) }.orEmpty()
        "half_hour_duration" ->
            if (values.size >= 4) listOf(
                ClockMoment(values[0], values[1]),
                ClockMoment(values[2], values[3])
            ) else emptyList()
        "hour_duration" ->
            if (values.size >= 2) listOf(
                ClockMoment(values[0], 0),
                ClockMoment(values[1], 0)
            ) else emptyList()
        "duration" ->
            if (values.size >= 4) listOf(
                ClockMoment(values[0], values[1]),
                ClockMoment(values[2], values[3])
            ) else emptyList()
        "minutes_after" ->
            values.getOrNull(0)?.let { listOf(ClockMoment(10, it)) }.orEmpty()
        else -> emptyList()
    }
}

@Composable
private fun ClockExerciseVisual(moments: List<ClockMoment>) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically
    ) {
        moments.forEach { moment ->
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                AnalogClock(moment)
                Text(
                    "%02d:%02d".format(moment.hour, moment.minute),
                    color = PlanetGold,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Composable
private fun AnalogClock(moment: ClockMoment) {
    Canvas(modifier = Modifier.size(112.dp)) {
        val center = Offset(size.width / 2f, size.height / 2f)
        val radius = size.minDimension * .43f
        drawCircle(
            color = StarWhite.copy(alpha = .90f),
            radius = radius,
            center = center,
            style = androidx.compose.ui.graphics.drawscope.Stroke(width = 3.dp.toPx())
        )
        repeat(12) { index ->
            val angle = (index * 30.0 - 90.0) * PI / 180.0
            val outer = Offset(
                center.x + cos(angle).toFloat() * radius,
                center.y + sin(angle).toFloat() * radius
            )
            val innerRadius = radius * if (index % 3 == 0) .82f else .88f
            val inner = Offset(
                center.x + cos(angle).toFloat() * innerRadius,
                center.y + sin(angle).toFloat() * innerRadius
            )
            drawLine(
                color = StarWhite.copy(alpha = .82f),
                start = inner,
                end = outer,
                strokeWidth = if (index % 3 == 0) 3.dp.toPx() else 1.5.dp.toPx(),
                cap = StrokeCap.Round
            )
        }
        val minuteAngle = (moment.minute * 6.0 - 90.0) * PI / 180.0
        val hourAngle = (((moment.hour % 12) * 30.0) + moment.minute * .5 - 90.0) * PI / 180.0
        drawLine(
            color = PlanetGold,
            start = center,
            end = Offset(
                center.x + cos(minuteAngle).toFloat() * radius * .72f,
                center.y + sin(minuteAngle).toFloat() * radius * .72f
            ),
            strokeWidth = 3.dp.toPx(),
            cap = StrokeCap.Round
        )
        drawLine(
            color = StarWhite,
            start = center,
            end = Offset(
                center.x + cos(hourAngle).toFloat() * radius * .52f,
                center.y + sin(hourAngle).toFloat() * radius * .52f
            ),
            strokeWidth = 5.dp.toPx(),
            cap = StrokeCap.Round
        )
        drawCircle(color = PlanetGold, radius = 4.dp.toPx(), center = center)
    }
}

@Composable
private fun LessonError(message: String, locale: String, onBack: () -> Unit) {
    Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        Text(lessonUiText("cannotOpen", locale), color = PlanetGold, style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(12.dp))
        Text(message, color = StarWhite.copy(alpha = .75f), textAlign = TextAlign.Center)
        Spacer(Modifier.height(20.dp))
        Button(onClick = onBack) { Text(lessonUiText("back", locale)) }
    }
}

private val NUMBER_PAD_ROWS = listOf(
    listOf("1", "2", "3"),
    listOf("4", "5", "6"),
    listOf("7", "8", "9"),
    listOf("⌫", "0", "✓")
)

internal fun lessonUiText(key: String, locale: String): String {
    val language = locale.substringBefore('-').substringBefore('_').lowercase()
    val translations = when (language) {
        "fr" -> mapOf(
            "back" to "Retour", "lessons" to "Leçons", "lesson" to "Leçon", "grade" to "Année",
            "noLessons" to "Aucune leçon disponible pour les années sélectionnées.", "of" to "sur", "completed" to "terminées",
            "parentHelp" to "Aide d'un adulte", "best" to "Meilleur score", "done" to "Terminée", "resume" to "Continuer",
            "start" to "Commencer", "locked" to "Verrouillée", "retry" to "Réessayer", "previous" to "Précédente", "continue" to "Continuer", "explanation" to "Explication",
            "listen" to "Écouter", "correct" to "Bien joué !", "tryAgain" to "Essaie encore.", "tryNext" to "Regarde la correction et continue.",
            "practical" to "Activité pratique", "adultVerification" to "Fais cette activité avec un adulte. Elle n'est pas notée automatiquement.",
            "confirmAdult" to "Nous l'avons fait ensemble", "confirmed" to "Confirmé", "lessonFinished" to "Leçon terminée",
            "correctAnswers" to "réponses correctes", "backToLessons" to "Retour aux leçons", "backToLesson" to "Retour à la leçon", "replay" to "Redémarrer la leçon",
            "reviewAnswers" to "Voir les résultats", "answerReview" to "Résultats détaillés",
            "yourAnswer" to "Ta réponse :", "correctAnswer" to "Bonne réponse :",
            "reviewUnavailable" to "Le détail des réponses n'est pas disponible pour cette ancienne tentative.",
            "cannotOpen" to "Impossible d'ouvrir cette leçon", "extraPractice" to "Exercices supplémentaires",
            "fourStages" to "3 parties", "phases" to "parties",
            "lessonOverview" to "Aperçu de la leçon", "sharedTheory" to "Comprendre",
            "exercises" to "exercices", "viewResult" to "Voir le résultat", "startLesson" to "Commencer la leçon",
            "series" to "Série", "summaryTest" to "Test récapitulatif", "correctShort" to "correct",
            "minimumShort" to "min.", "theoryFinished" to "Théorie terminée !",
            "theoryFinishedBody" to "Tu connais maintenant l'explication et les exemples. Tu peux commencer les exercices.",
            "theoryRetry" to "Reprends la théorie", "theoryRetryBody" to "Une réponse était incorrecte. Relis la théorie et réponds correctement à toutes les questions.",
            "retryTheory" to "Recommencer la théorie",
            "exercisesFinished" to "Exercices terminés !", "exercisesFinishedBody" to "Ta progression est enregistrée. Tu peux continuer avec la série suivante.",
            "testFinished" to "Test terminé", "viewAgain" to "Revoir",
            "startExtraPractice" to "Commencer les exercices",
            "chooseCountingIcon" to "Choisis une image pour compter.",
            "openLetterPractice" to "Ouvrir l'exercice d'écriture",
            "extraPracticeDescription" to "Série libre supplémentaire, non obligatoire",
            "practiceOnlyDescription" to "Cette tuile contient pour l'instant des exercices supplémentaires. Les leçons structurées seront ajoutées ici."
        )
        "en" -> mapOf(
            "back" to "Back", "lessons" to "Lessons", "lesson" to "Lesson", "grade" to "Grade",
            "noLessons" to "No lessons are available for the selected grades.", "of" to "of", "completed" to "completed",
            "parentHelp" to "Adult help", "best" to "Best", "done" to "Completed", "resume" to "Resume",
            "start" to "Start", "locked" to "Locked", "retry" to "Try again", "previous" to "Previous", "continue" to "Continue", "explanation" to "Explanation",
            "listen" to "Listen", "correct" to "Great job!", "tryAgain" to "Try once more.", "tryNext" to "Review the answer and continue.",
            "practical" to "Practical activity", "adultVerification" to "Do this activity with an adult. It is not scored automatically.",
            "confirmAdult" to "We did this together", "confirmed" to "Confirmed", "lessonFinished" to "Lesson complete",
            "correctAnswers" to "correct answers", "backToLessons" to "Back to lessons", "backToLesson" to "Back to the lesson", "replay" to "Restart lesson",
            "reviewAnswers" to "View results", "answerReview" to "Answer review",
            "yourAnswer" to "Your answer:", "correctAnswer" to "Correct answer:",
            "reviewUnavailable" to "Answer details are unavailable for this older attempt.",
            "cannotOpen" to "This lesson cannot be opened", "extraPractice" to "Extra practice",
            "fourStages" to "3 parts", "phases" to "parts",
            "lessonOverview" to "Lesson overview", "sharedTheory" to "Understand",
            "exercises" to "exercises", "viewResult" to "View result", "startLesson" to "Start lesson",
            "series" to "Series", "summaryTest" to "Summary test", "correctShort" to "correct",
            "minimumShort" to "min.", "theoryFinished" to "Theory completed!",
            "theoryFinishedBody" to "You now know the explanation and examples. You can start the exercises.",
            "theoryRetry" to "Review the theory again", "theoryRetryBody" to "One answer was incorrect. Review the theory and answer every question correctly.",
            "retryTheory" to "Restart theory",
            "exercisesFinished" to "Exercises completed!", "exercisesFinishedBody" to "Your progress is saved. You can continue with the next set.",
            "testFinished" to "Test completed", "viewAgain" to "View again",
            "startExtraPractice" to "Start extra practice",
            "chooseCountingIcon" to "Choose a picture to count.",
            "openLetterPractice" to "Open writing practice",
            "extraPracticeDescription" to "Optional free practice set",
            "practiceOnlyDescription" to "This tile currently contains extra practice. Structured lessons will be added here."
        )
        else -> mapOf(
            "back" to "Terug", "lessons" to "Lessen", "lesson" to "Les", "grade" to "Leerjaar",
            "noLessons" to "Er zijn geen lessen voor de gekozen leerjaren.", "of" to "van", "completed" to "lessen voltooid",
            "parentHelp" to "Hulp van een volwassene", "best" to "Beste score", "done" to "Voltooid", "resume" to "Verdergaan",
            "start" to "Start", "locked" to "Vergrendeld", "retry" to "Probeer opnieuw", "previous" to "Vorige", "continue" to "Ga verder", "explanation" to "Uitleg",
            "listen" to "Luister", "correct" to "Goed gedaan!", "tryAgain" to "Probeer nog eens.", "tryNext" to "Bekijk het antwoord en ga verder.",
            "practical" to "Praktische opdracht", "adultVerification" to "Doe deze opdracht samen met een volwassene. Ze krijgt geen automatische score.",
            "confirmAdult" to "We hebben dit samen gedaan", "confirmed" to "Bevestigd", "lessonFinished" to "Les voltooid",
            "correctAnswers" to "juiste antwoorden", "backToLessons" to "Terug naar de lessen", "backToLesson" to "Terug naar de les", "replay" to "Les herstarten",
            "reviewAnswers" to "Bekijk uitslag", "answerReview" to "Overzicht van de uitslag",
            "yourAnswer" to "Jouw antwoord:", "correctAnswer" to "Juiste antwoord:",
            "reviewUnavailable" to "De antwoorddetails zijn voor deze oudere poging niet beschikbaar.",
            "cannotOpen" to "Deze les kan niet worden geopend", "extraPractice" to "Extra oefeningen",
            "fourStages" to "3 delen", "phases" to "delen",
            "lessonOverview" to "Lesoverzicht", "sharedTheory" to "Begrijp",
            "exercises" to "oefeningen", "viewResult" to "Bekijk resultaat", "startLesson" to "Start de les",
            "series" to "Reeks", "summaryTest" to "Samenvattende toets", "correctShort" to "goed",
            "minimumShort" to "min.", "theoryFinished" to "Theorie afgerond!",
            "theoryFinishedBody" to "Je kent nu de uitleg en voorbeelden. Je kunt aan de oefeningen beginnen.",
            "theoryRetry" to "Herhaal de theorie", "theoryRetryBody" to "Eén antwoord was fout. Bekijk de theorie opnieuw en beantwoord alle vragen juist.",
            "retryTheory" to "Theorie herstarten",
            "exercisesFinished" to "Oefeningen afgerond!", "exercisesFinishedBody" to "Je voortgang is bewaard. Je kunt verder met de volgende reeks.",
            "testFinished" to "Toets afgerond", "viewAgain" to "Bekijk opnieuw",
            "startExtraPractice" to "Start extra oefeningen",
            "chooseCountingIcon" to "Kies een figuur om mee te tellen.",
            "openLetterPractice" to "Open schrijfoefening",
            "extraPracticeDescription" to "Vrije extra oefenreeks, niet verplicht",
            "practiceOnlyDescription" to "Deze tegel bevat voorlopig extra oefeningen. De gestructureerde lessen worden hier nog toegevoegd."
        )
    }
    return translations[key] ?: key
}
