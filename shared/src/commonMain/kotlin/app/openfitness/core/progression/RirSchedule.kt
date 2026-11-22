package app.openfitness.core.progression

import kotlin.math.roundToInt

/**
 * Target reps in reserve (RIR) for each week of a block.
 * The first week starts at [ProgressionConfig.startRir]. The last hard week ends at 0.
 * The deload week uses [ProgressionConfig.deloadRir].
 */
object RirSchedule {
    /** The default start RIR. See [ProgressionConfig.startRir]. */
    val START_RIR = ProgressionConfig.Default.startRir
    /** The default deload RIR. See [ProgressionConfig.deloadRir]. */
    val DELOAD_RIR = ProgressionConfig.Default.deloadRir

    fun targetRir(week: Int, accumulationWeeks: Int, config: ProgressionConfig = ProgressionConfig.Default): Int {
        require(week in 0..accumulationWeeks) { "week out of range" }
        if (week == accumulationWeeks) return config.deloadRir
        if (accumulationWeeks == 1) return 0
        val fraction = week.toDouble() / (accumulationWeeks - 1)
        return (config.startRir * (1 - fraction)).roundToInt()
    }
}
