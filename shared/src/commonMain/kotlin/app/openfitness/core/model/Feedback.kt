package app.openfitness.core.model

/**
 * Feedback scales. Each value has an ordinal from low to high.
 * The user gives this feedback during or after a workout.
 */

/** How much soreness the muscle had since the last session for it. */
enum class Soreness {
    /** No soreness at all. */
    NONE,
    /** Sore, but recovered well before this session. */
    RECOVERED_EARLY,
    /** Sore, and recovered just in time for this session. */
    RECOVERED_ON_TIME,
    /** Still sore at the start of this session. */
    STILL_SORE,
}

/** How strong the pump was in the target muscle. */
enum class Pump { LOW, MODERATE, HIGH }

/** How hard the total work for the muscle felt in this session. */
enum class Workload { EASY, GOOD, HARD, TOO_MUCH }

/** Joint pain during the exercise. */
enum class JointPain { NONE, MILD, MODERATE, SEVERE }

/** Feedback for one muscle in one session. */
data class MuscleFeedback(
    val soreness: Soreness? = null,
    val pump: Pump? = null,
    val workload: Workload? = null,
)
