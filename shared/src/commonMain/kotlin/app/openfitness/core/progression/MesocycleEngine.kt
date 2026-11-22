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
 * Week 0 comes from the start volume of each muscle. Each later week
 * comes from the previous week's sessions and feedback.
 */
class MesocycleEngine(
    private val plan: MesocyclePlan,
    private val exercises: Map<String, Exercise>,
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
        val sets = mutableMapOf<Pair<Int, Int>, Int>()
        for ((muscle, refs) in slotsByMuscle) {
            // Slots with their own start sets keep them. The other slots share the rest.
            val (fixed, free) = refs.partition { it.slot.startSets != null }
            fixed.forEach { sets[it.dayIndex to it.slotIndex] = it.slot.startSets!!.coerceIn(1, MAX_SETS_PER_SLOT) }
            if (free.isNotEmpty()) {
                val rest = range(muscle).startSets - fixed.sumOf { sets.getValue(it.dayIndex to it.slotIndex) }
                val weekly = rest.coerceAtLeast(MIN_SETS_PER_SLOT * free.size)
                // One slot never gets more than MAX_SETS_PER_SLOT, also when a muscle has few slots.
                distribute(weekly, free.size).forEachIndexed { i, n ->
                    sets[free[i].dayIndex to free[i].slotIndex] = n.coerceAtMost(MAX_SETS_PER_SLOT)
                }
            }
        }
        val rir = RirSchedule.targetRir(0, plan.accumulationWeeks)
        return plan.days.mapIndexed { d, day ->
            day.slots.mapIndexed { s, slot ->
                Prescription(slot.exerciseId, sets.getValue(d to s), rir, weightKg = null, targetReps = null)
            }
        }
    }

    /**
     * Prescriptions for [week], from the sessions of week - 1.
     * [previous] has one record for each day of the previous week.
     */
    fun nextWeek(week: Int, previous: List<SessionRecord>): List<List<Prescription>> {
        require(week in 1 until plan.totalWeeks) { "week out of range" }
        val byDay = previous.associateBy { it.dayIndex }
        require(byDay.keys.containsAll(plan.days.indices.toList())) { "Need a record for every day" }

        val lastRir = RirSchedule.targetRir(week - 1, plan.accumulationWeeks)
        val nextRir = RirSchedule.targetRir(week, plan.accumulationWeeks)
        val deload = plan.isDeload(week)

        val newSets = mutableMapOf<Pair<Int, Int>, Int>()
        for ((muscle, refs) in slotsByMuscle) {
            // Start from the sets that the user did. If no set was logged, use the prescription.
            val oldSets = refs.map { ref -> setsDone(byDay.getValue(ref.dayIndex), ref.slotIndex) }
            val planned = if (deload) {
                oldSets.map { (it / 2).coerceAtLeast(1) }
            } else {
                val deltas = refs.map { ref -> SetProgression.setDelta(byDay.getValue(ref.dayIndex).feedback[muscle]) }
                capWeekly(oldSets, deltas, range(muscle).maxSets)
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
        return if (logged > 0) logged.coerceAtMost(MAX_SETS_PER_SLOT) else rx.sets
    }

    companion object {
        const val MIN_SETS_PER_SLOT = 2
        const val MAX_SETS_PER_SLOT = 8

        /** Splits [total] sets over [slots] as evenly as possible. Earlier slots get the extra sets. */
        fun distribute(total: Int, slots: Int): List<Int> =
            List(slots) { i -> total / slots + if (i < total % slots) 1 else 0 }

        /**
         * Applies the deltas, but keeps the weekly total at or below [maxWeekly].
         * When the cap cuts additions, slots with fewer sets get them first.
         */
        fun capWeekly(old: List<Int>, deltas: List<Int>, maxWeekly: Int): List<Int> {
            val result = old.mapIndexed { i, n ->
                val d = deltas[i]
                if (d < 0) (n + d).coerceAtLeast(1) else n
            }.toMutableList()
            var budget = maxWeekly - result.sum()
            val adds = deltas.map { it.coerceAtLeast(0) }.toMutableList()
            while (budget > 0 && adds.any { it > 0 }) {
                val i = adds.indices.filter { adds[it] > 0 && result[it] < MAX_SETS_PER_SLOT }
                    .minByOrNull { result[it] } ?: break
                result[i]++
                adds[i]--
                budget--
            }
            return result
        }
    }
}
