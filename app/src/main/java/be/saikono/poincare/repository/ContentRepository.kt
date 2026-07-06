package be.saikono.poincare.repository

import android.content.Context
import android.util.Log
import be.saikono.poincare.data.content.PoincareContentPack
import be.saikono.poincare.data.content.PoincareContentParser
import be.saikono.poincare.data.content.PoincareAudioSource
import be.saikono.poincare.data.content.PoincareExercise
import be.saikono.poincare.data.content.PoincareExerciseTranslation
import be.saikono.poincare.data.content.PoincareSubcategory
import be.saikono.poincare.model.ExerciseContent
import be.saikono.poincare.model.ExerciseSection
import be.saikono.poincare.model.formatVisibleNumbers
import be.saikono.poincare.model.InputMode
import be.saikono.poincare.model.LearningDomain
import be.saikono.poincare.model.LocalizedText
import be.saikono.poincare.model.RewardMetadata
import be.saikono.poincare.model.SkillLevel
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlin.random.Random

interface ContentRepository {
    suspend fun getExercises(domain: LearningDomain, level: SkillLevel): List<ExerciseContent>
    suspend fun getExerciseById(id: String): ExerciseContent?
    suspend fun getExercises(
        mainCategory: String? = null,
        subcategoryId: String? = null,
        grade: Int? = null,
        difficultyStars: Int? = null,
        locale: String? = null
    ): List<ExerciseContent>
}

class AssetContentRepository(private val context: Context) : ContentRepository {
    private val xmlParser = PoincareContentParser()
    private val contentLoadMutex = Mutex()
    private val xmlExercisesCache = mutableMapOf<LearningDomain, List<ExerciseContent>>()
    private var loadedManifestSignature: String? = null

    override suspend fun getExercises(domain: LearningDomain, level: SkillLevel): List<ExerciseContent> = withContext(Dispatchers.IO) {
        if (level == SkillLevel.NOT_APPLICABLE) {
            emptyList()
        } else {
            loadXmlExercises(domain).filter { it.level == level }.also { exercises ->
                Log.i(TAG, "Query domain=$domain level=$level returned ${exercises.size} exercises")
            }
        }
    }

    override suspend fun getExerciseById(id: String): ExerciseContent? = withContext(Dispatchers.IO) {
        xmlExercisesCache.values.flatten().firstOrNull { it.id == id } 
            ?: loadXmlExercises().firstOrNull { it.id == id }
    }

    override suspend fun getExercises(
        mainCategory: String?,
        subcategoryId: String?,
        grade: Int?,
        difficultyStars: Int?,
        locale: String?
    ): List<ExerciseContent> = withContext(Dispatchers.IO) {
        val domain = mainCategory?.toLearningDomain()
        val baseExercises = if (domain != null) loadXmlExercises(domain) else loadXmlExercises()
        
        baseExercises.filter { exercise ->
            (mainCategory == null || exercise.data["mainCategory"] == mainCategory) &&
                (subcategoryId == null || exercise.data["subcategoryId"] == subcategoryId) &&
                (grade == null || exercise.data["grade"]?.toIntOrNull() == grade) &&
                (difficultyStars == null || exercise.data["difficultyStars"]?.toIntOrNull() == difficultyStars) &&
                (locale == null || exercise.data.containsKey("question_${normalizeLocale(locale)}") || exercise.data.containsKey("question_nl-BE"))
        }.also { exercises ->
            Log.i(TAG, "Query mainCategory=$mainCategory subcategoryId=$subcategoryId grade=$grade difficultyStars=$difficultyStars locale=$locale returned ${exercises.size} exercises")
        }
    }

    private suspend fun loadXmlExercises(domain: LearningDomain? = null): List<ExerciseContent> = contentLoadMutex.withLock {
        invalidateCacheIfContentChanged()
        loadXmlExercisesLocked(domain)
    }

