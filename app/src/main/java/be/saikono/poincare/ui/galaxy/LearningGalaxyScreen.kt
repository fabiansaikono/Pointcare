package be.saikono.poincare.ui.galaxy

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas as AndroidCanvas
import android.util.LruCache
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import be.saikono.poincare.R
import be.saikono.poincare.data.story.StoryBook
import be.saikono.poincare.domain.curriculum.v2.LessonStageTypeV2
import be.saikono.poincare.domain.curriculum.v2.isExercises
import be.saikono.poincare.domain.curriculum.v2.isTheory
import be.saikono.poincare.model.LearningDomain
import be.saikono.poincare.ui.components.systemNavigationMargin
import be.saikono.poincare.ui.components.MainCategoryHeader
import be.saikono.poincare.ui.components.NextLessonOption
import be.saikono.poincare.ui.components.NextLessonOverviewDialog
import be.saikono.poincare.ui.home.SUBCATEGORY_SCREEN_TOP_PADDING_DP
import be.saikono.poincare.ui.lesson.LessonIntroUiState
import be.saikono.poincare.ui.lesson.LessonPhasePreviewUi
import be.saikono.poincare.ui.lesson.LessonViewModel
import be.saikono.poincare.ui.spaceMap.ProfileHeader
import be.saikono.poincare.ui.spaceMap.LevelProgressSummary
import be.saikono.poincare.ui.spaceMap.SpaceBackground
import be.saikono.poincare.ui.spaceMap.SpaceMapViewModel
import be.saikono.poincare.ui.theme.CockpitGray
import be.saikono.poincare.ui.theme.DeepSpaceBlue
import be.saikono.poincare.ui.theme.NebulaPurple
import be.saikono.poincare.ui.theme.PlanetBlue
import be.saikono.poincare.ui.theme.PlanetGold
import be.saikono.poincare.ui.theme.PlanetGreen
import be.saikono.poincare.ui.theme.PlanetRed
import be.saikono.poincare.ui.theme.StarWhite
import be.saikono.poincare.ui.theme.PoincareTheme
import com.caverock.androidsvg.SVG
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext

internal const val MIN_GALAXY_SCALE = 0.42f

internal fun galaxyMinimumScale(fittedScale: Float): Float =
    minOf(MIN_GALAXY_SCALE, fittedScale.coerceIn(0.01f, 1f))

private const val GALAXY_BITMAP_CACHE_KB = 16 * 1024
private const val SHOW_GALAXY_RESET_CONTROL = false
private val galaxyBitmapCache = object : LruCache<String, Bitmap>(GALAXY_BITMAP_CACHE_KB) {
    override fun sizeOf(key: String, value: Bitmap): Int = value.allocationByteCount / 1024
}
private val galaxyBitmapDecodeSemaphore = Semaphore(permits = 2)

