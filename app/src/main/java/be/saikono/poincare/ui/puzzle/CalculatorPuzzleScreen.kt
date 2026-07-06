package be.saikono.poincare.ui.puzzle

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import be.saikono.poincare.ui.theme.CockpitGray
import be.saikono.poincare.ui.theme.DeepSpaceBlue
import be.saikono.poincare.ui.theme.NebulaPurple
import be.saikono.poincare.ui.theme.PlanetBlue
import be.saikono.poincare.ui.theme.PlanetGold
import be.saikono.poincare.ui.theme.PlanetRed
import be.saikono.poincare.ui.theme.StarWhite

@Composable
fun CalculatorPuzzleGame(
    mathGrade: Int,
    difficultyStars: Int,
    exerciseLanguage: String,
    onAttemptSubmitted: (CalculatorPuzzleAttempt) -> Unit,
    modifier: Modifier = Modifier
) {
    val copy = remember(exerciseLanguage) { calculatorPuzzleCopy(exerciseLanguage) }
    var challenge by remember(mathGrade, difficultyStars) {
        mutableStateOf(CalculatorPuzzleGenerator.randomChallenge(mathGrade, difficultyStars))
    }
    var mode by remember { mutableStateOf(CalculatorPuzzleMode.ANSWER_QUESTION) }
    var display by remember { mutableStateOf("") }
    var feedback by remember { mutableStateOf<String?>(null) }
    var feedbackIsCorrect by remember { mutableStateOf<Boolean?>(null) }
    var tipCount by remember { mutableStateOf(0) }
    var answerCount by remember { mutableStateOf(0) }

    LaunchedEffect(challenge.id) {
        display = ""
        feedback = null
        feedbackIsCorrect = null
        tipCount = 0
        answerCount = 0
    }

    fun startRandomExercise() {
        challenge = CalculatorPuzzleGenerator.randomChallenge(mathGrade, difficultyStars)
        mode = CalculatorPuzzleMode.ANSWER_QUESTION
    }

    fun startCreateQuestion() {
        display = ""
        feedback = null
        feedbackIsCorrect = null
        tipCount = 0
        answerCount = 0
        mode = CalculatorPuzzleMode.CREATE_QUESTION
    }

    fun submitDisplay() {
        if (display.isBlank()) return
        when (mode) {
            CalculatorPuzzleMode.CREATE_QUESTION -> {
                val created = CalculatorPuzzleGenerator.fromExpression(display, mathGrade, difficultyStars)
                if (created == null) {
                    feedback = copy.invalidExpression
                    feedbackIsCorrect = false
                } else {
                    challenge = created
                    mode = CalculatorPuzzleMode.ANSWER_QUESTION
                    feedback = copy.questionCreated
                    feedbackIsCorrect = true
                }
            }

            CalculatorPuzzleMode.ANSWER_QUESTION -> {
                val submittedResult = CalculatorPuzzleGenerator.evaluateExpression(display)
                val submittedExpression = CalculatorPuzzleGenerator.canonicalExpression(display)
                val targetExpression = CalculatorPuzzleGenerator.canonicalExpression(challenge.expression)
                val isCorrect = submittedResult == challenge.result &&
                    submittedResult != null &&
                    submittedExpression != targetExpression
                answerCount += 1
                feedback = if (isCorrect) copy.correct else copy.wrong
                feedbackIsCorrect = isCorrect
                onAttemptSubmitted(
                    CalculatorPuzzleAttempt(
                        challengeId = challenge.id,
                        targetExpression = challenge.expression,
                        targetResult = challenge.result,
                        submittedExpression = display,
                        submittedResult = submittedResult,
                        isCorrect = isCorrect,
                        attemptNumber = answerCount
                    )
                )
            }
        }
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
            else -> display += key
        }
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Text(
            text = copy.title,
            color = StarWhite,
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold
        )

        Text(
            text = if (mode == CalculatorPuzzleMode.CREATE_QUESTION) copy.subtitleCreate else copy.subtitleAnswer,
            color = StarWhite.copy(alpha = 0.82f),
            fontSize = 15.sp,
            lineHeight = 20.sp
        )

        ChallengeCard(
            copy = copy,
            challenge = challenge,
            mode = mode,
            answerCount = answerCount
        )

        AnimatedVisibility(visible = tipCount > 0) {
            TipCard(
                copy = copy,
                challenge = challenge,
                tipCount = tipCount
            )
        }

        feedback?.let { message ->
            FeedbackPill(
                text = message,
                isCorrect = feedbackIsCorrect == true
            )
        }

        CalculatorDisplay(display = display.ifBlank { "0" })

        CalculatorKeypad(onKeyPressed = ::pressKey)

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            OutlinedButton(
                onClick = ::startCreateQuestion,
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = PlanetGold)
            ) {
                Text(copy.newQuestion, textAlign = TextAlign.Center)
            }
            Button(
                onClick = ::startRandomExercise,
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.buttonColors(
                    containerColor = PlanetBlue,
                    contentColor = DeepSpaceBlue
                )
            ) {
                Text(copy.randomExercise, textAlign = TextAlign.Center)
            }
        }

        Button(
            onClick = { tipCount = (tipCount + 1).coerceAtMost(4) },
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(
                containerColor = if (tipCount >= 3) PlanetGold else NebulaPurple,
                contentColor = if (tipCount >= 3) DeepSpaceBlue else StarWhite
            )
        ) {
            Text(if (tipCount >= 3) copy.showSolution else "${copy.tip} ${tipCount + 1}/3")
        }
    }
}