    private fun invalidateCacheIfContentChanged() {
        val signature = runCatching {
            context.assets.open("poincare/content/manifest.json").use { stream ->
                stream.bufferedReader().readText().hashCode().toString()
            }
        }.getOrNull()
        if (signature != null && signature != loadedManifestSignature) {
            xmlExercisesCache.clear()
            loadedManifestSignature = signature
        }
    }

    private suspend fun loadXmlExercisesLocked(domain: LearningDomain? = null): List<ExerciseContent> {
        if (domain != null) {
            xmlExercisesCache[domain]?.let { return it }
            val packs = loadPacksForDomain(domain)
            val parsed = packs.flatMap { pack ->
                pack.exercises.mapNotNull { it.toExerciseContent(pack) }
            }.filter { it.domain == domain }.distinctBy { it.id }
            Log.i(TAG, "Converted ${parsed.size} XML exercises for domain $domain")
            xmlExercisesCache[domain] = parsed
            return parsed
        } else {
            val allDomains = LearningDomain.entries
            return allDomains.flatMap { loadXmlExercisesLocked(it) }.distinctBy { it.id }
        }
    }

    private fun loadPacksForDomain(domain: LearningDomain): List<PoincareContentPack> {
        val fileNames = domain.toFileNames()
        return fileNames.mapNotNull { fileName ->
            val path = "poincare/content/$fileName"
            try {
                context.assets.open(path).use(xmlParser::parse)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to parse content pack asset: $path", e)
                null
            }
        }
    }

    private fun LearningDomain.toFileNames(): List<String> = when (this) {
        LearningDomain.MATH -> listOf("poincare-exercises-math.xml")
        LearningDomain.READING, LearningDomain.WRITING -> listOf("poincare-exercises-language.xml")
        LearningDomain.PUZZLE -> listOf("poincare-exercises-logic-puzzles.xml")
        LearningDomain.WORLD_KNOWLEDGE -> listOf("poincare-exercises-world-knowledge.xml")
        LearningDomain.GEOMETRY -> listOf("poincare-exercises-geometry.xml")
        else -> emptyList()
    }

    private fun String.toLearningDomain(): LearningDomain? = when (this.lowercase()) {
        "wiskunde", "math", "mathematics" -> LearningDomain.MATH
        "language", "lezen", "reading" -> LearningDomain.READING
        "schrijven", "writing" -> LearningDomain.WRITING
        "puzzel", "puzzels", "puzzle", "logic-puzzles" -> LearningDomain.PUZZLE
        "world-themes", "world-knowledge", "world_knowledge" -> LearningDomain.WORLD_KNOWLEDGE
        "geometry" -> LearningDomain.GEOMETRY
        else -> null
    }

