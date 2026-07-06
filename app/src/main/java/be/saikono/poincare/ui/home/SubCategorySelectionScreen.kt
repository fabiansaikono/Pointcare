package be.saikono.poincare.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import be.saikono.poincare.R
import be.saikono.poincare.data.content.PoincareContentParser
import be.saikono.poincare.data.content.PoincareArticle
import be.saikono.poincare.model.ChildProfile
import be.saikono.poincare.model.LevelProgress
import be.saikono.poincare.model.LearningDomain
import be.saikono.poincare.model.SkillLevel
import be.saikono.poincare.model.isSubcategoryEnabled
import be.saikono.poincare.model.levelProgressFor
import be.saikono.poincare.ui.components.systemNavigationMargin
import be.saikono.poincare.ui.spaceMap.SpaceBackground
import be.saikono.poincare.ui.theme.*

data class SubCategory(
    val id: String,
    val nameNl: String,
    val iconResId: Int,
    val exerciseCount: Int? = 100,
    val accentColor: Color = PlanetBlue
)

@Composable
fun SubCategorySelectionScreen(
    domain: String,
    currentLevel: SkillLevel,
    categoryProgress: LevelProgress = levelProgressFor(0),
    subcategoryCompletions: Map<String, Int> = emptyMap(),
    profile: ChildProfile?,
    onSubCategorySelected: (String, Int) -> Unit,
    onNavigateBack: () -> Unit
) {
    var pendingSubcategory by remember { mutableStateOf<SubCategory?>(null) }
    var pendingMissionChoice by remember { mutableStateOf<SubCategory?>(null) }
    var briefingSubcategory by remember { mutableStateOf<SubCategory?>(null) }
    var briefingLocale by remember { mutableStateOf("nl-BE") }
    val context = LocalContext.current
    val articles = remember { loadArticleBriefings(context.assets) }
    val appLanguage = profile?.appLanguage ?: "nl-BE"
    val subCategories = getSubCategoriesForDomain(domain).filter { subcategory ->
        profile?.isSubcategoryEnabled(subcategory.id) != false && subcategory.isVisibleForAppLocale(appLanguage)
    }
    val hasExercisesForGrade = currentLevel != SkillLevel.NOT_APPLICABLE
    val domainTitle = when (domain) {
        LearningDomain.MATH.name -> localizedCategoryTitle(LearningDomain.MATH, appLanguage)
        LearningDomain.READING.name -> localizedCategoryTitle(LearningDomain.READING, appLanguage)
        LearningDomain.WRITING.name -> localizedCategoryTitle(LearningDomain.WRITING, appLanguage)
        LearningDomain.PUZZLE.name -> localizedCategoryTitle(LearningDomain.PUZZLE, appLanguage)
        LearningDomain.WORLD_KNOWLEDGE.name -> localizedCategoryTitle(LearningDomain.WORLD_KNOWLEDGE, appLanguage)
        LearningDomain.GEOMETRY.name -> localizedCategoryTitle(LearningDomain.GEOMETRY, appLanguage)
        else -> domain
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(DeepSpaceBlue)
    ) {
        SpaceBackground()

        pendingMissionChoice?.let { subcategory ->
            val briefing = articleForSubcategory(subcategory.id, articles)
            AlertDialog(
                onDismissRequest = { pendingMissionChoice = null },
                containerColor = PlanetGold.copy(alpha = 0.85f),
                titleContentColor = DeepSpaceBlue,
                textContentColor = DeepSpaceBlue,
                title = { Text(subcategory.nameNl) },
                text = { Text(localizedUiText("briefingChoice", appLanguage)) },
                confirmButton = {
                    Button(
                        onClick = {
                        pendingMissionChoice = null
                        if (domain == LearningDomain.PUZZLE.name) pendingSubcategory = subcategory else onSubCategorySelected(subcategory.id, 0)
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = DeepSpaceBlue, contentColor = PlanetGold)
                    ) { Text(localizedUiText("startMission", appLanguage)) }
                },
                dismissButton = {
                    if (briefing != null) {
                        TextButton(onClick = {
                            pendingMissionChoice = null
                            briefingLocale = profile?.supportLanguage?.let(::normalizeBriefingLocale) ?: "nl-BE"
                            briefingSubcategory = subcategory
                        }) { Text(localizedUiText("missionBriefing", appLanguage)) }
                    }
                }
            )
        }

        briefingSubcategory?.let { subcategory ->
            val briefing = articleForSubcategory(subcategory.id, articles) ?: return@let
            AlertDialog(
                onDismissRequest = { briefingSubcategory = null },
                containerColor = PlanetGold.copy(alpha = 0.85f),
                titleContentColor = DeepSpaceBlue,
                textContentColor = DeepSpaceBlue,
                title = { Text(briefing.title(briefingLocale)) },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(briefing.summary(briefingLocale), style = MaterialTheme.typography.bodyMedium)
                        briefing.bodyWithoutSummary(briefingLocale).forEach { paragraph ->
                            Text(paragraph, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                },
                confirmButton = {
                    Button(
                        onClick = {
                        val selected = briefingSubcategory ?: return@Button
                        briefingSubcategory = null
                        if (domain == LearningDomain.PUZZLE.name) pendingSubcategory = selected else onSubCategorySelected(selected.id, 0)
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = DeepSpaceBlue, contentColor = PlanetGold)
                    ) { Text(localizedUiText("startMission", appLanguage)) }
                },
                dismissButton = { TextButton(onClick = { briefingSubcategory = null }) { Text(localizedUiText("close", appLanguage)) } }
            )
        }

        if (pendingSubcategory != null) {
            AlertDialog(
                onDismissRequest = { pendingSubcategory = null },
                containerColor = PlanetGold.copy(alpha = 0.85f),
                titleContentColor = DeepSpaceBlue,
                textContentColor = DeepSpaceBlue,
                title = { Text(localizedUiText("chooseDifficulty", appLanguage)) },
                text = {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        (1..3).forEach { stars ->
                            Button(
                                onClick = {
                                val subcategory = pendingSubcategory ?: return@Button
                                pendingSubcategory = null
                                onSubCategorySelected(subcategory.id, stars)
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = DeepSpaceBlue, contentColor = PlanetGold)
                            ) { Text("${"★".repeat(stars)}") }
                        }
                    }
                },
                confirmButton = {},
                dismissButton = { TextButton(onClick = { pendingSubcategory = null }) { Text(localizedUiText("cancel", appLanguage)) } }
            )
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .systemNavigationMargin()
                .padding(horizontal = 24.dp, vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Top Bar
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onNavigateBack) {
                    Icon(Icons.Default.ArrowBack, contentDescription = localizedUiText("back", appLanguage), tint = StarWhite)
                }
                Spacer(modifier = Modifier.width(16.dp))
                Text(
                    text = domainTitle,
                    style = MaterialTheme.typography.titleLarge,
                    color = StarWhite
                )
            }

            Spacer(modifier = Modifier.height(20.dp))

            Text(
                text = localizedUiText("chooseMission", appLanguage),
                style = MaterialTheme.typography.headlineMedium,
                color = StarWhite,
                textAlign = TextAlign.Center
            )

            CategoryProgressBar(categoryProgress)

            Spacer(modifier = Modifier.height(20.dp))

            BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
                val minTileSize = if (maxHeight < 480.dp) 96.dp else 120.dp
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(minSize = minTileSize),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(subCategories) { sub ->
                        SubCategoryCard(sub.localized(appLanguage), completedCount = subcategoryCompletions[sub.id] ?: 0, enabled = hasExercisesForGrade, locale = appLanguage) {
                            when {
                                domain == LearningDomain.PUZZLE.name -> pendingSubcategory = sub
                                articleForSubcategory(sub.id, articles) != null -> pendingMissionChoice = sub
                                else -> onSubCategorySelected(sub.id, 0)
                            }
                        }
                    }
                }
            }
        }
    }
}