@Composable
fun LearningGalaxyScreen(
    spaceMapViewModel: SpaceMapViewModel,
    lessonViewModel: LessonViewModel,
    onSwitchToTiles: () -> Unit,
    onNavigateToParent: () -> Unit,
    onNavigateToTasks: () -> Unit,
    onSwitchProfile: () -> Unit,
    onOpenLesson: (String, String?) -> Unit,
    onOpenStoryLibrary: () -> Unit,
    onOpenStoryBook: (String) -> Unit,
    storyBooks: List<StoryBook> = emptyList(),
    onOpenExistingCategory: (LearningDomain) -> Unit
) {
    val spaceMapState by spaceMapViewModel.uiState.collectAsState()
    val introState by lessonViewModel.introState.collectAsState()
    val profile = spaceMapState.currentProfile
    val locale = profile?.appLanguage ?: "nl-BE"
    val galaxy = remember(spaceMapState.learningGalaxy, storyBooks, locale) {
        spaceMapState.learningGalaxy.withStoryBooks(storyBooks, locale)
    }
    val isLandscape = LocalConfiguration.current.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE
    var selectedCategoryId by rememberSaveable { mutableStateOf<String?>(null) }
    var selectedNodeId by rememberSaveable { mutableStateOf<String?>(null) }
    val selectedCategory = galaxy.categories.firstOrNull { it.id == selectedCategoryId }
    val selectedLessonAndOrb = selectedCategory
        ?.tracks
        ?.asSequence()
        ?.flatMap { it.lessons.asSequence() }
        ?.mapNotNull { lesson ->
            galaxyOrbNodes(lesson)
                .firstOrNull { node -> node.nodeId == selectedNodeId }
                ?.let { node -> lesson to node.orb }
        }
        ?.firstOrNull()
    val selectedLesson = selectedLessonAndOrb?.first
    val selectedOrb = selectedLessonAndOrb?.second
    val nextLessons = selectedCategory?.nextLessonCandidates().orEmpty()
    val selectGalaxyNode: (GalaxyLessonNodeUi, GalaxyPhaseOrbUi) -> Unit = { lesson, orb ->
        when (lesson.portalDestination) {
            GalaxyPortalDestination.STORY_LIBRARY -> onOpenStoryLibrary()
            GalaxyPortalDestination.STORY_BOOK ->
                lesson.portalTargetId?.let(onOpenStoryBook) ?: onOpenStoryLibrary()
            null -> {
                selectedNodeId = galaxyOrbNodeId(lesson, orb)
                if (introState.lessonId != lesson.id) lessonViewModel.showLessonIntro(lesson.id)
            }
        }
    }
    val selectDisplayedOrb: (GalaxyPhaseOrbUi) -> Unit = { orb ->
        selectedLesson?.let { lesson -> selectedNodeId = galaxyOrbNodeId(lesson, orb) }
    }

    BackHandler(enabled = selectedCategory != null) {
        selectedCategoryId = null
        selectedNodeId = null
    }
    LaunchedEffect(selectedCategoryId) {
        selectedNodeId = null
    }

    Box(modifier = Modifier.fillMaxSize().background(DeepSpaceBlue)) {
        SpaceBackground()
        Column(
            modifier = Modifier
                .fillMaxSize()
                .widthIn(max = 800.dp)
                .align(Alignment.TopCenter)
                .systemNavigationMargin(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            if (selectedCategory == null) {
                ProfileHeader(
                    profile = profile,
                    onSwitchProfile = onSwitchProfile,
                    onNavigateToParent = onNavigateToParent,
                    onNavigateToTasks = onNavigateToTasks,
                    onSwitchInterface = onSwitchToTiles,
                    switchInterfaceToTiles = true,
                    switchInterfaceDescription = localizedGalaxyString(
                        LocalContext.current,
                        R.string.galaxy_open_tiles,
                        locale
                    ),
                    locale = locale,
                    landscapeLevelProgress = if (isLandscape) spaceMapState.overallProgress else null,
                    availableGrades = spaceMapState.availableGrades,
                    selectedGrade = spaceMapState.selectedGrade,
                    onGradeSelected = spaceMapViewModel::selectGrade
                )
                if (!isLandscape) {
                    BoxWithConstraints(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 6.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        val logoSize = if (maxHeight < 620.dp) 56.dp else 64.dp
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Image(
                                painter = painterResource(id = R.drawable.square_logo),
                                contentDescription = "Poincaré Logo",
                                modifier = Modifier.size(logoSize)
                            )
                            LevelProgressSummary(
                                progress = spaceMapState.overallProgress,
                                locale = locale,
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }
            }

            if (galaxy.isLoading || spaceMapState.isLoading) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = PlanetGold)
                }
            } else if (selectedCategory == null) {
                UniverseOverview(
                    categories = galaxy.categories,
                    locale = locale,
                    modifier = Modifier.fillMaxSize(),
                    onCategorySelected = { category ->
                        if (category.tracks.isEmpty()) onOpenExistingCategory(category.domain)
                        else selectedCategoryId = category.id
                    }
                )
            } else {
                CategoryFocusHeader(
                    category = selectedCategory,
                    locale = locale,
                    nextLessons = nextLessons,
                    onLessonSelected = { lesson ->
                        selectGalaxyNode(lesson, preferredGalaxyOrb(lesson))
                    },
                    onBack = {
                        selectedCategoryId = null
                        selectedNodeId = null
                    }
                )
                if (isLandscape) {
                    Row(modifier = Modifier.fillMaxWidth().weight(1f)) {
                        CategoryGalaxyCanvas(
                            category = selectedCategory,
                            selectedNodeId = selectedNodeId,
                            locale = locale,
                            onLessonSelected = selectGalaxyNode,
                            onClearSelection = { selectedNodeId = null },
                            modifier = Modifier.weight(1f).fillMaxHeight()
                        )
                        GalaxySelectionSupport(
                            selectedLesson = selectedLesson,
                            selectedOrb = selectedOrb,
                            introState = introState,
                            category = selectedCategory,
                            locale = locale,
                            onOrbSelected = selectDisplayedOrb,
                            onContinue = {
                                selectedLesson?.let { lesson -> onOpenLesson(lesson.id, selectedOrb?.stageId) }
                            },
                            landscape = true,
                            modifier = Modifier.widthIn(min = 280.dp, max = 360.dp).fillMaxHeight()
                        )
                    }
                } else {
                    CategoryGalaxyCanvas(
                        category = selectedCategory,
                        selectedNodeId = selectedNodeId,
                        locale = locale,
                        onLessonSelected = selectGalaxyNode,
                        onClearSelection = { selectedNodeId = null },
                        modifier = Modifier.fillMaxWidth().weight(1f)
                    )
                    GalaxySelectionSupport(
                        selectedLesson = selectedLesson,
                        selectedOrb = selectedOrb,
                        introState = introState,
                        category = selectedCategory,
                        locale = locale,
                        onOrbSelected = selectDisplayedOrb,
                        onContinue = {
                            selectedLesson?.let { lesson -> onOpenLesson(lesson.id, selectedOrb?.stageId) }
                        },
                        landscape = false,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }
    }
}

private fun galaxyPhaseOrbs(lesson: GalaxyLessonNodeUi): List<GalaxyPhaseOrbUi> =
    lesson.phaseOrbs.ifEmpty {
        galaxyOrbKinds(lesson).map { kind ->
            GalaxyPhaseOrbUi(
                kind = kind,
                state = lesson.state,
                progressPercent = lesson.progressPercent
            )
        }
    }

private fun preferredGalaxyOrb(lesson: GalaxyLessonNodeUi): GalaxyPhaseOrbUi {
    val orbs = galaxyPhaseOrbs(lesson)
    return orbs.firstOrNull { it.state == GalaxyNodeState.IN_PROGRESS }
        ?: orbs.firstOrNull { it.state == GalaxyNodeState.RECOMMENDED }
        ?: orbs.firstOrNull { it.state == GalaxyNodeState.AVAILABLE }
        ?: orbs.firstOrNull { it.kind == GalaxyOrbKind.THEORY }
        ?: orbs.first()
}

@Composable
private fun GalaxySelectionSupport(
    selectedLesson: GalaxyLessonNodeUi?,
    selectedOrb: GalaxyPhaseOrbUi?,
    introState: LessonIntroUiState,
    category: GalaxyCategoryUi,
    locale: String,
    onOrbSelected: (GalaxyPhaseOrbUi) -> Unit,
    onContinue: () -> Unit,
    landscape: Boolean,
    modifier: Modifier = Modifier
) {
    if (selectedLesson != null) {
        SelectedLessonPanel(
            lesson = selectedLesson,
            selectedOrb = selectedOrb ?: preferredGalaxyOrb(selectedLesson),
            introState = introState.takeIf { it.lessonId == selectedLesson.id },
            accentColor = galaxyAccentColor(category.domain),
            locale = locale,
            onOrbSelected = onOrbSelected,
            onContinue = onContinue,
            fillHeight = landscape,
            modifier = modifier
        )
    }
}

@Composable
private fun UniverseOverview(
    categories: List<GalaxyCategoryUi>,
    locale: String,
    modifier: Modifier = Modifier,
    onCategorySelected: (GalaxyCategoryUi) -> Unit
) {
    val context = LocalContext.current
    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        BoxWithConstraints(modifier = Modifier.fillMaxWidth().weight(1f).padding(12.dp)) {
            val layout = remember(categories.map { it.id }, maxWidth, maxHeight) {
                GalaxyLayoutEngine.layoutUniverse(categories.map { it.id }, maxWidth.value, maxHeight.value)
            }
            Canvas(modifier = Modifier.fillMaxSize()) {
                categories.forEach { category ->
                    val target = layout.categoryCentres[category.id] ?: return@forEach
                    val centre = Offset(target.x.dp.toPx(), target.y.dp.toPx())
                    val accent = galaxyAccentColor(category.domain)
                    drawCircle(
                        color = accent.copy(alpha = 0.07f),
                        radius = 42.dp.toPx(),
                        center = centre
                    )
                    drawCircle(
                        color = accent.copy(alpha = 0.24f),
                        radius = 42.dp.toPx(),
                        center = centre,
                        style = Stroke(width = 1.dp.toPx())
                    )
                }
                categories.forEach { category ->
                    val target = layout.categoryCentres[category.id] ?: return@forEach
                    val startCentre = Offset(layout.centre.x.dp.toPx(), layout.centre.y.dp.toPx())
                    val end = Offset(target.x.dp.toPx(), target.y.dp.toPx())
                    val centreDelta = end - startCentre
                    val centreDistance = centreDelta.getDistance().coerceAtLeast(1f)
                    val start = startCentre + centreDelta * (35.dp.toPx() / centreDistance)
                    val delta = end - start
                    val distance = delta.getDistance().coerceAtLeast(1f)
                    val normal = Offset(-delta.y / distance, delta.x / distance)
                    val direction = if (category.id.hashCode() and 1 == 0) 1f else -1f
                    val bend = minOf(54.dp.toPx(), distance * 0.16f) * direction
                    val path = Path().apply {
                        moveTo(start.x, start.y)
                        cubicTo(
                            start.x + delta.x * 0.32f + normal.x * bend,
                            start.y + delta.y * 0.32f + normal.y * bend,
                            start.x + delta.x * 0.68f + normal.x * bend,
                            start.y + delta.y * 0.68f + normal.y * bend,
                            end.x,
                            end.y
                        )
                    }
                    drawPath(
                        path = path,
                        color = galaxyAccentColor(category.domain).copy(alpha = 0.42f),
                        style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round)
                    )
                }
            }
            PlayerOrb(
                description = localizedGalaxyString(context, R.string.galaxy_player_orb, locale),
                modifier = Modifier
                    .offset(x = (layout.centre.x - 42f).dp, y = (layout.centre.y - 42f).dp)
                    .size(84.dp)
            )
            categories.forEach { category ->
                val point = layout.categoryCentres[category.id] ?: return@forEach
                CategoryOrb(
                    category = category,
                    locale = locale,
                    onClick = { onCategorySelected(category) },
                    modifier = Modifier.offset(
                        x = (point.x - 54f).dp,
                        y = (point.y - 36f).dp
                    )
                )
            }
        }
    }
}

@Composable
private fun PlayerOrb(description: String, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier.semantics { contentDescription = description }) {
        drawCircle(color = PlanetBlue.copy(alpha = 0.16f), radius = size.minDimension * 0.48f)
        drawCircle(
            color = PlanetBlue,
            radius = size.minDimension * 0.42f,
            style = Stroke(width = 4.dp.toPx())
        )
    }
}

