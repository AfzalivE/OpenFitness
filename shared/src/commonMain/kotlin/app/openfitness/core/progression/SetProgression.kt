package app.openfitness.core.progression

import app.openfitness.core.model.MuscleFeedback
import app.openfitness.core.model.Pump
import app.openfitness.core.model.Soreness
import app.openfitness.core.model.Workload

/**
 * Finds how many sets to add or remove for a muscle next week.
 *
 * The rule: recovery comes first. If the muscle did not recover, or the
 * work was too much, remove a set. Otherwise, add sets when the muscle
 * recovered fast and the session felt easy or gave a low pump.
 */
object SetProgression {
    const val MAX_ADD = 2

    fun setDelta(feedback: MuscleFeedback?): Int {
        if (feedback == null) return 1
        if (feedback.soreness == Soreness.STILL_SORE) return -1
        if (feedback.workload == Workload.TOO_MUCH) return -1

        val recovery = when (feedback.soreness) {
            Soreness.NONE -> 2
            Soreness.RECOVERED_EARLY -> 1
            Soreness.RECOVERED_ON_TIME, null -> 0
            Soreness.STILL_SORE -> error("handled above")
        }
        val stimulus = when (feedback.pump) {
            Pump.LOW -> 1
            Pump.MODERATE, null -> 0
            Pump.HIGH -> -1
        }
        val effort = when (feedback.workload) {
            Workload.EASY -> 1
            Workload.GOOD, null -> 0
            Workload.HARD -> -1
            Workload.TOO_MUCH -> error("handled above")
        }
        // A muscle that just recovered on time gets no new sets.
        if (feedback.soreness == Soreness.RECOVERED_ON_TIME && effort <= 0) return 0

        return when (recovery + stimulus + effort) {
            in 3..Int.MAX_VALUE -> MAX_ADD
            in 1..2 -> 1
            else -> 0
        }
    }
}
