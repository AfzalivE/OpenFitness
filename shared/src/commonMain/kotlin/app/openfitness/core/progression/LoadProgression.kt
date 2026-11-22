package app.openfitness.core.progression

import app.openfitness.core.model.Exercise
import app.openfitness.core.model.JointPain
import app.openfitness.core.model.LoadType
import app.openfitness.core.model.LoggedSet
import kotlin.math.ceil
import kotlin.math.roundToInt

data class LoadTarget(val weightKg: Double?, val targetReps: Int?, val note: String? = null)

/**
 * Finds the weight and rep target for an exercise next week.
 *
 * It uses double progression: add reps until the top of the rep range,
 * then add one load step and go back down in reps. Joint pain makes the
 * load go down.
 */
object LoadProgression {
    fun next(
        exercise: Exercise,
        lastSets: List<LoggedSet>,
        lastRir: Int,
        nextRir: Int,
        jointPain: JointPain,
        deload: Boolean,
        config: ProgressionConfig = ProgressionConfig.Default,
    ): LoadTarget {
        val top = lastSets.firstOrNull() ?: return LoadTarget(null, null)
        val usesWeight = exercise.loadType != LoadType.REPS_ONLY
        val weight = if (usesWeight) top.weightKg else null

        if (deload) {
            val reps = ceil(top.reps * config.deloadRepFactor).toInt().coerceAtLeast(1)
            return LoadTarget(weight, reps, "Deload: easy sets, fewer reps.")
        }

        when (jointPain) {
            JointPain.MODERATE, JointPain.SEVERE -> {
                val lighter = weight?.let { roundToStep(it * config.jointPainLoadFactor, exercise.loadStepKg) }
                val note = if (jointPain == JointPain.SEVERE) {
                    "Joint pain was severe. Use less weight or change to a different exercise."
                } else {
                    "Joint pain was moderate. The weight is lower this week."
                }
                return LoadTarget(lighter, top.reps, note)
            }
            JointPain.NONE, JointPain.MILD -> Unit
        }

        // Each step of RIR that we remove is about one more rep.
        val repsGain = (lastRir - nextRir).coerceAtLeast(0).coerceAtMost(2)
        val wantedReps = top.reps + repsGain.coerceAtLeast(1)

        if (!usesWeight || exercise.loadType == LoadType.BODYWEIGHT_PLUS && (weight ?: 0.0) == 0.0) {
            return LoadTarget(weight, wantedReps)
        }

        return if (wantedReps > exercise.repRange.last) {
            val heavier = (weight ?: 0.0) + exercise.loadStepKg
            // About 1 rep is lost for each 2.5% of added load.
            val repLoss = ((exercise.loadStepKg / (weight ?: 1.0).coerceAtLeast(1.0)) / 0.025)
                .roundToInt().coerceIn(1, 4)
            LoadTarget(heavier, (wantedReps - repLoss).coerceAtLeast(exercise.repRange.first))
        } else {
            LoadTarget(weight, wantedReps)
        }
    }

    /** Rounds [value] to the nearest multiple of [step]. */
    fun roundToStep(value: Double, step: Double): Double =
        if (step <= 0) value else (value / step).roundToInt() * step
}
