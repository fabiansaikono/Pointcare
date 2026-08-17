package be.saikono.poincare.ui.story

import android.graphics.BitmapFactory
import android.provider.Settings
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import be.saikono.poincare.data.story.Story
import be.saikono.poincare.data.story.StoryPageLayout
import be.saikono.poincare.data.story.StoryReadingPagePlanner
import be.saikono.poincare.data.story.StoryResponsiveLayout
import be.saikono.poincare.data.story.StoryTextPagePart
import be.saikono.poincare.ui.components.systemNavigationMargin
import be.saikono.poincare.ui.spaceMap.SpaceBackground
import be.saikono.poincare.ui.theme.DeepSpaceBlue
import be.saikono.poincare.ui.theme.PlanetBlue
import be.saikono.poincare.ui.theme.PlanetGold
import be.saikono.poincare.ui.theme.PlanetGreen
import be.saikono.poincare.ui.theme.PlanetRed
import be.saikono.poincare.ui.theme.StarWhite

@Composable
fun StoryLibraryScreen(
    viewModel: StoryViewModel,
    onBookSelected: (String) -> Unit,
    onNavigateBack: () -> Unit
) {
    val state by viewModel.uiState.collectAsState()
    var selectedSeriesId by remember(state.locale) { mutableStateOf<String?>(null) }
    val seriesGroups = remember(state.books, state.locale) {
        state.books
            .groupBy { item -> item.book.seriesId ?: item.book.imageStyle ?: "series.kleine-verhaaltjes" }
            .map { (id, books) ->
                val title = books.firstOrNull()?.book?.seriesTitle?.text(state.locale)
                    ?.takeIf(String::isNotBlank)
                    ?: when (books.firstOrNull()?.book?.imageStyle) {
                        "SPACE_TRAVELERS" -> "Nova"
                        "VIDEOGAME" -> "Robbie"
                        else -> "Kleine verhaaltjes"
                    }
                StorySeriesLibraryGroup(
                    id = id,
                    title = title,
                    order = books.minOfOrNull { it.book.seriesOrder ?: Int.MAX_VALUE } ?: Int.MAX_VALUE,
                    books = books
                )
            }
            .sortedBy(StorySeriesLibraryGroup::order)
    }
    val selectedSeries = seriesGroups.firstOrNull { it.id == selectedSeriesId }
    DisposableEffect(Unit) {
        viewModel.refreshLibrary()
        onDispose { viewModel.stopNarration() }
    }
    Box(Modifier.fillMaxSize().background(DeepSpaceBlue)) {
        SpaceBackground()
        Column(
            modifier = Modifier
                .fillMaxSize()
                .systemNavigationMargin()
                .padding(horizontal = 20.dp, vertical = 18.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { if (selectedSeriesId != null) selectedSeriesId = null else onNavigateBack() }) {
                    Icon(
                        Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = storyText("back", state.locale),
                        tint = StarWhite
                    )
                }
                Text(
                    selectedSeries?.title ?: storyText("library", state.locale),
                    color = StarWhite,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold
                )
            }
            StoryLanguagePills(
                selectedLocale = state.locale,
                onLocaleSelected = viewModel::selectStoryLocale,
                modifier = Modifier.padding(start = 12.dp, bottom = 12.dp)
            )
            when {
                state.loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = PlanetGold)
                }
                state.error != null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(state.error.orEmpty(), color = PlanetRed)
                }
                else -> LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    contentPadding = PaddingValues(bottom = 24.dp)
                ) {
                    if (selectedSeries == null) {
                        items(seriesGroups, key = { it.id }) { series ->
                            StorySeriesCard(series, state.locale) { selectedSeriesId = series.id }
                        }
                    } else {
                        items(selectedSeries.books, key = { it.book.id }) { item ->
                            StoryBookCard(item, state.locale) { onBookSelected(item.book.id) }
                        }
                    }
                }
            }
        }
    }
}

private data class StorySeriesLibraryGroup(
    val id: String,
    val title: String,
    val order: Int,
    val books: List<StoryBookLibraryItem>
)

