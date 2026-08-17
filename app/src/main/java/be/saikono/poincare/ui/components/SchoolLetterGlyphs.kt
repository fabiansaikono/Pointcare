package be.saikono.poincare.ui.components

import androidx.compose.ui.geometry.Offset

/**
 * Simple centre-line templates based on the Handschrift D'haese.nu letter house.
 * Body letters remain between x-height and baseline, ascenders reach the roof line and
 * descenders enter the lower room. The paths intentionally omit ornamental lead-in and
 * lead-out loops so a beginning writer traces only the movement that forms the letter.
 */
internal data class SchoolLetterGlyph(val strokes: List<List<Offset>>)

internal object SchoolLetterGlyphs {
    private const val TOP = .12f
    private const val X = .43f
    private const val BASE = .76f
    private const val DESC = .94f

    fun forLetter(letter: String): SchoolLetterGlyph =
        basicStrokes[letter] ?: glyphs[letter.singleOrNull()] ?: glyphs.getValue('a')

    internal val supportedLetters: Set<Char>
        get() = glyphs.keys

    private fun s(vararg coordinates: Float): List<Offset> {
        require(coordinates.size % 2 == 0)
        return coordinates.asList().chunked(2).map { Offset(it[0], it[1]) }
    }

    private fun g(vararg strokes: List<Offset>) = SchoolLetterGlyph(strokes.toList())

    /** Pre-writing movements used before a child is shown any letter. */
    private val basicStrokes = mapOf(
        "line-horizontal" to g(s(.18f, .58f, .82f, .58f)),
        "line-vertical" to g(s(.50f, .18f, .50f, .82f)),
        "line-slanted" to g(s(.28f, .22f, .72f, .80f)),
        "shape-arch" to g(s(.18f, .72f, .24f, .46f, .38f, .30f, .55f, .32f, .70f, .49f, .78f, .72f)),
        "shape-circle" to g(s(.50f, .24f, .34f, .28f, .24f, .44f, .25f, .62f, .38f, .76f, .56f, .76f, .72f, .64f, .76f, .46f, .65f, .30f, .50f, .24f))
    )