private data class MissionBriefing(
    val nlTitle: String,
    val nlSummary: String,
    val nlBody: List<String>,
    val enTitle: String = nlTitle,
    val enSummary: String = nlSummary,
    val enBody: List<String> = nlBody,
    val frTitle: String = nlTitle,
    val frSummary: String = nlSummary,
    val frBody: List<String> = nlBody
) {
    fun title(locale: String): String = when (normalizeBriefingLocale(locale)) {
        "en-US" -> enTitle
        "fr-BE" -> frTitle
        else -> nlTitle
    }

    fun summary(locale: String): String = when (normalizeBriefingLocale(locale)) {
        "en-US" -> enSummary
        "fr-BE" -> frSummary
        else -> nlSummary
    }

    fun body(locale: String): List<String> = when (normalizeBriefingLocale(locale)) {
        "en-US" -> enBody
        "fr-BE" -> frBody
        else -> nlBody
    }

    fun bodyWithoutSummary(locale: String): List<String> {
        val summaryText = summary(locale).trim()
        return body(locale).filterNot { it.trim().equals(summaryText, ignoreCase = true) }.distinct()
    }
}

private fun loadArticleBriefings(assetManager: android.content.res.AssetManager): List<PoincareArticle> = runCatching {
    assetManager.open("poincare/content/articles/poincare-original-math-l4-articles-001.xml").use { stream ->
        PoincareContentParser().parse(stream).articles
    }
}.getOrDefault(emptyList())

