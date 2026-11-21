package app.openfitness.app.data

import app.openfitness.core.library.ExerciseLibrary
import app.openfitness.core.model.Exercise
import app.openfitness.core.model.ExerciseSlot
import app.openfitness.core.model.MesocyclePlan
import app.openfitness.core.model.Muscle
import app.openfitness.core.model.TrainingDay
import app.openfitness.core.model.VolumeRange
import app.openfitness.core.model.linkWithNext
import app.openfitness.core.model.normalized
import app.openfitness.core.model.split
import app.openfitness.core.progression.MesocycleEngine

/**
 * Pure functions to make and change a plan in the planner.
 * Each function returns a new plan.
 */
object Planner {
    const val MIN_DAYS = 2
    const val MAX_DAYS = 6
    const val MAX_START_SETS = MesocycleEngine.MAX_SETS_PER_SLOT

    /** A new, empty plan with [days] days. */
    fun newPlan(name: String = "My plan", days: Int = 3, hardWeeks: Int = 4): MesocyclePlan {
        require(days in MIN_DAYS..MAX_DAYS)
        return MesocyclePlan(name, List(days) { TrainingDay("Day ${it + 1}", emptyList()) }, hardWeeks)
    }

    /** A copy of [template] that the user can change. */
    fun fromTemplate(template: MesocyclePlan): MesocyclePlan = template.copy(name = "${template.name} (copy)")

    fun rename(plan: MesocyclePlan, name: String) = plan.copy(name = name)

    fun setHardWeeks(plan: MesocyclePlan, weeks: Int) =
        plan.copy(accumulationWeeks = weeks.coerceIn(Workflow.MIN_HARD_WEEKS, Workflow.MAX_HARD_WEEKS))

    /** Adds empty days or removes days from the end, to get [count] days. */
    fun setDayCount(plan: MesocyclePlan, count: Int): MesocyclePlan {
        val n = count.coerceIn(MIN_DAYS, MAX_DAYS)
        val days = if (n <= plan.days.size) plan.days.take(n)
        else plan.days + List(n - plan.days.size) { TrainingDay("Day ${plan.days.size + it + 1}", emptyList()) }
        return plan.copy(days = days)
    }

    fun renameDay(plan: MesocyclePlan, day: Int, name: String) = updateDay(plan, day) { it.copy(name = name) }

    /** Exercises that the user can add to [day]: not in the day yet, and of [muscle] if set. */
    fun addOptions(plan: MesocyclePlan, day: Int, muscle: Muscle? = null, library: List<Exercise> = ExerciseLibrary.all): List<Exercise> {
        val used = plan.days[day].slots.map { it.exerciseId }.toSet()
        return library.filter { it.id !in used && (muscle == null || it.primary == muscle) }
    }

    fun addExercise(plan: MesocyclePlan, day: Int, exerciseId: String, exercises: Map<String, Exercise> = ExerciseLibrary.byId): MesocyclePlan {
        val ex = requireNotNull(exercises[exerciseId]) { "Unknown exercise $exerciseId" }
        require(plan.days[day].slots.none { it.exerciseId == exerciseId }) { "The day already has this exercise" }
        return updateDay(plan, day) { it.copy(slots = it.slots + ExerciseSlot(ex.id, ex.primary)) }
    }

    fun removeExercise(plan: MesocyclePlan, day: Int, slot: Int) =
        updateDay(plan, day) { d -> d.copy(slots = d.slots.filterIndexed { i, _ -> i != slot }).normalized() }

    /** Moves a slot up (-1) or down (+1). A move can break a superset; the groups are then fixed. */
    fun moveExercise(plan: MesocyclePlan, day: Int, slot: Int, by: Int) = updateDay(plan, day) { d ->
        val to = (slot + by).coerceIn(0, d.slots.lastIndex)
        if (to == slot) d else {
            val list = d.slots.toMutableList()
            val item = list.removeAt(slot)
            list.add(to, item)
            d.copy(slots = list).normalized()
        }
    }