    private fun PoincareExercise.toExerciseContent(pack: PoincareContentPack): ExerciseContent? {
        val category = mainCategory ?: pack.mainCategory
        val subcategory = pack.subcategories.firstOrNull { it.id == subcategoryId }
        val domain = when (category ?: subject?.lowercase()) {
            "wiskunde", "math", "mathematics" -> LearningDomain.MATH
            "language" -> if (subcategoryId in WRITING_SUBCATEGORIES) LearningDomain.WRITING else LearningDomain.READING
            "lezen", "reading" -> LearningDomain.READING
            "schrijven", "writing" -> LearningDomain.WRITING
            "puzzel", "puzzels", "puzzle" -> LearningDomain.PUZZLE
            "logic-puzzles", "logic_puzzles" -> LearningDomain.PUZZLE
            "world-themes", "world_themes", "world-knowledge", "world_knowledge" -> LearningDomain.WORLD_KNOWLEDGE
            "geometry" -> LearningDomain.GEOMETRY
            else -> LearningDomain.MATH
        }
        val level = when (grade) {
            1 -> SkillLevel.FIRST_GRADE
            2 -> SkillLevel.SECOND_GRADE
            3 -> SkillLevel.THIRD_GRADE
            4 -> SkillLevel.FOURTH_GRADE
            5 -> SkillLevel.FIFTH_GRADE
            6 -> SkillLevel.SIXTH_GRADE
            else -> SkillLevel.FIRST_GRADE
        }
        val nlTranslation = translationFor("nl-BE")
        val enTranslation = translationFor("en-US")
        val frTranslation = translationFor("fr-BE")
        val nlAnswers = shuffledAnswers(id, "nl-BE", nlTranslation.answers.ifEmpty { answers })
        val enAnswers = shuffledAnswers(id, "en-US", enTranslation.answers.ifEmpty { nlTranslation.answers.ifEmpty { answers } })
        val frAnswers = shuffledAnswers(id, "fr-BE", frTranslation.answers.ifEmpty { nlTranslation.answers.ifEmpty { answers } })
        val correctAnswer = nlTranslation.answers.firstOrNull { it.correct }?.text
            ?: answers.firstOrNull { it.correct }?.text
            ?: solutionValue
            ?: return null
        val nlCorrectAnswer = formatVisibleNumbers(nlTranslation.answers.firstOrNull { it.correct }?.displayFor("nl-BE") ?: correctAnswer)
        val enCorrectAnswer = formatVisibleNumbers(enTranslation.answers.firstOrNull { it.correct }?.displayFor("en-US") ?: correctAnswer)
        val frCorrectAnswer = formatVisibleNumbers(frTranslation.answers.firstOrNull { it.correct }?.displayFor("fr-BE") ?: correctAnswer)
        val xmlType = type ?: "open_numeric"
        val inputMode = when (xmlType) {
            "audio_choice", "audio_translation_choice" -> InputMode.LISTEN_AND_SELECT
            "multiple_choice", "shape_choice" -> if (subcategoryId == "listening") InputMode.LISTEN_AND_SELECT else InputMode.TAP_CHOICE
            "open_numeric" -> InputMode.CUSTOM_NUMBER_PAD
            "puzzle" -> when (subcategoryId) {
                "sudoku" -> InputMode.SUDOKU_GRID
                "math-crosswords" -> InputMode.MATH_CROSSWORD
                else -> InputMode.TAP_CHOICE
            }
            else -> InputMode.TAP_CHOICE
        }
        val audioNl = audioFor("nl-BE")
        val audioEn = audioFor("en-US")
        val audioFr = audioFor("fr-BE")
        val visualNl = visualFor("nl-BE")
        val visualEn = visualFor("en-US")
        val visualFr = visualFor("fr-BE")
        val nlPrompt = displayPrompt(cleanAudioReference(nlTranslation.instruction ?: instruction), cleanAudioReference(nlTranslation.question ?: questionText), id)
        val enPrompt = displayPrompt(cleanAudioReference(enTranslation.instruction), cleanAudioReference(enTranslation.question), nlPrompt)
        val frPrompt = displayPrompt(cleanAudioReference(frTranslation.instruction), cleanAudioReference(frTranslation.question), nlPrompt)
        return ExerciseContent(
            id = id,
            domain = domain,
            skill = skillRef ?: topic ?: subject ?: "xml",
            section = ExerciseSection.EXERCISES,
            level = level,
            inputMode = inputMode,
            prompt = LocalizedText(nl = formatVisibleNumbers(nlPrompt), en = formatVisibleNumbers(enPrompt), fr = formatVisibleNumbers(frPrompt)),
            speechText = if (inputMode == InputMode.LISTEN_AND_SELECT) {
                LocalizedText(
                    nl = formatVisibleNumbers(audioNl?.speechText() ?: nlCorrectAnswer),
                    en = formatVisibleNumbers(audioEn?.speechText() ?: audioNl?.speechText() ?: enCorrectAnswer),
                    fr = formatVisibleNumbers(audioFr?.speechText() ?: audioNl?.speechText() ?: frCorrectAnswer)
                )
            } else null,
            data = buildMap {
                put("mainCategory", category ?: "")
                put("progressKey", progressKey ?: id)
                subcategoryId?.let { put("subcategoryId", it) }
                put("grade", (grade ?: 0).toString())
                gradeDisplay?.let { put("gradeDisplay", formatVisibleNumbers(it)) }
                put("difficultyStars", (difficultyStars ?: difficulty ?: 1).toString())
                difficultyDisplay?.let { put("difficultyDisplay", it) }
                difficultyValueDisplay?.let { put("difficultyValueDisplay", formatVisibleNumbers(it)) }
                contentType?.let { put("contentType", it) }
                put("xmlType", xmlType)
                missingMarker?.let { put("missingMarker", it) }
                missingMarkerColor?.let { put("missingMarkerColor", it) }
                missingVisibleLine?.let { put("missingVisibleLine", formatVisibleNumbers(it)) }
                missingTtsFullWord?.let { put("missingTtsFullWord", it) }
                audioSources?.let { sources ->
                    put("audioVisibleText", sources.visibleText.toString())
                    put("audioPlayButtonOnly", sources.playButtonOnly.toString())
                    sources.kind?.let { put("audioKind", it) }
                    sources.sourceLocale?.let { put("audioSourceLocale", it) }
                    sources.targetLocale?.let { put("audioTargetLocale", it) }
                }
                putAudioData("nl-BE", audioNl)
                putAudioData("en-US", audioEn)
                putAudioData("fr-BE", audioFr)
                putVisualData("nl-BE", visualNl)
                putVisualData("en-US", visualEn)
                putVisualData("fr-BE", visualFr)
                putPromptTtsData("nl-BE", nlTranslation, this)
                putPromptTtsData("en-US", enTranslation, this)
                putPromptTtsData("fr-BE", frTranslation, this)
                put("topic", subcategory.labelFor("nl-BE") ?: topic ?: subcategoryId ?: "")
                put("topic_en-US", subcategory.labelFor("en-US") ?: subcategory.labelFor("nl-BE") ?: topic ?: "")
                put("topic_fr-BE", subcategory.labelFor("fr-BE") ?: subcategory.labelFor("nl-BE") ?: topic ?: "")
                iconRef?.let { put("iconRef", it) }
                skillIconRef?.let { put("skillIconRef", it) }
                skillRef?.let { put("skillRef", it) }
                putTranslationData("nl-BE", nlTranslation, nlAnswers, category, subcategoryId)
                putTranslationData("en-US", enTranslation, enAnswers, category, subcategoryId)
                putTranslationData("fr-BE", frTranslation, frAnswers, category, subcategoryId)
                if (nlAnswers.isNotEmpty()) put("options", nlAnswers.joinToString("\n") { formatVisibleNumbers(it.displayFor("nl-BE")) })
            },
            answer = nlCorrectAnswer,
            rewardMetadata = RewardMetadata(stamps = 1)
        )
    }

