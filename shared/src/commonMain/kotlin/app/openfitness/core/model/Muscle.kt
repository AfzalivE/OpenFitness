package app.openfitness.core.model

import kotlinx.serialization.Serializable

/** A muscle group that the app tracks weekly volume for. */
enum class Muscle(val displayName: String) {
    CHEST("Chest"),
    BACK("Back"),
    FRONT_DELTS("Front delts"),
    SIDE_DELTS("Side delts"),
    REAR_DELTS("Rear delts"),
    BICEPS("Biceps"),
    TRICEPS("Triceps"),
    QUADS("Quads"),
    HAMSTRINGS("Hamstrings"),
    GLUTES("Glutes"),
    CALVES("Calves"),
    TRAPS("Traps"),
    ABS("Abs"),
    FOREARMS("Forearms"),
}

/**
 * Weekly set range for one muscle.
 *
 * [startSets] is not used by the engine since week 1 gives each exercise
 * [app.openfitness.core.progression.ProgressionConfig.startSetsPerExercise] sets.
 * It stays in the model so that saved data still loads.
 * [maxSets] is the highest weekly volume that the app will program.
 */
@Serializable
data class VolumeRange(val startSets: Int, val maxSets: Int) {
    init {
        require(startSets in 0..maxSets) { "startSets must be in 0..maxSets" }
    }
}
