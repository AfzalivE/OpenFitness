package app.openfitness.core.model

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
 * [startSets] is the weekly volume in the first week of a block.
 * [maxSets] is the highest weekly volume that the app will program.
 */
data class VolumeRange(val startSets: Int, val maxSets: Int) {
    init {
        require(startSets in 0..maxSets) { "startSets must be in 0..maxSets" }
    }
}