    private fun PoincareExercise.audioFor(locale: String): PoincareAudioSource? {
        val normalized = normalizeLocale(locale)
        return audioSources?.audio?.firstOrNull { normalizeLocale(it.locale) == normalized }
            ?: audioSources?.audio?.firstOrNull { normalizeLocale(it.locale) == normalizeLocale(audioSources.sourceLocale ?: "") }
            ?: audioSources?.audio?.firstOrNull()
    }

    private fun MutableMap<String, String>.putAudioData(locale: String, audio: PoincareAudioSource?) {
        if (audio == null) return
        put("audioText_$locale", formatVisibleNumbers(audio.speechText()))
        put("audioTtsLanguage_$locale", normalizeLocale(audio.ttsLanguage))
        put("audioId_$locale", audio.audioId)
    }

    private fun PoincareAudioSource.speechText(): String = ttsText ?: phoneticText ?: text

    private fun PoincareExercise.visualFor(locale: String): be.saikono.poincare.data.content.PoincareVisualSource? {
        val normalized = normalizeLocale(locale)
        return visualSources?.visuals?.firstOrNull { normalizeLocale(it.locale) == normalized }
            ?: visualSources?.visuals?.firstOrNull { normalizeLocale(it.locale) == "nl-BE" }
            ?: visualSources?.visuals?.firstOrNull { normalizeLocale(it.locale) == "en-US" }
            ?: visualSources?.visuals?.firstOrNull()
    }