@Composable
private fun CategoryOrb(
    category: GalaxyCategoryUi,
    locale: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val accent = galaxyAccentColor(category.domain)
    Column(
        modifier = modifier.width(108.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Box(
            modifier = Modifier
                .size(72.dp)
                .semantics {
                    role = Role.Button
                    contentDescription = localizedGalaxyString(
                        context,
                        R.string.galaxy_category_description,
                        locale,
                        category.title,
                        category.progressPercent
                    )
                }
                .clip(CircleShape)
                .background(DeepSpaceBlue.copy(alpha = 0.94f))
                .border(1.5.dp, accent.copy(alpha = 0.82f), CircleShape)
                .clickable(onClick = onClick),
            contentAlignment = Alignment.Center
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                drawArc(
                    color = accent,
                    startAngle = -90f,
                    sweepAngle = category.progressPercent * 3.6f,
                    useCenter = false,
                    style = Stroke(width = 4.dp.toPx(), cap = StrokeCap.Round)
                )
            }
            Text(
                text = "${category.progressPercent}%",
                color = StarWhite,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold
            )
        }
        Text(
            text = category.title,
            color = accent,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            maxLines = 2
        )
    }
}

@Composable
private fun CategoryFocusHeader(
    category: GalaxyCategoryUi,
    locale: String,
    nextLessons: List<GalaxyLessonNodeUi>,
    onLessonSelected: (GalaxyLessonNodeUi) -> Unit,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    var showNextLessonOverview by remember { mutableStateOf(false) }
    if (showNextLessonOverview) {
        NextLessonOverviewDialog(
            lessons = nextLessons.map { lesson ->
                NextLessonOption(
                    id = lesson.id,
                    title = lesson.title,
                    subcategoryTitle = lesson.subcategoryTitle,
                    status = galaxyNodeStateLabel(context, lesson.state, locale)
                )
            },
            locale = locale,
            onLessonSelected = { lessonId ->
                showNextLessonOverview = false
                nextLessons.firstOrNull { it.id == lessonId }?.let(onLessonSelected)
            },
            onDismiss = { showNextLessonOverview = false }
        )
    }
    MainCategoryHeader(
        title = category.title,
        progressPercent = category.progressPercent,
        locale = locale,
        onBack = onBack,
        onNextLesson = if (nextLessons.isNotEmpty()) {
            { showNextLessonOverview = true }
        } else {
            null
        },
        modifier = Modifier.padding(
            start = 24.dp,
            top = SUBCATEGORY_SCREEN_TOP_PADDING_DP.dp,
            end = 24.dp
        )
    )
}

