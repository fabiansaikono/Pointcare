package be.saikono.poincare.data.audio

internal object SpeechTextNormalizer {
    private val euroPrefix = Regex("€\\s*([0-9][0-9 .]*(?:[,.][0-9]+)?)")
    private val euroSuffix = Regex("([0-9][0-9 .]*(?:[,.][0-9]+)?)\\s*€")
    private val time = Regex("\\b(\\d{1,2}):(\\d{2})\\b")
    private val slashDate = Regex("\\b(\\d{1,2})/(\\d{1,2})/(\\d{2,4})\\b")
    private val dashDate = Regex("\\b(\\d{1,2})-(\\d{1,2})-(\\d{2,4})\\b")
    private val phonemeNotation = Regex("(?<![\\p{L}\\d])/([\\p{L}]{1,3})/(?![\\p{L}\\d])")
    private val compactFraction = Regex("(?<![\\d/])(\\d{1,3})/(\\d{1,3})(?![\\d/])")
    private val missingNumeratorFraction =
        Regex("(?<![\\d/?])\\?\\s*/\\s*(\\d{1,3})(?![\\d/])")
    private val dutchUnitAfterQuantity = Regex(
        """(?i)([0-9]+(?:[.,][0-9]+)?|[?_]+)\s*(km²|hm²|dam²|dm²|cm²|mm²|m²|km³|hm³|dam³|dm³|cm³|mm³|m³|km|hm|dam|m|dm|cm|mm|kg|hg|dag|dg|cg|mg|gr|g|kl|hl|dal|dl|cl|ml|l|min|sec|s|u|h|°c|%)(?=\z|[\s.,;:!?=])"""
    )

    private val dutchUnitWords = mapOf(
        "km" to "kilometer", "hm" to "hectometer", "dam" to "decameter",
        "m" to "meter", "dm" to "decimeter", "cm" to "centimeter", "mm" to "millimeter",
        "kg" to "kilogram", "hg" to "hectogram", "dag" to "decagram",
        "g" to "gram", "gr" to "gram", "dg" to "decigram", "cg" to "centigram", "mg" to "milligram",
        "kl" to "kiloliter", "hl" to "hectoliter", "dal" to "decaliter",
        "l" to "liter", "dl" to "deciliter", "cl" to "centiliter", "ml" to "milliliter",
        "u" to "uur", "h" to "uur", "°c" to "graden Celsius", "%" to "procent",
        "km²" to "vierkante kilometer", "hm²" to "vierkante hectometer",
        "dam²" to "vierkante decameter", "m²" to "vierkante meter",
        "dm²" to "vierkante decimeter", "cm²" to "vierkante centimeter", "mm²" to "vierkante millimeter",
        "km³" to "kubieke kilometer", "hm³" to "kubieke hectometer",
        "dam³" to "kubieke decameter", "m³" to "kubieke meter",
        "dm³" to "kubieke decimeter", "cm³" to "kubieke centimeter", "mm³" to "kubieke millimeter"
    )

    private val dutchVowelSounds = setOf(
        "a", "e", "i", "o", "u", "y", "aa", "ee", "ie", "oo", "uu", "oe", "eu", "ui",
        "ei", "ij", "au", "ou", "ai", "oi", "aai", "ooi", "oei", "eeuw", "ieuw"
    )
    private const val DUTCH_VOWEL_LABEL_DELAY_MS = 200L
    private val whitespace = Regex("\\s+")

    private val dutchDenominators = mapOf(
        2 to "tweede",
        3 to "derde",
        4 to "vierde",
        5 to "vijfde",
        6 to "zesde",
        7 to "zevende",
        8 to "achtste",
        9 to "negende",
        10 to "tiende",
        11 to "elfde",
        12 to "twaalfde",
        20 to "twintigste",
        25 to "vijfentwintigste",
        100 to "honderdste"
    )

    private val dutchWordOverrides = listOf(
        Regex("\\bbananenschil\\b", RegexOption.IGNORE_CASE) to "bananen schil"
    )

    private val dutchSoundExamples = mapOf(
        "a" to "de klank a zoals in kat",
        "e" to "de klank e zoals in pen",
        "i" to "de klank i zoals in vis",
        "o" to "de klank o zoals in pot",
        "u" to "de klank u zoals in bus",
        "aa" to "de klank aa zoals in maan",
        "ee" to "de klank ee zoals in been",
        "ie" to "de klank ie zoals in fiets",
        "oo" to "de klank oo zoals in boom",
        "uu" to "de klank uu zoals in vuur",
        "oe" to "de klank oe zoals in boek",
        "eu" to "de klank eu zoals in neus",
        "ui" to "de klank ui zoals in huis",
        "au" to "de klank au zoals in pauw",
        "ou" to "de klank ou zoals in koud",
        "ei" to "de klank ei zoals in trein",
        "ij" to "de klank ij zoals in ijs"
    )

