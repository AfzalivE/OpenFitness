package app.openfitness.app

import app.openfitness.app.data.AppData
import app.openfitness.app.data.AppStore
import app.openfitness.app.data.JsonFileStore
import app.openfitness.app.data.Planner
import app.openfitness.app.data.SetEntry
import app.openfitness.app.data.Settings
import app.openfitness.app.data.Workflow
import app.openfitness.core.library.ExerciseLibrary
import app.openfitness.core.library.Templates
import app.openfitness.core.model.Muscle
import app.openfitness.core.model.groups
import app.openfitness.core.units.Units
import app.openfitness.core.units.WeightUnit
import okio.Path.Companion.toPath
import okio.fakefilesystem.FakeFileSystem
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PlannerTest {
    private fun twoDayPlan() = Planner.newPlan("Test", days = 2)
        .let { Planner.addExercise(it, 0, "bb-bench") }
        .let { Planner.addExercise(it, 0, "cable-row") }
        .let { Planner.addExercise(it, 0, "db-lateral") }
        .let { Planner.addExercise(it, 1, "bb-squat") }

    @Test fun newPlanHasEmptyNamedDays() {
        val p = Planner.newPlan(days = 3)
        assertEquals(listOf("Day 1", "Day 2", "Day 3"), p.days.map { it.name })
        assertTrue(p.days.all { it.slots.isEmpty() })
        assertFalse(Planner.isValid(p))
    }

    @Test fun dayCountStaysInRange() {
        var p = Planner.newPlan(days = 3)
        p = Planner.setDayCount(p, 6)
        assertEquals(6, p.days.size)
        assertEquals("Day 6", p.days.last().name)
        assertEquals(6, Planner.setDayCount(p, 9).days.size)
        assertEquals(2, Planner.setDayCount(p, 1).days.size)
    }

    @Test fun hardWeeksStayInRange() {
        val p = Planner.newPlan()
        assertEquals(8, Planner.setHardWeeks(p, 12).accumulationWeeks)
        assertEquals(3, Planner.setHardWeeks(p, 1).accumulationWeeks)
    }

    @Test fun addMoveAndRemoveExercises() {
        var p = twoDayPlan()
        assertEquals(listOf("bb-bench", "cable-row", "db-lateral"), p.days[0].slots.map { it.exerciseId })
        assertEquals(Muscle.BACK, p.days[0].slots[1].muscle)
        p = Planner.moveExercise(p, 0, 2, -1)
        assertEquals(listOf("bb-bench", "db-lateral", "cable-row"), p.days[0].slots.map { it.exerciseId })
        p = Planner.moveExercise(p, 0, 0, -1) // Already first: no change.
        assertEquals("bb-bench", p.days[0].slots[0].exerciseId)
        p = Planner.removeExercise(p, 0, 0)
        assertEquals(listOf("db-lateral", "cable-row"), p.days[0].slots.map { it.exerciseId })
        assertFailsWith<IllegalArgumentException> { Planner.addExercise(p, 0, "cable-row") }
    }

    @Test fun addOptionsFilterByMuscleAndSkipUsedExercises() {
        val p = twoDayPlan()
        val chest = Planner.addOptions(p, 0, Muscle.CHEST)
        assertTrue(chest.all { it.primary == Muscle.CHEST })
        assertFalse(chest.any { it.id == "bb-bench" })
        assertTrue(Planner.addOptions(p, 1, Muscle.CHEST).any { it.id == "bb-bench" })
    }

    @Test fun validationNeedsAnExerciseOnEachDay() {
        val p = Planner.newPlan("X", days = 2).let { Planner.addExercise(it, 0, "bb-bench") }
        assertEquals(listOf("Add at least one exercise to Day 2."), Planner.problems(p))
        assertFailsWith<IllegalArgumentException> { Workflow.startBlock(p, 4) }
        assertTrue(Planner.isValid(twoDayPlan()))
        assertFalse(Planner.isValid(Planner.rename(twoDayPlan(), " ")))
        assertFalse(Planner.isValid(Planner.renameDay(twoDayPlan(), 1, "")))
    }

    @Test fun startFromATemplateAndEdit() {
        var p = Planner.fromTemplate(Templates.fullBody3Day)
        assertEquals("Full body, 3 days (copy)", p.name)
        p = Planner.renameDay(p, 0, "Monday")
        p = Planner.addExercise(p, 0, "face-pull")
        assertEquals("Monday", p.days[0].name)
        assertEquals("face-pull", p.days[0].slots.last().exerciseId)
        assertEquals("Day 1", Templates.fullBody3Day.days[0].name) // The template does not change.
    }

    @Test fun startSetsPerMuscleFollowTheVolumeSettings() {
        var p = twoDayPlan()
        assertEquals(8, Planner.startSetsPerMuscle(p)[Muscle.CHEST])
        p = Planner.setMuscleStartSets(p, Muscle.CHEST, 6)
        assertEquals(6, Planner.startSetsPerMuscle(p)[Muscle.CHEST])
        p = Planner.setMuscleMaxSets(p, Muscle.CHEST, 4) // Start goes down with the max.
        assertEquals(4, Planner.volume(p, Muscle.CHEST).startSets)
        assertEquals(4, Planner.volume(p, Muscle.CHEST).maxSets)
        p = Planner.setMuscleStartSets(p, Muscle.CHEST, 10) // Max goes up with the start.
        assertEquals(10, Planner.volume(p, Muscle.CHEST).maxSets)
        assertEquals(listOf(Muscle.CHEST, Muscle.BACK, Muscle.SIDE_DELTS, Muscle.QUADS), Planner.musclesInPlan(p))
    }

    @Test fun startSetsPerExercise() {
        var p = Planner.setSlotStartSets(twoDayPlan(), 0, 0, 3)
        assertEquals(3, Planner.startSetsPerSlot(p)[0][0])
        assertEquals(3, Planner.startSetsPerMuscle(p)[Muscle.CHEST])
        p = Planner.setSlotStartSets(p, 0, 0, null)
        assertNull(p.days[0].slots[0].startSets)
        assertEquals(8, Planner.startSetsPerSlot(p)[0][0])
    }

    @Test fun supersetsInThePlanner() {
        var p = Planner.linkWithNext(twoDayPlan(), 0, 0)
        assertEquals(listOf(listOf(0, 1), listOf(2)), p.days[0].groups())
        // Moving an exercise out of the superset breaks it.
        p = Planner.moveExercise(p, 0, 1, 1)
        assertEquals(listOf(listOf(0), listOf(1), listOf(2)), p.days[0].groups())
        p = Planner.splitSuperset(Planner.linkWithNext(p, 0, 1), 0, 2)
        assertTrue(p.days[0].slots.all { it.supersetGroup == null })
    }

    @Test fun saveReplacesAPlanWithTheSameName() {
        var data = Planner.save(AppData(), twoDayPlan())
        data = Planner.save(data, Planner.setHardWeeks(twoDayPlan(), 6))
        assertEquals(1, data.customPlans.size)
        assertEquals(6, data.customPlans[0].accumulationWeeks)
        data = Planner.save(data, Planner.rename(twoDayPlan(), "Other"))
        assertEquals(listOf("Test", "Other"), data.customPlans.map { it.name })
        assertEquals(listOf("Other"), Planner.delete(data, "Test").customPlans.map { it.name })
        assertFailsWith<IllegalArgumentException> { Planner.save(data, Planner.newPlan()) }
    }

    @Test fun customPlansAndSettingsAreStored() {
        val fs = FakeFileSystem()
        val path = "/openfitness.json".toPath()
        val store = AppStore(JsonFileStore(fs, path))
        assertEquals(WeightUnit.LB, store.state.value.settings.unit) // lb is the default.
        val plan = Planner.linkWithNext(Planner.setSlotStartSets(twoDayPlan(), 0, 1, 4), 0, 0)
        store.update { Planner.save(it, plan).copy(settings = Settings(WeightUnit.KG), draft = plan) }
        val loaded = JsonFileStore(fs, path).load()
        assertEquals(plan, loaded.customPlans.single())
        assertEquals(plan, loaded.draft)
        assertEquals(WeightUnit.KG, loaded.settings.unit)
    }

    @Test fun customPlanStartsABlock() {
        val b = Workflow.startBlock(twoDayPlan(), 3)
        assertEquals(2, b.weeks[0].size)
        assertEquals(3, b.weeks[0][0].size)
    }
}

