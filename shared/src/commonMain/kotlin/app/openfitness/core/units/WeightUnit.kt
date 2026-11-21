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

    /** The weight in [unit], rounded to the load step of [exercise]. */
    fun display(kg: Double, exercise: Exercise, unit: WeightUnit): Double =
        roundToStep(toUnit(kg, unit), loadStep(exercise, unit))

    /**
     * The exercises with a load step in kg that matches the load step in
     * [unit]. The engine adds these steps, so weights stay on the steps of
     * the unit that the user selected.
     */
    fun exercisesFor(unit: WeightUnit, exercises: Collection<Exercise>): Map<String, Exercise> =
        exercises.associate { it.id to it.copy(loadStepKg = toKg(loadStep(it, unit), unit)) }

    /** Formats a weight with up to 2 decimals and no trailing zeros. */
    fun format(value: Double): String {
        val hundredths = (value * 100).roundToInt()
        val whole = hundredths / 100
        val frac = kotlin.math.abs(hundredths % 100)
        return when {
            frac == 0 -> "$whole"
            frac % 10 == 0 -> "$whole.${frac / 10}"
            else -> "$whole.${frac.toString().padStart(2, '0')}"
        }
    }
}