    private val lowerCase = mapOf(
        'a' to g(s(.22f,BASE, .31f,.63f, .32f,.50f, .43f,X, .55f,.48f, .58f,.61f, .53f,.71f, .42f,.74f, .33f,.68f, .32f,.56f, .39f,.47f, .55f,X, .59f,.49f, .59f,BASE, .73f,.69f)),
        'b' to g(s(.26f,BASE, .31f,.54f, .32f,.24f, .40f,TOP, .48f,.18f, .45f,.30f, .33f,.47f, .34f,.66f, .43f,.74f, .55f,.71f, .61f,.61f, .58f,.49f, .48f,.45f, .37f,.50f, .40f,.64f, .53f,.72f, .72f,.69f)),
        'c' to g(s(.61f,.49f, .53f,X, .41f,.45f, .33f,.54f, .32f,.66f, .41f,.74f, .54f,.72f, .64f,.67f)),
        'd' to g(s(.22f,BASE, .31f,.63f, .32f,.50f, .43f,X, .55f,.48f, .58f,.61f, .53f,.71f, .42f,.74f, .33f,.68f, .32f,.56f, .39f,.47f, .55f,X, .59f,.49f, .59f,.28f, .63f,TOP, .68f,.21f, .62f,.40f, .60f,BASE, .74f,.69f)),
        'e' to g(s(.25f,.61f, .38f,.56f, .56f,.54f, .54f,.47f, .45f,X, .35f,.47f, .30f,.57f, .33f,.69f, .44f,.74f, .58f,.71f, .67f,.67f)),
        'f' to g(
            s(.31f,BASE, .34f,.51f, .35f,.25f, .44f,TOP, .51f,.18f, .47f,.31f, .38f,.47f, .36f,.70f, .37f,.87f, .43f,DESC, .49f,.89f),
            s(.25f,.48f, .52f,.47f)
        ),
        'g' to g(s(.22f,BASE, .31f,.63f, .32f,.50f, .43f,X, .55f,.48f, .58f,.61f, .53f,.71f, .42f,.74f, .33f,.68f, .32f,.56f, .39f,.47f, .55f,X, .59f,.49f, .58f,.78f, .55f,.90f, .47f,DESC, .39f,.89f)),
        'h' to g(s(.27f,BASE, .31f,.53f, .32f,.24f, .40f,TOP, .48f,.18f, .45f,.31f, .33f,.47f, .33f,BASE, .38f,.61f, .46f,.47f, .56f,X, .63f,.49f, .62f,BASE, .75f,.69f)),
        'i' to g(s(.31f,X, .32f,.62f, .34f,.74f, .49f,.70f), s(.32f,.29f, .33f,.27f)),
        'j' to g(s(.40f,X, .40f,.72f, .38f,.88f, .31f,DESC, .24f,.90f), s(.40f,.29f, .41f,.27f)),
        'k' to g(
            s(.28f,BASE, .31f,.52f, .32f,.24f, .40f,TOP, .48f,.18f, .45f,.31f, .33f,.47f, .33f,BASE),
            s(.60f,X, .50f,.49f, .39f,.57f, .50f,.60f, .62f,BASE)
        ),
        'l' to g(s(.28f,BASE, .31f,.53f, .32f,.24f, .40f,TOP, .48f,.18f, .45f,.31f, .34f,.48f, .34f,.67f, .42f,.74f, .55f,.71f)),
        'm' to g(s(.19f,BASE, .22f,.54f, .23f,X, .23f,BASE, .28f,.60f, .36f,.46f, .45f,X, .51f,.49f, .50f,BASE, .55f,.60f, .63f,.46f, .72f,X, .78f,.49f, .77f,BASE)),
        'n' to g(s(.23f,BASE, .26f,.54f, .27f,X, .27f,BASE, .33f,.60f, .42f,.46f, .53f,X, .60f,.49f, .59f,BASE, .72f,.69f)),
        'o' to g(s(.24f,.60f, .27f,.49f, .38f,X, .51f,.46f, .58f,.55f, .57f,.66f, .48f,.74f, .36f,.73f, .28f,.66f, .28f,.54f, .37f,.46f, .51f,X, .61f,.53f, .70f,.69f)),
        'p' to g(s(.27f,DESC, .28f,.70f, .29f,.51f, .36f,X, .49f,.45f, .57f,.54f, .57f,.65f, .49f,.73f, .38f,.71f, .30f,.62f, .36f,.48f, .52f,.46f, .65f,.69f)),
        'q' to g(s(.22f,BASE, .31f,.63f, .32f,.50f, .43f,X, .55f,.48f, .58f,.61f, .53f,.71f, .42f,.74f, .33f,.68f, .32f,.56f, .39f,.47f, .55f,X, .59f,.49f, .59f,DESC)),
        'r' to g(s(.26f,BASE, .29f,.54f, .30f,X, .30f,BASE, .36f,.58f, .44f,.45f, .54f,X, .61f,.48f, .56f,.54f, .47f,.52f, .55f,.62f, .68f,.70f)),
        's' to g(s(.57f,.48f, .50f,X, .40f,.45f, .34f,.53f, .40f,.60f, .52f,.63f, .57f,.70f, .50f,.75f, .39f,.73f, .33f,.68f)),
        't' to g(s(.35f,.25f, .36f,.55f, .36f,.70f, .43f,.75f, .54f,.71f), s(.27f,.49f, .50f,.47f)),
        'u' to g(s(.25f,X, .26f,.64f, .33f,.74f, .43f,.73f, .52f,.61f, .54f,X, .54f,BASE, .68f,.69f)),
        'v' to g(s(.25f,X, .31f,.67f, .40f,.75f, .49f,.67f, .56f,X, .60f,.61f, .68f,.70f)),
        'w' to g(s(.18f,X, .23f,.67f, .31f,.75f, .39f,.67f, .45f,X, .50f,.67f, .58f,.75f, .66f,.67f, .72f,X)),
        'x' to g(s(.25f,X, .34f,.53f, .43f,.66f, .51f,BASE), s(.54f,X, .46f,.55f, .37f,.67f, .29f,BASE)),
        'y' to g(s(.25f,X, .26f,.64f, .33f,.74f, .43f,.73f, .52f,.61f, .54f,X, .53f,.78f, .49f,.90f, .41f,DESC, .34f,.89f)),
        'z' to g(s(.26f,.47f, .38f,X, .54f,.45f, .45f,.55f, .33f,.67f, .29f,.73f, .45f,.73f, .58f,.69f))
    )

