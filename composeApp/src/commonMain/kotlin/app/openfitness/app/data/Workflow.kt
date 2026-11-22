package app.openfitness.app.data

import app.openfitness.core.library.ExerciseLibrary
import app.openfitness.core.model.Exercise
import app.openfitness.core.model.ExerciseResult
import app.openfitness.core.model.JointPain
import app.openfitness.core.model.LoadType
import app.openfitness.core.model.LoggedSet
import app.openfitness.core.model.MesocyclePlan
import app.openfitness.core.model.Muscle
import app.openfitness.core.model.MuscleFeedback
import app.openfitness.core.model.SessionRecord
import app.openfitness.core.model.groups
import app.openfitness.core.progression.MesocycleEngine
import app.openfitness.core.units.Units
import app.openfitness.core.units.WeightUnit

/**
 * The training workflow. All functions are pure: they take a [Block] and
 * return a new [Block]. The UI calls them and the store saves the result.
 */
object Workflow {
    const val MIN_HARD_WEEKS = 3
    const val MAX_SETS_IN_WORKOUT = 10
    const val MAX_HARD_WEEKS = 8

    /** Text that the UI shows for a slot that the user swapped. */
    const val SWAP_NOTE = "New exercise. Choose a weight that gives the target RIR."

    private fun engine(block: Block, exercises: Map<String, Exercise>) = MesocycleEngine(block.plan, exercises)

    /** Starts a new block from [template] with [hardWeeks] hard weeks and one deload week. */
    fun startBlock(
        template: MesocyclePlan,
        hardWeeks: Int,
        exercises: Map<String, Exercise> = ExerciseLibrary.byId,
    ): Block {
        require(hardWeeks in MIN_HARD_WEEKS..MAX_HARD_WEEKS) { "hardWeeks must be in $MIN_HARD_WEEKS..$MAX_HARD_WEEKS" }
        require(template.days.all { it.slots.isNotEmpty() }) { "Each day must have at least one exercise" }
        val plan = template.copy(accumulationWeeks = hardWeeks)
        return Block(plan = plan, weeks = listOf(MesocycleEngine(plan, exercises).firstWeek()))
    }

    /**
     * Starts the next workout of the current week. If a workout is in progress,
     * this does nothing. The suggested weights are rounded to the load steps of [unit].
     */
    fun startWorkout(
        block: Block,
        unit: WeightUnit = WeightUnit.KG,
        exercises: Map<String, Exercise> = ExerciseLibrary.byId,
    ): Block {
        if (block.active != null) return block
        val day = block.nextDayIndex ?: return block
        val week = block.currentWeek
        val exercises = block.weeks[week][day].mapIndexed { slot, rx ->
            ExerciseEntry(
                slotIndex = slot,
                exerciseId = rx.exerciseId,
                sets = List(rx.sets) {
                    // The field shows the suggestion as the user sees it, in their unit.
                    val shown = rx.weightKg?.let { w -> Units.display(w, exercises.getValue(rx.exerciseId), unit) }
                    SetEntry(reps = rx.targetReps).withWeight(shown, unit)
                },
            )
        }
        return block.copy(active = ActiveWorkout(week, day, exercises))
    }

    private fun Block.updateActive(change: (ActiveWorkout) -> ActiveWorkout): Block =
        active?.let { copy(active = change(it)) } ?: this

    private fun ActiveWorkout.updateExercise(index: Int, change: (ExerciseEntry) -> ExerciseEntry) =
        copy(exercises = exercises.mapIndexed { i, e -> if (i == index) change(e) else e })

    fun updateSet(block: Block, exerciseIndex: Int, setIndex: Int, change: (SetEntry) -> SetEntry): Block =
        block.updateActive { w ->
            w.updateExercise(exerciseIndex) { e ->
                e.copy(sets = e.sets.mapIndexed { i, s -> if (i == setIndex) change(s) else s })
            }
        }

    /** The heaviest weight that the app accepts, in kg (about 2200 lb). */
    const val MAX_WEIGHT_KG = 1000.0
    const val MAX_REPS = 100