@Composable
private fun ChallengeCard(
    copy: CalculatorPuzzleCopy,
    challenge: CalculatorPuzzleChallenge,
    mode: CalculatorPuzzleMode,
    answerCount: Int
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = CockpitGray.copy(alpha = 0.42f)),
        shape = RoundedCornerShape(22.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = if (mode == CalculatorPuzzleMode.CREATE_QUESTION) copy.noQuestion else copy.target,
                    color = PlanetGold,
                    fontWeight = FontWeight.Bold
                )
                AnswerCounter(copy = copy, answerCount = answerCount)
            }

            Text(
                text = if (mode == CalculatorPuzzleMode.CREATE_QUESTION) "—" else challenge.expression,
                color = StarWhite,
                fontSize = 28.sp,
                fontWeight = FontWeight.Black
            )

            if (mode == CalculatorPuzzleMode.ANSWER_QUESTION) {
                Text(
                    text = "${copy.result}: ${challenge.result}",
                    color = PlanetBlue,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }
}

@Composable
private fun AnswerCounter(copy: CalculatorPuzzleCopy, answerCount: Int) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(28.dp)
                .background(PlanetRed, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = answerCount.toString(),
                color = StarWhite,
                fontWeight = FontWeight.Bold,
                fontSize = 13.sp
            )
        }
        Spacer(modifier = Modifier.width(6.dp))
        Text(
            text = copy.attempts,
            color = StarWhite.copy(alpha = 0.75f),
            fontSize = 13.sp
        )
    }
}

@Composable
private fun TipCard(
    copy: CalculatorPuzzleCopy,
    challenge: CalculatorPuzzleChallenge,
    tipCount: Int
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = PlanetGold.copy(alpha = 0.9f)),
        shape = RoundedCornerShape(18.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            val visibleAlternatives = challenge.alternatives.take(tipCount.coerceAtMost(3))
            visibleAlternatives.forEachIndexed { index, alternative ->
                Text(
                    text = "${copy.alternatives} ${index + 1}: $alternative = ${challenge.result}",
                    color = DeepSpaceBlue,
                    fontWeight = FontWeight.SemiBold
                )
            }
            if (tipCount >= 4) {
                Text(
                    text = "${copy.realSolution}: ${challenge.expression} = ${challenge.result}",
                    color = PlanetRed,
                    fontWeight = FontWeight.Black
                )
            }
        }
    }
}

@Composable
private fun FeedbackPill(text: String, isCorrect: Boolean) {
    Surface(
        color = if (isCorrect) PlanetBlue.copy(alpha = 0.24f) else PlanetRed.copy(alpha = 0.22f),
        contentColor = if (isCorrect) PlanetBlue else PlanetRed,
        shape = RoundedCornerShape(999.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
            textAlign = TextAlign.Center,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
private fun CalculatorDisplay(display: String) {
    Surface(
        color = DeepSpaceBlue.copy(alpha = 0.82f),
        contentColor = StarWhite,
        shape = RoundedCornerShape(20.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Text(
            text = display,
            modifier = Modifier.padding(18.dp),
            textAlign = TextAlign.End,
            fontSize = 34.sp,
            fontWeight = FontWeight.Black,
            maxLines = 2
        )
    }
}

@Composable
private fun CalculatorKeypad(onKeyPressed: (String) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        CalculatorKeyRow(keys = listOf("C", "⌫", "÷", "×"), onKeyPressed = onKeyPressed)
        CalculatorKeyRow(keys = listOf("7", "8", "9", "−"), onKeyPressed = onKeyPressed)
        CalculatorKeyRow(keys = listOf("4", "5", "6", "+"), onKeyPressed = onKeyPressed)
        CalculatorKeyRow(keys = listOf("1", "2", "3", "="), onKeyPressed = onKeyPressed)
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
            CalculatorKey(label = "0", onClick = { onKeyPressed("0") }, modifier = Modifier.weight(1f))
            CalculatorKey(label = "00", onClick = { onKeyPressed("00") }, modifier = Modifier.weight(1f))
            Spacer(modifier = Modifier.weight(2f))
        }
    }
}

@Composable
private fun CalculatorKeyRow(keys: List<String>, onKeyPressed: (String) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
        keys.forEach { key ->
            CalculatorKey(
                label = key,
                onClick = { onKeyPressed(key) },
                modifier = Modifier.weight(1f),
                highlight = key == "="
            )
        }
    }
}

@Composable
private fun CalculatorKey(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    highlight: Boolean = false
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
            .height(58.dp)
            .clickable(onClick = onClick)
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(
                text = label,
                fontSize = 23.sp,
                fontWeight = FontWeight.Black
            )
        }
    }
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
