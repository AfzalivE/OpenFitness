package app.openfitness.core.model

import kotlinx.serialization.Serializable

/** Planned exercise in a training day. The set count changes each week. */
@Serializable
data class ExerciseSlot(
    val exerciseId: String,
    val muscle: Muscle,
)

@Serializable
data class TrainingDay(
    val name: String,
    val slots: List<ExerciseSlot>,
)

/**
 * A block of training. It has [accumulationWeeks] weeks of hard training,
 * then one deload week.
 */
@Serializable
data class MesocyclePlan(
    val name: String,
    val days: List<TrainingDay>,
    val accumulationWeeks: Int = 4,
    val volume: Map<Muscle, VolumeRange> = DefaultVolume.ranges,
) {
    init {
        require(accumulationWeeks in 3..8) { "accumulationWeeks must be in 3..8" }
        require(days.isNotEmpty()) { "A plan must have at least one day" }
    }

    val totalWeeks: Int get() = accumulationWeeks + 1
    fun isDeload(week: Int): Boolean = week == accumulationWeeks
}

/** One logged set. */
@Serializable
data class LoggedSet(
    val weightKg: Double,
    val reps: Int,
    /** Reps in reserve that the user reports, if any. */
    val rir: Int? = null,
)

/** The result of one exercise in one session. */
@Serializable
data class ExerciseResult(
    val exerciseId: String,
    val sets: List<LoggedSet>,
    val jointPain: JointPain = JointPain.NONE,
)

/** Prescription for one exercise in one session. */
@Serializable
data class Prescription(
    val exerciseId: String,
    val sets: Int,
    val targetRir: Int,
    val weightKg: Double?,
    val targetReps: Int?,
    /** Short reason text that the UI can show, or null. */
    val note: String? = null,
)

/** Default weekly set ranges. The user can change them per block. */
object DefaultVolume {
    val ranges: Map<Muscle, VolumeRange> = mapOf(
        Muscle.CHEST to VolumeRange(8, 22),
        Muscle.BACK to VolumeRange(10, 25),
        Muscle.FRONT_DELTS to VolumeRange(0, 12),
        Muscle.SIDE_DELTS to VolumeRange(8, 26),
        Muscle.REAR_DELTS to VolumeRange(6, 22),
        Muscle.BICEPS to VolumeRange(8, 20),
        Muscle.TRICEPS to VolumeRange(6, 18),
        Muscle.QUADS to VolumeRange(8, 20),
        Muscle.HAMSTRINGS to VolumeRange(6, 16),
        Muscle.GLUTES to VolumeRange(0, 16),
        Muscle.CALVES to VolumeRange(8, 16),
        Muscle.TRAPS to VolumeRange(0, 26),
        Muscle.ABS to VolumeRange(0, 25),
        Muscle.FOREARMS to VolumeRange(0, 20),
    )
}
