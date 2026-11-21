package app.openfitness.core.model

enum class Equipment { BARBELL, DUMBBELL, MACHINE, CABLE, BODYWEIGHT, SMITH_MACHINE, BAND }

/** How the weight of an exercise is set. This controls the load step. */
enum class LoadType { FIXED_STEP, BODYWEIGHT_PLUS, REPS_ONLY }

data class Exercise(
    val id: String,
    val name: String,
    val primary: Muscle,
    val secondary: Set<Muscle> = emptySet(),
    val equipment: Equipment,
    val loadType: LoadType = LoadType.FIXED_STEP,
    /** The smallest weight change in kg that is practical for this exercise. */
    val loadStepKg: Double = 2.5,
    val repRange: IntRange = 8..12,
    val notes: String = "",
)