class WorkoutSetsTest {
    private val plan = Templates.upperLower4Day

    @Test fun addAndRemoveSets() {
        var b = Workflow.startWorkout(Workflow.startBlock(plan, 4))
        val n = b.active!!.exercises[0].sets.size
        b = Workflow.updateSet(b, 0, n - 1) { it.copy(weightKg = 60.0, reps = 9) }
        b = Workflow.addSet(b, 0)
        assertEquals(n + 1, b.active!!.exercises[0].sets.size)
        assertEquals(SetEntry(60.0, 9, false), b.active.exercises[0].sets.last())
        b = Workflow.removeSet(b, 0)
        b = Workflow.removeSet(b, 0)
        assertEquals(n - 1, b.active!!.exercises[0].sets.size)
    }

    @Test fun removeSetKeepsDoneSetsAndOneSet() {
        var b = Workflow.startWorkout(Workflow.startBlock(plan, 4))
        b.active!!.exercises[0].sets.indices.forEach { s -> b = Workflow.updateSet(b, 0, s) { it.copy(reps = 8, done = true) } }
        assertEquals(b, Workflow.removeSet(b, 0)) // All sets are done: nothing to remove.
        var c = Workflow.startWorkout(Workflow.startBlock(plan, 4))
        repeat(10) { c = Workflow.removeSet(c, 1) }
        assertEquals(1, c.active!!.exercises[1].sets.size)
    }

