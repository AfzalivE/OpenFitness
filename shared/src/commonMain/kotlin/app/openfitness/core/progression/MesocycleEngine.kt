package app.openfitness.core.progression

import app.openfitness.core.model.Exercise
import app.openfitness.core.model.ExerciseSlot
import app.openfitness.core.model.MesocyclePlan
import app.openfitness.core.model.Muscle
import app.openfitness.core.model.Prescription
import app.openfitness.core.model.SessionRecord
import app.openfitness.core.model.VolumeRange

/**
 * Makes the prescriptions for each week of a [MesocyclePlan].
 *
 * Week 0 gives each exercise [ProgressionConfig.startSetsPerExercise] sets.
 * Each later week
 * comes from the previous week's sessions and feedback. The weekly sets of
 * a muscle do not go above its [VolumeRange.maxSets].
 */
class MesocycleEngine(
    private val plan: MesocyclePlan,
    private val exercises: Map<String, Exercise>,
    val config: ProgressionConfig = ProgressionConfig.Default,
) {
    init {
        plan.days.flatMap { it.slots }.forEach {
            require(it.exerciseId in exercises) { "Unknown exercise ${it.exerciseId}" }
        }
    }

    private data class SlotRef(val dayIndex: Int, val slotIndex: Int, val slot: ExerciseSlot)

    private val slotsByMuscle: Map<Muscle, List<SlotRef>> =
        plan.days.flatMapIndexed { d, day -> day.slots.mapIndexed { s, slot -> SlotRef(d, s, slot) } }
            .groupBy { it.slot.muscle }

    private fun range(muscle: Muscle): VolumeRange = plan.volume[muscle] ?: VolumeRange(4, 16)

    /** Prescriptions for the first week, by day index. */
    fun firstWeek(): List<List<Prescription>> {
        val rir = RirSchedule.targetRir(0, plan.accumulationWeeks, config)
        return plan.days.map { day ->
            day.slots.map { slot ->
                Prescription(slot.exerciseId, startSets(slot), rir, weightKg = null, targetReps = null)
            }
        }
    }

    /**
     * Week 1 sets for a slot: always [ProgressionConfig.startSetsPerExercise].
     * The user changes sets in the first workout, and the engine goes on from
     * the sets that were logged. [ExerciseSlot.startSets] in old data is ignored.
     */
    @Suppress("UNUSED_PARAMETER")
    fun startSets(slot: ExerciseSlot): Int = config.startSetsPerExercise

    /**
     * Prescriptions for [week], from the sessions of week - 1.
     * [previous] has one record for each day of the previous week.
     */
    fun nextWeek(week: Int, previous: List<SessionRecord>): List<List<Prescription>> {
        require(week in 1 until plan.totalWeeks) { "week out of range" }
        val byDay = previous.associateBy { it.dayIndex }
        require(byDay.keys.containsAll(plan.days.indices.toList())) { "Need a record for every day" }

        val lastRir = RirSchedule.targetRir(week - 1, plan.accumulationWeeks, config)
        val nextRir = RirSchedule.targetRir(week, plan.accumulationWeeks, config)
        val deload = plan.isDeload(week)

        val newSets = mutableMapOf<Pair<Int, Int>, Int>()
        for ((muscle, refs) in slotsByMuscle) {
            // Start from the sets that the user did. If no set was logged, use the prescription.
            val oldSets = refs.map { ref -> setsDone(byDay.getValue(ref.dayIndex), ref.slotIndex) }
            val planned = if (deload) {
                oldSets.map { (it * config.deloadSetFactor).toInt().coerceAtLeast(config.minSetsPerExercise) }
            } else {
                val deltas = refs.map { ref -> SetProgression.setDelta(byDay.getValue(ref.dayIndex).feedback[muscle]) }
                capWeekly(oldSets, deltas, range(muscle).maxSets, config.minSetsPerExercise, config.maxSetsPerExercise)
            }
            planned.forEachIndexed { i, n -> newSets[refs[i].dayIndex to refs[i].slotIndex] = n }
        }

        return plan.days.mapIndexed { d, day ->
            val record = byDay.getValue(d)
            day.slots.mapIndexed { s, slot ->
                val exercise = exercises.getValue(slot.exerciseId)
                val result = record.resultFor(slot.exerciseId)
                val load = LoadProgression.next(
                    exercise = exercise,
                    lastSets = result?.sets.orEmpty(),
                    lastRir = lastRir,
                    nextRir = nextRir,
                    jointPain = result?.jointPain ?: app.openfitness.core.model.JointPain.NONE,
                    deload = deload,
                    config = config,
                )
                Prescription(slot.exerciseId, newSets.getValue(d to s), nextRir, load.weightKg, load.targetReps, load.note)
            }
        }
    }

    /**
     * The sets that the user did for a slot: the logged sets, or the
     * prescribed sets when no set was logged. The user can add or remove
     * sets in a workout, so this can differ from the prescription.
     */
    private fun setsDone(record: SessionRecord, slotIndex: Int): Int {
        val rx = record.prescriptions[slotIndex]
        val logged = record.resultFor(rx.exerciseId)?.sets?.size ?: 0
        return if (logged > 0) logged.coerceAtMost(config.maxSetsPerExercise) else rx.sets
    }

    companion object {
        /** The default most sets for one exercise. See [ProgressionConfig.maxSetsPerExercise]. */
        val MAX_SETS_PER_SLOT = ProgressionConfig.Default.maxSetsPerExercise

        /**
         * Applies the deltas, but keeps the weekly total at or below [maxWeekly].
         * When the cap cuts additions, slots with fewer sets get them first.
         * A slot stays in [minPerSlot]..[maxPerSlot].
         */
        fun capWeekly(
            old: List<Int>,
            deltas: List<Int>,
            maxWeekly: Int,
            minPerSlot: Int = ProgressionConfig.Default.minSetsPerExercise,
            maxPerSlot: Int = ProgressionConfig.Default.maxSetsPerExercise,
        ): List<Int> {
            val result = old.mapIndexed { i, n ->
                val d = deltas[i]
                if (d < 0) (n + d).coerceAtLeast(minPerSlot) else n
            }.toMutableList()
            var budget = maxWeekly - result.sum()
            val adds = deltas.map { it.coerceAtLeast(0) }.toMutableList()
            while (budget > 0 && adds.any { it > 0 }) {
                val i = adds.indices.filter { adds[it] > 0 && result[it] < maxPerSlot }
                    .minByOrNull { result[it] } ?: break
                result[i]++
                adds[i]--
                budget--
            }
            return result
        }
    }
}
