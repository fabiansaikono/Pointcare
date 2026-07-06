package be.saikono.poincare.data.icons

import android.content.Context
import org.w3c.dom.Element
import java.io.InputStream
import javax.xml.parsers.DocumentBuilderFactory

class PoincareIconCatalog private constructor(
    val icons: List<PoincareIcon>
) {
    private val byId = icons.associateBy { normalizeKey(it.id) }
    private val byTopic = icons.mapNotNull { icon -> icon.topic?.let { normalizeKey(it) to icon } }.toMap()
    private val bySkill = icons.mapNotNull { icon -> icon.skillRef?.let { normalizeKey(it) to icon } }.toMap()
    private val byUiKey = icons.filter { it.section == "uiIcons" }.mapNotNull { icon -> icon.key?.let { normalizeKey(it) to icon } }.toMap()
    private val byRewardKey = icons.filter { it.section == "rewardIcons" }.flatMap { icon ->
        listOfNotNull(icon.key, icon.rewardType, icon.id.removePrefix("reward-")).map { normalizeKey(it) to icon }
    }.toMap()
    private val byContentType = icons.filter { it.section == "contentTypeIcons" }.flatMap { icon ->
        listOfNotNull(icon.key, icon.contentType, icon.id.removePrefix("content-")).map { normalizeKey(it) to icon }
    }.toMap()

    fun findById(id: String): PoincareIcon? = byId[normalizeKey(id)]
    fun findByTopic(topic: String): PoincareIcon? = byTopic[normalizeKey(topic)] ?: findById("topic-$topic")
    fun findBySkill(skillRef: String): PoincareIcon? = bySkill[normalizeKey(skillRef)] ?: findById("skill-$skillRef")
    fun findByUiKey(key: String): PoincareIcon? = byUiKey[normalizeKey(key)] ?: findById("ui-$key")
    fun findByReward(key: String): PoincareIcon? = byRewardKey[normalizeKey(key)] ?: findById("reward-$key")
    fun findByContentType(key: String): PoincareIcon? = byContentType[normalizeKey(key)] ?: findById("content-$key")
    fun groupedBySection(): Map<String, List<PoincareIcon>> = icons.groupBy { it.section }

    companion object {
        private const val ASSET_PATH = "poincare/icons/poincare-icons-final-complete-pack-v1.xml"

        fun fromAssets(context: Context): PoincareIconCatalog =
            context.assets.open(ASSET_PATH).use { fromStream(it) }

        fun fromStream(stream: InputStream): PoincareIconCatalog {
            val document = secureDocumentBuilderFactory().newDocumentBuilder().parse(stream)
            val root = document.documentElement
            val icons = buildList {
                val sections = root.childNodes
                for (sectionIndex in 0 until sections.length) {
                    val section = sections.item(sectionIndex) as? Element ?: continue
                    val iconNodes = section.getElementsByTagName("icon")
                    for (iconIndex in 0 until iconNodes.length) {
                        val icon = iconNodes.item(iconIndex) as? Element ?: continue
                        val id = icon.attr("id") ?: continue
                        add(
                            PoincareIcon(
                                id = id,
                                section = section.tagName,
                                label = icon.attr("label"),
                                androidVectorFile = icon.attr("androidVectorFile"),
                                svgFile = icon.attr("svgFile"),
                                style = icon.attr("style"),
                                viewport = icon.attr("viewport"),
                                key = icon.attr("key"),
                                skillRef = icon.attr("skillRef"),
                                topic = icon.attr("topic"),
                                contentType = icon.attr("contentType"),
                                rewardType = icon.attr("rewardType"),
                                theme = icon.attr("theme")
                            )
                        )
                    }
                }
            }
            return PoincareIconCatalog(icons)
        }
    }
}

internal fun normalizeKey(value: String): String = java.text.Normalizer.normalize(value.trim(), java.text.Normalizer.Form.NFD)
    .replace("\\p{Mn}+".toRegex(), "")
    .lowercase()
    .replace("&", " en ")
    .replace("[^a-z0-9]+".toRegex(), "-")
    .trim('-')

private fun Element.attr(name: String): String? =
    if (hasAttribute(name)) getAttribute(name).takeIf { it.isNotBlank() } else null

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