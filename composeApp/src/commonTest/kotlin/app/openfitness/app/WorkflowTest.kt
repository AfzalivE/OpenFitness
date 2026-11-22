package app.openfitness.app

import app.openfitness.app.data.AppData
import app.openfitness.app.data.AppStore
import app.openfitness.app.data.Block
import app.openfitness.app.data.JsonFileStore
import app.openfitness.app.data.Workflow
import app.openfitness.core.library.ExerciseLibrary
import app.openfitness.core.library.Templates
import app.openfitness.core.model.JointPain
import app.openfitness.core.model.Muscle
import app.openfitness.core.model.MuscleFeedback
import app.openfitness.core.model.Pump
import app.openfitness.core.model.Soreness
import app.openfitness.core.model.Workload
import app.openfitness.core.progression.RirSchedule
import okio.Path.Companion.toPath
import okio.fakefilesystem.FakeFileSystem
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class WorkflowTest {
    private val plan = Templates.upperLower4Day

    /** Starts the next workout, does every set with [weight] kg and the target reps (or 8), and finishes. */
    private fun doNextWorkout(block: Block, weight: Double = 50.0, setUp: (Block) -> Block = { it }): Block {
        var b = Workflow.startWorkout(block)
        val w = assertNotNull(b.active)
        w.exercises.forEachIndexed { i, e ->
            e.sets.indices.forEach { s ->
                b = Workflow.updateSet(b, i, s) { it.copy(weightKg = it.weightKg ?: weight, reps = it.reps ?: 8, done = true) }
            }
        }
        return Workflow.finishWorkout(setUp(b))
    }

    private fun doWeek(block: Block): Block {
        var b = block
        repeat(b.plan.days.size) { b = doNextWorkout(b) }
        return b
    }

    @Test fun startBlockMakesTheFirstWeek() {
        val block = Workflow.startBlock(plan, hardWeeks = 5)
        assertEquals(5, block.plan.accumulationWeeks)
        assertEquals(6, block.plan.totalWeeks)
        assertEquals(1, block.weeks.size)
        assertEquals(plan.days.size, block.weeks[0].size)
        assertEquals(0, block.nextDayIndex)
        assertNull(block.active)
        assertTrue(block.weeks[0].flatten().all { it.targetRir == RirSchedule.START_RIR })
        assertEquals(4, block.weeklySets(0)[Muscle.CHEST]) // 2 chest exercises × 2 sets.
    }

    @Test fun startBlockRejectsBadWeekCounts() {
        assertFailsWith<IllegalArgumentException> { Workflow.startBlock(plan, 2) }
        assertFailsWith<IllegalArgumentException> { Workflow.startBlock(plan, 9) }
    }

    @Test fun startWorkoutUsesThePrescription() {
        val block = Workflow.startWorkout(Workflow.startBlock(plan, 4))
        val w = assertNotNull(block.active)
        assertEquals(0, w.week)
        assertEquals(0, w.dayIndex)
        assertEquals(block.weeks[0][0].map { it.sets }, w.exercises.map { it.sets.size })
        // A second call keeps the same workout.
        assertEquals(block, Workflow.startWorkout(block))
    }

    @Test fun finishWorkoutSavesOnlyDoneSets() {
        var b = Workflow.startWorkout(Workflow.startBlock(plan, 4))
        b = Workflow.updateSet(b, 0, 0) { it.copy(weightKg = 80.0, reps = 8, done = true) }
        b = Workflow.updateSet(b, 0, 1) { it.copy(weightKg = 80.0, reps = 7) } // Not done.
        b = Workflow.setJointPain(b, 0, JointPain.MILD)
        b = Workflow.finishWorkout(b)

        assertNull(b.active)
        val record = b.sessions.single()
        assertEquals(0, record.week)
        assertEquals(0, record.dayIndex)
        assertEquals(1, record.results[0].sets.size)
        assertEquals(80.0, record.results[0].sets[0].weightKg)
        assertEquals(JointPain.MILD, record.results[0].jointPain)
        assertTrue(b.isDayDone(0, 0))
        assertEquals(1, b.nextDayIndex)
        assertEquals(1, b.weeks.size) // The week is not done yet.
    }

    @Test fun feedbackIsSavedByMuscle() {
        var b = Workflow.startWorkout(Workflow.startBlock(plan, 4))
        b = Workflow.updateFeedback(b, Muscle.CHEST) { it.copy(pump = Pump.HIGH) }
        b = Workflow.updateFeedback(b, Muscle.CHEST) { it.copy(workload = Workload.HARD) }
        b = Workflow.finishWorkout(b)
        val fb = b.sessions.single().feedback.getValue(Muscle.CHEST)
        assertEquals(Pump.HIGH, fb.pump)
        assertEquals(Workload.HARD, fb.workload)
        assertNull(fb.soreness)
    }

    @Test fun lastDayOfTheWeekMakesTheNextWeek() {
        val b = doWeek(Workflow.startBlock(plan, 4))
        assertEquals(2, b.weeks.size)
        assertEquals(1, b.currentWeek)
        assertEquals(0, b.nextDayIndex)
        assertTrue(b.weeks[1].flatten().all { it.targetRir == RirSchedule.targetRir(1, 4) })
        // Logged weights become the suggested weights.
        assertTrue(b.weeks[1].flatten().filter { ExerciseLibrary.byId.getValue(it.exerciseId).loadType.name != "REPS_ONLY" }.all { it.weightKg != null })
    }

    @Test fun blockIsCompleteAfterTheDeloadWeek() {
        var b = Workflow.startBlock(plan, 3)
        repeat(3) { b = doWeek(b) }
        assertEquals(3, b.currentWeek)
        assertTrue(b.plan.isDeload(b.currentWeek))
        assertFalse(b.isComplete)
        assertTrue(b.weeks[3].flatten().all { it.targetRir == RirSchedule.DELOAD_RIR })
        b = doWeek(b)
        assertTrue(b.isComplete)
        assertEquals(4, b.weeks.size) // No week after the deload.
        assertNull(b.nextDayIndex)
        assertEquals(b, Workflow.startWorkout(b))
    }

    @Test fun sorenessIsAskedAtTheFirstExerciseOfAMuscleAfterWeekOne() {
        val week0 = Workflow.startWorkout(Workflow.startBlock(plan, 4))
        assertFalse(Workflow.asksSoreness(week0, 0))

        val week1 = Workflow.startWorkout(doWeek(Workflow.startBlock(plan, 4)))
        assertEquals(1, week1.active!!.week)
        week1.active.exercises.indices.forEach { assertTrue(Workflow.asksSoreness(week1, it)) } // One slot per muscle on Upper A.
    }

    @Test fun pumpAndWorkloadAreAskedAfterTheLastSetForTheMuscle() {
        // A plan day with two chest exercises.
        val twoChest = plan.copy(days = listOf(plan.days[0].copy(slots = plan.days[0].slots.take(1) + plan.days[2].slots.take(1))) + plan.days.drop(1))
        var b = Workflow.startWorkout(Workflow.startBlock(twoChest, 4))
        assertFalse(Workflow.asksPumpAndWorkload(b, 0))
        assertFalse(Workflow.asksJointPain(b, 1))
        // The last chest exercise is done, but the first is not: no pump question yet.
        b.active!!.exercises[1].sets.indices.forEach { s -> b = Workflow.updateSet(b, 1, s) { it.copy(reps = 8, done = true) } }
        assertTrue(Workflow.asksJointPain(b, 1))
        assertFalse(Workflow.asksPumpAndWorkload(b, 1))
        b.active.exercises.forEachIndexed { i, e -> e.sets.indices.forEach { s -> b = Workflow.updateSet(b, i, s) { it.copy(reps = 8, done = true) } } }
        assertTrue(Workflow.asksJointPain(b, 0))
        assertFalse(Workflow.asksPumpAndWorkload(b, 0)) // Not the last chest exercise.
        assertTrue(Workflow.asksPumpAndWorkload(b, 1))
    }

    @Test fun exerciseIsCompleteWhenSetsAndAnswersAreDone() {
        var b = Workflow.startWorkout(doWeek(Workflow.startBlock(plan, 4)))
        assertFalse(Workflow.isExerciseComplete(b, 0))
        b.active!!.exercises[0].sets.indices.forEach { s -> b = Workflow.updateSet(b, 0, s) { it.copy(reps = 8, done = true) } }
        assertFalse(Workflow.isExerciseComplete(b, 0)) // Questions have no answer yet.
        b = Workflow.setJointPain(b, 0, JointPain.NONE)
        b = Workflow.updateFeedback(b, Muscle.CHEST) { MuscleFeedback(Soreness.RECOVERED_ON_TIME, Pump.MODERATE, Workload.GOOD) }
        assertTrue(Workflow.isExerciseComplete(b, 0))
        assertFalse(Workflow.isExerciseComplete(b, 1))
    }

    @Test fun swapChangesThePlanAndTheCurrentWeek() {
        var b = Workflow.startBlock(plan, 4)
        b = Workflow.swapExercise(b, dayIndex = 0, slotIndex = 0, exerciseId = "machine-chest")
        assertEquals("machine-chest", b.plan.days[0].slots[0].exerciseId)
        val rx = b.weeks[0][0][0]
        assertEquals("machine-chest", rx.exerciseId)
        assertNull(rx.weightKg)
        assertEquals(Workflow.SWAP_NOTE, rx.note)

        // The swap stays in the next week.
        b = doWeek(b)
        assertEquals("machine-chest", b.weeks[1][0][0].exerciseId)
    }

    @Test fun swapInTheActiveWorkoutResetsTheSets() {
        var b = Workflow.startWorkout(Workflow.startBlock(plan, 4))
        b = Workflow.updateSet(b, 0, 0) { it.copy(weightKg = 60.0) }
        b = Workflow.swapExercise(b, 0, 0, "dip")
        val e = b.active!!.exercises[0]
        assertEquals("dip", e.exerciseId)
        assertTrue(e.sets.all { it.weightKg == null && !it.done })
    }

    @Test fun swapRejectsOtherMusclesAndDoneSets() {
        val b = Workflow.startBlock(plan, 4)
        assertFailsWith<IllegalArgumentException> { Workflow.swapExercise(b, 0, 0, "bb-squat") }
        val started = Workflow.updateSet(Workflow.startWorkout(b), 0, 0) { it.copy(reps = 8, done = true) }
        assertFailsWith<IllegalArgumentException> { Workflow.swapExercise(started, 0, 0, "dip") }
        assertTrue(Workflow.swapOptions(b, 0, 0).all { it.primary == Muscle.CHEST })
    }

    @Test fun storeSavesAndLoadsTheBlock() {
        val fs = FakeFileSystem()
        val path = "/data/openfitness.json".toPath()
        val store = AppStore(JsonFileStore(fs, path))
        assertEquals(AppData(), store.state.value)

        store.update { it.copy(block = Workflow.startBlock(plan, 4)) }
        store.updateBlock(Workflow::startWorkout)
        store.updateBlock { Workflow.updateFeedback(it, Muscle.BACK) { f -> f.copy(soreness = Soreness.STILL_SORE) } }
        store.updateBlock { Workflow.updateSet(it, 0, 0) { s -> s.copy(weightKg = 82.5, reps = 9, done = true) } }

        val reloaded = JsonFileStore(fs, path).load()
        assertEquals(store.state.value, reloaded)
        assertEquals(82.5, reloaded.block!!.active!!.exercises[0].sets[0].weightKg)
    }

    @Test fun storeIgnoresABrokenFile() {
        val fs = FakeFileSystem()
        val path = "/openfitness.json".toPath()
        fs.write(path) { writeUtf8("{not json") }
        assertEquals(AppData(), JsonFileStore(fs, path).load())
    }
}