@Composable
private fun CategoryGalaxyCanvas(
    category: GalaxyCategoryUi,
    selectedNodeId: String?,
    locale: String,
    onLessonSelected: (GalaxyLessonNodeUi, GalaxyPhaseOrbUi) -> Unit,
    onClearSelection: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val density = LocalDensity.current
    val layout = remember(category) {
        GalaxyLayoutEngine.layoutCategory(category.tracks, category.checkpoints)
    }
    val lessonsById = remember(category) {
        category.tracks.flatMap { it.lessons }.associateBy { it.id }
    }
    val orbsByNodeId = remember(category) {
        buildMap {
            category.tracks.flatMap { it.lessons }.forEach { lesson ->
                galaxyOrbNodes(lesson).forEach { node ->
                    put(node.nodeId, node.orb)
                }
            }
        }
    }
    val checkpointsById = remember(category) { category.checkpoints.associateBy { it.id } }
    val allConnections = remember(layout) {
        (layout.startConnections + layout.connections + layout.checkpointConnections).distinct()
    }
    val lockedDependencyConnections = remember(selectedNodeId, orbsByNodeId, allConnections) {
        if (selectedNodeId != null && orbsByNodeId[selectedNodeId]?.state == GalaxyNodeState.LOCKED) {
            galaxyDependencyConnections(selectedNodeId, allConnections)
        } else {
            emptySet()
        }
    }
    BoxWithConstraints(modifier = modifier.clipToBounds().background(DeepSpaceBlue.copy(alpha = 0.36f))) {
        val viewportWidthPx = with(density) { maxWidth.toPx() }
        val viewportHeightPx = with(density) { maxHeight.toPx() }
        val contentWidthPx = with(density) { layout.width.dp.toPx() }
        val contentHeightPx = with(density) { layout.height.dp.toPx() }
        val fittedScale = minOf(
            viewportWidthPx / contentWidthPx,
            viewportHeightPx / contentHeightPx
        ).coerceAtMost(1f)
        val minimumScale = galaxyMinimumScale(fittedScale)
        val startPointPx = Offset(
            with(density) { layout.startPoint.x.dp.toPx() },
            with(density) { layout.startPoint.y.dp.toPx() }
        )
        val fittedOffset = Offset(
            x = viewportWidthPx / 2f - startPointPx.x * fittedScale,
            y = viewportHeightPx / 2f - startPointPx.y * fittedScale
        )
        var scale by remember(category.id) { mutableFloatStateOf(fittedScale) }
        var panOffset by remember(category.id) { mutableStateOf(fittedOffset) }

        fun boundedOffset(candidate: Offset, requestedScale: Float): Offset {
            val scaledWidth = contentWidthPx * requestedScale
            val scaledHeight = contentHeightPx * requestedScale
            val margin = with(density) { 36.dp.toPx() }
            val minX = minOf(margin, viewportWidthPx - scaledWidth - margin)
            val maxX = maxOf(-margin, viewportWidthPx - scaledWidth + margin)
            val minY = minOf(margin, viewportHeightPx - scaledHeight - margin)
            val maxY = maxOf(-margin, viewportHeightPx - scaledHeight + margin)
            return Offset(candidate.x.coerceIn(minX, maxX), candidate.y.coerceIn(minY, maxY))
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .clickable(onClick = onClearSelection)
                .pointerInput(category.id, viewportWidthPx, viewportHeightPx) {
                    detectTransformGestures { centroid, pan, zoom, _ ->
                        val previousScale = scale
                        val nextScale = (scale * zoom).coerceIn(minimumScale, 2.15f)
                        val zoomRatio = nextScale / previousScale
                        val zoomedOffset = (panOffset - centroid) * zoomRatio + centroid + pan
                        scale = nextScale
                        panOffset = boundedOffset(zoomedOffset, nextScale)
                    }
                }
        ) {
            Box(
                modifier = Modifier
                    .size(layout.width.dp, layout.height.dp)
                    .graphicsLayer {
                        translationX = panOffset.x
                        translationY = panOffset.y
                        scaleX = scale
                        scaleY = scale
                        transformOrigin = androidx.compose.ui.graphics.TransformOrigin(0f, 0f)
                    }
            ) {
                Canvas(
                    modifier = Modifier
                        .fillMaxSize()
                        .semantics {
                            contentDescription = category.tracks.joinToString { it.title }
                        }
                ) {
                    val positions = buildMap {
                        layout.nodes.forEach { put(it.nodeId, it.point) }
                        layout.checkpointNodes.forEach { put(it.checkpointId, it.point) }
                        put(GALAXY_START_NODE_ID, layout.startPoint)
                    }
                    layout.trackSpheres.forEach { sphere ->
                        val centre = Offset(sphere.centre.x.dp.toPx(), sphere.centre.y.dp.toPx())
                        drawCircle(
                            color = galaxyAccentColor(category.domain).copy(alpha = 0.035f),
                            radius = sphere.radius.dp.toPx(),
                            center = centre
                        )
                        drawCircle(
                            color = galaxyAccentColor(category.domain).copy(alpha = 0.13f),
                            radius = sphere.radius.dp.toPx(),
                            center = centre,
                            style = Stroke(width = 1.dp.toPx())
                        )
                    }
                    val accent = galaxyAccentColor(category.domain)
                    layout.nodes
                        .groupBy { it.lessonId }
                        .forEach { (_, lessonNodes) ->
                            val outline = galaxyLessonGroupOutline(
                                nodePoints = lessonNodes.map { it.point },
                                orbPadding = 38f
                            )
                            if (outline.size < 3) return@forEach
                            val groupPath = Path().apply {
                                val first = outline.first()
                                moveTo(first.x.dp.toPx(), first.y.dp.toPx())
                                outline.drop(1).forEach { point ->
                                    lineTo(point.x.dp.toPx(), point.y.dp.toPx())
                                }
                                close()
                            }
                            drawPath(
                                path = groupPath,
                                color = accent.copy(alpha = 0.26f),
                                style = Stroke(width = 1.dp.toPx())
                            )
                        }
                    fun drawConnection(connection: GalaxyConnection) {
                        val from = positions[connection.fromLessonId] ?: return
                        val to = positions[connection.toLessonId] ?: return
                        val destinationState = orbsByNodeId[connection.toLessonId]?.state
                            ?: checkpointsById[connection.toLessonId]?.state
                            ?: GalaxyNodeState.AVAILABLE
                        val lineColor = when (destinationState) {
                            GalaxyNodeState.COMPLETED, GalaxyNodeState.MASTERED -> PlanetGold.copy(alpha = 0.8f)
                            GalaxyNodeState.AVAILABLE, GalaxyNodeState.RECOMMENDED, GalaxyNodeState.IN_PROGRESS ->
                                PlanetBlue.copy(alpha = 0.48f)
                            GalaxyNodeState.LOCKED -> StarWhite.copy(alpha = 0.13f)
                        }
                        val start = Offset(from.x.dp.toPx(), from.y.dp.toPx())
                        val end = Offset(to.x.dp.toPx(), to.y.dp.toPx())
                        val delta = end - start
                        val distance = delta.getDistance().coerceAtLeast(1f)
                        val normal = Offset(-delta.y / distance, delta.x / distance)
                        val direction = if ((connection.fromLessonId + connection.toLessonId).hashCode() and 1 == 0) 1f else -1f
                        val bend = minOf(72.dp.toPx(), distance * 0.18f) * direction
                        val path = Path().apply {
                            moveTo(start.x, start.y)
                            cubicTo(
                                start.x + delta.x * 0.32f + normal.x * bend,
                                start.y + delta.y * 0.32f + normal.y * bend,
                                start.x + delta.x * 0.68f + normal.x * bend,
                                start.y + delta.y * 0.68f + normal.y * bend,
                                end.x,
                                end.y
                            )
                        }
                        val highlighted = connection in lockedDependencyConnections
                        drawPath(
                            path = path,
                            color = if (highlighted) StarWhite.copy(alpha = 0.96f) else lineColor,
                            style = Stroke(
                                width = if (highlighted) 5.dp.toPx() else 2.dp.toPx(),
                                cap = StrokeCap.Round
                            )
                        )
                    }
                    allConnections
                        .sortedBy { it in lockedDependencyConnections }
                        .forEach(::drawConnection)
                }
                StartOrb(
                    locale = locale,
                    modifier = Modifier.offset(
                        x = (layout.startPoint.x - 42f).dp,
                        y = (layout.startPoint.y - 42f).dp
                    )
                )
                layout.nodes.forEach { nodePosition ->
                    val lesson = lessonsById[nodePosition.lessonId] ?: return@forEach
                    val orb = orbsByNodeId[nodePosition.nodeId] ?: return@forEach
                    val pointPx = Offset(
                        with(density) { nodePosition.point.x.dp.toPx() },
                        with(density) { nodePosition.point.y.dp.toPx() }
                    )
                    val screenPoint = pointPx * scale + panOffset
                    val visibilityMargin = with(density) { 96.dp.toPx() }
                    val isNearViewport = screenPoint.x in -visibilityMargin..(viewportWidthPx + visibilityMargin) &&
                        screenPoint.y in -visibilityMargin..(viewportHeightPx + visibilityMargin)
                    if (!isNearViewport) return@forEach
                    LessonOrb(
                        lesson = lesson,
                        orb = orb,
                        selected = nodePosition.nodeId == selectedNodeId,
                        locale = locale,
                        onClick = { onLessonSelected(lesson, orb) },
                        modifier = Modifier.offset(
                            x = (nodePosition.point.x - 36f).dp,
                            y = (nodePosition.point.y - 36f).dp
                        )
                    )
                    val isLessonLabelAnchor = galaxyOrbNodes(lesson).firstOrNull()?.nodeId == nodePosition.nodeId
                    if (
                        scale >= 1.12f &&
                        isLessonLabelAnchor &&
                        nodePosition.kind in setOf(GalaxyOrbKind.THEORY, GalaxyOrbKind.PORTAL)
                    ) {
                        Text(
                            text = lesson.title,
                            color = StarWhite.copy(alpha = 0.88f),
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.SemiBold,
                            textAlign = TextAlign.Center,
                            maxLines = 3,
                            modifier = Modifier
                                .offset(
                                    x = (nodePosition.point.x - 70f).dp,
                                    y = (nodePosition.point.y + 42f).dp
                                )
                                .width(140.dp)
                                .background(DeepSpaceBlue.copy(alpha = 0.84f), RoundedCornerShape(8.dp))
                                .padding(horizontal = 4.dp, vertical = 2.dp)
                        )
                    }
                }
                layout.checkpointNodes.forEach { checkpointPosition ->
                    val checkpoint = checkpointsById[checkpointPosition.checkpointId] ?: return@forEach
                    CheckpointOrb(
                        checkpoint = checkpoint,
                        locale = locale,
                        modifier = Modifier.offset(
                            x = (checkpointPosition.point.x - 38f).dp,
                            y = (checkpointPosition.point.y - 38f).dp
                        )
                    )
                }
                // Labels are intentionally emitted last so they stay above lines,
                // orbs and lesson titles while zooming.
                layout.trackLabels.forEach { labelPosition ->
                    val track = category.tracks.firstOrNull { it.id == labelPosition.trackId } ?: return@forEach
                    val readableScale = if (scale < 1f) 1f / scale else 1f
                    Text(
                        text = track.title,
                        color = StarWhite.copy(alpha = 0.75f),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        maxLines = 3,
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .offset(
                                x = (labelPosition.point.x - 90f).dp,
                                y = (labelPosition.point.y - 32f).dp
                            )
                            .width(180.dp)
                            .graphicsLayer {
                                scaleX = readableScale
                                scaleY = readableScale
                            }
                    )
                }
            }
        }
        if (SHOW_GALAXY_RESET_CONTROL) {
            IconButton(
                onClick = {
                    scale = fittedScale
                    panOffset = fittedOffset
                },
                modifier = Modifier.align(Alignment.TopEnd).padding(8.dp)
            ) {
                Icon(
                    Icons.Default.Refresh,
                    contentDescription = localizedGalaxyString(context, R.string.galaxy_reset_view, locale),
                    tint = StarWhite.copy(alpha = 0.72f)
                )
            }
        }
    }
}