    @Test fun extraSetsChangeNextWeek() {
        var b = Workflow.startBlock(plan, 4)
        val benchWeek0 = b.weeks[0][0][0].sets
        repeat(plan.days.size) { d ->
            b = Workflow.startWorkout(b)
            if (d == 0) b = Workflow.addSet(b, 0)
            b.active!!.exercises.forEachIndexed { i, e ->
                e.sets.indices.forEach { s -> b = Workflow.updateSet(b, i, s) { it.copy(weightKg = 50.0, reps = 8, done = true) } }
            }
            // Feedback that holds the sets the same.
            b.active.exercises.forEach { e ->
                b = Workflow.updateFeedback(b, Workflow.muscleOf(b, e)) {
                    app.openfitness.core.model.MuscleFeedback(app.openfitness.core.model.Soreness.RECOVERED_ON_TIME, app.openfitness.core.model.Pump.MODERATE, app.openfitness.core.model.Workload.GOOD)
                }
            }
            b = Workflow.finishWorkout(b)
        }
        assertEquals(benchWeek0 + 1, b.weeks[1][0][0].sets)
    }

    @Test fun supersetRoundsStartTheRestAfterTheLastExercise() {
        var b = Workflow.startBlock(plan, 4)
        b = Workflow.linkWithNext(b, 0, 0) // Bench + row.
        b = Workflow.startWorkout(b)
        assertEquals(listOf(listOf(0, 1), listOf(2), listOf(3), listOf(4)), Workflow.workoutGroups(b))
        b = Workflow.updateSet(b, 0, 0) { it.copy(reps = 8, done = true) }
        assertFalse(Workflow.startsRest(b, 0, 0)) // A1 done, B1 not yet.
        b = Workflow.updateSet(b, 1, 0) { it.copy(reps = 8, done = true) }
        assertTrue(Workflow.startsRest(b, 1, 0)) // B1 ends the round.
        b = Workflow.updateSet(b, 2, 0) { it.copy(reps = 8, done = true) }
        assertTrue(Workflow.startsRest(b, 2, 0)) // A single exercise rests after each set.
        // The superset stays in the plan for the next weeks.
        assertEquals(listOf(0, 1), b.plan.days[0].groups()[0])
        assertEquals(listOf(listOf(0), listOf(1)), Workflow.splitSuperset(b, 0, 0).plan.days[0].groups().take(2))
    }

