package app.openfitness.core.progression

import kotlinx.serialization.Serializable

/**
 * The values that the recommendation engine uses. The app uses [ProgressionConfig.Default].
 * A test or a later setting can give other values to [MesocycleEngine].
 */
@Serializable
data class ProgressionConfig(
    /** Sets for each exercise in week 1, when the exercise has no start sets of its own. */
    val startSetsPerExercise: Int = 2,
    /** The fewest sets that an exercise can go down to. */
    val minSetsPerExercise: Int = 1,
    /** The most sets that one exercise can have in one session. */
    val maxSetsPerExercise: Int = 8,
    /** Target reps in reserve in week 1. The last hard week is always 0. */
    val startRir: Int = 3,
    /** Target reps in reserve in the deload week. */
    val deloadRir: Int = 5,
    /** The deload week has this part of the sets of the last hard week. */
    val deloadSetFactor: Double = 0.5,
    /** The deload week has this part of the reps of the last hard week. */
    val deloadRepFactor: Double = 0.5,
    /** Moderate or severe joint pain changes the weight by this factor. */
    val jointPainLoadFactor: Double = 0.9,
) {
    init {
        require(minSetsPerExercise >= 1) { "minSetsPerExercise must be 1 or more" }
        require(maxSetsPerExercise >= minSetsPerExercise) { "maxSetsPerExercise must be minSetsPerExercise or more" }
        require(startSetsPerExercise in minSetsPerExercise..maxSetsPerExercise) { "startSetsPerExercise must be in min..max" }
        require(startRir >= 0 && deloadRir >= 0) { "RIR must be 0 or more" }
        require(deloadSetFactor in 0.0..1.0 && deloadRepFactor in 0.0..1.0) { "Deload factors must be in 0..1" }
        require(jointPainLoadFactor in 0.0..1.0) { "jointPainLoadFactor must be in 0..1" }
    }

    companion object {
        val Default = ProgressionConfig()
    }
}