@Composable
private fun StartOrb(locale: String, modifier: Modifier = Modifier) {
    val label = when {
        locale.startsWith("fr", ignoreCase = true) -> "Départ"
        locale.startsWith("en", ignoreCase = true) -> "Start"
        else -> "Start"
    }
    Box(
        modifier = modifier
            .size(84.dp)
            .semantics { contentDescription = label },
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            drawCircle(color = PlanetBlue.copy(alpha = 0.16f), radius = size.minDimension * 0.48f)
            drawCircle(
                color = PlanetBlue,
                radius = size.minDimension * 0.42f,
                style = Stroke(width = 4.dp.toPx())
            )
        }
    }
}

@Composable
private fun CheckpointOrb(
    checkpoint: GalaxyCheckpointNodeUi,
    locale: String,
    modifier: Modifier = Modifier
) {
    val isComplete = checkpoint.state == GalaxyNodeState.COMPLETED ||
        checkpoint.state == GalaxyNodeState.MASTERED
    val label = when {
        locale.startsWith("fr", ignoreCase = true) -> "Point de contrôle"
        locale.startsWith("en", ignoreCase = true) -> "Checkpoint"
        else -> "Checkpoint"
    }
    Box(
        modifier = modifier
            .size(76.dp)
            .semantics {
                contentDescription = "$label, ${checkpoint.progressPercent}%"
            },
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val radius = size.minDimension * 0.4f
            drawCircle(
                color = if (isComplete) PlanetGold.copy(alpha = 0.17f) else CockpitGray.copy(alpha = 0.9f),
                radius = radius
            )
            drawCircle(
                color = if (isComplete) PlanetGold else StarWhite.copy(alpha = 0.35f),
                radius = radius,
                style = Stroke(width = if (isComplete) 4.dp.toPx() else 2.dp.toPx())
            )
            if (!isComplete && checkpoint.progressPercent > 0) {
                drawArc(
                    color = PlanetBlue,
                    startAngle = -90f,
                    sweepAngle = checkpoint.progressPercent * 3.6f,
                    useCenter = false,
                    style = Stroke(width = 5.dp.toPx(), cap = StrokeCap.Round)
                )
            }
        }
        Icon(
            imageVector = if (isComplete) Icons.Default.Check else Icons.Default.Lock,
            contentDescription = null,
            tint = if (isComplete) PlanetGold else StarWhite.copy(alpha = 0.82f),
            modifier = Modifier.size(24.dp)
        )
        Text(
            text = label,
            color = if (isComplete) PlanetGold else StarWhite.copy(alpha = 0.86f),
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.offset(y = 47.dp)
        )
    }
}

@Composable
private fun LessonOrb(
    lesson: GalaxyLessonNodeUi,
    orb: GalaxyPhaseOrbUi,
    selected: Boolean,
    locale: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val selectionScale by animateFloatAsState(if (selected) 1.12f else 1f, label = "galaxy-orb-selection")
    val stateLabel = galaxyNodeStateLabel(context, orb.state, locale)
    val kindLabel = galaxyOrbKindLabel(orb.kind, locale)
    Box(
        modifier = modifier
            .size(72.dp)
            .graphicsLayer { scaleX = selectionScale; scaleY = selectionScale }
            .semantics {
                role = Role.Button
                this.selected = selected
                contentDescription = "$kindLabel. " + localizedGalaxyString(
                    context,
                    R.string.galaxy_lesson_description,
                    locale,
                    lesson.title,
                    lesson.subcategoryTitle,
                    lesson.grade,
                    stateLabel,
                    orb.progressPercent
                )
            }
            .clickable(
                onClickLabel = if (orb.kind == GalaxyOrbKind.PORTAL) kindLabel else localizedGalaxyString(
                    context,
                    R.string.galaxy_view_lesson_details,
                    locale
                ),
                onClick = onClick
            ),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.size(64.dp)) {
            val radius = size.minDimension * 0.42f
            val fill = when (orb.state) {
                GalaxyNodeState.LOCKED -> CockpitGray.copy(alpha = 0.82f)
                GalaxyNodeState.COMPLETED, GalaxyNodeState.MASTERED -> PlanetGold.copy(alpha = 0.16f)
                else -> DeepSpaceBlue.copy(alpha = 0.94f)
            }
            drawCircle(color = fill, radius = radius)
            when (orb.state) {
                GalaxyNodeState.LOCKED -> drawCircle(
                    color = StarWhite.copy(alpha = 0.34f),
                    radius = radius,
                    style = Stroke(width = 1.dp.toPx())
                )
                GalaxyNodeState.AVAILABLE, GalaxyNodeState.RECOMMENDED -> drawCircle(
                    color = PlanetBlue,
                    radius = radius,
                    style = Stroke(width = 1.5.dp.toPx())
                )
                GalaxyNodeState.IN_PROGRESS -> drawCircle(
                    color = PlanetBlue,
                    radius = radius,
                    style = Stroke(width = 1.5.dp.toPx())
                )
                GalaxyNodeState.COMPLETED, GalaxyNodeState.MASTERED -> drawCircle(
                    color = PlanetGold,
                    radius = radius,
                    style = Stroke(width = 4.dp.toPx())
                )
            }
            if (selected) {
                drawCircle(
                    color = PlanetGold.copy(alpha = 0.92f),
                    radius = radius + 5.dp.toPx(),
                    style = Stroke(width = 2.dp.toPx())
                )
            }
            if (orb.state == GalaxyNodeState.IN_PROGRESS && orb.progressPercent in 1..99) {
                drawArc(
                    color = PlanetBlue,
                    startAngle = -90f,
                    sweepAngle = orb.progressPercent * 3.6f,
                    useCenter = false,
                    style = Stroke(width = 5.dp.toPx(), cap = StrokeCap.Round)
                )
            }
        }
        if (orb.kind == GalaxyOrbKind.PORTAL) {
            GalaxyLessonIcon(
                assetPath = null,
                fallbackIconResId = lesson.fallbackIconResId,
                contentDescription = null,
                modifier = Modifier.size(34.dp).alpha(if (orb.state == GalaxyNodeState.LOCKED) 0.34f else 0.94f)
            )
        } else {
            GalaxyOrbKindIcon(
                kind = orb.kind,
                tint = StarWhite.copy(alpha = if (orb.state == GalaxyNodeState.LOCKED) 0.34f else 0.94f),
                modifier = Modifier.size(34.dp)
            )
        }
        when (orb.state) {
            GalaxyNodeState.LOCKED -> Icon(
                Icons.Default.Lock,
                contentDescription = null,
                tint = StarWhite.copy(alpha = 0.82f),
                modifier = Modifier.align(Alignment.BottomEnd).size(20.dp).background(CockpitGray, CircleShape).padding(3.dp)
            )
            GalaxyNodeState.COMPLETED, GalaxyNodeState.MASTERED -> Icon(
                Icons.Default.Check,
                contentDescription = null,
                tint = DeepSpaceBlue,
                modifier = Modifier.align(Alignment.BottomEnd).size(20.dp).background(
                    PlanetGold,
                    CircleShape
                ).padding(3.dp)
            )
            else -> Unit
        }
    }
}