    /** Sets the start sets of one exercise. Null gives the split from the muscle's start sets. */
    fun setSlotStartSets(plan: MesocyclePlan, day: Int, slot: Int, sets: Int?) = updateDay(plan, day) { d ->
        d.copy(slots = d.slots.mapIndexed { i, s -> if (i == slot) s.copy(startSets = sets?.coerceIn(1, MAX_START_SETS)) else s })
    }

    fun linkWithNext(plan: MesocyclePlan, day: Int, slot: Int) = updateDay(plan, day) { it.linkWithNext(slot) }

    fun splitSuperset(plan: MesocyclePlan, day: Int, slot: Int) = updateDay(plan, day) { it.split(slot) }

    fun volume(plan: MesocyclePlan, muscle: Muscle): VolumeRange = plan.volume[muscle] ?: VolumeRange(4, 16)

    /** Sets the weekly start sets of a muscle. The max goes up if it is lower. */
    fun setMuscleStartSets(plan: MesocyclePlan, muscle: Muscle, sets: Int): MesocyclePlan {
        val v = volume(plan, muscle)
        val start = sets.coerceIn(0, 40)
        return plan.copy(volume = plan.volume + (muscle to VolumeRange(start, maxOf(v.maxSets, start))))
    }

    /** Sets the weekly max sets of a muscle. The start goes down if it is higher. */
    fun setMuscleMaxSets(plan: MesocyclePlan, muscle: Muscle, sets: Int): MesocyclePlan {
        val v = volume(plan, muscle)
        val max = sets.coerceIn(1, 40)
        return plan.copy(volume = plan.volume + (muscle to VolumeRange(minOf(v.startSets, max), max)))
    }

    /** Muscles that the plan trains, in the order of [Muscle]. */
    fun musclesInPlan(plan: MesocyclePlan): List<Muscle> {
        val used = plan.days.flatMap { d -> d.slots.map { it.muscle } }.toSet()
        return Muscle.entries.filter { it in used }
    }

    /** The weekly sets for each muscle in the first week of this plan. */
    fun startSetsPerMuscle(plan: MesocyclePlan, exercises: Map<String, Exercise> = ExerciseLibrary.byId): Map<Muscle, Int> {
        val week = MesocycleEngine(plan, exercises).firstWeek()
        val result = linkedMapOf<Muscle, Int>()
        musclesInPlan(plan).forEach { result[it] = 0 }
        week.forEachIndexed { d, day -> day.forEachIndexed { s, rx -> val m = plan.days[d].slots[s].muscle; result[m] = result.getValue(m) + rx.sets } }
        return result
    }

    /** The sets of each slot in the first week, by day and slot. */
    fun startSetsPerSlot(plan: MesocyclePlan, exercises: Map<String, Exercise> = ExerciseLibrary.byId): List<List<Int>> =
        MesocycleEngine(plan, exercises).firstWeek().map { day -> day.map { it.sets } }

    /** Problems that stop the user from saving or starting the plan. Empty when the plan is good. */
    fun problems(plan: MesocyclePlan): List<String> = buildList {
        if (plan.name.isBlank()) add("Give the plan a name.")
        if (plan.days.size !in MIN_DAYS..MAX_DAYS) add("A plan must have $MIN_DAYS to $MAX_DAYS days.")
        plan.days.forEachIndexed { i, d ->
            if (d.name.isBlank()) add("Give day ${i + 1} a name.")
            if (d.slots.isEmpty()) add("Add at least one exercise to ${d.name.ifBlank { "day ${i + 1}" }}.")
        }
    }

    fun isValid(plan: MesocyclePlan) = problems(plan).isEmpty()

    /** Saves [plan] in the list of custom plans. A plan with the same name is replaced. */
    fun save(data: AppData, plan: MesocyclePlan): AppData {
        require(isValid(plan)) { problems(plan).joinToString(" ") }
        val name = plan.name.trim()
        val saved = plan.copy(name = name)
        return data.copy(customPlans = data.customPlans.filterNot { it.name == name } + saved)
    }

    fun delete(data: AppData, name: String): AppData = data.copy(customPlans = data.customPlans.filterNot { it.name == name })

    private fun updateDay(plan: MesocyclePlan, day: Int, change: (TrainingDay) -> TrainingDay) =
        plan.copy(days = plan.days.mapIndexed { i, d -> if (i == day) change(d) else d })
}