    fun normalize(rawText: String, locale: String): String {
        val text = if (normalizeLocale(locale) == "nl-BE") expandDutchUnits(rawText) else rawText
        val normalizedLocale = normalizeLocale(locale)
        val raw = text.trim()
        if (raw.isEmpty()) return ""

        if (normalizedLocale == "nl-BE") {
            dutchSoundExamples[raw.lowercase()]?.let { return it }
        }

        var value = raw
            .replace('_', ' ')
            .replace('\n', ' ')
            .replace('\r', ' ')
            .replace('\t', ' ')

        value = phonemeNotation.replace(value) { it.groupValues[1] }
        value = time.replace(value) { match ->
            val hour = match.groupValues[1].toIntOrNull()?.toString() ?: match.groupValues[1]
            val minutes = match.groupValues[2].toIntOrNull() ?: return@replace match.value
            if (minutes == 0) hour else "$hour ${minutes.toString().padStart(2, '0')}"
        }
        value = slashDate.replace(value) { "${it.groupValues[1]} ${it.groupValues[2]} ${it.groupValues[3]}" }
        value = dashDate.replace(value) { "${it.groupValues[1]} ${it.groupValues[2]} ${it.groupValues[3]}" }
        value = euroPrefix.replace(value) { "${it.groupValues[1]} ${currencyWord(normalizedLocale)}" }
        value = euroSuffix.replace(value) { "${it.groupValues[1]} ${currencyWord(normalizedLocale)}" }

        if (normalizedLocale == "nl-BE") {
            value = missingNumeratorFraction.replace(value) { match ->
                val denominator = match.groupValues[1].toIntOrNull()
                val denominatorWord = denominator?.let(dutchDenominators::get)
                    ?: return@replace match.value
                "Hoeveel $denominatorWord"
            }
            value = compactFraction.replace(value) { match ->
                val numerator = match.groupValues[1].toIntOrNull()
                val denominator = match.groupValues[2].toIntOrNull()
                val denominatorWord = denominator?.let(dutchDenominators::get)
                    ?: return@replace match.value
                val numeratorText = if (numerator == 1) "één" else match.groupValues[1]
                "$numeratorText $denominatorWord"
            }
            dutchWordOverrides.forEach { (pattern, spokenText) ->
                value = pattern.replace(value, spokenText)
            }
        }

        val operators = operatorWords(normalizedLocale)
        value = replaceNumericOperator(value, "\\+", operators.plus)
        value = replaceNumericOperator(value, "[-−–—]", operators.minus)
        value = replaceNumericOperator(value, "[xX×*·]", operators.times)
        value = replaceNumericOperator(value, "[:∶]", operators.dividedBy)
        value = replaceNumericOperator(value, "[÷/]", operators.dividedBy)
        value = value.replace(Regex("(?<=\\d)\\s*=\\s*(?=\\d)"), " ${operators.equals} ")

        value = value
            .replace(Regex("[–—]"), ", ")
            .replace(Regex("(?<=\\p{L})-(?=\\p{L})"), " ")
            .replace(whitespace, " ")
            .trim()

        return value
    }

    fun speedFor(text: String, locale: String, requestedRate: Float): Float {
        val safeRate = requestedRate.takeIf { it > 0f }?.coerceIn(0.70f, 1.35f) ?: 1.0f
        val isDutchSoundCue = normalizeLocale(locale) == "nl-BE" &&
            text.trim().startsWith("de klank", ignoreCase = true)
        return if (isDutchSoundCue) minOf(safeRate, 0.88f) else safeRate
    }

    fun normalizeLocale(locale: String): String = when (locale.trim().replace('_', '-').lowercase()) {
        "nl", "nl-be" -> "nl-BE"
        "fr", "fr-be", "fr-fr" -> "fr-BE"
        "en", "en-us", "en-gb" -> "en-US"
        else -> locale.ifBlank { "nl-BE" }
    }

    fun isBelgianDutch(locale: String): Boolean = normalizeLocale(locale) == "nl-BE"

    fun leadingSilenceMillisFor(text: String, locale: String): Long {
        if (normalizeLocale(locale) != "nl-BE") return 0L
        val normalized = text.trim().lowercase()
        val sound = if (normalized.startsWith("de klank ")) {
            normalized.removePrefix("de klank ").substringBefore(' ')
        } else {
            normalized
        }
        return if (sound in dutchVowelSounds) DUTCH_VOWEL_LABEL_DELAY_MS else 0L
    }

    private fun expandDutchUnits(value: String): String =
        dutchUnitAfterQuantity.replace(value) { match ->
            val quantity = match.groupValues[1]
            val unit = match.groupValues[2].lowercase()
            val unitWord = when (unit) {
                "min" -> if (quantity.toDoubleOrNull() == 1.0) "minuut" else "minuten"
                "s", "sec" -> if (quantity.toDoubleOrNull() == 1.0) "seconde" else "seconden"
                else -> dutchUnitWords[unit] ?: return@replace match.value
            }
            "$quantity $unitWord"
        }

    private fun replaceNumericOperator(value: String, operatorPattern: String, spoken: String): String =
        value.replace(Regex("(?<=\\d)\\s*$operatorPattern\\s*(?=\\d)"), " $spoken ")

    private fun currencyWord(locale: String): String = when (locale) {
        "fr-BE" -> "euros"
        else -> "euro"
    }

    private fun operatorWords(locale: String): OperatorWords = when (locale) {
        "fr-BE" -> OperatorWords("plus", "moins", "fois", "divisé par", "égale")
        "en-US" -> OperatorWords("plus", "minus", "times", "divided by", "equals")
        else -> OperatorWords("plus", "min", "maal", "gedeeld door", "is")
    }

    private data class OperatorWords(
        val plus: String,
        val minus: String,
        val times: String,
        val dividedBy: String,
        val equals: String
    )
}