@Composable
private fun StorySeriesCard(group: StorySeriesLibraryGroup, locale: String, onClick: () -> Unit) {
    val draft = group.books.any { it.book.publicationStatus != "ACTIVE" }
    val chapterCount = group.books.sumOf { it.stories.size }
    Surface(
        color = DeepSpaceBlue.copy(alpha = .90f),
        shape = RoundedCornerShape(22.dp),
        tonalElevation = 7.dp,
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier.padding(20.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            group.books.firstOrNull()?.book?.let { book ->
                StoryAssetImage(book.coverAsset, group.title, Modifier.size(92.dp).clip(RoundedCornerShape(16.dp)))
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    text = buildAnnotatedString {
                        if (draft) {
                            pushStyle(SpanStyle(fontStyle = FontStyle.Italic))
                            append("Draft ")
                            pop()
                        }
                        append(group.title)
                    },
                    color = PlanetGold,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    "${group.books.size} boeken · $chapterCount ${storyText("stories", locale)}",
                    color = StarWhite.copy(alpha = .72f)
                )
            }
            Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = group.title, tint = PlanetGold)
        }
    }
}

@Composable
private fun StoryLanguagePills(
    selectedLocale: String,
    onLocaleSelected: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        listOf(
            "nl-BE" to "Vlaams",
            "fr-BE" to "Français",
            "en-US" to "English"
        ).forEach { (locale, label) ->
            val selected = selectedLocale == locale
            Surface(
                color = if (selected) PlanetGold else DeepSpaceBlue.copy(alpha = .72f),
                contentColor = if (selected) DeepSpaceBlue else PlanetGold,
                shape = RoundedCornerShape(50),
                border = BorderStroke(1.dp, PlanetGold.copy(alpha = if (selected) 1f else .55f)),
                modifier = Modifier.clickable { onLocaleSelected(locale) }
            ) {
                Text(
                    text = label,
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.labelLarge,
                    modifier = Modifier.padding(horizontal = 13.dp, vertical = 8.dp)
                )
            }
        }
    }
}

@Composable
private fun StoryBookCard(item: StoryBookLibraryItem, locale: String, onClick: () -> Unit) {
    val translation = item.book.translation(locale)
    val completed = item.stories.count { it.progress.completed }
    val fraction = if (item.stories.isEmpty()) 0f else {
        item.stories.sumOf { story ->
            when {
                story.progress.completed -> 1.0
                !story.progress.started -> 0.0
                else -> (story.progress.pageIndex + 1.0) / story.story.pages.size
            }
        }.toFloat() / item.stories.size
    }
    Surface(
        color = DeepSpaceBlue.copy(alpha = .90f),
        shape = RoundedCornerShape(22.dp),
        tonalElevation = 7.dp,
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick)
    ) {
        BoxWithConstraints {
            val compact = StoryResponsiveLayout.libraryUsesCompactCards(maxWidth.value.toInt())
            if (compact) {
                Column {
                    StoryAssetImage(item.book.coverAsset, translation.title, Modifier.fillMaxWidth().height(210.dp))
                    BookCardDetails(item, locale, completed, fraction, Modifier.padding(16.dp))
                }
            } else {
                Row(Modifier.heightIn(min = 220.dp)) {
                    StoryAssetImage(
                        item.book.coverAsset,
                        translation.title,
                        Modifier.width(230.dp).fillMaxHeight()
                    )
                    BookCardDetails(item, locale, completed, fraction, Modifier.padding(18.dp).weight(1f))
                }
            }
        }
    }
}