    /**
     * True when the user can mark this set done: it has at least 1 rep, and a
     * weight if the exercise uses one. Bodyweight-plus exercises can have no
     * added weight.
     */
    fun canComplete(set: SetEntry, exercise: Exercise): Boolean {
        val reps = set.reps ?: return false
        if (reps !in 1..MAX_REPS) return false
        val w = set.weightKg
        return when (exercise.loadType) {
            LoadType.FIXED_STEP -> w != null && w > 0 && w <= MAX_WEIGHT_KG
            LoadType.BODYWEIGHT_PLUS -> w == null || w in 0.0..MAX_WEIGHT_KG
            LoadType.REPS_ONLY -> true
        }
    }

    /**
     * Marks a set done. The next sets of the exercise that have no weight or
     * no reps get the values of this set, so that the user does not type them
     * again.
     */
    fun completeSet(block: Block, exerciseIndex: Int, setIndex: Int): Block = block.updateActive { w ->
        w.updateExercise(exerciseIndex) { e ->
            val done = e.sets[setIndex].copy(done = true)
            e.copy(sets = e.sets.mapIndexed { i, s ->
                when {
                    i == setIndex -> done
                    i > setIndex && !s.done -> s.copy(
                        weightKg = s.weightKg ?: done.weightKg,
                        enteredValue = if (s.weightKg == null) done.enteredValue else s.enteredValue,
                        enteredUnit = if (s.weightKg == null) done.enteredUnit else s.enteredUnit,
                        reps = s.reps ?: done.reps,
                    )
                    else -> s
                }
            })
        }
    }

    /** Adds a set to an exercise in this workout. It copies the weight and reps of the last set. */
    fun addSet(block: Block, exerciseIndex: Int): Block = block.updateActive { w ->
        w.updateExercise(exerciseIndex) { e ->
            if (e.sets.size >= MAX_SETS_IN_WORKOUT) e
            else e.copy(sets = e.sets + (e.sets.lastOrNull()?.copy(done = false) ?: SetEntry()))
        }
    }

    /** Removes the last set that is not done. An exercise keeps at least one set. */
    fun removeSet(block: Block, exerciseIndex: Int): Block = block.updateActive { w ->
        w.updateExercise(exerciseIndex) { e ->
            val i = e.sets.indexOfLast { !it.done }
            if (e.sets.size <= 1 || i < 0) e else e.copy(sets = e.sets.filterIndexed { j, _ -> j != i })
        }
    }

    /** The exercise indices of the active workout, in groups (single exercises and supersets). */
    fun workoutGroups(block: Block): List<List<Int>> {
        val w = block.active ?: return emptyList()
        val day = block.plan.days[w.dayIndex]
        return day.groups().map { g -> g.map { slot -> w.exercises.indexOfFirst { it.slotIndex == slot } } }
    }

    /**
     * True when the rest timer starts after set [setIndex] of an exercise is
     * done. For a single exercise, that is after each set. For a superset, it
     * is after the last exercise of the round: all exercises of the superset
     * that have this set are done with it.
     */
    fun startsRest(block: Block, exerciseIndex: Int, setIndex: Int): Boolean {
        val w = block.active ?: return false
        val group = workoutGroups(block).firstOrNull { exerciseIndex in it } ?: return true
        return group.all { i -> w.exercises[i].sets.getOrNull(setIndex)?.done ?: true }
    }

    /** Puts an exercise and the next one in a superset. This changes the plan for the next weeks too. */
    fun linkWithNext(block: Block, dayIndex: Int, slotIndex: Int): Block =
        block.copy(plan = Planner.linkWithNext(block.plan, dayIndex, slotIndex))

    /** Splits a superset into single exercises. This changes the plan for the next weeks too. */
    fun splitSuperset(block: Block, dayIndex: Int, slotIndex: Int): Block =
        block.copy(plan = Planner.splitSuperset(block.plan, dayIndex, slotIndex))

    fun setJointPain(block: Block, exerciseIndex: Int, pain: JointPain): Block =
        block.updateActive { w -> w.updateExercise(exerciseIndex) { it.copy(jointPain = pain) } }

    fun updateFeedback(block: Block, muscle: Muscle, change: (MuscleFeedback) -> MuscleFeedback): Block =
        block.updateActive { w ->
            w.copy(feedback = w.feedback + (muscle to change(w.feedback[muscle] ?: MuscleFeedback())))
        }

