package com.appssimple.alarmsimple.quest

import kotlin.random.Random

data class MathProblem(
    val expression: String,
    val correctAnswer: Int,
    val startTimeMs: Long = System.currentTimeMillis()
)

enum class MathDifficulty(val title: String, val description: String) {
    EASY("Простой", "Сложение и вычитание (например, 23 + 14)"),
    MEDIUM("Стандартный", "Двузначные числа и простое умножение (например, 47 - 19, 7 × 8)"),
    HARD("Хардкор", "Сложные вычисления для полного пробуждения (например, 14 × 12, 134 - 58)")
}

sealed interface MathSubmitResult {
    data object CorrectNext : MathSubmitResult
    data object AllSolved : MathSubmitResult
    data object WrongAnswerReset : MathSubmitResult
    data object TimeoutReset : MathSubmitResult
}

class MathQuestEngine(
    val totalProblems: Int = 3,
    val timePerProblemSeconds: Int = 15,
    val difficulty: MathDifficulty = MathDifficulty.MEDIUM
) {
    var solvedCount: Int = 0
        private set

    var currentProblem: MathProblem? = null
        private set

    companion object {
        fun generateSample(difficulty: MathDifficulty): MathProblem {
            return when (difficulty) {
                MathDifficulty.EASY -> {
                    val a = Random.nextInt(10, 45)
                    val b = Random.nextInt(10, 45)
                    if (Random.nextBoolean()) MathProblem("$a + $b", a + b)
                    else {
                        val max = maxOf(a, b)
                        val min = minOf(a, b)
                        MathProblem("$max - $min", max - min)
                    }
                }
                MathDifficulty.MEDIUM -> {
                    val op = listOf('+', '-', '×').random()
                    when (op) {
                        '+' -> {
                            val a = Random.nextInt(25, 85)
                            val b = Random.nextInt(15, 65)
                            MathProblem("$a + $b", a + b)
                        }
                        '-' -> {
                            val a = Random.nextInt(40, 99)
                            val b = Random.nextInt(15, a)
                            MathProblem("$a - $b", a - b)
                        }
                        else -> {
                            val a = Random.nextInt(6, 12)
                            val b = Random.nextInt(4, 9)
                            MathProblem("$a × $b", a * b)
                        }
                    }
                }
                MathDifficulty.HARD -> {
                    val op = listOf('+', '-', '×').random()
                    when (op) {
                        '+' -> {
                            val a = Random.nextInt(85, 250)
                            val b = Random.nextInt(65, 180)
                            MathProblem("$a + $b", a + b)
                        }
                        '-' -> {
                            val a = Random.nextInt(110, 300)
                            val b = Random.nextInt(45, 105)
                            MathProblem("$a - $b", a - b)
                        }
                        else -> {
                            val a = Random.nextInt(12, 19)
                            val b = Random.nextInt(7, 14)
                            MathProblem("$a × $b", a * b)
                        }
                    }
                }
            }
        }
    }

    fun nextProblem(): MathProblem {
        val problem = generateSample(difficulty).copy(startTimeMs = System.currentTimeMillis())
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