private fun articleForSubcategory(subcategoryId: String, articles: List<PoincareArticle>): MissionBriefing? {
    val article = when (subcategoryId) {
        "division-remainder", "multiply-divide", "times-tables" -> articles.firstOrNull { it.skillRef == "MUL-DIV" }
        "fractions" -> articles.firstOrNull { it.skillRef == "FRAC" }
        "money" -> articles.firstOrNull { it.skillRef == "MONEY" }
        else -> null
    } ?: return null
    return MissionBriefing(
        nlTitle = article.title ?: "Mission briefing",
        nlSummary = article.summary.orEmpty(),
        nlBody = article.body,
        enTitle = when (article.skillRef) {
            "MUL-DIV" -> "Division with remainder"
            "FRAC" -> "Comparing fractions"
            "MONEY" -> "Calculating change"
            else -> article.title ?: "Mission briefing"
        },
        enSummary = when (article.skillRef) {
            "MUL-DIV" -> "Sometimes a number cannot be split exactly into equal groups. The part left over is the remainder."
            "FRAC" -> "Fractions with the same denominator can be compared by looking at the numerator."
            "MONEY" -> "You calculate change by subtracting the price from the amount paid."
            else -> article.summary.orEmpty()
        },
        enBody = when (article.skillRef) {
            "MUL-DIV" -> listOf("For 29 ÷ 4, you can make 7 groups of 4. That is 28. There is 1 left over.", "Always check: divisor × quotient + remainder = starting number.")
            "FRAC" -> listOf("When denominators are the same, compare the numerators.", "When numerators are the same, the smaller denominator makes larger pieces.")
            "MONEY" -> listOf("If you pay € 10,00 for something that costs € 7,35, subtract: € 10,00 - € 7,35 = € 2,65.", "You can also count up to the amount paid.")
            else -> article.body
        },
        frTitle = when (article.skillRef) {
            "MUL-DIV" -> "Division avec reste"
            "FRAC" -> "Comparer des fractions"
            "MONEY" -> "Calculer la monnaie"
            else -> article.title ?: "Mission briefing"
        },
        frSummary = when (article.skillRef) {
            "MUL-DIV" -> "Parfois, un nombre ne se partage pas exactement en groupes egaux. Ce qui reste s'appelle le reste."
            "FRAC" -> "Pour comparer des fractions avec le meme denominateur, regarde le numerateur."
            "MONEY" -> "On calcule la monnaie en soustrayant le prix du montant paye."
            else -> article.summary.orEmpty()
        },
        frBody = when (article.skillRef) {
            "MUL-DIV" -> listOf("Pour 29 ÷ 4, on fait 7 groupes de 4. Cela fait 28. Il reste 1.", "Verifie toujours : diviseur × quotient + reste = nombre de depart.")
            "FRAC" -> listOf("Quand les denominateurs sont les memes, compare les numerateurs.", "Quand les numerateurs sont les memes, le plus petit denominateur donne les plus grands morceaux.")
            "MONEY" -> listOf("Si tu paies € 10,00 pour quelque chose qui coute € 7,35, calcule : € 10,00 - € 7,35 = € 2,65.", "Tu peux aussi compter jusqu'au montant paye.")
            else -> article.body
        }
    )
}

private fun normalizeBriefingLocale(locale: String): String = when (locale) {
    "fr", "fr-BE", "fr_BE", "fr-FR", "fr_FR" -> "fr-BE"
    "en", "en-US", "en_US", "en-GB", "en_GB" -> "en-US"
    else -> "nl-BE"
}

