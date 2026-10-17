package com.appssimple.alarmsimple.quest

import kotlin.random.Random

data class MathProblem(
    val expression: String,
    val correctAnswer: Int,
    val startTimeMs: Long
)

sealed interface MathSubmitResult {
    data object CorrectNext : MathSubmitResult
    data object AllSolved : MathSubmitResult
    data object WrongAnswerReset : MathSubmitResult
    data object TimeoutReset : MathSubmitResult
}

class MathQuestEngine(
    val totalProblems: Int = 3,
    val timePerProblemSeconds: Int = 15
) {
    var solvedCount: Int = 0
        private set

    var currentProblem: MathProblem? = null
        private set

    fun nextProblem(): MathProblem {
        val op = listOf('+', '-', '×').random()
        val problem = when (op) {
            '+' -> {
                val a = Random.nextInt(12, 89)
                val b = Random.nextInt(12, 89)
                MathProblem("$a + $b", a + b, System.currentTimeMillis())
            }
            '-' -> {
                val a = Random.nextInt(30, 99)
                val b = Random.nextInt(12, a)
                MathProblem("$a - $b", a - b, System.currentTimeMillis())
            }
            '×' -> {
                val a = Random.nextInt(4, 14)
                val b = Random.nextInt(4, 12)
                MathProblem("$a × $b", a * b, System.currentTimeMillis())
            }
            else -> MathProblem("10 + 10", 20, System.currentTimeMillis())
        }
        currentProblem = problem
        return problem
    }

    fun submitAnswer(answer: Int): MathSubmitResult {
        val problem = currentProblem ?: return MathSubmitResult.WrongAnswerReset
        val elapsedMs = System.currentTimeMillis() - problem.startTimeMs

        if (elapsedMs > timePerProblemSeconds * 1000L) {
            solvedCount = 0
            currentProblem = null
            return MathSubmitResult.TimeoutReset
        }

        if (answer != problem.correctAnswer) {
            solvedCount = 0
            currentProblem = null
            return MathSubmitResult.WrongAnswerReset
        }

        solvedCount++
        return if (solvedCount >= totalProblems) {
            MathSubmitResult.AllSolved
        } else {
            nextProblem()
            MathSubmitResult.CorrectNext
        }
    }
}
