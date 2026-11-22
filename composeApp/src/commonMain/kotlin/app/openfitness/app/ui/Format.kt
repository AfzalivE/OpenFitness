package app.openfitness.app.ui

import app.openfitness.core.model.Exercise
import app.openfitness.core.model.Prescription
import app.openfitness.core.progression.RirSchedule
import app.openfitness.core.units.Units
import app.openfitness.core.units.WeightUnit

/** A weight in kg, shown in [unit] and rounded to the load step of [exercise]. */
fun weightText(kg: Double, exercise: Exercise, unit: WeightUnit): String =
    "${Units.format(Units.display(kg, exercise, unit))} ${unit.label}"

fun rirText(rir: Int): String = if (rir >= RirSchedule.DELOAD_RIR) "Easy" else "RIR $rir"

fun repRangeText(exercise: Exercise): String = "${exercise.repRange.first}–${exercise.repRange.last} reps"

fun suggestionText(rx: Prescription, exercise: Exercise, unit: WeightUnit): String {
    val reps = rx.targetReps
    val kg = rx.weightKg
    return when {
        kg != null && reps != null -> "Do ${weightText(kg, exercise, unit)} × $reps"
        reps != null -> "Do $reps reps"
        kg != null -> "Use ${weightText(kg, exercise, unit)}"
        else -> "No data yet. Choose a weight that gives the target RIR."
    }
}

fun formatSeconds(total: Int): String {
    val m = total / 60
    val s = total % 60
    return "$m:${s.toString().padStart(2, '0')}"
}