@Composable
private fun BookCardDetails(
    item: StoryBookLibraryItem,
    locale: String,
    completed: Int,
    fraction: Float,
    modifier: Modifier
) {
    val book = item.book
    val translation = book.translation(locale)
    Column(modifier, verticalArrangement = Arrangement.spacedBy(9.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                translation.title,
                color = StarWhite,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f)
            )
            if (completed == item.stories.size) {
                Icon(Icons.Default.CheckCircle, contentDescription = storyText("completed", locale), tint = PlanetGreen)
            }
        }
        Text(translation.description, color = StarWhite.copy(alpha = .8f))
        book.bookNumber?.let { number ->
            Text(
                text = "Boek $number${if (book.publicationStatus != "ACTIVE") " · Draft" else ""}",
                color = StarWhite.copy(alpha = .62f),
                style = MaterialTheme.typography.labelMedium,
                fontStyle = if (book.publicationStatus != "ACTIVE") FontStyle.Italic else FontStyle.Normal
            )
        }
        Text(
            book.aviLevel?.takeIf(String::isNotBlank)?.let { "AVI $it" }
                ?: aviSummary(item.stories.map { it.story.aviLevel }),
            color = PlanetBlue,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "${storyText("grade", locale)} ${book.grade} · ${item.stories.size} ${storyText("stories", locale)}",
                color = PlanetGold,
                modifier = Modifier.weight(1f)
            )
            if (item.stories.all { it.story.hasVoiceOver }) {
                VoiceIcon(
                    modifier = Modifier.size(24.dp).semantics {
                        contentDescription = storyText("audioYes", locale)
                    },
                    color = PlanetGold
                )
            }
        }
        LinearProgressIndicator(
            progress = { fraction.coerceIn(0f, 1f) },
            color = PlanetGold,
            trackColor = Color.White.copy(alpha = .15f),
            modifier = Modifier.fillMaxWidth().height(7.dp).clip(RoundedCornerShape(50))
        )
    }
}

@Composable
fun StoryBookScreen(
    bookId: String,
    viewModel: StoryViewModel,
    onStorySelected: (String) -> Unit,
    onNavigateBack: () -> Unit
) {
    val state by viewModel.uiState.collectAsState()
    val bookItem = state.books.firstOrNull { it.book.id == bookId }
    Box(Modifier.fillMaxSize().background(DeepSpaceBlue)) {
        SpaceBackground()
        Column(
            modifier = Modifier
                .fillMaxSize()
                .systemNavigationMargin()
                .padding(horizontal = 20.dp, vertical = 18.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onNavigateBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, storyText("back", state.locale), tint = StarWhite)
                }
                Text(
                    bookItem?.book?.translation(state.locale)?.title ?: storyText("book", state.locale),
                    color = StarWhite,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold
                )
            }
            when {
                state.loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = PlanetGold)
                }
                bookItem == null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(storyText("bookUnavailable", state.locale), color = PlanetRed)
                }
                else -> LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    contentPadding = PaddingValues(bottom = 24.dp)
                ) {
                    items(bookItem.stories, key = { it.story.id }) { item ->
                        StoryLibraryCard(item, state.locale) { onStorySelected(item.story.id) }
                    }
                }
            }
        }
    }
}

@Composable
private fun StoryLibraryCard(item: StoryLibraryItem, locale: String, onClick: () -> Unit) {
    val translation = item.story.translation(locale)
    Surface(
        color = DeepSpaceBlue.copy(alpha = .90f),
        shape = RoundedCornerShape(22.dp),
        tonalElevation = 7.dp,
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick)
    ) {
        Row(Modifier.heightIn(min = 148.dp)) {
            StoryAssetImage(
                item.story.coverAsset,
                translation.title,
                Modifier.padding(start = 16.dp, top = 16.dp, bottom = 16.dp).size(116.dp)
            )
            StoryCardDetails(item, locale, Modifier.padding(16.dp).weight(1f))
        }
    }
}

