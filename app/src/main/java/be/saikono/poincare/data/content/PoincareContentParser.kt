package be.saikono.poincare.data.content

import org.w3c.dom.Element
import org.w3c.dom.Node
import java.io.InputStream
import javax.xml.parsers.DocumentBuilderFactory

class PoincareContentParser {
    fun parse(stream: InputStream): PoincareContentPack {
        val document = secureDocumentBuilderFactory().newDocumentBuilder().parse(stream)
        val root = document.documentElement
        val metadata = root.directChild("metadata")?.children().orEmpty().associate { it.tagName to it.textContent.trim() }

        return PoincareContentPack(
            id = root.attr("packId"),
            version = root.attr("version"),
            language = root.attr("language"),
            title = metadata["title"],
            description = metadata["description"],
            metadata = metadata,
            mainCategory = root.attr("mainCategory"),
            defaultLocale = root.attr("defaultLocale") ?: root.attr("language"),
            subcategories = parseSubcategories(root.directChild("subcategories")),
            skills = parseSkills(root.directChild("skills")),
            exercises = parseExercises(root.directChild("exercises")),
            articles = parseArticles(root.directChild("articles")),
            worksheets = parseWorksheets(root.directChild("worksheets"))
        )
    }

    private fun parseSkills(parent: Element?): List<PoincareSkill> = parent?.children("skill").orEmpty().mapNotNull { skill ->
        val id = skill.attr("id") ?: return@mapNotNull null
        PoincareSkill(
            id = id,
            label = skill.directChildText("name") ?: skill.attr("label"),
            description = skill.directChildText("description"),
            iconRef = skill.attr("iconRef")
        )
    }

    private fun parseSubcategories(parent: Element?): List<PoincareSubcategory> = parent?.children("subcategory").orEmpty().mapNotNull { subcategory ->
        val id = subcategory.attr("id") ?: return@mapNotNull null
        val titles = subcategory.children("title").associate { title ->
            (title.attr("locale") ?: "nl-BE") to title.textContent.trim()
        }
        PoincareSubcategory(
            id = id,
            labels = mapOf(
                "nl-BE" to (subcategory.attr("label") ?: titles["nl-BE"] ?: id),
                "en-US" to (subcategory.attr("labelEn") ?: titles["en-US"] ?: subcategory.attr("label") ?: titles["nl-BE"] ?: id),
                "fr-BE" to (subcategory.attr("labelFr") ?: titles["fr-BE"] ?: subcategory.attr("label") ?: titles["nl-BE"] ?: id)
            ),
            iconRef = subcategory.attr("iconRef"),
            skillRef = subcategory.attr("skillRef"),
            skillIconRef = subcategory.attr("skillIconRef")
        )
    }

    private fun parseExercises(parent: Element?): List<PoincareExercise> = parent?.children("exercise").orEmpty().mapNotNull { exercise ->
        val id = exercise.attr("id") ?: return@mapNotNull null
        val translations = parseTranslations(exercise.directChild("translations"))
            .ifEmpty { parsePromptTranslations(exercise.directChild("prompts"), exercise.directChild("answers")) }
        PoincareExercise(
            id = id,
            progressKey = exercise.attr("progressKey"),
            mainCategory = exercise.attr("mainCategory"),
            subcategoryId = exercise.attr("subcategoryId") ?: exercise.attr("subCategory"),
            subject = exercise.attr("subject"),
            grade = exercise.attr("grade")?.toIntOrNull(),
            gradeDisplay = exercise.attr("gradeDisplay"),
            topic = exercise.attr("topic"),
            skillRef = exercise.attr("skillRef"),
            difficulty = exercise.attr("difficulty")?.toIntOrNull(),
            difficultyStars = exercise.attr("difficultyStars")?.toIntOrNull() ?: exercise.attr("difficulty")?.toIntOrNull(),
            difficultyDisplay = exercise.attr("difficultyDisplay"),
            difficultyValueDisplay = exercise.attr("difficultyValueDisplay"),
            type = exercise.attr("type"),
            contentType = exercise.attr("contentType"),
            iconRef = exercise.attr("iconRef"),
            skillIconRef = exercise.attr("skillIconRef"),
            instruction = exercise.directChildText("instruction"),
            questionText = exercise.directChild("question")?.directChildText("text") ?: exercise.directChildText("question") ?: exercise.directChild("prompts")?.directChildText("prompt"),
            missingMarker = exercise.attr("missingMarker") ?: exercise.directChild("missingLetterDisplay")?.attr("marker"),
            missingMarkerColor = exercise.attr("missingMarkerColor") ?: exercise.directChild("missingLetterDisplay")?.attr("markerColor"),
            missingVisibleLine = exercise.directChild("missingLetterDisplay")?.attr("visibleLine"),
            missingTtsFullWord = exercise.directChild("missingLetterDisplay")?.attr("ttsFullWord"),
            promptTtsTexts = parsePromptTtsTexts(exercise.directChild("prompts")),
            solutionValue = exercise.directChild("solution")?.directChildText("value") ?: exercise.directChild("solution")?.attr("display") ?: exercise.directChild("solution")?.attr("value"),
            audioSources = parseAudioSources(exercise.directChild("audioSources")),
            visualSources = parseVisualSources(exercise.directChild("visualSources")),
            answers = parseAnswers(exercise.directChild("answers")),
            translations = translations,
            feedback = parseFeedback(exercise.directChild("feedback")),
            tags = parseTags(exercise.directChild("tags"))
        )
    }