    @Test fun supersetRoundWithUnevenSets() {
        var b = Workflow.startWorkout(Workflow.linkWithNext(Workflow.startBlock(plan, 4), 0, 0))
        // Bench has fewer sets than row: in the last round, only row has a set.
        val bench = b.active!!.exercises[0].sets.size
        val row = b.active.exercises[1].sets.size
        assertTrue(row > bench)
        b = Workflow.updateSet(b, 1, row - 1) { it.copy(reps = 8, done = true) }
        assertTrue(Workflow.startsRest(b, 1, row - 1))
    }

    @Test fun lbSuggestionsAreRoundedToLbSteps() {
        var b = Workflow.startBlock(plan, 4)
        val lb = WeightUnit.LB
        val ex = Units.exercisesFor(lb, ExerciseLibrary.all)
        // Week 1 at 82.5 kg (181.9 lb) on bench.
        repeat(plan.days.size) {
            b = Workflow.startWorkout(b, lb, ex)
            b.active!!.exercises.forEachIndexed { i, e ->
                e.sets.indices.forEach { s -> b = Workflow.updateSet(b, i, s) { it.copy(weightKg = 82.5, reps = 7, done = true) } }
            }
            b = Workflow.finishWorkout(b, ex)
        }
        b = Workflow.startWorkout(b, lb, ex)
        val kg = b.active!!.exercises[0].sets[0].weightKg!!
        // 82.5 kg is 181.9 lb, not on a 5 lb step: it shows as 182 lb.
        assertEquals(182.0, b.active.exercises[0].sets[0].enteredValue)
        assertEquals(182.0, Units.toUnit(kg, lb), 1e-9)
    }
}

class EnteredUnitTest {
    private val plan = Templates.upperLower4Day
    private val lb = WeightUnit.LB
    private val kg = WeightUnit.KG

    @Test fun lbEntryShowsInKg() {
        val set = SetEntry().withWeight(185.0, lb)
        assertEquals(185.0, set.weightIn(lb))
        assertEquals(84.0, set.weightIn(kg))
        assertEquals(Units.toKg(185.0, lb), set.weightKg)
    }

    @Test fun kgEntryShowsInLb() {
        val set = SetEntry().withWeight(82.5, kg)
        assertEquals(82.5, set.weightIn(kg))
        assertEquals(182.0, set.weightIn(lb))
    }

    @Test fun unitSwitchesDoNotChangeStoredValues() {
        val fs = FakeFileSystem()
        val path = "/openfitness.json".toPath()
        val store = AppStore(JsonFileStore(fs, path))
        store.update { it.copy(block = Workflow.startWorkout(Workflow.startBlock(plan, 4))) }
        store.updateBlock { Workflow.updateSet(it, 0, 0) { s -> s.withWeight(185.0, lb).copy(reps = 8, done = true) } }
        val before = store.state.value.block!!.active!!.exercises[0].sets[0]
        repeat(5) {
            store.update { it.copy(settings = Settings(kg)) }
            assertEquals(84.0, store.state.value.block!!.active!!.exercises[0].sets[0].weightIn(kg))
            store.update { it.copy(settings = Settings(lb)) }
            assertEquals(185.0, store.state.value.block!!.active!!.exercises[0].sets[0].weightIn(lb))
        }
        val after = JsonFileStore(fs, path).load().block!!.active!!.exercises[0].sets[0]
        assertEquals(before, after)
        // The logged set keeps the entered value and unit.
        store.updateBlock { Workflow.finishWorkout(it) }
        val logged = store.state.value.block!!.sessions.single().results[0].sets.single()
        assertEquals(185.0, logged.enteredValue)
        assertEquals(lb, logged.enteredUnit)
        assertEquals(185.0, Units.displayLogged(logged.weightKg, logged.enteredValue, logged.enteredUnit, lb))
    }