@Composable
private fun StoryCardDetails(item: StoryLibraryItem, locale: String, modifier: Modifier) {
    val story = item.story
    val translation = story.translation(locale)
    val fraction = when {
        item.progress.completed -> 1f
        !item.progress.started -> 0f
        else -> (item.progress.pageIndex + 1f) / story.pages.size
    }
    Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(translation.title, color = StarWhite, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Text(translation.description, color = StarWhite.copy(alpha = .8f), maxLines = 2)
        Text(
            "AVI ${story.aviLevel} · ${story.pages.size} ${storyText("pages", locale)} · ${story.estimatedMinutes} min",
            color = PlanetGold
        )
        LinearProgressIndicator(
            progress = { fraction.coerceIn(0f, 1f) },
            color = PlanetGold,
            trackColor = Color.White.copy(alpha = .15f),
            modifier = Modifier.fillMaxWidth().height(7.dp).clip(RoundedCornerShape(50))
        )
        Text(
            when {
                item.progress.completed -> storyText("completed", locale)
                item.progress.started -> storyText("resume", locale)
                else -> storyText("start", locale)
            },
            color = PlanetBlue,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
fun StoryReaderScreen(
    storyId: String,
    viewModel: StoryViewModel,
    onNavigateBack: () -> Unit
) {
    val state by viewModel.uiState.collectAsState()
    DisposableEffect(storyId) {
        viewModel.openStory(storyId)
        onDispose { viewModel.stopNarration() }
    }
    Box(Modifier.fillMaxSize().background(DeepSpaceBlue)) {
        SpaceBackground()
        when {
            state.loading || state.story?.id != storyId -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = PlanetGold)
            }
            state.error != null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(state.error.orEmpty(), color = PlanetRed)
            }
            state.showQuiz -> StoryQuiz(state, viewModel, onNavigateBack)
            else -> StoryPageContent(state, viewModel, onNavigateBack)
        }
        if (state.showReadingModeDialog && state.story?.id == storyId) {
            ReadingModeDialog(
                locale = state.locale,
                voiceAvailable = state.story?.hasVoiceOver == true,
                onReadAlone = { viewModel.selectReadingMode(readWithMe = false) },
                onReadWithMe = { viewModel.selectReadingMode(readWithMe = true) }
            )
        }
    }
}

@Composable
private fun ReadingModeDialog(
    locale: String,
    voiceAvailable: Boolean,
    onReadAlone: () -> Unit,
    onReadWithMe: () -> Unit
) {
    AlertDialog(
        onDismissRequest = {},
        containerColor = DeepSpaceBlue,
        titleContentColor = StarWhite,
        title = {
            Text(storyText("howRead", locale), fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Button(
                    onClick = onReadAlone,
                    colors = ButtonDefaults.buttonColors(containerColor = PlanetBlue, contentColor = DeepSpaceBlue),
                    modifier = Modifier.fillMaxWidth().heightIn(min = 62.dp)
                ) {
                    GlassesIcon(Modifier.size(30.dp))
                    Spacer(Modifier.width(12.dp))
                    Text(storyText("readAlone", locale), fontWeight = FontWeight.Bold)
                }
                Button(
                    onClick = onReadWithMe,
                    enabled = voiceAvailable,
                    colors = ButtonDefaults.buttonColors(containerColor = PlanetGold, contentColor = DeepSpaceBlue),
                    modifier = Modifier.fillMaxWidth().heightIn(min = 62.dp)
                ) {
                    VoiceIcon(Modifier.size(30.dp))
                    Spacer(Modifier.width(12.dp))
                    Text(storyText("readWithMe", locale), fontWeight = FontWeight.Bold)
                }
            }
        },
        confirmButton = {}
    )
}

@Composable
private fun GlassesIcon(modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val radius = size.minDimension * .25f
        val centerY = size.height * .55f
        val left = Offset(size.width * .27f, centerY)
        val right = Offset(size.width * .73f, centerY)
        drawCircle(DeepSpaceBlue, radius, left, style = Stroke(width = size.minDimension * .09f))
        drawCircle(DeepSpaceBlue, radius, right, style = Stroke(width = size.minDimension * .09f))
        drawLine(DeepSpaceBlue, Offset(left.x + radius, centerY), Offset(right.x - radius, centerY), size.minDimension * .08f)
        drawLine(DeepSpaceBlue, Offset(left.x - radius, centerY - radius * .35f), Offset(0f, centerY - radius), size.minDimension * .07f)
        drawLine(DeepSpaceBlue, Offset(right.x + radius, centerY - radius * .35f), Offset(size.width, centerY - radius), size.minDimension * .07f)
    }
}