    private fun parseTranslations(parent: Element?): Map<String, PoincareExerciseTranslation> = parent?.children("translation").orEmpty().mapNotNull { translation ->
        val locale = translation.attr("locale") ?: return@mapNotNull null
        val feedback = parseFeedback(translation.directChild("feedback"))
        locale to PoincareExerciseTranslation(
            locale = locale,
            title = translation.directChildText("title"),
            instruction = translation.directChildText("instruction"),
            question = translation.directChildText("question"),
            questionTts = translation.directChild("question")?.attr("ttsText"),
            answers = parseAnswers(translation.directChild("answers")),
            feedback = feedback
        )
    }.toMap()


    private fun parseAudioSources(parent: Element?): PoincareAudioSources? {
        if (parent == null) return null
        val audio = parent.children("audio").mapNotNull { audio ->
            val locale = audio.attr("locale") ?: return@mapNotNull null
            val text = audio.attr("text")?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
            val ttsLanguage = audio.attr("ttsLanguage") ?: locale
            val audioId = audio.attr("audioId") ?: return@mapNotNull null
            PoincareAudioSource(
                locale = locale,
                ttsLanguage = ttsLanguage,
                text = text,
                audioId = audioId,
                ttsText = audio.attr("ttsText"),
                phoneticText = audio.attr("phoneticText")
            )
        }
        return PoincareAudioSources(
            visibleText = parent.attr("visibleText")?.toBooleanStrictOrNull() ?: true,
            playButtonOnly = parent.attr("playButtonOnly")?.toBooleanStrictOrNull() ?: false,
            ttsRequired = parent.attr("ttsRequired")?.toBooleanStrictOrNull() ?: false,
            ttsShouldUsePhoneticText =
                parent.attr("ttsShouldUsePhoneticText")?.toBooleanStrictOrNull() ?: false,
            kind = parent.attr("kind"),
            sourceLocale = parent.attr("sourceLocale"),
            targetLocale = parent.attr("targetLocale"),
            audio = audio
        )
    }

    private fun parseVisualSources(parent: Element?): PoincareVisualSources? {
        if (parent == null) return null
        val visuals = parent.children("visual").mapNotNull { visual ->
            val locale = visual.attr("locale") ?: return@mapNotNull null
            val icon = visual.attr("icon")?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
            PoincareVisualSource(
                locale = locale,
                icon = icon,
                alt = visual.attr("alt"),
                labelVisible = visual.attr("labelVisible")?.toBooleanStrictOrNull() ?: false
            )
        }
        return PoincareVisualSources(
            visibleText = parent.attr("visibleText")?.toBooleanStrictOrNull() ?: true,
            kind = parent.attr("kind"),
            iconType = parent.attr("iconType"),
            displayStyle = parent.attr("displayStyle"),
            targetValue = parent.attr("targetValue"),
            visuals = visuals
        )
    }
    private fun parsePromptTranslations(promptsParent: Element?, answersParent: Element?): Map<String, PoincareExerciseTranslation> {
        val answers = parseAnswers(answersParent)
        return promptsParent?.children("prompt").orEmpty().mapNotNull { prompt ->
            val locale = prompt.attr("locale") ?: return@mapNotNull null
            locale to PoincareExerciseTranslation(
                locale = locale,
                title = null,
                instruction = null,
                question = prompt.textContent.trim().takeIf { it.isNotBlank() },
                questionTts = prompt.attr("ttsText"),
                answers = answers,
                feedback = null
            )
        }.toMap()
    }

