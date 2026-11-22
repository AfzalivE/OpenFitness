package app.openfitness.core.units

import app.openfitness.core.model.Equipment
import app.openfitness.core.model.Exercise
import kotlinx.serialization.Serializable
import kotlin.math.roundToInt

/** The unit that the app shows weights in. The app always stores weights in kg. */
@Serializable
enum class WeightUnit(val label: String) { KG("kg"), LB("lb") }

object Units {
    const val KG_PER_LB = 0.45359237

    fun toUnit(kg: Double, unit: WeightUnit): Double = when (unit) {
        WeightUnit.KG -> kg
        WeightUnit.LB -> kg / KG_PER_LB
    }

    fun toKg(value: Double, unit: WeightUnit): Double = when (unit) {
        WeightUnit.KG -> value
        WeightUnit.LB -> value * KG_PER_LB
    }

    /**
     * The smallest practical weight change for [exercise], in [unit].
     * In lb: 5 lb for barbells, machines, and heavy cable or dumbbell
     * work; 2.5 lb for light dumbbell and cable work.
     */
    fun loadStep(exercise: Exercise, unit: WeightUnit): Double = when (unit) {
        WeightUnit.KG -> exercise.loadStepKg
        WeightUnit.LB -> when (exercise.equipment) {
            Equipment.DUMBBELL, Equipment.CABLE, Equipment.BAND -> if (exercise.loadStepKg >= 2.0) 5.0 else 2.5
            Equipment.BARBELL, Equipment.MACHINE, Equipment.SMITH_MACHINE, Equipment.BODYWEIGHT -> 5.0
        }
    }

    /** Rounds [value] to the nearest multiple of [step]. */
    fun roundToStep(value: Double, step: Double): Double =
        if (step <= 0) value else (value / step).roundToInt() * step

    /**
     * Rounds a weight that came from the other unit: kg to the nearest
     * 0.5 kg, lb to the nearest 1 lb.
     */
    fun roundConverted(value: Double, unit: WeightUnit): Double = when (unit) {
        WeightUnit.KG -> (value * 2).roundToInt() / 2.0
        WeightUnit.LB -> value.roundToInt().toDouble()
    }

    /**
     * A weight in kg, shown in [unit]. If it is on a load step of [exercise]
     * in [unit], it shows exactly (for example 185 lb). Else it came from the
     * other unit, and it is rounded with [roundConverted] (185 lb shows 84 kg).
     */
    fun display(kg: Double, exercise: Exercise, unit: WeightUnit): Double {
        val value = toUnit(kg, unit)
        val onStep = roundToStep(value, loadStep(exercise, unit))
        return if (kotlin.math.abs(onStep - value) < 0.01) onStep else roundConverted(value, unit)
    }

    /**
     * A logged weight, shown in [unit]. When the user entered it in [unit],
     * it shows the exact value that they entered. Else it is converted and
     * rounded with [roundConverted]. A weight with no entered unit is old
     * data, which was entered in kg.
     */
    fun displayLogged(kg: Double, enteredValue: Double?, enteredUnit: WeightUnit?, unit: WeightUnit): Double {
        val fromUnit = enteredUnit ?: WeightUnit.KG
        return when {
            fromUnit == unit && enteredValue != null -> enteredValue
            fromUnit == unit -> toUnit(kg, unit)
            else -> roundConverted(toUnit(kg, unit), unit)
        }
    }

    /**
     * The exercises with a load step in kg that matches the load step in
     * [unit]. The engine adds these steps, so weights stay on the steps of
     * the unit that the user selected.
     */
    fun exercisesFor(unit: WeightUnit, exercises: Collection<Exercise>): Map<String, Exercise> =
        exercises.associate { it.id to it.copy(loadStepKg = toKg(loadStep(it, unit), unit)) }

    /** Formats a weight with at most 1 decimal and no trailing zero. */
    fun format(value: Double): String {
        val tenths = (value * 10).roundToInt()
        val whole = tenths / 10
        val frac = kotlin.math.abs(tenths % 10)
        val sign = if (tenths < 0 && whole == 0) "-" else ""
        return if (frac == 0) "$sign$whole" else "$sign$whole.$frac"
    }
}