@Composable
private fun VoiceIcon(modifier: Modifier = Modifier, color: Color = DeepSpaceBlue) {
    Canvas(modifier) {
        val stroke = size.minDimension * .08f
        drawCircle(color, size.minDimension * .12f, Offset(size.width * .28f, size.height * .35f))
        drawLine(color, Offset(size.width * .16f, size.height * .68f), Offset(size.width * .40f, size.height * .68f), stroke)
        drawArc(color, -55f, 110f, false, Offset(size.width * .36f, size.height * .20f), androidx.compose.ui.geometry.Size(size.width * .34f, size.height * .60f), style = Stroke(stroke))
        drawArc(color, -55f, 110f, false, Offset(size.width * .50f, size.height * .10f), androidx.compose.ui.geometry.Size(size.width * .45f, size.height * .80f), style = Stroke(stroke))
    }
}

@Composable
private fun StoryPageContent(state: StoryUiState, viewModel: StoryViewModel, onNavigateBack: () -> Unit) {
    val story = state.story ?: return
    val page = story.page(state.pageIndex)
    val pageText = page.text(state.locale)
    val nextPageHasIllustration = story.pages.getOrNull(state.pageIndex + 1)?.illustrationAsset != null
    val pageParts = remember(pageText, story.grade, nextPageHasIllustration) {
        StoryReadingPagePlanner.paginate(pageText, story.grade, nextPageHasIllustration)
    }
    var pagePartIndex by remember { mutableStateOf(0) }
    var openPreviousAtLastPart by remember { mutableStateOf(false) }
    val safePagePartIndex = pagePartIndex.coerceIn(pageParts.indices)
    val pagePart = pageParts[safePagePartIndex]
    val isFirstDisplayPage = state.pageIndex == 0 && safePagePartIndex == 0
    val isLastDisplayPage = state.pageIndex == story.pages.lastIndex && safePagePartIndex == pageParts.lastIndex
    val pageLabel = buildString {
        append("${state.pageIndex + 1}/${story.pages.size}")
        if (pageParts.size > 1) append(" · ${safePagePartIndex + 1}/${pageParts.size}")
    }

    LaunchedEffect(story.id, state.pageIndex, state.locale, pageParts.size) {
        pagePartIndex = if (openPreviousAtLastPart) pageParts.lastIndex else 0
        openPreviousAtLastPart = false
    }
    LaunchedEffect(state.highlightedWord, state.pageIndex, pageParts) {
        val highlightedStart = state.highlightedWord
            ?.let(state.words::getOrNull)
            ?.start
            ?: return@LaunchedEffect
        pageParts.indexOfLast { highlightedStart >= it.startOffset }
            .takeIf { it >= 0 && it != safePagePartIndex }
            ?.let { pagePartIndex = it }
    }

    fun showPreviousDisplayPage() {
        viewModel.stopNarration()
        if (safePagePartIndex > 0) {
            pagePartIndex = safePagePartIndex - 1
        } else if (state.pageIndex > 0) {
            openPreviousAtLastPart = true
            viewModel.previousPage()
        }
    }

    fun showNextDisplayPage() {
        viewModel.stopNarration()
        if (safePagePartIndex < pageParts.lastIndex) {
            pagePartIndex = safePagePartIndex + 1
        } else {
            if (isLastDisplayPage && story.questions.isEmpty()) {
                viewModel.finishStory()
                onNavigateBack()
            } else {
                viewModel.nextPage()
            }
        }
    }

    var dragDistance = 0f
    Column(
        modifier = Modifier
            .fillMaxSize()
            .systemNavigationMargin()
            .padding(horizontal = 16.dp, vertical = 12.dp)
            .semantics { contentDescription = "${story.translation(state.locale).title}, $pageLabel" }
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { viewModel.stopNarration(); onNavigateBack() }) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = storyText("back", state.locale), tint = StarWhite)
            }
            Column(Modifier.weight(1f)) {
                Text(
                    story.translation(state.locale).title,
                    color = StarWhite,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1
                )
                Text(
                    pageLabel,
                    color = PlanetGold,
                    style = MaterialTheme.typography.labelMedium
                )
            }
            if (story.hasVoiceOver) {
                IconButton(onClick = { viewModel.playOrResumeNarrationFrom(pagePart.startOffset) }) {
                    Icon(
                        Icons.Default.PlayArrow,
                        contentDescription = if (state.isNarrating) storyText("pause", state.locale) else storyText("play", state.locale),
                        tint = PlanetGold
                    )
                }
                IconButton(onClick = { viewModel.replayPageFrom(pagePart.startOffset) }) {
                    Icon(Icons.Default.Refresh, contentDescription = storyText("replay", state.locale), tint = PlanetBlue)
                }
            }
        }
        Box(Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
            LinearProgressIndicator(
                progress = {
                    (state.pageIndex + (safePagePartIndex + 1f) / pageParts.size)
                        .div(story.pages.size)
                        .coerceIn(0f, 1f)
                },
                color = PlanetGold,
                trackColor = Color.White.copy(alpha = .14f),
                modifier = Modifier.fillMaxWidth().height(5.dp)
            )
        }
        page.chapterTitle(state.locale).takeIf { safePagePartIndex == 0 && it.isNotBlank() }?.let { chapter ->
            Text(
                chapter,
                color = PlanetGold,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(top = 10.dp, start = 4.dp)
            )
        }
        BoxWithConstraints(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(vertical = 10.dp)
                .pointerInput(state.pageIndex) {
                    detectHorizontalDragGestures(
                        onDragStart = { dragDistance = 0f },
                        onHorizontalDrag = { _, amount -> dragDistance += amount },
                        onDragEnd = {
                            when {
                                dragDistance < -90f -> showNextDisplayPage()
                                dragDistance > 90f -> showPreviousDisplayPage()
                            }
                        }
                    )
                }
        ) {
            val landscape = StoryResponsiveLayout.pageLayout(
                maxWidth.value.toInt(),
                maxHeight.value.toInt()
            ) == StoryPageLayout.LANDSCAPE_SPLIT
            val illustrationAsset = page.illustrationAsset.takeIf { safePagePartIndex == 0 }
            if (illustrationAsset == null) {
                StoryPageText(state, pagePart, Modifier.fillMaxSize())
            } else if (landscape) {
                val weights = StoryReadingPagePlanner.imageTextWeights(
                    story.grade,
                    pagePart.text.length,
                    landscape = true
                )
                Row(horizontalArrangement = Arrangement.spacedBy(18.dp)) {
                    StoryAssetImage(
                        illustrationAsset,
                        pagePart.text,
                        Modifier.weight(weights.image).fillMaxHeight(),
                        parallax = true
                    )
                    StoryPageText(state, pagePart, Modifier.weight(weights.text).fillMaxHeight())
                }
            } else {
                val weights = StoryReadingPagePlanner.imageTextWeights(
                    story.grade,
                    pagePart.text.length,
                    landscape = false
                )
                Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    StoryAssetImage(
                        illustrationAsset,
                        pagePart.text,
                        Modifier.fillMaxWidth().weight(weights.image),
                        parallax = true
                    )
                    StoryPageText(state, pagePart, Modifier.fillMaxWidth().weight(weights.text))
                }
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (!isFirstDisplayPage) {
                OutlinedButton(
                    onClick = ::showPreviousDisplayPage,
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = StarWhite),
                    border = BorderStroke(1.dp, StarWhite.copy(alpha = .75f)),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
                    Spacer(Modifier.width(6.dp))
                    Text(storyText("previous", state.locale))
                }
            } else {
                Spacer(Modifier.width(1.dp))
            }
            Button(
                onClick = ::showNextDisplayPage,
                colors = ButtonDefaults.buttonColors(containerColor = PlanetGold, contentColor = DeepSpaceBlue),
                shape = RoundedCornerShape(14.dp)
            ) {
                Text(
                    if (isLastDisplayPage) {
                        if (story.questions.isEmpty()) storyText("finish", state.locale) else storyText("questions", state.locale)
                    } else storyText("next", state.locale)
                )
                Spacer(Modifier.width(6.dp))
                Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null)
            }
        }
    }
}