    /** The muscle of one exercise in the active workout. */
    fun muscleOf(block: Block, entry: ExerciseEntry): Muscle {
        val day = block.active?.dayIndex ?: error("No active workout")
        return block.plan.days[day].slots[entry.slotIndex].muscle
    }

    /**
     * True when the app asks for soreness at this exercise: it is the first
     * exercise for its muscle in the session, and it is not the first week.
     */
    fun asksSoreness(block: Block, exerciseIndex: Int): Boolean {
        val w = block.active ?: return false
        if (w.week == 0) return false
        val muscle = muscleOf(block, w.exercises[exerciseIndex])
        return w.exercises.indexOfFirst { muscleOf(block, it) == muscle } == exerciseIndex
    }

    /**
     * True when the app asks for pump and workload at this exercise: it is the
     * last exercise for its muscle in the session, and all the sets for that
     * muscle in the session are done.
     */
    fun asksPumpAndWorkload(block: Block, exerciseIndex: Int): Boolean {
        val w = block.active ?: return false
        val muscle = muscleOf(block, w.exercises[exerciseIndex])
        val forMuscle = w.exercises.withIndex().filter { muscleOf(block, it.value) == muscle }
        return forMuscle.last().index == exerciseIndex && forMuscle.all { it.value.allSetsDone }
    }

    /** True when the app asks for joint pain at this exercise: all its sets are done. */
    fun asksJointPain(block: Block, exerciseIndex: Int): Boolean =
        block.active?.exercises?.getOrNull(exerciseIndex)?.allSetsDone == true

    /** The kinds of feedback questions. */
    enum class QuestionKind { SORENESS, JOINT_PAIN, PUMP, WORKLOAD }

    /** One feedback question for an exercise of the active workout. */
    data class Question(val kind: QuestionKind, val exerciseIndex: Int, val muscle: Muscle)

    /**
     * The questions that the app asks now, in workout order. Soreness comes
     * at the first exercise of a muscle (not in week 1), once that exercise is
     * the current one or before it. Joint pain comes after the last set of the
     * exercise. Pump and workload come after all the sets for the muscle.
     */
    fun questions(block: Block): List<Question> {
        val w = block.active ?: return emptyList()
        val current = w.exercises.indexOfFirst { !it.allSetsDone }.let { if (it < 0) w.exercises.size else it }
        return buildList {
            w.exercises.forEachIndexed { i, e ->
                val m = muscleOf(block, e)
                if (asksSoreness(block, i) && i <= current) add(Question(QuestionKind.SORENESS, i, m))
                if (asksJointPain(block, i)) add(Question(QuestionKind.JOINT_PAIN, i, m))
                if (asksPumpAndWorkload(block, i)) {
                    add(Question(QuestionKind.PUMP, i, m))
                    add(Question(QuestionKind.WORKLOAD, i, m))
                }
            }
        }
    }

    fun isAnswered(block: Block, q: Question): Boolean {
        val w = block.active ?: return false
        val fb = w.feedback[q.muscle]
        return when (q.kind) {
            QuestionKind.SORENESS -> fb?.soreness != null
            QuestionKind.JOINT_PAIN -> w.exercises[q.exerciseIndex].jointPain != null
            QuestionKind.PUMP -> fb?.pump != null
            QuestionKind.WORKLOAD -> fb?.workload != null
        }
    }

    /** The first question that has no answer, or null. */
    fun nextQuestion(block: Block, skipped: Set<Question> = emptySet()): Question? =
        questions(block).firstOrNull { it !in skipped && !isAnswered(block, it) }

    /**
     * True when all the sets of the exercise are done and all the questions
     * that the app asks at this exercise have an answer.
     */
    fun isExerciseComplete(block: Block, exerciseIndex: Int): Boolean {
        val w = block.active ?: return false
        val entry = w.exercises[exerciseIndex]
        if (!entry.allSetsDone) return false
        val fb = w.feedback[muscleOf(block, entry)]
        if (asksSoreness(block, exerciseIndex) && fb?.soreness == null) return false
        if (asksJointPain(block, exerciseIndex) && entry.jointPain == null) return false
        if (asksPumpAndWorkload(block, exerciseIndex) && (fb?.pump == null || fb.workload == null)) return false
        return true
    }