@Composable
private fun GalaxyOrbKindIcon(
    kind: GalaxyOrbKind,
    tint: Color,
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier) {
        val stroke = Stroke(width = 2.2.dp.toPx(), cap = StrokeCap.Round)
        when (kind) {
            GalaxyOrbKind.THEORY -> {
                val leftPage = Path().apply {
                    moveTo(size.width * 0.12f, size.height * 0.22f)
                    lineTo(size.width * 0.43f, size.height * 0.29f)
                    lineTo(size.width * 0.49f, size.height * 0.78f)
                    lineTo(size.width * 0.18f, size.height * 0.68f)
                    close()
                }
                val rightPage = Path().apply {
                    moveTo(size.width * 0.88f, size.height * 0.22f)
                    lineTo(size.width * 0.57f, size.height * 0.29f)
                    lineTo(size.width * 0.51f, size.height * 0.78f)
                    lineTo(size.width * 0.82f, size.height * 0.68f)
                    close()
                }
                drawPath(leftPage, tint, style = stroke)
                drawPath(rightPage, tint, style = stroke)
            }
            GalaxyOrbKind.PRACTICE -> {
                drawLine(
                    tint,
                    Offset(size.width * 0.25f, size.height * 0.75f),
                    Offset(size.width * 0.75f, size.height * 0.25f),
                    strokeWidth = 5.dp.toPx(),
                    cap = StrokeCap.Round
                )
                drawLine(
                    tint,
                    Offset(size.width * 0.20f, size.height * 0.82f),
                    Offset(size.width * 0.33f, size.height * 0.76f),
                    strokeWidth = 2.dp.toPx(),
                    cap = StrokeCap.Round
                )
                drawLine(
                    tint,
                    Offset(size.width * 0.68f, size.height * 0.21f),
                    Offset(size.width * 0.79f, size.height * 0.32f),
                    strokeWidth = 2.dp.toPx(),
                    cap = StrokeCap.Round
                )
            }
            GalaxyOrbKind.TEST -> {
                drawRoundRect(
                    color = tint,
                    topLeft = Offset(size.width * 0.18f, size.height * 0.17f),
                    size = androidx.compose.ui.geometry.Size(size.width * 0.64f, size.height * 0.68f),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(size.width * 0.08f),
                    style = stroke
                )
                drawLine(
                    tint,
                    Offset(size.width * 0.31f, size.height * 0.53f),
                    Offset(size.width * 0.44f, size.height * 0.66f),
                    strokeWidth = 2.4.dp.toPx(),
                    cap = StrokeCap.Round
                )
                drawLine(
                    tint,
                    Offset(size.width * 0.44f, size.height * 0.66f),
                    Offset(size.width * 0.69f, size.height * 0.38f),
                    strokeWidth = 2.4.dp.toPx(),
                    cap = StrokeCap.Round
                )
            }
            GalaxyOrbKind.PORTAL -> {
                drawCircle(
                    color = tint,
                    radius = size.minDimension * 0.32f,
                    center = Offset(size.width * 0.48f, size.height * 0.53f),
                    style = stroke
                )
                drawLine(
                    tint,
                    Offset(size.width * 0.20f, size.height * 0.53f),
                    Offset(size.width * 0.76f, size.height * 0.53f),
                    strokeWidth = 2.dp.toPx(),
                    cap = StrokeCap.Round
                )
                drawCircle(
                    color = PlanetGold,
                    radius = size.minDimension * 0.08f,
                    center = Offset(size.width * 0.78f, size.height * 0.20f)
                )
            }
        }
    }
}

private fun galaxyOrbKindLabel(kind: GalaxyOrbKind, locale: String): String {
    val language = locale.substringBefore('-').substringBefore('_').lowercase()
    return when (kind) {
        GalaxyOrbKind.THEORY -> when (language) { "fr" -> "Théorie"; "en" -> "Theory"; else -> "Theorie" }
        GalaxyOrbKind.PRACTICE -> when (language) { "fr" -> "Exercices"; "en" -> "Practice"; else -> "Oefen" }
        GalaxyOrbKind.TEST -> when (language) { "fr" -> "Test"; "en" -> "Test"; else -> "Toets" }
        GalaxyOrbKind.PORTAL -> when (language) { "fr" -> "Histoires"; "en" -> "Stories"; else -> "Verhalen" }
    }
}