@Composable
private fun StoryPageText(state: StoryUiState, pagePart: StoryTextPagePart, modifier: Modifier) {
    val pageText = pagePart.text
    val highlighted = state.highlightedWord
    val annotated = remember(pageText, pagePart.startOffset, pagePart.endOffset, state.words, highlighted) {
        buildAnnotatedString {
            append(pageText)
            highlighted?.let { index ->
                state.words.getOrNull(index)?.let { word ->
                    if (word.endExclusive > pagePart.startOffset && word.start < pagePart.endOffset) {
                        addStyle(
                            SpanStyle(color = PlanetGold, fontWeight = FontWeight.Bold),
                            (word.start - pagePart.startOffset).coerceIn(0, pageText.length),
                            (word.endExclusive - pagePart.startOffset).coerceIn(0, pageText.length)
                        )
                    }
                }
            }
        }
    }
    Surface(
        color = DeepSpaceBlue.copy(alpha = .90f),
        shape = RoundedCornerShape(20.dp),
        modifier = modifier
    ) {
        Box(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(18.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                annotated,
                color = StarWhite,
                fontSize = if (state.story?.grade == 1) 28.sp else 20.sp,
                lineHeight = if (state.story?.grade == 1) 38.sp else 30.sp,
                textAlign = if (state.story?.grade == 1) TextAlign.Center else TextAlign.Start,
                fontWeight = FontWeight.Normal
            )
        }
    }
}

