package be.saikono.poincare.ui.puzzle

import android.content.res.Configuration
import android.speech.tts.TextToSpeech
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import be.saikono.poincare.ui.theme.CockpitGray
import be.saikono.poincare.ui.theme.DeepSpaceBlue
import be.saikono.poincare.ui.theme.NebulaPurple
import be.saikono.poincare.ui.theme.PlanetBlue
import be.saikono.poincare.ui.theme.PlanetGold
import be.saikono.poincare.ui.theme.PlanetRed
import be.saikono.poincare.ui.theme.StarWhite
import java.util.Locale

@Composable
fun CalculatorPuzzleGame(
    mathGrade: Int,
    difficultyStars: Int,
    exerciseLanguage: String,
    onAttemptSubmitted: (CalculatorPuzzleAttempt) -> Unit,
    modifier: Modifier = Modifier
) {
    val copy = remember(exerciseLanguage) { calculatorPuzzleCopy(exerciseLanguage) }
    var challenge by remember(mathGrade, difficultyStars) { mutableStateOf<CalculatorPuzzleChallenge?>(null) }
    var mode by remember { mutableStateOf(CalculatorPuzzleMode.CREATE_QUESTION) }
    var display by remember { mutableStateOf("") }
    var feedback by remember { mutableStateOf<String?>(null) }
    var feedbackIsCorrect by remember { mutableStateOf<Boolean?>(null) }
    var tipCount by remember { mutableStateOf(0) }
    var answerCount by remember { mutableStateOf(0) }

    val context = LocalContext.current
    val inPreview = LocalInspectionMode.current
    var ttsReady by remember { mutableStateOf(false) }
    val tts: TextToSpeech? = remember(context, inPreview) {
        if (inPreview) {
            null
        } else {
            TextToSpeech(context) { status ->
                ttsReady = status == TextToSpeech.SUCCESS
            }
        }
    }

    DisposableEffect(tts) {
        onDispose {
            tts?.stop()
            tts?.shutdown()
        }
    }

    LaunchedEffect(mathGrade, difficultyStars) {
        challenge = null
        mode = CalculatorPuzzleMode.CREATE_QUESTION
        display = ""
        feedback = null
        feedbackIsCorrect = null
        tipCount = 0
        answerCount = 0
    }

    fun resetForNewChallenge() {
        display = ""
        feedback = null
        feedbackIsCorrect = null
        tipCount = 0
        answerCount = 0
    }

    fun resetPuzzle() {
        challenge = null
        mode = CalculatorPuzzleMode.CREATE_QUESTION
        resetForNewChallenge()
    }

    fun startRandomExercise() {
        challenge = CalculatorPuzzleGenerator.randomChallenge(mathGrade, difficultyStars)
        mode = CalculatorPuzzleMode.ANSWER_QUESTION
        resetForNewChallenge()
    }

    fun speakQuestion() {
        val activeChallenge = challenge ?: return
        val engine = tts ?: return
        if (!ttsReady) return
        val text = calculatorPuzzleQuestionText(copy, activeChallenge.expression)
        engine.language = Locale.forLanguageTag(exerciseLanguage.replace('_', '-'))
        engine.speak(text, TextToSpeech.QUEUE_FLUSH, null, "calculator-question-${activeChallenge.id}")
    }

    fun submitDisplay() {
        if (display.isBlank()) return

        val activeChallenge = challenge
        if (activeChallenge == null || mode == CalculatorPuzzleMode.CREATE_QUESTION) {
            val created = CalculatorPuzzleGenerator.fromExpression(display, mathGrade, difficultyStars)
            if (created == null) {
                feedback = copy.invalidExpression
                feedbackIsCorrect = false
            } else {
                challenge = created
                mode = CalculatorPuzzleMode.ANSWER_QUESTION
                display = ""
                feedback = null
                feedbackIsCorrect = null
                tipCount = 0
                answerCount = 0
            }
            return
        }

        val submittedResult = CalculatorPuzzleGenerator.evaluateExpression(display)
        val submittedExpression = CalculatorPuzzleGenerator.canonicalExpression(display)
        val targetExpression = CalculatorPuzzleGenerator.canonicalExpression(activeChallenge.expression)
        val isCorrect = submittedResult != null &&
            CalculatorPuzzleGenerator.resultsEquivalent(submittedResult, activeChallenge.result) &&
            submittedExpression != targetExpression

        answerCount += 1
        feedback = if (isCorrect) copy.correct else copy.wrong
        feedbackIsCorrect = isCorrect
        onAttemptSubmitted(
            CalculatorPuzzleAttempt(
                challengeId = activeChallenge.id,
                targetExpression = activeChallenge.expression,
                targetResult = activeChallenge.result,
                submittedExpression = display,
                submittedResult = submittedResult,
                isCorrect = isCorrect,
                attemptNumber = answerCount
            )
        )
    }

    fun isOperator(char: Char): Boolean = char == '+' || char == '−' || char == '×' || char == '÷'

    fun appendOperator(operator: String) {
        if (display.isBlank()) {
            if (operator == "−") display = operator
            return
        }
        display = if (isOperator(display.last()) || display.last() == '.') {
            display.dropLast(1) + operator
        } else {
            display + operator
        }
    }

    fun appendDecimal() {
        val lastOperatorIndex = display.indexOfLast { isOperator(it) }
        val currentNumber = display.substring(lastOperatorIndex + 1)
        if (currentNumber.contains('.')) return
        display += if (currentNumber.isBlank() || currentNumber == "−") "0." else "."
    }

    fun pressKey(key: String) {
        when (key) {
            "C" -> {
                display = ""
                feedback = null
                feedbackIsCorrect = null
            }
            "⌫" -> display = display.dropLast(1)
            "=" -> submitDisplay()
            "." -> appendDecimal()
            "+", "−", "×", "÷" -> appendOperator(key)
            else -> display += key
        }
    }

    val configuration = LocalConfiguration.current

    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .padding(12.dp)
    ) {
        val useTwoColumns = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE || maxWidth >= 720.dp
        val compactLayout = useTwoColumns || maxHeight < 780.dp

        if (useTwoColumns) {
            Row(
                modifier = Modifier.fillMaxSize(),
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                verticalAlignment = Alignment.Top
            ) {
                CalculatorPuzzleInfoColumn(
                    copy = copy,
                    challenge = challenge,
                    mode = mode,
                    display = display,
                    feedback = feedback,
                    feedbackIsCorrect = feedbackIsCorrect,
                    tipCount = tipCount,
                    isComplete = challenge != null && feedbackIsCorrect == true,
                    compact = true,
                    onQuestionClick = ::speakQuestion,
                    onRandomExercise = ::startRandomExercise,
                    onReset = ::resetPuzzle,
                    onNextMission = ::startRandomExercise,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                )

                CalculatorKeypad(
                    copy = copy,
                    tipCount = tipCount,
                    tipEnabled = challenge != null && tipCount < 3,
                    onTip = { tipCount = (tipCount + 1).coerceAtMost(3) },
                    onKeyPressed = ::pressKey,
                    compact = true,
                    modifier = Modifier
                        .weight(0.9f)
                        .fillMaxHeight()
                )
            }
        } else {
            Column(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(if (compactLayout) 10.dp else 14.dp)
            ) {
                CalculatorPuzzleInfoColumn(
                    copy = copy,
                    challenge = challenge,
                    mode = mode,
                    display = display,
                    feedback = feedback,
                    feedbackIsCorrect = feedbackIsCorrect,
                    tipCount = tipCount,
                    isComplete = challenge != null && feedbackIsCorrect == true,
                    compact = compactLayout,
                    onQuestionClick = ::speakQuestion,
                    onRandomExercise = ::startRandomExercise,
                    onReset = ::resetPuzzle,
                    onNextMission = ::startRandomExercise,
                    modifier = Modifier.fillMaxWidth()
                )

                CalculatorKeypad(
                    copy = copy,
                    tipCount = tipCount,
                    tipEnabled = challenge != null && tipCount < 3,
                    onTip = { tipCount = (tipCount + 1).coerceAtMost(3) },
                    onKeyPressed = ::pressKey,
                    compact = compactLayout,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}

@Composable
private fun CalculatorPuzzleInfoColumn(
    copy: CalculatorPuzzleCopy,
    challenge: CalculatorPuzzleChallenge?,
    mode: CalculatorPuzzleMode,
    display: String,
    feedback: String?,
    feedbackIsCorrect: Boolean?,
    tipCount: Int,
    isComplete: Boolean,
    compact: Boolean,
    onQuestionClick: () -> Unit,
    onRandomExercise: () -> Unit,
    onReset: () -> Unit,
    onNextMission: () -> Unit,
    modifier: Modifier = Modifier
) {
    val sectionSpacing = if (compact) 14.dp else 20.dp
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(sectionSpacing)
    ) {
        Text(
            text = copy.title,
            color = StarWhite,
            fontSize = if (compact) 20.sp else 23.sp,
            fontWeight = FontWeight.Bold
        )

        CalculatorTopActions(
            copy = copy,
            compact = compact,
            isComplete = isComplete,
            onRandomExercise = onRandomExercise,
            onReset = onReset,
            onNextMission = onNextMission
        )

        ChallengeCard(
            copy = copy,
            challenge = challenge,
            mode = mode,
            onQuestionClick = onQuestionClick,
            compact = compact
        )

        AnimatedVisibility(visible = challenge != null && tipCount > 0) {
            TipCard(
                copy = copy,
                challenge = challenge,
                tipCount = tipCount
            )
        }

        feedback?.let { message ->
            FeedbackPill(
                text = message,
                isCorrect = feedbackIsCorrect == true,
                compact = compact
            )
        }

        CalculatorDisplay(
            display = display.ifBlank { "0" },
            compact = compact
        )
    }
}

@Composable
private fun CalculatorTopActions(
    copy: CalculatorPuzzleCopy,
    compact: Boolean,
    isComplete: Boolean,
    onRandomExercise: () -> Unit,
    onReset: () -> Unit,
    onNextMission: () -> Unit
) {
    val actionHeight = if (compact) 38.dp else 42.dp
    val secondaryLabel = if (isComplete) copy.nextMission else copy.reset
    val secondaryAction = if (isComplete) onNextMission else onReset
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Button(
            onClick = onRandomExercise,
            modifier = Modifier
                .weight(1f)
                .height(actionHeight),
            colors = ButtonDefaults.buttonColors(
                containerColor = PlanetBlue,
                contentColor = DeepSpaceBlue
            ),
            shape = RoundedCornerShape(999.dp)
        ) {
            Text(
                text = copy.randomExercise,
                textAlign = TextAlign.Center,
                fontWeight = FontWeight.Bold,
                fontSize = if (compact) 12.sp else 13.sp,
                maxLines = 1
            )
        }

        Button(
            onClick = secondaryAction,
            modifier = Modifier
                .weight(1f)
                .height(actionHeight),
            colors = ButtonDefaults.buttonColors(
                containerColor = PlanetGold,
                contentColor = DeepSpaceBlue
            ),
            shape = RoundedCornerShape(999.dp)
        ) {
            Text(
                text = secondaryLabel,
                textAlign = TextAlign.Center,
                fontWeight = FontWeight.Bold,
                fontSize = if (compact) 12.sp else 13.sp,
                maxLines = 1
            )
        }
    }
}

@Composable
private fun ChallengeCard(
    copy: CalculatorPuzzleCopy,
    challenge: CalculatorPuzzleChallenge?,
    mode: CalculatorPuzzleMode,
    onQuestionClick: () -> Unit,
    compact: Boolean
) {
    val hasQuestion = challenge != null && mode == CalculatorPuzzleMode.ANSWER_QUESTION
    val questionText = if (hasQuestion) {
        calculatorPuzzleQuestionText(copy, challenge.expression)
    } else {
        copy.noQuestionTitle
    }

    Card(
        colors = CardDefaults.cardColors(containerColor = CockpitGray.copy(alpha = 0.42f)),
        shape = RoundedCornerShape(20.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Text(
            text = questionText,
            color = PlanetGold,
            fontSize = if (hasQuestion) {
                if (compact) 22.sp else 26.sp
            } else {
                if (compact) 16.sp else 18.sp
            },
            fontWeight = if (hasQuestion) FontWeight.Black else FontWeight.Bold,
            textAlign = TextAlign.Start,
            modifier = Modifier
                .fillMaxWidth()
                .clickable(enabled = hasQuestion, onClick = onQuestionClick)
                .padding(if (compact) 16.dp else 20.dp)
        )
    }
}

@Composable
private fun TipCard(
    copy: CalculatorPuzzleCopy,
    challenge: CalculatorPuzzleChallenge?,
    tipCount: Int
) {
    val currentChallenge = challenge ?: return
    Card(
        colors = CardDefaults.cardColors(containerColor = PlanetGold.copy(alpha = 0.9f)),
        shape = RoundedCornerShape(18.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(if (tipCount <= 1) 13.dp else 14.dp),
            verticalArrangement = Arrangement.spacedBy(7.dp)
        ) {
            val visibleAlternatives = currentChallenge.alternatives.take(tipCount.coerceAtMost(3))
            visibleAlternatives.forEachIndexed { index, alternative ->
                Text(
                    text = "${copy.tip} ${index + 1}: $alternative",
                    color = DeepSpaceBlue,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 14.sp
                )
            }
        }
    }
}

@Composable
private fun FeedbackPill(text: String, isCorrect: Boolean, compact: Boolean) {
    Surface(
        color = if (isCorrect) PlanetBlue.copy(alpha = 0.24f) else PlanetRed.copy(alpha = 0.22f),
        contentColor = if (isCorrect) PlanetBlue else PlanetRed,
        shape = RoundedCornerShape(999.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = if (compact) 10.dp else 12.dp),
            textAlign = TextAlign.Center,
            fontWeight = FontWeight.Bold,
            fontSize = if (compact) 13.sp else 14.sp
        )
    }
}

@Composable
private fun CalculatorDisplay(display: String, compact: Boolean) {
    Surface(
        color = DeepSpaceBlue.copy(alpha = 0.82f),
        contentColor = StarWhite,
        shape = RoundedCornerShape(18.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Text(
            text = display,
            modifier = Modifier.padding(if (compact) 13.dp else 18.dp),
            textAlign = TextAlign.End,
            fontSize = if (compact) 25.sp else 30.sp,
            fontWeight = FontWeight.Black,
            maxLines = 2
        )
    }
}

@Composable
private fun CalculatorKeypad(
    copy: CalculatorPuzzleCopy,
    tipCount: Int,
    tipEnabled: Boolean,
    onTip: () -> Unit,
    onKeyPressed: (String) -> Unit,
    compact: Boolean,
    modifier: Modifier = Modifier
) {
    val keyHeight = if (compact) 46.dp else 54.dp
    val gap = if (compact) 8.dp else 10.dp
    val tipLabel = copy.tip

    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(gap, Alignment.CenterVertically)
    ) {
        CalculatorKeyRow(
            keys = listOf("C", "⌫", "÷", "×"),
            onKeyPressed = onKeyPressed,
            keyHeight = keyHeight,
            gap = gap
        )
        CalculatorKeyRow(
            keys = listOf("7", "8", "9", "−"),
            onKeyPressed = onKeyPressed,
            keyHeight = keyHeight,
            gap = gap
        )
        CalculatorKeyRow(
            keys = listOf("4", "5", "6", "+"),
            onKeyPressed = onKeyPressed,
            keyHeight = keyHeight,
            gap = gap
        )
        CalculatorKeyRow(
            keys = listOf("1", "2", "3", "="),
            onKeyPressed = onKeyPressed,
            keyHeight = keyHeight,
            gap = gap
        )
        Row(horizontalArrangement = Arrangement.spacedBy(gap), modifier = Modifier.fillMaxWidth()) {
            CalculatorKey(
                label = "0",
                onClick = { onKeyPressed("0") },
                modifier = Modifier.weight(1f),
                height = keyHeight
            )
            CalculatorKey(
                label = ".",
                onClick = { onKeyPressed(".") },
                modifier = Modifier.weight(1f),
                height = keyHeight
            )
            CalculatorActionKey(
                label = tipLabel,
                onClick = onTip,
                enabled = tipEnabled,
                modifier = Modifier.weight(2f),
                height = keyHeight
            )
        }
    }
}

@Composable
private fun CalculatorKeyRow(
    keys: List<String>,
    onKeyPressed: (String) -> Unit,
    keyHeight: Dp,
    gap: Dp
) {
    Row(horizontalArrangement = Arrangement.spacedBy(gap), modifier = Modifier.fillMaxWidth()) {
        keys.forEach { key ->
            CalculatorKey(
                label = key,
                onClick = { onKeyPressed(key) },
                modifier = Modifier.weight(1f),
                highlight = key == "=",
                height = keyHeight
            )
        }
    }
}

@Composable
private fun CalculatorKey(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    highlight: Boolean = false,
    height: Dp
) {
    val isOperator = label in listOf("+", "−", "×", "÷", "=", "C", "⌫")
    Surface(
        color = when {
            highlight -> PlanetRed
            isOperator -> NebulaPurple.copy(alpha = 0.92f)
            else -> Color.White.copy(alpha = 0.08f)
        },
        contentColor = StarWhite,
        shape = RoundedCornerShape(18.dp),
        modifier = modifier
            .height(height)
            .clickable(onClick = onClick)
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(
                text = label,
                fontSize = 22.sp,
                fontWeight = FontWeight.Black
            )
        }
    }
}

@Composable
private fun CalculatorActionKey(
    label: String,
    onClick: () -> Unit,
    enabled: Boolean,
    modifier: Modifier = Modifier,
    height: Dp
) {
    Surface(
        color = if (enabled) NebulaPurple.copy(alpha = 0.92f) else CockpitGray.copy(alpha = 0.55f),
        contentColor = if (enabled) StarWhite else StarWhite.copy(alpha = 0.45f),
        shape = RoundedCornerShape(18.dp),
        modifier = modifier
            .height(height)
            .clickable(enabled = enabled, onClick = onClick)
    ) {
        Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(horizontal = 6.dp)) {
            Text(
                text = label,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                maxLines = 2
            )
        }
    }
}

private fun calculatorPuzzleQuestionText(copy: CalculatorPuzzleCopy, expression: String): String {
    return "${copy.questionPrefix} $expression${copy.questionSuffix}"
}

@Preview(showBackground = true, backgroundColor = 0xFF0B0D17)
@Composable
private fun CalculatorPuzzleGamePreview() {
    CalculatorPuzzleGame(
        mathGrade = 4,
        difficultyStars = 2,
        exerciseLanguage = "nl-BE",
        onAttemptSubmitted = {}
    )
}