    private fun MutableMap<String, String>.putVisualData(locale: String, visual: be.saikono.poincare.data.content.PoincareVisualSource?) {
        if (visual == null) return
        put("visualIcon_${normalizeLocale(locale)}", visual.icon)
        visual.alt?.let { put("visualAlt_${normalizeLocale(locale)}", formatVisibleNumbers(it)) }
    }

    private fun PoincareExercise.putPromptTtsData(locale: String, translation: PoincareExerciseTranslation, data: MutableMap<String, String>) {
        val promptTts = translation.questionTts ?: promptTtsTexts[normalizeLocale(locale)]
        if (!promptTts.isNullOrBlank()) data["promptTts_${normalizeLocale(locale)}"] = formatVisibleNumbers(promptTts)
    }

    private fun PoincareExercise.translationFor(locale: String): PoincareExerciseTranslation {
        val normalized = normalizeLocale(locale)
        return translations[normalized]
            ?: translations["nl-BE"]
            ?: PoincareExerciseTranslation(
                locale = normalized,
                title = null,
                instruction = instruction,
                question = questionText,
                questionTts = null,
                answers = answers,
                feedback = feedback
            )
    }

    private fun MutableMap<String, String>.putTranslationData(
        locale: String,
        translation: PoincareExerciseTranslation,
        answersForDisplay: List<be.saikono.poincare.data.content.PoincareAnswer>,
        mainCategory: String?,
        subcategoryId: String?
    ) {
        translation.title?.let { put("title_$locale", formatVisibleNumbers(it)) }
        translation.instruction?.let { put("instruction_$locale", formatVisibleNumbers(it)) }
        translation.question?.let { put("question_$locale", formatVisibleNumbers(it)) }
        if (answersForDisplay.isNotEmpty()) put("options_$locale", answersForDisplay.joinToString("\n") { formatVisibleNumbers(it.displayFor(locale)) })
        if (answersForDisplay.isNotEmpty()) put("optionSpeech_$locale", answersForDisplay.joinToString("\n") { it.speakFor(locale) })
        translation.answers.firstOrNull { it.correct }?.let { put("answer_$locale", formatVisibleNumbers(it.displayFor(locale))) }
        translation.feedback?.correct?.let { put("feedbackCorrect_$locale", formatVisibleNumbers(it)) }
        translation.feedback?.incorrect?.let { put("feedbackIncorrect_$locale", formatVisibleNumbers(it)) }
    }

    private fun be.saikono.poincare.data.content.PoincareAnswer.displayFor(locale: String): String {
        val normalized = normalizeLocale(locale)
        return displays[normalized]
            ?: displays["nl-BE"]
            ?: displays["en-US"]
            ?: displays.values.firstOrNull()
            ?: text
    }

    private fun be.saikono.poincare.data.content.PoincareAnswer.speakFor(locale: String): String {
        val normalized = normalizeLocale(locale)
        return speaks[normalized]
            ?: speaks["nl-BE"]
            ?: speaks["en-US"]
            ?: speaks.values.firstOrNull()
            ?: displayFor(locale)
    }