@Composable
private fun StoryQuiz(state: StoryUiState, viewModel: StoryViewModel, onNavigateBack: () -> Unit) {
    val story = state.story ?: return
    val question = story.questions.getOrNull(state.questionIndex) ?: return
    Column(
        modifier = Modifier
            .fillMaxSize()
            .systemNavigationMargin()
            .padding(22.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onNavigateBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = storyText("back", state.locale), tint = StarWhite)
            }
            Text(
                "${storyText("questions", state.locale)} ${state.questionIndex + 1}/${story.questions.size}",
                color = PlanetGold,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = viewModel::readQuestionAloud)
                .semantics { contentDescription = storyText("readQuestion", state.locale) }
                .padding(vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                question.prompt(state.locale),
                color = StarWhite,
                style = MaterialTheme.typography.headlineSmall,
                modifier = Modifier.weight(1f)
            )
            VoiceIcon(Modifier.size(26.dp), color = PlanetGold)
        }
        question.options(state.locale).forEachIndexed { index, option ->
            val selected = state.selectedAnswerIndex == index
            val previewed = state.previewAnswerIndex == index
            Surface(
                color = if (selected || previewed) PlanetBlue.copy(alpha = .40f) else Color.White.copy(alpha = .08f),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .graphicsLayer {
                        if (previewed) {
                            scaleX = .98f
                            scaleY = .94f
                            translationY = 4.dp.toPx()
                        }
                    }
                    .clickable(enabled = state.selectedAnswerIndex == null) {
                        viewModel.answerQuestion(index)
                    }
            ) {
                Text(
                    option,
                    color = StarWhite,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(18.dp),
                    style = MaterialTheme.typography.titleMedium
                )
            }
        }
        state.selectedAnswerIndex?.let {
            Surface(
                color = if (state.answerCorrect == true) PlanetGreen.copy(alpha = .22f) else PlanetRed.copy(alpha = .22f),
                shape = RoundedCornerShape(16.dp)
            ) {
                Text(
                    question.explanation(state.locale),
                    color = StarWhite,
                    modifier = Modifier.fillMaxWidth().padding(16.dp)
                )
            }
            Button(
                onClick = {
                    val finishesStory = state.questionIndex == story.questions.lastIndex
                    if (finishesStory) {
                        viewModel.finishStory()
                        onNavigateBack()
                    } else {
                        viewModel.nextQuestion()
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = PlanetGold, contentColor = DeepSpaceBlue),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    if (state.questionIndex == story.questions.lastIndex) storyText("finish", state.locale)
                    else storyText("next", state.locale)
                )
            }
        }
    }
}