    @Test fun oldDataWithoutUnitsLoadsAsKg() {
        val fs = FakeFileSystem()
        val path = "/openfitness.json".toPath()
        // Make data, then remove the new fields, like a file from an older version.
        val store = AppStore(JsonFileStore(fs, path))
        store.update { it.copy(block = Workflow.startWorkout(Workflow.startBlock(plan, 4))) }
        store.updateBlock { Workflow.updateSet(it, 0, 0) { s -> s.copy(weightKg = 80.0, reps = 8, done = true) } }
        val old = fs.read(path) { readUtf8() }
            .replace(Regex(""","enteredValue":[^,}]*"""), "")
            .replace(Regex(""","enteredUnit":[^,}]*"""), "")
            .replace(Regex(""","settings":\{[^}]*\}"""), "")
        check("enteredUnit" !in old && "settings" !in old)
        fs.write(path) { writeUtf8(old) }
        val loaded = JsonFileStore(fs, path).load()
        val set = loaded.block!!.active!!.exercises[0].sets[0]
        assertNull(set.enteredUnit)
        assertEquals(80.0, set.weightIn(kg))
        assertEquals(176.0, set.weightIn(lb))
        assertEquals(WeightUnit.LB, loaded.settings.unit)
    }
}

class SetInputTest {
    private val plan = Templates.upperLower4Day
    private val bench = ExerciseLibrary.byId.getValue("bb-bench")
    private val pushUp = ExerciseLibrary.byId.getValue("push-up")
    private val dip = ExerciseLibrary.byId.getValue("dip")

    @Test fun aSetNeedsRepsAndAWeight() {
        assertFalse(Workflow.canComplete(SetEntry(), bench))
        assertFalse(Workflow.canComplete(SetEntry(reps = 8), bench)) // No weight.
        assertFalse(Workflow.canComplete(SetEntry().withWeight(135.0, WeightUnit.LB).copy(reps = 0), bench)) // 0 reps.
        assertFalse(Workflow.canComplete(SetEntry(weightKg = 0.0, reps = 8), bench))
        assertFalse(Workflow.canComplete(SetEntry(weightKg = 5000.0, reps = 8), bench)) // Too heavy.
        assertFalse(Workflow.canComplete(SetEntry(weightKg = 60.0, reps = 500), bench)) // Too many reps.
        assertTrue(Workflow.canComplete(SetEntry().withWeight(135.0, WeightUnit.LB).copy(reps = 8), bench))
        assertTrue(Workflow.canComplete(SetEntry(reps = 20), pushUp)) // Reps only.
        assertTrue(Workflow.canComplete(SetEntry(reps = 10), dip)) // Bodyweight: no added weight is fine.
    }

    @Test fun completeSetFillsTheNextEmptySets() {
        var b = Workflow.startWorkout(Workflow.startBlock(plan, 4)) // Week 1: no suggestions.
        b = Workflow.updateSet(b, 0, 0) { it.withWeight(135.0, WeightUnit.LB).copy(reps = 10) }
        b = Workflow.updateSet(b, 0, 2) { it.copy(reps = 6) } // The user typed reps for set 3.
        b = Workflow.completeSet(b, 0, 0)
        val sets = b.active!!.exercises[0].sets
        assertTrue(sets[0].done)
        assertEquals(135.0, sets[1].weightIn(WeightUnit.LB))
        assertEquals(10, sets[1].reps)
        assertFalse(sets[1].done)
        assertEquals(6, sets[2].reps) // A value that the user typed stays.
        assertEquals(135.0, sets[2].weightIn(WeightUnit.LB))
        // Other exercises do not change.
        assertNull(b.active.exercises[1].sets[0].weightKg)
    }
}
