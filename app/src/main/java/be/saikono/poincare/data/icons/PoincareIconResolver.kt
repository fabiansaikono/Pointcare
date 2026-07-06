package be.saikono.poincare.data.icons

import android.content.Context
import androidx.annotation.DrawableRes
import be.saikono.poincare.R
import java.util.concurrent.ConcurrentHashMap

class PoincareIconResolver(
    private val catalog: PoincareIconCatalog
) {
    private val resourceCache = ConcurrentHashMap<String, Int>()

    @DrawableRes
    fun resolveDrawableRes(context: Context, icon: PoincareIcon?): Int {
        val fileName = icon?.androidVectorFile?.substringAfterLast('/')?.removeSuffix(".xml")
        val resourceName = fileName?.let { normalizeResourceName(it) }?.takeIf { it.isNotBlank() }
        if (resourceName == null) return fallbackDrawableRes
        return resourceCache.getOrPut(resourceName) {
            context.resources.getIdentifier(resourceName, "drawable", context.packageName).takeIf { it != 0 }
                ?: fallbackDrawableRes
        }
    }

    @DrawableRes
    fun resolveById(context: Context, iconId: String): Int = resolveDrawableRes(context, catalog.findById(iconId))

    @DrawableRes
    fun resolveTopic(context: Context, topic: String): Int = resolveDrawableRes(context, catalog.findByTopic(topic))

    @DrawableRes
    fun resolveSkill(context: Context, skillRef: String): Int = resolveDrawableRes(context, catalog.findBySkill(skillRef))

    @DrawableRes
    fun resolveUi(context: Context, key: String): Int = resolveDrawableRes(context, catalog.findByUiKey(key))

    @DrawableRes
    fun resolveReward(context: Context, key: String): Int = resolveDrawableRes(context, catalog.findByReward(key))

    @DrawableRes
    fun resolveContentType(context: Context, key: String): Int = resolveDrawableRes(context, catalog.findByContentType(key))

    @DrawableRes
    fun resolveExercise(context: Context, exercise: be.saikono.poincare.data.content.PoincareExercise): Int {
        val icon = exercise.iconRef?.let(catalog::findById)
            ?: exercise.topic?.let(catalog::findByTopic)
            ?: exercise.skillRef?.let(catalog::findBySkill)
            ?: exercise.type?.let(catalog::findByContentType)
        return resolveDrawableRes(context, icon)
    }

    companion object {
        @DrawableRes
        val fallbackDrawableRes: Int = R.drawable.ic_ui_info

        fun fromAssets(context: Context): PoincareIconResolver = PoincareIconResolver(PoincareIconCatalog.fromAssets(context))
    }
}

private fun normalizeResourceName(value: String): String = normalizeKey(value).replace('-', '_')