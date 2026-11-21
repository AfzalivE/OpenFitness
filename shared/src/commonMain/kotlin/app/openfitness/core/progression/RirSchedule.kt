package app.openfitness.core.progression

import kotlin.math.roundToInt

/**
 * Target reps in reserve (RIR) for each week of a block.
 * The first week starts at [START_RIR]. The last hard week ends at 0.
 * The deload week uses [DELOAD_RIR].
 */
object RirSchedule {
    const val START_RIR = 3
    const val DELOAD_RIR = 5

    fun targetRir(week: Int, accumulationWeeks: Int): Int {
        require(week in 0..accumulationWeeks) { "week out of range" }
        if (week == accumulationWeeks) return DELOAD_RIR
        if (accumulationWeeks == 1) return 0
        val fraction = week.toDouble() / (accumulationWeeks - 1)
        return (START_RIR * (1 - fraction)).roundToInt()
    }
}