@Composable
private fun SelectedLessonPanel(
    lesson: GalaxyLessonNodeUi,
    selectedOrb: GalaxyPhaseOrbUi,
    introState: LessonIntroUiState?,
    accentColor: Color,
    locale: String,
    onOrbSelected: (GalaxyPhaseOrbUi) -> Unit,
    onContinue: () -> Unit,
    fillHeight: Boolean,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val phases = introState?.phases.orEmpty()
    val progressPercent = selectedOrb.progressPercent
    val locked = selectedOrb.state == GalaxyNodeState.LOCKED
    val panelModifier = if (fillHeight) {
        modifier.fillMaxHeight()
    } else {
        modifier.heightIn(max = 260.dp)
    }
    Surface(
        color = CockpitGray.copy(alpha = 0.94f),
        border = BorderStroke(2.dp, accentColor.copy(alpha = 0.82f)),
        shape = if (fillHeight) {
            RoundedCornerShape(topStart = 24.dp, bottomStart = 24.dp)
        } else {
            RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
        },
        modifier = panelModifier
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 14.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                GalaxyLessonIcon(
                    assetPath = lesson.iconAssetPath,
                    fallbackIconResId = lesson.fallbackIconResId,
                    contentDescription = null,
                    modifier = Modifier.size(42.dp)
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = lesson.title,
                        color = StarWhite,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        maxLines = 2
                    )
                }
                Text(
                    text = "$progressPercent%",
                    color = PlanetGold,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }
            if (locked && lesson.unmetPrerequisiteTitles.isNotEmpty()) {
                val visiblePrerequisites = lesson.unmetPrerequisiteTitles.take(2).joinToString(", ")
                val remainingCount = (lesson.unmetPrerequisiteTitles.size - 2).coerceAtLeast(0)
                Text(
                    text = localizedGalaxyString(
                        context,
                        R.string.galaxy_prerequisites,
                        locale,
                        if (remainingCount == 0) visiblePrerequisites else "$visiblePrerequisites +$remainingCount"
                    ),
                    color = StarWhite.copy(alpha = 0.72f),
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
            Spacer(Modifier.height(8.dp))
            if (introState == null || introState.isLoading) {
                CircularProgressIndicator(
                    color = accentColor,
                    strokeWidth = 2.dp,
                    modifier = Modifier.size(24.dp).align(Alignment.CenterHorizontally)
                )
            } else {
                LessonPhaseRail(
                    phases = phases,
                    phaseOrbs = galaxyPhaseOrbs(lesson),
                    selectedKind = selectedOrb.kind,
                    locale = locale,
                    accentColor = accentColor,
                    onOrbSelected = onOrbSelected
                )
            }
            Spacer(Modifier.height(10.dp))
            Button(
                onClick = onContinue,
                enabled = !locked,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                contentPadding = PaddingValues(vertical = 10.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = accentColor,
                    contentColor = DeepSpaceBlue,
                    disabledContainerColor = StarWhite.copy(alpha = 0.10f),
                    disabledContentColor = StarWhite.copy(alpha = 0.45f)
                )
            ) {
                Text(
                    text = when {
                        locked -> localizedGalaxyString(context, R.string.galaxy_locked_action, locale)
                        progressPercent > 0 -> localizedGalaxyString(context, R.string.galaxy_continue, locale)
                        else -> localizedGalaxyString(context, R.string.galaxy_start, locale)
                    },
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Composable
private fun LessonPhaseRail(
    phases: List<LessonPhasePreviewUi>,
    phaseOrbs: List<GalaxyPhaseOrbUi>,
    selectedKind: GalaxyOrbKind,
    locale: String,
    accentColor: Color,
    onOrbSelected: (GalaxyPhaseOrbUi) -> Unit
) {
    val context = LocalContext.current
    val scrollState = rememberScrollState()
    val parts = listOf(
        Triple(
            localizedGalaxyString(context, R.string.galaxy_theory, locale),
            GalaxyOrbKind.THEORY,
            phases.filter { it.type.isTheory() }
        ),
        Triple(
            localizedGalaxyString(context, R.string.galaxy_exercises, locale),
            GalaxyOrbKind.PRACTICE,
            phases.filter { it.type.isExercises() }
        ),
        Triple(
            localizedGalaxyString(context, R.string.galaxy_test, locale),
            GalaxyOrbKind.TEST,
            phases.filter { it.type == LessonStageTypeV2.MASTERY_TEST }
        )
    ).filter { (_, _, partPhases) -> partPhases.isNotEmpty() }
    Row(
        modifier = Modifier.fillMaxWidth().horizontalScroll(scrollState),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        parts.forEach { (label, kind, partPhases) ->
            val orb = phaseOrbs.firstOrNull { it.kind == kind }
            LessonPartPill(
                phases = partPhases,
                label = label,
                orbState = orb?.state,
                selected = selectedKind == kind,
                accentColor = accentColor,
                locale = locale,
                onClick = orb?.let { selectedOrb -> { onOrbSelected(selectedOrb) } }
            )
        }
    }
}

@Composable
private fun LessonPartPill(
    phases: List<LessonPhasePreviewUi>,
    label: String,
    orbState: GalaxyNodeState?,
    selected: Boolean,
    accentColor: Color,
    locale: String,
    onClick: (() -> Unit)?
) {
    val context = LocalContext.current
    val effectiveState = orbState ?: when {
        phases.all { it.completed } -> GalaxyNodeState.COMPLETED
        phases.any { it.available } -> GalaxyNodeState.AVAILABLE
        else -> GalaxyNodeState.LOCKED
    }
    val completed = effectiveState == GalaxyNodeState.COMPLETED || effectiveState == GalaxyNodeState.MASTERED
    val available = effectiveState != GalaxyNodeState.LOCKED
    val isCurrent = effectiveState == GalaxyNodeState.IN_PROGRESS
    val status = galaxyNodeStateLabel(context, effectiveState, locale)
    val interactionModifier = onClick?.let { action -> Modifier.clickable(onClick = action) } ?: Modifier
    Column(
        modifier = Modifier
            .width(104.dp)
            .height(76.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(
                when {
                    selected -> accentColor.copy(alpha = 0.30f)
                    completed -> accentColor.copy(alpha = 0.24f)
                    isCurrent -> accentColor.copy(alpha = 0.14f)
                    else -> DeepSpaceBlue.copy(alpha = 0.72f)
                }
            )
            .border(
                if (selected) 2.dp else 1.dp,
                when {
                    selected -> PlanetGold.copy(alpha = 0.92f)
                    available -> accentColor.copy(alpha = 0.62f)
                    else -> StarWhite.copy(alpha = 0.10f)
                },
                RoundedCornerShape(12.dp)
            )
            .then(interactionModifier)
            .semantics { this.selected = selected }
            .padding(horizontal = 10.dp, vertical = 5.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, color = StarWhite, style = MaterialTheme.typography.labelMedium, maxLines = 1)
        Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
            phases.forEach { phase ->
                Box(
                    Modifier.size(6.dp).background(
                        when {
                            phase.completed -> accentColor
                            phase.available -> accentColor.copy(alpha = 0.48f)
                            else -> StarWhite.copy(alpha = 0.16f)
                        },
                        CircleShape
                    )
                )
            }
        }
        Text(status, color = if (available) accentColor else StarWhite.copy(alpha = 0.42f), style = MaterialTheme.typography.labelSmall)
    }
}

@Composable
private fun GalaxyLessonIcon(
    assetPath: String?,
    fallbackIconResId: Int,
    contentDescription: String?,
    modifier: Modifier = Modifier
) {
    val bitmap = rememberGalaxyAssetBitmap(assetPath, 128)
    if (bitmap != null) {
        Image(
            bitmap = bitmap,
            contentDescription = contentDescription,
            modifier = modifier,
            contentScale = ContentScale.Fit
        )
    } else {
        Image(
            painter = painterResource(fallbackIconResId),
            contentDescription = contentDescription,
            modifier = modifier,
            contentScale = ContentScale.Fit
        )
    }
}

@Composable
private fun rememberGalaxyAssetBitmap(assetPath: String?, sizePx: Int): ImageBitmap? {
    val context = LocalContext.current
    val cacheKey = assetPath?.let { "$it@$sizePx" }
    val bitmap by produceState<Bitmap?>(
        initialValue = cacheKey?.let(galaxyBitmapCache::get),
        key1 = cacheKey
    ) {
        if (assetPath == null || cacheKey == null || value != null) return@produceState
        value = withContext(Dispatchers.IO) {
            galaxyBitmapDecodeSemaphore.withPermit {
                galaxyBitmapCache.get(cacheKey) ?: runCatching {
                    context.assets.open(assetPath).use { input ->
                        if (assetPath.endsWith(".svg", ignoreCase = true)) {
                            val svg = SVG.getFromInputStream(input)
                            Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888).also { decoded ->
                                svg.setDocumentWidth(sizePx.toFloat())
                                svg.setDocumentHeight(sizePx.toFloat())
                                svg.renderToCanvas(AndroidCanvas(decoded))
                            }
                        } else {
                            BitmapFactory.decodeStream(input)
                        }
                    }?.also { decoded ->
                        galaxyBitmapCache.put(cacheKey, decoded)
                    }
                }.getOrNull()
            }
        }
    }
    return bitmap?.asImageBitmap()
}

private fun galaxyNodeStateLabel(context: android.content.Context, state: GalaxyNodeState, locale: String): String =
    localizedGalaxyString(
        context,
        when (state) {
            GalaxyNodeState.LOCKED -> R.string.galaxy_state_locked
            GalaxyNodeState.AVAILABLE -> R.string.galaxy_state_available
            GalaxyNodeState.RECOMMENDED -> R.string.galaxy_state_recommended
            GalaxyNodeState.IN_PROGRESS -> R.string.galaxy_state_in_progress
            GalaxyNodeState.COMPLETED -> R.string.galaxy_state_completed
            GalaxyNodeState.MASTERED -> R.string.galaxy_state_mastered
        },
        locale
    )

fun galaxyAccentColor(domain: LearningDomain): Color = when (domain) {
    LearningDomain.MATH -> PlanetBlue
    LearningDomain.GEOMETRY -> PlanetGold
    LearningDomain.READING -> Color(0xFF54C6A9)
    LearningDomain.WRITING -> Color(0xFFC59BFF)
    LearningDomain.WORLD_KNOWLEDGE -> Color(0xFFFF8A65)
    LearningDomain.PUZZLE -> Color(0xFF90CAF9)
}

@Preview(name = "Galaxy short track", showBackground = true, widthDp = 390, heightDp = 420)
@Composable
private fun GalaxyShortTrackPreview() {
    PoincareTheme {
        CategoryGalaxyCanvas(
            category = previewGalaxyCategory(listOf(3)),
            selectedNodeId = galaxyOrbNodeId("preview-1-1", GalaxyOrbKind.THEORY),
            locale = "nl-BE",
            onLessonSelected = { _, _ -> },
            onClearSelection = {},
            modifier = Modifier.fillMaxSize().background(DeepSpaceBlue)
        )
    }
}

@Preview(name = "Galaxy multiple tracks", showBackground = true, widthDp = 390, heightDp = 620)
@Composable
private fun GalaxyMultipleTracksPreview() {
    PoincareTheme {
        CategoryGalaxyCanvas(
            category = previewGalaxyCategory(listOf(4, 8, 12)),
            selectedNodeId = galaxyOrbNodeId("preview-2-3", GalaxyOrbKind.THEORY),
            locale = "nl-BE",
            onLessonSelected = { _, _ -> },
            onClearSelection = {},
            modifier = Modifier.fillMaxSize().background(DeepSpaceBlue)
        )
    }
}

@Preview(name = "Galaxy consecutive grades", showBackground = true, widthDp = 700, heightDp = 420)
@Composable
private fun GalaxyConsecutiveGradesPreview() {
    PoincareTheme {
        CategoryGalaxyCanvas(
            category = previewMultiGradeCategory(),
            selectedNodeId = galaxyOrbNodeId("preview-0-1-g2", GalaxyOrbKind.THEORY),
            locale = "nl-BE",
            onLessonSelected = { _, _ -> },
            onClearSelection = {},
            modifier = Modifier.fillMaxSize().background(DeepSpaceBlue)
        )
    }
}

private fun previewMultiGradeCategory(): GalaxyCategoryUi {
    val gradeOne = previewGalaxyCategory(listOf(4, 5))
    return gradeOne.copy(
        tracks = gradeOne.tracks.map { track ->
            val gradeTwoLessons = track.lessons.mapIndexed { index, lesson ->
                lesson.copy(
                    id = "${lesson.id}-g2",
                    title = "Leerjaar 2 · ${lesson.title}",
                    grade = 2,
                    prerequisiteLessonIds = if (index == 0) emptyList() else {
                        listOf("${track.lessons[index - 1].id}-g2")
                    }
                )
            }
            track.copy(lessons = track.lessons + gradeTwoLessons)
        }
    )
}

private fun previewGalaxyCategory(trackSizes: List<Int>): GalaxyCategoryUi = GalaxyCategoryUi(
    id = "preview-math",
    domain = LearningDomain.MATH,
    title = "Wiskunde",
    progressPercent = 42,
    tracks = trackSizes.mapIndexed { trackIndex, count ->
        GalaxyTrackUi(
            id = "preview-track-$trackIndex",
            title = listOf("Getallen", "Optellen", "Meetkunde").getOrElse(trackIndex) { "Traject ${trackIndex + 1}" },
            iconResId = R.drawable.ic_math_sequences_new,
            lessons = (0 until count).map { lessonIndex ->
                GalaxyLessonNodeUi(
                    id = "preview-$trackIndex-$lessonIndex",
                    title = "Voorbeeldles ${lessonIndex + 1}",
                    categoryTitle = "Wiskunde",
                    subcategoryId = "preview-track-$trackIndex",
                    subcategoryTitle = "Voorbeeldtraject",
                    grade = 1,
                    sequence = lessonIndex + 1,
                    fallbackIconResId = R.drawable.ic_math_sequences_new,
                    iconAssetPath = null,
                    prerequisiteLessonIds = if (lessonIndex == 0) emptyList() else listOf("preview-$trackIndex-${lessonIndex - 1}"),
                    unmetPrerequisiteTitles = emptyList(),
                    state = GalaxyNodeState.entries[(trackIndex + lessonIndex) % GalaxyNodeState.entries.size],
                    progressPercent = if (lessonIndex % 3 == 0) 65 else 0
                )
            }
        )
    }
)