@Composable
private fun CategoryProgressBar(progress: LevelProgress) {
    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("Level ${progress.level}", color = StarWhite, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
            Column(horizontalAlignment = Alignment.End) {
                Text("Level ${progress.level + 1}", color = StarWhite, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
                Text("${progress.currentLevelPoints}/${progress.pointsNeededForNextLevel}", color = StarWhite.copy(alpha = 0.7f), style = MaterialTheme.typography.labelSmall)
            }
        }
        LinearProgressIndicator(
            progress = { progress.progressFraction.coerceIn(0f, 1f) },
            color = PlanetGold,
            trackColor = Color.White.copy(alpha = 0.12f),
            modifier = Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp))
        )
    }
}

@Composable
fun SubCategoryCard(sub: SubCategory, completedCount: Int, enabled: Boolean, locale: String, onClick: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(1f)
            .clip(RoundedCornerShape(18.dp))
            .alpha(if (enabled) 1f else 0.4f)
            .clickable(enabled = enabled) { onClick() },
        colors = CardDefaults.cardColors(containerColor = if (enabled) Color.White.copy(alpha = 0.05f) else Color.Gray.copy(alpha = 0.12f))
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                painter = painterResource(id = sub.iconResId),
                contentDescription = null,
                tint = if (enabled) StarWhite else StarWhite.copy(alpha = 0.55f),
                modifier = Modifier.size(48.dp)
            )
            
            Spacer(modifier = Modifier.height(10.dp))

            Text(
                text = sub.nameNl,
                style = MaterialTheme.typography.titleMedium,
                color = if (enabled) StarWhite else StarWhite.copy(alpha = 0.55f),
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 16.dp)
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = sub.exerciseCount?.let { "$completedCount/$it ${localizedUiText("completed", locale)}" } ?: "$completedCount ${localizedUiText("completed", locale)}",
                style = MaterialTheme.typography.labelMedium,
                color = if (enabled) PlanetGold else StarWhite.copy(alpha = 0.45f),
                textAlign = TextAlign.Center
            )
        }
    }
}

private fun SubCategory.localized(locale: String): SubCategory = copy(nameNl = localizedSubcategoryName(id, locale) ?: nameNl)

private fun languageOf(locale: String): String = when (locale) {
    "fr", "fr-BE", "fr_BE", "fr-FR", "fr_FR" -> "fr"
    "en", "en-US", "en_US", "en-GB", "en_GB" -> "en"
    else -> "nl"
}

private fun localizedCategoryTitle(domain: LearningDomain, locale: String): String = when (domain) {
    LearningDomain.READING -> when (languageOf(locale)) { "en" -> "Communicate"; "fr" -> "Communiquer"; else -> "Communicatie" }
    LearningDomain.WRITING -> when (languageOf(locale)) { "en" -> "Writing"; "fr" -> "Ecriture"; else -> "Schrijven" }
    LearningDomain.MATH -> when (languageOf(locale)) { "en" -> "Numbers"; "fr" -> "Nombres"; else -> "Wiskunde" }
    LearningDomain.PUZZLE -> when (languageOf(locale)) { "en" -> "Puzzles"; "fr" -> "Puzzles"; else -> "Puzzels" }
    LearningDomain.WORLD_KNOWLEDGE -> when (languageOf(locale)) { "en" -> "World knowledge"; "fr" -> "Le Monde"; else -> "Wereldkennis" }
    LearningDomain.GEOMETRY -> when (languageOf(locale)) { "en" -> "Geometry"; "fr" -> "Geometrie"; else -> "Meetkunde" }
}

