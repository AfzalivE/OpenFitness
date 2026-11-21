package app.openfitness.app.ui

import app.openfitness.core.model.Exercise
import app.openfitness.core.model.Prescription
import app.openfitness.core.progression.RirSchedule
import kotlin.math.roundToInt

/** Formats a weight without a trailing ".0". */
fun formatKg(kg: Double): String {
    val tenths = (kg * 100).roundToInt()
    return if (tenths % 100 == 0) "${tenths / 100}" else {
        val s = (tenths / 100.0).toString()
        s.trimEnd('0').trimEnd('.')
    }
}

fun rirText(rir: Int): String = if (rir >= RirSchedule.DELOAD_RIR) "Easy" else "RIR $rir"

fun repRangeText(exercise: Exercise): String = "${exercise.repRange.first}–${exercise.repRange.last} reps"

fun suggestionText(rx: Prescription): String {
    val reps = rx.targetReps
    val kg = rx.weightKg
    return when {
        kg != null && reps != null -> "Do ${formatKg(kg)} kg × $reps"
        reps != null -> "Do $reps reps"
        kg != null -> "Use ${formatKg(kg)} kg"
        else -> "No data yet. Choose a weight that gives the target RIR."
    }
}

fun formatSeconds(total: Int): String {
    val m = total / 60
    val s = total % 60
    return "$m:${s.toString().padStart(2, '0')}"
}
