package app.openfitness.app.data

import app.openfitness.core.model.JointPain
import app.openfitness.core.model.MesocyclePlan
import app.openfitness.core.model.Muscle
import app.openfitness.core.model.MuscleFeedback
import app.openfitness.core.model.Prescription
import app.openfitness.core.model.SessionRecord
import app.openfitness.core.units.Units
import app.openfitness.core.units.WeightUnit
import kotlinx.serialization.Serializable

/** All the data that the app stores on the device. */
@Serializable
data class AppData(
    val block: Block? = null,
    val settings: Settings = Settings(),
    /** Plans that the user made and saved. */
    val customPlans: List<MesocyclePlan> = emptyList(),
    /** The plan that is open in the planner, or null. */
    val draft: MesocyclePlan? = null,
)

@Serializable
data class Settings(
    val unit: WeightUnit = WeightUnit.LB,
    val theme: ThemeMode = ThemeMode.DARK,
)

/** The color theme. Dark is the default. */
@Serializable
enum class ThemeMode { DARK, LIGHT, SYSTEM }

/** One value that the user enters for a set. Null means "not entered yet". */
@Serializable
data class SetEntry(
    /** The weight in kg. The app uses this value for all math. */
    val weightKg: Double? = null,
    val reps: Int? = null,
    val done: Boolean = false,
    /** The weight as the user entered (or saw) it, in [enteredUnit]. Null in old data. */
    val enteredValue: Double? = null,
    /** The unit of [enteredValue]. Null in old data, which is kg. */
    val enteredUnit: WeightUnit? = null,
) {
    /** Sets the weight from a value in [unit]. */
    fun withWeight(value: Double?, unit: WeightUnit): SetEntry =
        copy(weightKg = value?.let { Units.toKg(it, unit) }, enteredValue = value, enteredUnit = value?.let { unit })

    /** The weight to show in [unit], or null. */
    fun weightIn(unit: WeightUnit): Double? = weightKg?.let { Units.displayLogged(it, enteredValue, enteredUnit, unit) }
}

/** One exercise of the workout that is in progress. */
@Serializable
data class ExerciseEntry(
    /** Index of the slot in the training day. */
    val slotIndex: Int,
    val exerciseId: String,
    val sets: List<SetEntry>,
    val jointPain: JointPain? = null,
) {
    val allSetsDone: Boolean get() = sets.isNotEmpty() && sets.all { it.done }
    val anySetDone: Boolean get() = sets.any { it.done }
}

/** The workout that the user is doing now. */
@Serializable
data class ActiveWorkout(
    val week: Int,
    val dayIndex: Int,
    val exercises: List<ExerciseEntry>,
    val feedback: Map<Muscle, MuscleFeedback> = emptyMap(),
)

/**
 * A training block that the user started.
 *
 * [plan] holds the days and exercise slots, with the swaps that the user made.
 * [weeks] holds the prescriptions that the engine made, by week and then by day.
 * The last item of [weeks] is the current week.
 */
@Serializable
data class Block(
    val plan: MesocyclePlan,
    val weeks: List<List<List<Prescription>>>,
    val sessions: List<SessionRecord> = emptyList(),
    val active: ActiveWorkout? = null,
) {
    val currentWeek: Int get() = weeks.lastIndex

    fun isDayDone(week: Int, dayIndex: Int): Boolean =
        sessions.any { it.week == week && it.dayIndex == dayIndex }

    /** The next day to train in the current week, or null when the week is done. */
    val nextDayIndex: Int?
        get() = plan.days.indices.firstOrNull { !isDayDone(currentWeek, it) }

    /** True when the user finished all the days of the deload week. */
    val isComplete: Boolean
        get() = currentWeek == plan.accumulationWeeks && nextDayIndex == null

    /** Total sets for each muscle in [week], in the order of the plan. */
    fun weeklySets(week: Int): Map<Muscle, Int> {
        val result = linkedMapOf<Muscle, Int>()
        weeks[week].forEachIndexed { d, day ->
            day.forEachIndexed { s, rx ->
                val muscle = plan.days[d].slots[s].muscle
                result[muscle] = (result[muscle] ?: 0) + rx.sets
            }
        }
        return result
    }
}
