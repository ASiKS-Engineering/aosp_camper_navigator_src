package com.example.campernavigator.service

import com.graphhopper.json.Statement
import com.graphhopper.routing.ev.BooleanEncodedValue
import com.graphhopper.routing.ev.DecimalEncodedValue
import com.graphhopper.routing.ev.EncodedValue
import com.graphhopper.routing.ev.EncodedValueLookup
import com.graphhopper.routing.ev.EnumEncodedValue
import com.graphhopper.routing.ev.IntEncodedValue
import com.graphhopper.routing.weighting.Weighting
import com.graphhopper.util.CustomModel
import com.graphhopper.util.EdgeIteratorState

/**
 * Evaluates a GraphHopper [CustomModel] without Janino (which generates JVM bytecode and therefore
 * cannot run on Android). Semantics follow GraphHopper's CustomWeighting:
 *   weight = seconds / priority + distance * distance_influence / 1000
 * Anything outside the supported subset (areas, turn penalties, "add", unknown encoded values or
 * syntax) is rejected with an exception instead of being silently ignored.
 */
class PackCustomWeighting(
    lookup: EncodedValueLookup,
    model: CustomModel,
    private val headingPenaltySeconds: Double = DEFAULT_HEADING_PENALTY,
    private val name: String = NAME
) : Weighting {

    private val distanceInfluence: Double
    private val speedProgram: Block
    private val priorityProgram: Block
    private val minWeightPerDistance: Double

    init {
        require(model.areas == null || model.areas.features.isNullOrEmpty()) { "Custom model areas are not supported" }
        require(model.turnPenalty.isNullOrEmpty()) { "Custom model turn_penalty is not supported" }
        val di = model.distanceInfluence ?: 70.0
        require(di >= 0) { "distance_influence cannot be negative: $di" }
        distanceInfluence = di / 1000.0

        val compiler = Compiler(lookup)
        require(model.speed.isNotEmpty()) { "Custom model has no speed statements" }
        speedProgram = compiler.block(model.speed)
        priorityProgram = compiler.block(model.priority ?: emptyList())

        val maxSpeed = compiler.bound(model.speed, Double.POSITIVE_INFINITY)
        require(!maxSpeed.isInfinite() && maxSpeed > 0) { "Custom model needs an upper speed limit (unconditional limit_to)" }
        val maxPriority = compiler.bound(model.priority ?: emptyList(), 1.0)
        require(maxPriority > 0) { "Custom model priority is always zero" }
        minWeightPerDistance = 1.0 / (maxSpeed / SPEED_CONV) / maxPriority + distanceInfluence
    }

    override fun calcMinWeightPerDistance(): Double = minWeightPerDistance

    override fun calcEdgeWeight(edgeState: EdgeIteratorState, reverse: Boolean): Double {
        val priority = priorityProgram.run(edgeState, reverse, 1.0)
        if (priority == 0.0) return Double.POSITIVE_INFINITY
        val distance = edgeState.distance
        var seconds = calcSeconds(distance, edgeState, reverse)
        if (seconds.isInfinite()) return Double.POSITIVE_INFINITY
        if (edgeState.get(EdgeIteratorState.UNFAVORED_EDGE)) seconds += headingPenaltySeconds
        return seconds / priority + distance * distanceInfluence
    }

    private fun calcSeconds(distance: Double, edgeState: EdgeIteratorState, reverse: Boolean): Double {
        val speed = speedProgram.run(edgeState, reverse, Double.POSITIVE_INFINITY)
        if (speed == 0.0) return Double.POSITIVE_INFINITY
        require(speed > 0) { "Speed cannot be negative" }
        return distance / speed * SPEED_CONV
    }

    override fun calcEdgeMillis(edgeState: EdgeIteratorState, reverse: Boolean): Long =
        Math.round(calcSeconds(edgeState.distance, edgeState, reverse) * 1000)

    override fun calcTurnWeight(inEdge: Int, viaNode: Int, outEdge: Int): Double = 0.0
    override fun calcTurnMillis(inEdge: Int, viaNode: Int, outEdge: Int): Long = 0
    override fun hasTurnCosts(): Boolean = false
    override fun getName(): String = name

    private fun interface Cond { fun test(e: EdgeIteratorState, rev: Boolean): Boolean }
    private fun interface Value { fun get(e: EdgeIteratorState, rev: Boolean): Double }

    private class Step(
        val keyword: Statement.Keyword,
        val cond: Cond?,
        val op: Statement.Op,
        val value: Value?,
        val inner: Block?
    )

    private class Block(private val steps: List<Step>) {
        // An "if" starts a new if / else_if / else chain; only the first matching branch of a chain fires.
        fun run(e: EdgeIteratorState, rev: Boolean, start: Double): Double {
            var v = start
            var matched = false
            for (s in steps) {
                val fire = when (s.keyword) {
                    Statement.Keyword.IF -> { matched = s.cond!!.test(e, rev); matched }
                    Statement.Keyword.ELSEIF -> if (matched) false else { matched = s.cond!!.test(e, rev); matched }
                    Statement.Keyword.ELSE -> if (matched) false else { matched = true; true }
                }
                if (!fire) continue
                v = when (s.op) {
                    Statement.Op.MULTIPLY -> v * s.value!!.get(e, rev)
                    Statement.Op.LIMIT -> Math.min(v, s.value!!.get(e, rev))
                    Statement.Op.DO -> s.inner!!.run(e, rev, v)
                    else -> throw IllegalStateException("Unsupported operation ${s.op}")
                }
            }
            return v
        }
    }

    private class Compiler(private val lookup: EncodedValueLookup) {

        fun block(statements: List<Statement>): Block = Block(statements.map { s ->
            val cond = if (s.keyword() == Statement.Keyword.ELSE) null else condition(s.condition())
            when (s.operation()) {
                Statement.Op.MULTIPLY, Statement.Op.LIMIT -> Step(s.keyword(), cond, s.operation(), value(s.value()), null)
                Statement.Op.DO -> Step(s.keyword(), cond, Statement.Op.DO, null, block(s.doBlock()))
                else -> throw IllegalArgumentException("Unsupported custom model operation: ${s.operation()}")
            }
        })

        // Conservative upper bound of the value any edge can reach, used for calcMinWeightPerDistance.
        fun bound(statements: List<Statement>, start: Double): Double {
            var u = start
            for (s in statements) {
                val always = s.keyword() == Statement.Keyword.IF && s.condition().trim() == "true"
                when (s.operation()) {
                    Statement.Op.LIMIT -> if (always) u = Math.min(u, valueBound(s.value()))
                    Statement.Op.MULTIPLY -> {
                        val f = valueBound(s.value())
                        if (always || f > 1.0) u *= f
                    }
                    Statement.Op.DO -> {
                        val inner = bound(s.doBlock(), u)
                        u = if (always) inner else Math.max(u, inner)
                    }
                    else -> throw IllegalArgumentException("Unsupported custom model operation: ${s.operation()}")
                }
            }
            return u
        }

        private fun valueBound(expr: String): Double {
            val t = expr.trim()
            t.toDoubleOrNull()?.let { return it }
            return when (val ev = ev(t)) {
                is DecimalEncodedValue -> ev.maxOrMaxStorableDecimal
                is EnumEncodedValue<*> -> throw IllegalArgumentException("Enum '$t' cannot be used as a value")
                is IntEncodedValue -> ev.maxOrMaxStorableInt.toDouble()
                else -> throw IllegalArgumentException("'$t' cannot be used as a value")
            }
        }

        fun value(expr: String): Value {
            val t = expr.trim()
            t.toDoubleOrNull()?.let { c -> return Value { _, _ -> c } }
            return numeric(t) ?: throw IllegalArgumentException("Unsupported value expression: '$expr'")
        }

        private fun ev(name: String): EncodedValue {
            require(lookup.hasEncodedValue(name)) { "Unknown encoded value '$name'" }
            return lookup.getEncodedValue(name, EncodedValue::class.java)
        }

        private fun numeric(name: String): Value? {
            if (!lookup.hasEncodedValue(name)) return null
            return when (val ev = ev(name)) {
                is DecimalEncodedValue -> Value { e, r -> if (r) e.getReverse(ev) else e.get(ev) }
                is EnumEncodedValue<*> -> null
                is IntEncodedValue -> Value { e, r -> (if (r) e.getReverse(ev) else e.get(ev)).toDouble() }
                else -> null
            }
        }

        fun condition(expr: String): Cond {
            val p = Parser(expr)
            val c = p.parseOr()
            p.expectEnd()
            return c
        }

        private inner class Parser(private val src: String) {
            private val tokens = tokenize(src)
            private var pos = 0

            private fun peek(): String? = tokens.getOrNull(pos)
            private fun next(): String = tokens.getOrNull(pos++) ?: fail("unexpected end")
            private fun fail(msg: String): Nothing = throw IllegalArgumentException("Cannot parse condition '$src': $msg")

            fun expectEnd() { if (pos != tokens.size) fail("unexpected '${tokens[pos]}'") }

            fun parseOr(): Cond {
                var left = parseAnd()
                while (peek() == "||") {
                    next()
                    val a = left
                    val b = parseAnd()
                    left = Cond { e, r -> a.test(e, r) || b.test(e, r) }
                }
                return left
            }

            private fun parseAnd(): Cond {
                var left = parseUnary()
                while (peek() == "&&") {
                    next()
                    val a = left
                    val b = parseUnary()
                    left = Cond { e, r -> a.test(e, r) && b.test(e, r) }
                }
                return left
            }

            private fun parseUnary(): Cond {
                when (peek()) {
                    "!" -> { next(); val c = parseUnary(); return Cond { e, r -> !c.test(e, r) } }
                    "(" -> {
                        next()
                        val c = parseOr()
                        if (next() != ")") fail("missing ')'")
                        return c
                    }
                }
                return parseComparison()
            }

            private fun parseComparison(): Cond {
                val left = next()
                val op = peek()
                if (op == null || op !in COMPARISONS) return single(left)
                next()
                val right = next()
                return compare(left, op, right)
            }

            private fun single(token: String): Cond {
                if (token == "true") return Cond { _, _ -> true }
                if (token == "false") return Cond { _, _ -> false }
                val ev = ev(token)
                if (ev is BooleanEncodedValue) return Cond { e, r -> if (r) e.getReverse(ev) else e.get(ev) }
                fail("'$token' is not a boolean")
            }

            private fun compare(left: String, op: String, right: String): Cond {
                enumCompare(left, op, right)?.let { return it }
                enumCompare(right, op, left)?.let { return it }
                val a = operand(left)
                val b = operand(right)
                return when (op) {
                    "==" -> Cond { e, r -> a.get(e, r) == b.get(e, r) }
                    "!=" -> Cond { e, r -> a.get(e, r) != b.get(e, r) }
                    "<" -> Cond { e, r -> a.get(e, r) < b.get(e, r) }
                    "<=" -> Cond { e, r -> a.get(e, r) <= b.get(e, r) }
                    ">" -> Cond { e, r -> a.get(e, r) > b.get(e, r) }
                    else -> Cond { e, r -> a.get(e, r) >= b.get(e, r) }
                }
            }

            private fun operand(token: String): Value {
                token.toDoubleOrNull()?.let { c -> return Value { _, _ -> c } }
                return numeric(token) ?: fail("'$token' is not numeric")
            }

            // "<enum ev> ==/!= CONSTANT"; returns null if [evName] is not an enum encoded value.
            private fun enumCompare(evName: String, op: String, constant: String): Cond? {
                if (op != "==" && op != "!=") return null
                if (!lookup.hasEncodedValue(evName)) return null
                val ev = ev(evName) as? EnumEncodedValue<*> ?: return null
                val ordinal = ev.values.firstOrNull { it.name == constant }?.ordinal
                    ?: fail("'$constant' is not a value of '$evName'")
                return if (op == "==") Cond { e, r -> (if (r) e.getReverse(ev as IntEncodedValue) else e.get(ev as IntEncodedValue)) == ordinal }
                else Cond { e, r -> (if (r) e.getReverse(ev as IntEncodedValue) else e.get(ev as IntEncodedValue)) != ordinal }
            }
        }

        private fun tokenize(src: String): List<String> {
            val out = ArrayList<String>()
            var i = 0
            while (i < src.length) {
                val c = src[i]
                when {
                    c.isWhitespace() -> i++
                    src.startsWith("&&", i) || src.startsWith("||", i) || src.startsWith("==", i) ||
                        src.startsWith("!=", i) || src.startsWith("<=", i) || src.startsWith(">=", i) -> { out.add(src.substring(i, i + 2)); i += 2 }
                    c == '<' || c == '>' || c == '!' || c == '(' || c == ')' -> { out.add(c.toString()); i++ }
                    c.isLetterOrDigit() || c == '_' || c == '.' || c == '-' -> {
                        val start = i
                        while (i < src.length && (src[i].isLetterOrDigit() || src[i] == '_' || src[i] == '.' || src[i] == '-')) i++
                        out.add(src.substring(start, i))
                    }
                    else -> throw IllegalArgumentException("Cannot parse condition '$src': unexpected '$c'")
                }
            }
            return out
        }
    }

    companion object {
        const val NAME = "custom"
        const val DEFAULT_HEADING_PENALTY = 300.0
        private const val SPEED_CONV = 3.6
        private val COMPARISONS = setOf("==", "!=", "<", "<=", ">", ">=")

        fun parseModel(json: String): CustomModel =
            com.graphhopper.jackson.Jackson.newObjectMapper().readValue(json, CustomModel::class.java)
    }
}
