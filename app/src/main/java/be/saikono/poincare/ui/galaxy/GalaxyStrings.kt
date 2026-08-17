package be.saikono.poincare.ui.galaxy

import android.content.Context
import android.content.res.Configuration
import androidx.annotation.StringRes
import java.util.Locale

fun localizedGalaxyString(
    context: Context,
    @StringRes stringRes: Int,
    localeTag: String,
    vararg formatArgs: Any
): String {
    val languageTag = when (localeTag.substringBefore('_').substringBefore('-').lowercase()) {
        "fr" -> "fr-BE"
        "en" -> "en-US"
        else -> "nl-BE"
    }
    val configuration = Configuration(context.resources.configuration).apply {
        setLocale(Locale.forLanguageTag(languageTag))
    }
    return context.createConfigurationContext(configuration).resources.getString(stringRes, *formatArgs)
}