    private fun parsePromptTtsTexts(promptsParent: Element?): Map<String, String> = promptsParent?.children("prompt").orEmpty().mapNotNull { prompt ->
        val locale = prompt.attr("locale") ?: return@mapNotNull null
        val ttsText = prompt.attr("ttsText")?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
        locale to ttsText
    }.toMap()

    private fun parseAnswers(parent: Element?): List<PoincareAnswer> = parent?.children("answer").orEmpty().map { answer ->
        val displays = answer.children("display").mapIndexed { index, display ->
            (display.attr("locale") ?: if (index == 0) "nl-BE" else "") to display.textContent.trim()
        }.filter { (locale, text) -> locale.isNotBlank() && text.isNotBlank() }.toMap()
        val speaks = answer.children("speak").mapIndexed { index, speak ->
            val speech = speak.attr("ttsText") ?: speak.attr("phoneticText") ?: speak.textContent.trim()
            (speak.attr("locale") ?: if (index == 0) "nl-BE" else "") to speech
        }.filter { (locale, text) -> locale.isNotBlank() && text.isNotBlank() }.toMap()
        PoincareAnswer(
            id = answer.attr("id"),
            text = displays["nl-BE"] ?: displays["en-US"] ?: displays.values.firstOrNull() ?: answer.attr("display") ?: answer.attr("value") ?: answer.textContent.trim(),
            correct = answer.attr("correct")?.equals("true", ignoreCase = true) == true,
            value = answer.attr("value"),
            displays = displays,
            speaks = speaks
        )
    }

    private fun parseFeedback(parent: Element?): PoincareFeedback? {
        if (parent == null) return null
        return PoincareFeedback(
            correct = parent.directChildText("correct"),
            incorrect = parent.directChildText("incorrect")
        )
    }

    private fun parseTags(parent: Element?): List<String> = parent?.children().orEmpty().mapNotNull { tag ->
        tag.textContent.trim().takeIf { it.isNotBlank() }
    }

    private fun parseArticles(parent: Element?): List<PoincareArticle> = parent?.children("article").orEmpty().mapNotNull { article ->
        val id = article.attr("id") ?: return@mapNotNull null
        PoincareArticle(
            id = id,
            title = article.directChildText("title"),
            summary = article.directChildText("summary"),
            body = article.directChild("body")?.children("paragraph").orEmpty().map { it.textContent.trim() },
            subject = article.attr("subject"),
            grade = article.attr("grade")?.toIntOrNull(),
            skillRef = article.attr("skillRef"),
            iconRef = article.attr("iconRef")
        )
    }

    private fun parseWorksheets(parent: Element?): List<PoincareWorksheet> = parent?.children("worksheet").orEmpty().mapNotNull { worksheet ->
        val id = worksheet.attr("id") ?: return@mapNotNull null
        PoincareWorksheet(
            id = id,
            title = worksheet.directChildText("title") ?: worksheet.attr("title"),
            subject = worksheet.attr("subject"),
            grade = worksheet.attr("grade")?.toIntOrNull(),
            skillRef = worksheet.attr("skillRef"),
            iconRef = worksheet.attr("iconRef")
        )
    }
}

private fun Element.attr(name: String): String? =
    if (hasAttribute(name)) getAttribute(name).takeIf { it.isNotBlank() } else null

private fun Element.directChild(name: String): Element? {
    var node = firstChild
    while (node != null) {
        val element = node as? Element
        if (element?.tagName == name) return element
        node = node.nextSibling
    }
    return null
}

private fun Element.directChildText(name: String): String? = directChild(name)?.textContent?.trim()?.takeIf { it.isNotBlank() }

private fun Element.children(name: String? = null): List<Element> = buildList {
    var node: Node? = firstChild
    while (node != null) {
        val element = node as? Element
        if (element != null && (name == null || element.tagName == name)) add(element)
        node = node.nextSibling
    }
}

private fun secureDocumentBuilderFactory(): DocumentBuilderFactory = DocumentBuilderFactory.newInstance().apply {
    isNamespaceAware = false
    isExpandEntityReferences = false
    setFeatureIfSupported("http://apache.org/xml/features/disallow-doctype-decl", true)
    setFeatureIfSupported("http://xml.org/sax/features/external-general-entities", false)
    setFeatureIfSupported("http://xml.org/sax/features/external-parameter-entities", false)
    setFeatureIfSupported("http://apache.org/xml/features/nonvalidating/load-external-dtd", false)
}

private fun DocumentBuilderFactory.setFeatureIfSupported(name: String, value: Boolean) {
    runCatching { setFeature(name, value) }
}