    // D'Haese capitals are intentionally kept plain and movement-focused: no decorative loops.
    private val upperCase = mapOf(
        'A' to g(s(.25f,BASE, .43f,TOP, .61f,BASE), s(.33f,.55f, .54f,.55f)),
        'B' to g(s(.29f,BASE, .29f,TOP), s(.29f,TOP, .48f,.13f, .60f,.24f, .55f,.40f, .31f,.45f, .52f,.46f, .64f,.60f, .56f,.74f, .29f,BASE)),
        'C' to g(s(.65f,.23f, .55f,.13f, .39f,.15f, .29f,.31f, .27f,.57f, .37f,.73f, .54f,.75f, .66f,.66f)),
        'D' to g(s(.28f,BASE, .28f,TOP), s(.28f,TOP, .48f,.14f, .63f,.30f, .64f,.56f, .50f,.73f, .28f,BASE)),
        'E' to g(s(.31f,TOP, .31f,BASE), s(.31f,TOP, .64f,TOP), s(.31f,.44f, .56f,.44f), s(.31f,BASE, .65f,BASE)),
        'F' to g(s(.31f,BASE, .31f,TOP), s(.31f,TOP, .65f,TOP), s(.31f,.44f, .56f,.44f)),
        'G' to g(s(.66f,.23f, .55f,.13f, .39f,.15f, .29f,.31f, .27f,.57f, .37f,.73f, .55f,.75f, .65f,.65f, .65f,.52f, .52f,.52f)),
        'H' to g(s(.29f,TOP, .29f,BASE), s(.63f,TOP, .63f,BASE), s(.29f,.45f, .63f,.45f)),
        'I' to g(s(.45f,TOP, .45f,BASE), s(.32f,TOP, .58f,TOP), s(.32f,BASE, .58f,BASE)),
        'J' to g(s(.57f,TOP, .57f,.62f, .51f,.74f, .38f,.76f, .29f,.67f)),
        'K' to g(s(.29f,TOP, .29f,BASE), s(.63f,TOP, .29f,.48f, .64f,BASE)),
        'L' to g(s(.31f,TOP, .31f,BASE, .65f,BASE)),
        'M' to g(s(.20f,BASE, .20f,TOP, .43f,.48f, .66f,TOP, .66f,BASE)),
        'N' to g(s(.25f,BASE, .25f,TOP, .65f,BASE, .65f,TOP)),
        'O' to g(s(.46f,TOP, .32f,.17f, .25f,.36f, .26f,.60f, .37f,.75f, .53f,.75f, .64f,.60f, .65f,.35f, .58f,.17f, .46f,TOP)),
        'P' to g(s(.29f,BASE, .29f,TOP, .51f,.13f, .62f,.24f, .59f,.40f, .46f,.46f, .29f,.44f)),
        'Q' to g(s(.46f,TOP, .32f,.17f, .25f,.36f, .26f,.60f, .37f,.75f, .53f,.75f, .64f,.60f, .65f,.35f, .58f,.17f, .46f,TOP), s(.49f,.62f, .66f,.81f)),
        'R' to g(s(.29f,BASE, .29f,TOP, .51f,.13f, .62f,.24f, .59f,.40f, .46f,.46f, .29f,.44f), s(.45f,.45f, .65f,BASE)),
        'S' to g(s(.64f,.23f, .54f,.13f, .39f,.15f, .32f,.27f, .38f,.40f, .55f,.47f, .64f,.59f, .57f,.73f, .39f,.75f, .28f,.66f)),
        'T' to g(s(.22f,TOP, .68f,TOP), s(.45f,TOP, .45f,BASE)),
        'U' to g(s(.27f,TOP, .27f,.58f, .34f,.73f, .46f,.76f, .59f,.71f, .64f,.57f, .64f,TOP)),
        'V' to g(s(.24f,TOP, .44f,BASE, .66f,TOP)),
        'W' to g(s(.17f,TOP, .29f,BASE, .43f,.44f, .57f,BASE, .70f,TOP)),
        'X' to g(s(.25f,TOP, .65f,BASE), s(.65f,TOP, .25f,BASE)),
        'Y' to g(s(.23f,TOP, .45f,.45f, .67f,TOP), s(.45f,.45f, .45f,BASE)),
        'Z' to g(s(.24f,TOP, .66f,TOP, .25f,BASE, .67f,BASE))
    )

    private val digits = mapOf(
        '0' to g(s(.45f,TOP, .32f,.16f, .25f,.34f, .25f,.57f, .33f,.73f, .46f,BASE, .59f,.72f, .66f,.56f, .66f,.33f, .58f,.17f, .45f,TOP)),
        '1' to g(s(.35f,.24f, .46f,TOP, .46f,BASE), s(.32f,BASE, .61f,BASE)),
        '2' to g(s(.29f,.25f, .36f,.14f, .50f,TOP, .62f,.19f, .64f,.32f, .57f,.44f, .28f,BASE, .66f,BASE)),
        '3' to g(s(.30f,.20f, .43f,TOP, .58f,.15f, .63f,.27f, .58f,.40f, .46f,.45f, .58f,.48f, .64f,.61f, .58f,.73f, .42f,BASE, .29f,.69f)),
        '4' to g(s(.57f,BASE, .57f,TOP), s(.57f,TOP, .27f,.56f, .68f,.56f)),
        '5' to g(s(.64f,TOP, .34f,TOP, .31f,.42f, .46f,.38f, .59f,.43f, .65f,.56f, .61f,.69f, .48f,BASE, .33f,.73f, .27f,.66f)),
        '6' to g(s(.61f,.18f, .49f,TOP, .36f,.19f, .28f,.38f, .28f,.60f, .37f,.74f, .51f,BASE, .62f,.68f, .63f,.54f, .55f,.43f, .40f,.42f, .30f,.52f)),
        '7' to g(s(.26f,TOP, .67f,TOP, .47f,.42f, .36f,BASE)),
        '8' to g(s(.46f,.44f, .34f,.37f, .32f,.23f, .39f,.13f, .51f,TOP, .61f,.18f, .60f,.31f, .46f,.44f, .33f,.50f, .29f,.63f, .35f,.73f, .47f,BASE, .59f,.72f, .64f,.60f, .59f,.49f, .46f,.44f)),
        '9' to g(s(.62f,.37f, .54f,.46f, .40f,.45f, .31f,.34f, .32f,.20f, .43f,TOP, .57f,.15f, .64f,.29f, .63f,.52f, .57f,.69f, .44f,BASE, .31f,.71f))
    )

    private val glyphs: Map<Char, SchoolLetterGlyph> = lowerCase + upperCase + digits
}