    private fun localizedAnswerDisplay(value: String, locale: String, mainCategory: String?, subcategoryId: String?): String {
        val formatted = formatVisibleNumbers(value)
        if (normalizeLocale(locale) == "en-US") return formatted
        if (subcategoryId in setOf("spelling", "grammar", "sentence_order")) return formatted
        val normalizedCategory = mainCategory?.replace('-', '_')
        if (normalizedCategory !in setOf("language", "world_themes", "world_knowledge")) return formatted
        val key = formatted.trim().lowercase()
        return when (normalizeLocale(locale)) {
            "fr-BE" -> ANSWER_LABELS_FR[key]
            else -> ANSWER_LABELS_NL[key]
        } ?: formatted
    }

    private fun displayPrompt(instruction: String?, question: String?, fallback: String): String {
        val parts = listOfNotNull(instruction?.takeIf { it.isNotBlank() }, question?.takeIf { it.isNotBlank() })
        return parts.joinToString("\n\n").ifBlank { fallback }
    }

    private fun cleanAudioReference(text: String?): String? {
        if (text.isNullOrBlank()) return text
        return text
            .replace(Regex("AudioRef\\s*:?\\s*[^.?!\n]+[.?!]?", RegexOption.IGNORE_CASE), "")
            .replace(Regex("Audio wordt later gekoppeld via audioRef\\.?", RegexOption.IGNORE_CASE), "")
            .replace(Regex("Audio will be linked later with audioRef\\.?", RegexOption.IGNORE_CASE), "")
            .replace(Regex("L.audio sera lie plus tard via audioRef\\.?", RegexOption.IGNORE_CASE), "")
            .replace(Regex("L’audio sera lié plus tard via audioRef\\.?", RegexOption.IGNORE_CASE), "")
            .replace(Regex("\\s+"), " ")
            .trim()
            .takeIf { it.isNotBlank() }
    }

    private fun shuffledAnswers(
        exerciseId: String,
        locale: String,
        answers: List<be.saikono.poincare.data.content.PoincareAnswer>
    ): List<be.saikono.poincare.data.content.PoincareAnswer> {
        if (answers.size <= 1) return answers
        val shuffled = answers.shuffled(Random((exerciseId + locale).hashCode())).toMutableList()
        if (shuffled.firstOrNull()?.correct == true) {
            val first = shuffled.removeAt(0)
            shuffled.add(first)
        }
        return shuffled
    }

    private fun assetExists(path: String): Boolean = try {
        context.assets.open(path).close()
        true
    } catch (e: IOException) {
        false
    }

    private fun PoincareSubcategory?.labelFor(locale: String): String? = this?.labels?.get(normalizeLocale(locale))

    private fun List<String?>.firstNonEmpty(fallback: String): String = firstOrNull { !it.isNullOrBlank() } ?: fallback

    private fun normalizeLocale(locale: String): String = when (locale) {
        "nl", "nl_BE" -> "nl-BE"
        "en", "en_US" -> "en-US"
        "fr", "fr_BE", "fr-FR", "fr_FR" -> "fr-BE"
        else -> locale
    }

