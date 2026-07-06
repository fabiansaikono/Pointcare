package be.saikono.poincare.data.content

data class PoincareContentPack(
    val id: String?,
    val version: String?,
    val language: String?,
    val title: String?,
    val description: String?,
    val metadata: Map<String, String>,
    val mainCategory: String?,
    val defaultLocale: String?,
    val subcategories: List<PoincareSubcategory>,
    val skills: List<PoincareSkill>,
    val exercises: List<PoincareExercise>,
    val articles: List<PoincareArticle>,
    val worksheets: List<PoincareWorksheet>
)

data class PoincareExercise(
    val id: String,
    val progressKey: String?,
    val mainCategory: String?,
    val subcategoryId: String?,
    val subject: String?,
    val grade: Int?,
    val gradeDisplay: String?,
    val topic: String?,
    val skillRef: String?,
    val difficulty: Int?,
    val difficultyStars: Int?,
    val difficultyDisplay: String?,
    val difficultyValueDisplay: String?,
    val type: String?,
    val contentType: String?,
    val iconRef: String?,
    val skillIconRef: String?,
    val instruction: String?,
    val questionText: String?,
    val missingMarker: String?,
    val missingMarkerColor: String?,
    val missingVisibleLine: String?,
    val missingTtsFullWord: String?,
    val promptTtsTexts: Map<String, String>,
    val solutionValue: String?,
    val audioSources: PoincareAudioSources?,
    val visualSources: PoincareVisualSources?,
    val answers: List<PoincareAnswer>,
    val translations: Map<String, PoincareExerciseTranslation>,
    val feedback: PoincareFeedback?,
    val tags: List<String>
)

data class PoincareAnswer(
    val id: String?,
    val text: String,
    val correct: Boolean,
    val value: String? = null,
    val displays: Map<String, String> = emptyMap(),
    val speaks: Map<String, String> = emptyMap()
)

data class PoincareFeedback(
    val correct: String?,
    val incorrect: String?
)

data class PoincareExerciseTranslation(
    val locale: String,
    val title: String?,
    val instruction: String?,
    val question: String?,
    val questionTts: String?,
    val answers: List<PoincareAnswer>,
    val feedback: PoincareFeedback?
)

data class PoincareAudioSources(
    val visibleText: Boolean,
    val playButtonOnly: Boolean,
    val ttsRequired: Boolean,
    val kind: String?,
    val sourceLocale: String?,
    val targetLocale: String?,
    val audio: List<PoincareAudioSource>
)

data class PoincareAudioSource(
    val locale: String,
    val ttsLanguage: String,
    val text: String,
    val audioId: String,
    val ttsText: String? = null,
    val phoneticText: String? = null
)

data class PoincareVisualSources(
    val visibleText: Boolean,
    val kind: String?,
    val iconType: String?,
    val displayStyle: String?,
    val targetValue: String?,
    val visuals: List<PoincareVisualSource>
)

data class PoincareVisualSource(
    val locale: String,
    val icon: String,
    val alt: String?,
    val labelVisible: Boolean
)

data class PoincareSubcategory(
    val id: String,
    val labels: Map<String, String>,
    val iconRef: String?,
    val skillRef: String?,
    val skillIconRef: String?
)

data class PoincareSkill(
    val id: String,
    val label: String?,
    val description: String?,
    val iconRef: String?
)

data class PoincareArticle(
    val id: String,
    val title: String?,
    val summary: String?,
    val body: List<String>,
    val subject: String?,
    val grade: Int?,
    val skillRef: String?,
    val iconRef: String?
)

data class PoincareWorksheet(
    val id: String,
    val title: String?,
    val subject: String?,
    val grade: Int?,
    val skillRef: String?,
    val iconRef: String?
)