private fun localizedUiText(key: String, locale: String): String = when (languageOf(locale)) {
    "en" -> when (key) {
        "briefingChoice" -> "Read the explanation first or start right away?"
        "startMission" -> "Start mission"
        "missionBriefing" -> "Mission briefing"
        "close" -> "Close"
        "chooseDifficulty" -> "Choose difficulty"
        "cancel" -> "Cancel"
        "back" -> "Back"
        "chooseMission" -> "Choose your mission"
        "completed" -> "completed"
        else -> key
    }
    "fr" -> when (key) {
        "briefingChoice" -> "Lire d'abord l'explication ou commencer tout de suite ?"
        "startMission" -> "Commencer la mission"
        "missionBriefing" -> "Briefing de mission"
        "close" -> "Fermer"
        "chooseDifficulty" -> "Choisis la difficulte"
        "cancel" -> "Annuler"
        "back" -> "Retour"
        "chooseMission" -> "Choisis ta mission"
        "completed" -> "termine"
        else -> key
    }
    else -> when (key) {
        "briefingChoice" -> "Wil je eerst de uitleg lezen of meteen starten?"
        "startMission" -> "Naar de missie"
        "missionBriefing" -> "Mission briefing"
        "close" -> "Sluiten"
        "chooseDifficulty" -> "Kies moeilijkheid"
        "cancel" -> "Annuleren"
        "back" -> "Terug"
        "chooseMission" -> "Kies je missie"
        "completed" -> "voltooid"
        else -> key
    }
}

private fun localizedSubcategoryName(id: String, locale: String): String? = when (languageOf(locale)) {
    "en" -> mapOf(
        "addition" to "Addition", "subtraction" to "Subtraction", "multiplication" to "Multiplication", "division" to "Division", "mixed_arithmetic" to "Mixed operations", "number_sequences" to "Number sequences", "fractions" to "Fractions", "time" to "Time", "money" to "Money", "measurement" to "Measurement", "geometry_shapes" to "Shapes and geometry", "reading" to "Reading", "listening" to "Listening", "vocabulary" to "Vocabulary", "translation_nl_to_en" to "Dutch to English", "translation_nl_to_fr" to "Dutch to French", "translation_en_to_nl" to "English to Dutch", "translation_en_to_fr" to "English to French", "translation_fr_to_nl" to "French to Dutch", "translation_fr_to_en" to "French to English", "writing" to "Writing", "spelling" to "Spelling", "sentence_order" to "Sentence order", "grammar" to "Grammar", "space" to "Space", "nature" to "Nature", "geography" to "Geography", "science" to "Science", "history" to "History", "everyday_life" to "Everyday life", "sudoku" to "Sudoku", "math-crosswords" to "Math crossword", "pattern_sequences" to "Patterns", "odd_one_out" to "Odd one out", "analogies" to "Analogies", "grid_logic" to "Grid logic", "mazes" to "Mazes", "visual_reasoning" to "Visual reasoning", "memory_rules" to "Memory rules"
    )[id]
    "fr" -> mapOf(
        "addition" to "Addition", "subtraction" to "Soustraction", "multiplication" to "Multiplication", "division" to "Division", "mixed_arithmetic" to "Operations mixtes", "number_sequences" to "Suites de nombres", "fractions" to "Fractions", "time" to "Heure", "money" to "Argent", "measurement" to "Mesures", "geometry_shapes" to "Formes et geometrie", "reading" to "Lecture", "listening" to "Ecoute", "vocabulary" to "Vocabulaire", "translation_nl_to_en" to "Neerlandais vers anglais", "translation_nl_to_fr" to "Neerlandais vers francais", "translation_en_to_nl" to "Anglais vers neerlandais", "translation_en_to_fr" to "Anglais vers francais", "translation_fr_to_nl" to "Francais vers neerlandais", "translation_fr_to_en" to "Francais vers anglais", "writing" to "Ecriture", "spelling" to "Orthographe", "sentence_order" to "Ordre des phrases", "grammar" to "Grammaire", "space" to "Espace", "nature" to "Nature", "geography" to "Geographie", "science" to "Science", "history" to "Histoire", "everyday_life" to "Vie quotidienne", "sudoku" to "Sudoku", "math-crosswords" to "Mots croises de calcul", "pattern_sequences" to "Motifs", "odd_one_out" to "Intrus", "analogies" to "Analogies", "grid_logic" to "Logique de grille", "mazes" to "Labyrinthes", "visual_reasoning" to "Raisonnement visuel", "memory_rules" to "Regles de memoire"
    )[id]
    else -> null
}