    /**
     * Saves the active workout as a session. When this was the last day of the
     * week, the engine makes the next week (if the block has one).
     */
    fun finishWorkout(block: Block, exercises: Map<String, Exercise> = ExerciseLibrary.byId): Block {
        val w = block.active ?: return block
        val record = SessionRecord(
            week = w.week,
            dayIndex = w.dayIndex,
            prescriptions = block.weeks[w.week][w.dayIndex],
            results = w.exercises.map { e ->
                ExerciseResult(
                    exerciseId = e.exerciseId,
                    sets = e.sets.filter { it.done && it.reps != null }.map {
                        LoggedSet(it.weightKg ?: 0.0, it.reps!!, enteredValue = it.enteredValue, enteredUnit = it.enteredUnit)
                    },
                    jointPain = e.jointPain ?: JointPain.NONE,
                )
            },
            feedback = w.feedback,
        )
        val done = block.copy(active = null, sessions = block.sessions.filterNot { it.week == w.week && it.dayIndex == w.dayIndex } + record)
        return rollOverIfWeekDone(done, exercises)
    }

    /** Makes the next week when all the days of the current week are done. */
    fun rollOverIfWeekDone(block: Block, exercises: Map<String, Exercise> = ExerciseLibrary.byId): Block {
        if (block.nextDayIndex != null) return block
        val week = block.currentWeek
        if (week >= block.plan.accumulationWeeks) return block // Deload done: the block is complete.
        val previous = block.sessions.filter { it.week == week }
        val next = engine(block, exercises).nextWeek(week + 1, previous)
        return block.copy(weeks = block.weeks + listOf(next))
    }

    /** Stops the active workout and does not save it. */
    fun discardWorkout(block: Block): Block = block.copy(active = null)

    /** Exercises that can go in a slot: the same primary muscle as the slot. */
    fun swapOptions(block: Block, dayIndex: Int, slotIndex: Int, library: List<Exercise> = ExerciseLibrary.all): List<Exercise> {
        val muscle = block.plan.days[dayIndex].slots[slotIndex].muscle
        return library.filter { it.primary == muscle }
    }

    /**
     * Puts [exerciseId] in a slot of the plan. This changes the slot for all
     * the weeks that come after. If the day is not done in the current week,
     * its prescription changes too. The weight and reps are cleared, because
     * the old values are for a different exercise.
     */
    fun swapExercise(
        block: Block,
        dayIndex: Int,
        slotIndex: Int,
        exerciseId: String,
        exercises: Map<String, Exercise> = ExerciseLibrary.byId,
    ): Block {
        val slot = block.plan.days[dayIndex].slots[slotIndex]
        val replacement = requireNotNull(exercises[exerciseId]) { "Unknown exercise $exerciseId" }
        require(replacement.primary == slot.muscle) { "The new exercise must train ${slot.muscle}" }
        if (slot.exerciseId == exerciseId) return block
        val active = block.active
        if (active != null && active.dayIndex == dayIndex) {
            require(!active.exercises.first { it.slotIndex == slotIndex }.anySetDone) {
                "Cannot swap an exercise after a set is done"
            }
        }
        // A day must not have the same exercise two times.
        require(block.plan.days[dayIndex].slots.none { it.exerciseId == exerciseId }) { "The day already has this exercise" }

        val days = block.plan.days.mapIndexed { d, day ->
            if (d != dayIndex) day
            else day.copy(slots = day.slots.mapIndexed { s, it -> if (s == slotIndex) it.copy(exerciseId = exerciseId) else it })
        }
        val week = block.currentWeek
        val weeks = if (block.isDayDone(week, dayIndex)) block.weeks else {
            block.weeks.mapIndexed { wi, wk ->
                if (wi != week) wk
                else wk.mapIndexed { d, day ->
                    if (d != dayIndex) day
                    else day.mapIndexed { s, rx ->
                        if (s == slotIndex) rx.copy(exerciseId = exerciseId, weightKg = null, targetReps = null, note = SWAP_NOTE) else rx
                    }
                }
            }
        }
        val newActive = active?.takeIf { it.dayIndex == dayIndex }?.let { w ->
            w.copy(exercises = w.exercises.map { e ->
                if (e.slotIndex == slotIndex) e.copy(exerciseId = exerciseId, sets = e.sets.map { SetEntry() }, jointPain = null) else e
            })
        } ?: active
        return block.copy(plan = block.plan.copy(days = days), weeks = weeks, active = newActive)
    }
}
