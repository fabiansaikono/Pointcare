package be.saikono.poincare.domain.curriculum.generation

import be.saikono.poincare.domain.curriculum.LessonActivitySource
import be.saikono.poincare.domain.curriculum.LessonSeedPolicy
import java.security.MessageDigest

/**
 * Stable seed derivation for generated lesson content.
 *
 * Kotlin/JVM hashCode and collection iteration order are deliberately not used.
 * Every component is UTF-8 encoded with an explicit length prefix before SHA-256.
 */
object DeterministicLessonSeed {
    private const val CONTROL_PARAMETER = "editorial_status"

    fun derive(
        source: LessonActivitySource.Generated,
        context: LessonGenerationContext
    ): LessonGenerationSeed {
        val attemptComponent = when (source.seedPolicy) {
            LessonSeedPolicy.STABLE_PER_PROFILE_LESSON -> "lesson"
            LessonSeedPolicy.STABLE_PER_PROFILE_ATTEMPT ->
                context.attemptId ?: error("An attemptId is required for STABLE_PER_PROFILE_ATTEMPT")
        }
        val parts = buildList {
            add("poincare.lesson-generation.seed.v1")
            add(source.generatorId)
            add(source.generatorVersion.toString())
            add(context.profileId)
            add(context.unitId)
            add(context.lessonId)
            add(context.sectionId)
            add(source.seedPolicy.name)
            add(attemptComponent)
            source.parameters
                .filterKeys { it != CONTROL_PARAMETER }
                .toSortedMap()
                .forEach { (key, value) ->
                    add(key)
                    add(value)
                }
        }
        return fromParts(parts)
    }

    internal fun fromParts(parts: List<String>): LessonGenerationSeed {
        val digest = MessageDigest.getInstance("SHA-256")
        parts.forEach { part ->
            val bytes = part.toByteArray(Charsets.UTF_8)
            digest.update(byteArrayOf(
                ((bytes.size ushr 24) and 0xff).toByte(),
                ((bytes.size ushr 16) and 0xff).toByte(),
                ((bytes.size ushr 8) and 0xff).toByte(),
                (bytes.size and 0xff).toByte()
            ))
            digest.update(bytes)
        }
        val hash = digest.digest()
        var value = 0L
        for (index in 0 until Long.SIZE_BYTES) {
            value = (value shl 8) or (hash[index].toLong() and 0xffL)
        }
        val fingerprint = hash.take(Long.SIZE_BYTES).joinToString(separator = "") {
            "%02x".format(it.toInt() and 0xff)
        }
        return LessonGenerationSeed(value = value, fingerprint = fingerprint)
    }
}

/**
 * SplitMix64-based deterministic random stream.
 *
 * This is intentionally owned by the lesson-generation contract so output does
 * not drift when Kotlin's default Random implementation changes.
 */
internal class StableLessonRandom(seed: Long) {
    private var state: Long = seed

    fun nextLong(): Long {
        state += -7046029254386353131L
        var value = state
        value = (value xor (value ushr 30)) * -4658895280553007687L
        value = (value xor (value ushr 27)) * -7723592293110705685L
        return value xor (value ushr 31)
    }

    fun nextInt(bound: Int): Int {
        require(bound > 0) { "bound must be positive" }
        return ((nextLong() ushr 1) % bound.toLong()).toInt()
    }

    fun nextInt(fromInclusive: Int, untilExclusive: Int): Int {
        require(untilExclusive > fromInclusive) {
            "untilExclusive must be greater than fromInclusive"
        }
        return fromInclusive + nextInt(untilExclusive - fromInclusive)
    }
}