fun getSubCategoriesForDomain(domain: String): List<SubCategory> {
    return when (domain) {
        LearningDomain.MATH.name -> listOf(
            SubCategory("addition", "Optellen", R.drawable.ic_topic_add_subtract),
            SubCategory("subtraction", "Aftrekken", R.drawable.ic_topic_add_subtract),
            SubCategory("multiplication", "Vermenigvuldigen", R.drawable.ic_topic_multiply_divide),
            SubCategory("division", "Delen", R.drawable.ic_topic_division_remainder),
            SubCategory("mixed_arithmetic", "Gemengde bewerkingen", R.drawable.ic_topic_mixed_practice),
            SubCategory("number_sequences", "Getallenreeksen", R.drawable.ic_topic_numbers_10000),
            SubCategory("fractions", "Breuken", R.drawable.ic_topic_fractions),
            SubCategory("money", "Geld", R.drawable.ic_topic_money),
        )
        LearningDomain.GEOMETRY.name -> listOf(
            SubCategory("time", "Tijd", R.drawable.ic_topic_time),
            SubCategory("measurement", "Meten", R.drawable.ic_topic_measure),
            SubCategory("geometry_shapes", "Vormen en meetkunde", R.drawable.ic_topic_shapes)
        )
        LearningDomain.READING.name -> listOf(
            SubCategory("reading", "Lezen", R.drawable.ic_topic_reading),
            SubCategory("listening", "Luisteren", R.drawable.ic_topic_listening),
            SubCategory("translation_nl_to_en", "Nederlands naar Engels", R.drawable.ic_topic_vocabulary),
            SubCategory("translation_nl_to_fr", "Nederlands naar Frans", R.drawable.ic_topic_vocabulary),
            SubCategory("translation_en_to_nl", "Engels naar Nederlands", R.drawable.ic_topic_vocabulary),
            SubCategory("translation_en_to_fr", "Engels naar Frans", R.drawable.ic_topic_vocabulary),
            SubCategory("translation_fr_to_nl", "Frans naar Nederlands", R.drawable.ic_topic_vocabulary),
            SubCategory("translation_fr_to_en", "Frans naar Engels", R.drawable.ic_topic_vocabulary)
        )
        LearningDomain.WORLD_KNOWLEDGE.name -> listOf(
            SubCategory("space", "Ruimte", R.drawable.ic_theme_space),
            SubCategory("nature", "Natuur", R.drawable.ic_theme_nature),
            SubCategory("geography", "Aardrijkskunde", R.drawable.ic_theme_nature),
            SubCategory("science", "Wetenschap", R.drawable.ic_theme_space),
            SubCategory("history", "Geschiedenis", R.drawable.ic_topic_reading_comprehension),
            SubCategory("everyday_life", "Dagelijks leven", R.drawable.ic_theme_body)
        )
        LearningDomain.WRITING.name -> listOf(
            SubCategory("writing", "Schrijven", R.drawable.ic_topic_writing),
            SubCategory("spelling", "Spelling", R.drawable.ic_topic_spelling),
            SubCategory("sentence_order", "Zinsvolgorde", R.drawable.ic_topic_sentence_structure),
            SubCategory("grammar", "Grammatica", R.drawable.ic_topic_grammar)
        )
        LearningDomain.PUZZLE.name -> listOf(
            SubCategory("sudoku", "Sudoku", R.drawable.ic_topic_sudoku, null),
            SubCategory("math-crosswords", "Rekenkruiswoord", R.drawable.ic_topic_math_crosswords, null),
            SubCategory("pattern_sequences", "Patronen", R.drawable.ic_topic_patterns),
            SubCategory("odd_one_out", "Welke hoort er niet bij?", R.drawable.ic_topic_logic),
            SubCategory("analogies", "Analogieën", R.drawable.ic_topic_logic),
            SubCategory("grid_logic", "Roosterlogica", R.drawable.ic_topic_sudoku),
            SubCategory("mazes", "Doolhoven", R.drawable.ic_topic_mazes),
            SubCategory("visual_reasoning", "Visueel redeneren", R.drawable.ic_topic_spatial_insight),
            SubCategory("memory_rules", "Geheugenregels", R.drawable.ic_topic_memory)
        )
        else -> emptyList()
    }
}

private fun SubCategory.isVisibleForAppLocale(appLanguage: String): Boolean {
    val sourceLocale = when (id) {
        "translation_nl_to_en", "translation_nl_to_fr" -> "nl-BE"
        "translation_en_to_nl", "translation_en_to_fr" -> "en-US"
        "translation_fr_to_nl", "translation_fr_to_en" -> "fr-BE"
        else -> return true
    }
    return normalizeBriefingLocale(appLanguage) == sourceLocale
}