    companion object {
        private const val TAG = "PoincareContent"

        private val WRITING_SUBCATEGORIES = setOf("writing", "dictation", "spelling", "sentence-structure", "sentence_order", "grammar")

        private val ANSWER_LABELS_NL = mapOf(
            "a" to "een", "an" to "een", "the" to "de", "is" to "is", "am" to "ben", "are" to "zijn", "be" to "zijn", "many" to "veel",
            "apple" to "appel", "book" to "boek", "bread" to "brood", "chair" to "stoel", "forest" to "bos", "house" to "huis", "moon" to "maan", "music" to "muziek", "planet" to "planeet", "river" to "rivier", "rocket" to "raket", "school" to "school", "star" to "ster", "sun" to "zon", "tree" to "boom", "water" to "water",
            "bear" to "beer", "cat" to "kat", "dog" to "hond", "duck" to "eend", "fish" to "vis", "fox" to "vos", "friend" to "vriend", "frog" to "kikker", "horse" to "paard", "lion" to "leeuw", "mouse" to "muis", "owl" to "uil", "rabbit" to "konijn",
            "i read a book." to "Ik lees een boek.", "a book i read." to "Ik lees een boek.", "book read i a." to "Ik lees een boek.", "read i book a." to "Ik lees een boek.",
            "the cat drinks water." to "De kat drinkt water.", "cat the water drinks." to "De kat drinkt water.", "drinks the cat water." to "De kat drinkt water.",
            "the rocket is fast." to "De raket is snel.", "fast is rocket the." to "De raket is snel.", "is fast the rocket." to "De raket is snel.", "rocket the fast is." to "De raket is snel.",
            "the star is bright." to "De ster is helder.", "bright is star the." to "De ster is helder.", "is the bright star." to "De ster is helder.", "star the bright is." to "De ster is helder.",
            "a star" to "een ster", "air" to "lucht", "blood" to "bloed", "castle" to "kasteel", "clock" to "klok", "clouds" to "wolken", "earth" to "aarde", "egypt" to "Egypte", "explorer" to "ontdekkingsreiziger", "fossil" to "fossiel", "glass" to "glas", "honey" to "honing", "key" to "sleutel", "king" to "koning", "knight" to "ridder", "light" to "licht", "metal" to "metaal", "old text" to "oude tekst", "oxygen" to "zuurstof", "pan" to "pan", "pencil" to "potlood", "phone" to "telefoon", "railway" to "spoorweg", "scissors" to "schaar", "ship" to "schip", "soap" to "zeep", "solid" to "vast", "spoon" to "lepel", "toothbrush" to "tandenborstel",
            "mercury" to "Mercurius", "venus" to "Venus", "mars" to "Mars", "jupiter" to "Jupiter", "saturn" to "Saturnus", "uranus" to "Uranus", "neptune" to "Neptunus"
        )

        private val ANSWER_LABELS_FR = mapOf(
            "a" to "un", "an" to "un", "the" to "le", "is" to "est", "am" to "suis", "are" to "sont", "be" to "être", "many" to "beaucoup",
            "apple" to "pomme", "book" to "livre", "bread" to "pain", "chair" to "chaise", "forest" to "forêt", "house" to "maison", "moon" to "lune", "music" to "musique", "planet" to "planète", "river" to "rivière", "rocket" to "fusée", "school" to "école", "star" to "étoile", "sun" to "soleil", "tree" to "arbre", "water" to "eau",
            "bear" to "ours", "cat" to "chat", "dog" to "chien", "duck" to "canard", "fish" to "poisson", "fox" to "renard", "friend" to "ami", "frog" to "grenouille", "horse" to "cheval", "lion" to "lion", "mouse" to "souris", "owl" to "hibou", "rabbit" to "lapin",
            "i read a book." to "Je lis un livre.", "the cat drinks water." to "Le chat boit de l'eau.", "the rocket is fast." to "La fusée est rapide.", "the star is bright." to "L'étoile est brillante.",
            "a star" to "une étoile", "air" to "air", "blood" to "sang", "castle" to "château", "clock" to "horloge", "clouds" to "nuages", "earth" to "Terre", "egypt" to "Égypte", "explorer" to "explorateur", "fossil" to "fossile", "glass" to "verre", "honey" to "miel", "key" to "clé", "king" to "roi", "knight" to "chevalier", "light" to "lumière", "metal" to "métal", "old text" to "texte ancien", "oxygen" to "oxygène", "pan" to "poêle", "pencil" to "crayon", "phone" to "téléphone", "railway" to "chemin de fer", "scissors" to "ciseaux", "ship" to "navire", "soap" to "savon", "solid" to "solide", "spoon" to "cuillère", "toothbrush" to "brosse à dents",
            "mercury" to "Mercure", "venus" to "Vénus", "mars" to "Mars", "jupiter" to "Jupiter", "saturn" to "Saturne", "uranus" to "Uranus", "neptune" to "Neptune"
        )
    }
}