@Composable
private fun StoryAssetImage(
    assetPath: String,
    description: String,
    modifier: Modifier = Modifier,
    parallax: Boolean = false
) {
    val context = LocalContext.current
    val animationsEnabled = remember(context) {
        runCatching {
            Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) > 0f
        }.getOrDefault(true)
    }
    val movement = rememberInfiniteTransition(label = "story-parallax")
    val shift by movement.animateFloat(
        initialValue = if (parallax && animationsEnabled) -1f else 0f,
        targetValue = if (parallax && animationsEnabled) 1f else 0f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 8_000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "story-parallax-shift"
    )
    val bitmap = remember(assetPath) {
        runCatching {
            context.assets.open(assetPath).use(BitmapFactory::decodeStream)?.asImageBitmap()
        }.getOrNull()
    }
    Surface(
        color = PlanetBlue.copy(alpha = .15f),
        shape = RoundedCornerShape(18.dp),
        modifier = modifier.clip(RoundedCornerShape(18.dp))
    ) {
        if (bitmap != null) {
            Image(
                bitmap = bitmap,
                contentDescription = description,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        scaleX = if (parallax) 1.06f else 1f
                        scaleY = if (parallax) 1.06f else 1f
                        translationX = if (parallax) shift * 8.dp.toPx() else 0f
                        translationY = if (parallax) shift * -5.dp.toPx() else 0f
                    }
            )
        } else {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("✦", color = PlanetGold, fontSize = 44.sp)
            }
        }
    }
}

internal fun aviSummary(levels: List<String>): String {
    val order = mapOf("Start" to 0, "M3" to 1, "E3" to 2, "M4" to 3, "E4" to 4, "M5" to 5, "E5" to 6)
    val distinct = levels.filter(String::isNotBlank).distinct().sortedBy { order[it] ?: Int.MAX_VALUE }
    return "AVI ${distinct.joinToString(" · ")}".trim()
}

private fun storyText(key: String, locale: String): String {
    val language = locale.substringBefore('-').lowercase()
    val values = when (key) {
        "back" -> Triple("Terug", "Back", "Retour")
        "library" -> Triple("Mijn boeken", "My books", "Mes livres")
        "book" -> Triple("Boekje", "Book", "Livre")
        "bookUnavailable" -> Triple("Dit boekje is niet beschikbaar.", "This book is unavailable.", "Ce livre n’est pas disponible.")
        "stories" -> Triple("verhalen", "stories", "histoires")
        "pages" -> Triple("pagina's", "pages", "pages")
        "allAudio" -> Triple("Alle verhalen met voorleesstem", "All stories have read-aloud", "Toutes les histoires ont une lecture audio")
        "completedShort" -> Triple("uitgelezen", "completed", "terminées")
        "openBook" -> Triple("Open boekje", "Open book", "Ouvrir le livre")
        "howRead" -> Triple("Hoe wil je lezen?", "How would you like to read?", "Comment veux-tu lire ?")
        "readAlone" -> Triple("Ik lees alleen", "I read by myself", "Je lis seul")
        "readWithMe" -> Triple("Lees met mij mee", "Read along with me", "Lis avec moi")
        "grades" -> Triple("Leerjaar", "Grade", "Année")
        "grade" -> Triple("Leerjaar", "Grade", "Année")
        "audioYes" -> Triple("Met voorleesstem", "Read-aloud available", "Lecture audio disponible")
        "audioNo" -> Triple("Zelf lezen", "Read it yourself", "À lire soi-même")
        "completed" -> Triple("Uitgelezen", "Completed", "Terminé")
        "resume" -> Triple("Verder lezen", "Continue reading", "Continuer")
        "start" -> Triple("Begin lezen", "Start reading", "Commencer")
        "play" -> Triple("Lees voor", "Read aloud", "Lire à voix haute")
        "pause" -> Triple("Pauze", "Pause", "Pause")
        "replay" -> Triple("Herhaal bladzijde", "Replay page", "Répéter la page")
        "previous" -> Triple("Vorige", "Previous", "Précédente")
        "next" -> Triple("Volgende", "Next", "Suivante")
        "finish" -> Triple("Afronden", "Finish", "Terminer")
        "questions" -> Triple("Vragen", "Questions", "Questions")
        "readQuestion" -> Triple("Lees de vraag voor", "Read the question aloud", "Lis la question à voix haute")
        else -> Triple(key, key, key)
    }
    return when (language) {
        "en" -> values.second
        "fr" -> values.third
        else -> values.first
    }
}
