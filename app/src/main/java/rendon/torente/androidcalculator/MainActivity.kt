package rendon.torente.androidcalculator

import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import net.objecthunter.exp4j.ExpressionBuilder
import java.math.BigDecimal
import java.math.RoundingMode

class MainActivity : AppCompatActivity() {

    private lateinit var tvExpression: TextView
    private lateinit var tvResult: TextView

    // Remember whether last action produced a result (used to control input behavior)
    private var lastAnswerCalculated = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        tvExpression = findViewById(R.id.tvExpression)
        tvResult = findViewById(R.id.tvResult)

        setupNumberButtons()
        setupOperatorButtons()
        setupUtilityButtons()
    }

    // --- Setup input buttons -----------------------------------------------
    private fun setupNumberButtons() {
        val numberButtons = listOf(
            R.id.btn0, R.id.btn1, R.id.btn2, R.id.btn3, R.id.btn4,
            R.id.btn5, R.id.btn6, R.id.btn7, R.id.btn8, R.id.btn9, R.id.btnDot
        )

        for (id in numberButtons) {
            findViewById<Button>(id).setOnClickListener { btn ->
                val text = (btn as Button).text.toString()
                // If the last action was an evaluated answer and user presses a number -> start new expression
                if (lastAnswerCalculated) {
                    tvExpression.text = ""
                    tvResult.text = ""
                    lastAnswerCalculated = false
                }
                appendToExpression(text)
            }
        }
    }

    private fun setupOperatorButtons() {
        val operatorIds = listOf(
            R.id.btnPlus, R.id.btnMinus, R.id.btnMultiply, R.id.btnDivide,
            R.id.btnOpenParen, R.id.btnCloseParen
        )

        for (id in operatorIds) {
            findViewById<Button>(id).setOnClickListener { btn ->
                val op = (btn as Button).text.toString()
                val expr = tvExpression.text.toString()

                // If expression empty and user taps an operator: allow leading minus only
                if (expr.isEmpty()) {
                    if (op == "-" || op == "−") {
                        appendToExpression("-")
                    }
                    // ignore other operators at start
                    return@setOnClickListener
                }

                // Prevent two consecutive operators (replace last operator with the new one)
                val lastChar = expr.last()
                if (isOperatorChar(lastChar)) {
                    // If user typed '(' then allow an operator right after '(', except that usually we don't want operators after '('
                    // Replace previous operator with new one
                    tvExpression.text = expr.dropLast(1) + op
                } else {
                    appendToExpression(op)
                }
                lastAnswerCalculated = false
            }
        }
    }

    private fun setupUtilityButtons() {
        // Clear
        findViewById<Button>(R.id.btnClear).setOnClickListener {
            tvExpression.text = ""
            tvResult.text = ""
            lastAnswerCalculated = false
        }

        // Delete last char
        findViewById<Button>(R.id.btnDel).setOnClickListener {
            val expr = tvExpression.text.toString()
            if (expr.isNotEmpty()) {
                tvExpression.text = expr.dropLast(1)
            }
            lastAnswerCalculated = false
        }

        // Equal -> evaluate
        findViewById<Button>(R.id.btnEqual).setOnClickListener {
            calculateResult()
        }
    }

    // --- Expression helpers -----------------------------------------------
    private fun appendToExpression(s: String) {
        tvExpression.append(s)
    }

    private fun isOperatorChar(c: Char): Boolean {
        // Include common displayed operator glyphs and ascii
        return c == '+' || c == '-' || c == '−' || c == '×' || c == '÷' ||
                c == '*' || c == '/' || c == '^' || c == '.'
    }

    /**
     * Sanitize user-visible expression into a form parseable by exp4j.
     * Replacements:
     *  × -> *
     *  ÷ -> /
     *  − (unicode minus) or – (en dash) -> -
     *
     * Also trims trailing operator characters (so "2+3+" becomes "2+3").
     */
    private fun sanitizeForEvaluation(input: String): String {
        if (input.isBlank()) throw IllegalArgumentException("Empty expression")

        var s = input
        // Replace common unicode operator glyphs with ASCII equivalents:
        s = s.replace("\u00D7", "*")   // ×
            .replace("\u00F7", "/")     // ÷
            .replace("\u2212", "-")     // − (unicode minus)
            .replace("\u2013", "-")     // – (en dash)
            .replace("×", "*")
            .replace("÷", "/")

        // Remove any characters that are not digits, operators, decimal point, parentheses or caret
        // (keep it conservative — exp4j supports numbers, + - * / ^ and parentheses)
        // But don't remove negative signs or decimal points.
        // Trim trailing operators (like "12+"), because exp4j would throw.
        while (s.isNotEmpty() && isOperatorTrailing(s.last())) {
            s = s.dropLast(1)
        }

        if (s.isBlank()) throw IllegalArgumentException("Invalid expression")

        return s
    }

    private fun isOperatorTrailing(c: Char): Boolean {
        // trailing operators that should be removed: + - * / ^ . (but keep closing paren)
        return c == '+' || c == '-' || c == '*' || c == '/' || c == '^' || c == '.'
    }

    /**
     * Evaluate expression using exp4j (follows standard precedence / PEMDAS)
     */
    private fun calculateResult() {
        val rawExpr = tvExpression.text.toString()
        if (rawExpr.isBlank()) {
            tvResult.text = ""
            return
        }

        try {
            val sanitized = sanitizeForEvaluation(rawExpr)

            // Build and evaluate — exp4j respects parentheses and operator precedence (PEMDAS)
            val expression = ExpressionBuilder(sanitized).build()
            val result = expression.evaluate()

            // Format result: if whole number show without decimal, else show trimmed decimals
            tvResult.text = formatDouble(result)
            lastAnswerCalculated = true
        } catch (e: Exception) {
            // On parse/eval error, show helpful message. Keep generic "Error" to avoid confusing user.
            tvResult.text = "Error"
            lastAnswerCalculated = false
        }
    }

    /**
     * Nicely format double results: trim trailing zeros and avoid scientific notation for normal cases.
     */
    private fun formatDouble(value: Double): String {
        if (value.isNaN() || value.isInfinite()) return "Error"

        // Use BigDecimal to strip trailing zeros safely
        val bd = BigDecimal.valueOf(value).setScale(10, RoundingMode.HALF_UP).stripTrailingZeros()
        // If the value is an integer (e.g. 5.0) the BigDecimal will be "5"
        return bd.toPlainString()
    }
}
