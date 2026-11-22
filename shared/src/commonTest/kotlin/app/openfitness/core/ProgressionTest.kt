package app.openfitness.core

import app.openfitness.core.library.ExerciseLibrary
import app.openfitness.core.library.Templates
import app.openfitness.core.model.*
import app.openfitness.core.progression.*
import kotlin.test.*

class RirScheduleTest {
    @Test fun fourWeekBlockGoesFromThreeToZero() {
        assertEquals(listOf(3, 2, 1, 0, RirSchedule.DELOAD_RIR), (0..4).map { RirSchedule.targetRir(it, 4) })
    }

    @Test fun rirNeverGoesUp() {
        for (weeks in 3..8) {
            val values = (0 until weeks).map { RirSchedule.targetRir(it, weeks) }
            assertEquals(values.sortedDescending(), values)
            assertEquals(0, values.last())
        }
    }
}

class SetProgressionTest {
    @Test fun stillSoreRemovesASet() {
        assertEquals(-1, SetProgression.setDelta(MuscleFeedback(Soreness.STILL_SORE, Pump.LOW, Workload.EASY)))
    }

    @Test fun tooMuchWorkRemovesASet() {
        assertEquals(-1, SetProgression.setDelta(MuscleFeedback(Soreness.NONE, Pump.LOW, Workload.TOO_MUCH)))
    }

    @Test fun fastRecoveryAndEasyWorkAddsTwo() {
        assertEquals(2, SetProgression.setDelta(MuscleFeedback(Soreness.NONE, Pump.LOW, Workload.EASY)))
    }

    @Test fun recoveredOnTimeWithGoodWorkHolds() {
        assertEquals(0, SetProgression.setDelta(MuscleFeedback(Soreness.RECOVERED_ON_TIME, Pump.LOW, Workload.GOOD)))
    }

    @Test fun earlyRecoveryAddsOne() {
        assertEquals(1, SetProgression.setDelta(MuscleFeedback(Soreness.RECOVERED_EARLY, Pump.MODERATE, Workload.GOOD)))
    }

    @Test fun noFeedbackAddsOne() {
        assertEquals(1, SetProgression.setDelta(null))
    }
}

class LoadProgressionTest {
    private val bench = ExerciseLibrary.byId.getValue("bb-bench") // 6..10, step 2.5

    @Test fun addsRepsInsideTheRange() {
        val t = LoadProgression.next(bench, listOf(LoggedSet(80.0, 7)), 3, 2, JointPain.NONE, deload = false)
        assertEquals(80.0, t.weightKg)
        assertEquals(8, t.targetReps)
    }

    @Test fun addsWeightAtTheTopOfTheRange() {
        val t = LoadProgression.next(bench, listOf(LoggedSet(80.0, 10)), 2, 1, JointPain.NONE, deload = false)
        assertEquals(82.5, t.weightKg)
        assertTrue(t.targetReps!! in bench.repRange)
    }

    @Test fun severeJointPainLowersWeight() {
        val t = LoadProgression.next(bench, listOf(LoggedSet(80.0, 8)), 2, 1, JointPain.SEVERE, deload = false)
        assertEquals(72.5, t.weightKg)
        assertNotNull(t.note)
    }

    @Test fun deloadHalvesReps() {
        val t = LoadProgression.next(bench, listOf(LoggedSet(80.0, 9)), 0, RirSchedule.DELOAD_RIR, JointPain.NONE, deload = true)
        assertEquals(80.0, t.weightKg)
        assertEquals(5, t.targetReps)
    }

    @Test fun repsOnlyHasNoWeight() {
        val pushUp = ExerciseLibrary.byId.getValue("push-up")
        val t = LoadProgression.next(pushUp, listOf(LoggedSet(0.0, 20)), 3, 2, JointPain.NONE, deload = false)
        assertNull(t.weightKg)
        assertEquals(21, t.targetReps)
    }
}

class MesocycleEngineTest {
    private val plan = Templates.upperLower4Day
    private val engine = MesocycleEngine(plan, ExerciseLibrary.byId)

    private fun record(week: Int, day: Int, rx: List<Prescription>, fb: MuscleFeedback?) = SessionRecord(
        week = week,
        dayIndex = day,
        prescriptions = rx,
        results = rx.map { p -> ExerciseResult(p.exerciseId, List(p.sets) { LoggedSet(p.weightKg ?: 50.0, p.targetReps ?: 8) }) },
        feedback = if (fb == null) emptyMap() else plan.days[day].slots.associate { it.muscle to fb },
    )

    @Test fun firstWeekMeetsStartVolume() {
        val week0 = engine.firstWeek()
        val chest = week0.flatten().filter { ExerciseLibrary.byId.getValue(it.exerciseId).primary == Muscle.CHEST }.sumOf { it.sets }
        assertEquals(8, chest)
        assertTrue(week0.flatten().all { it.sets >= MesocycleEngine.MIN_SETS_PER_SLOT && it.targetRir == 3 })
    }

    @Test fun goodFeedbackGrowsVolumeButNotPastTheCap() {
        var rx = engine.firstWeek()
        val fb = MuscleFeedback(Soreness.NONE, Pump.LOW, Workload.EASY)
        val chestTotals = mutableListOf<Int>()
        for (week in 1 until plan.accumulationWeeks) {
            rx = engine.nextWeek(week, rx.mapIndexed { d, r -> record(week - 1, d, r, fb) })
            chestTotals += rx.flatten().filter { it.exerciseId in setOf("bb-bench", "db-incline") }.sumOf { it.sets }
        }
        assertEquals(chestTotals.sorted(), chestTotals)
        assertTrue(chestTotals.all { it <= plan.volume.getValue(Muscle.CHEST).maxSets })
        assertTrue(chestTotals.last() > 8)
    }

    @Test fun poorRecoveryCutsVolume() {
        val rx0 = engine.firstWeek()
        val fb = MuscleFeedback(Soreness.STILL_SORE, Pump.HIGH, Workload.TOO_MUCH)
        val rx1 = engine.nextWeek(1, rx0.mapIndexed { d, r -> record(0, d, r, fb) })
        assertTrue(rx1.flatten().sumOf { it.sets } < rx0.flatten().sumOf { it.sets })
    }

    @Test fun deloadWeekHalvesSets() {
        var rx = engine.firstWeek()
        for (week in 1..plan.accumulationWeeks) {
            val prev = rx
            rx = engine.nextWeek(week, prev.mapIndexed { d, r -> record(week - 1, d, r, null) })
            if (week == plan.accumulationWeeks) {
                prev.flatten().zip(rx.flatten()).forEach { (a, b) -> assertEquals((a.sets / 2).coerceAtLeast(1), b.sets) }
                assertTrue(rx.flatten().all { it.targetRir == RirSchedule.DELOAD_RIR })
            }
        }
    }

    @Test fun capWeeklyGivesSetsToSmallerSlotsFirst() {
        assertEquals(listOf(4, 4), MesocycleEngine.capWeekly(listOf(4, 3), listOf(2, 2), 8))
    }

    @Test fun allTemplatesUseKnownExercises() {
        Templates.all.forEach { MesocycleEngine(it, ExerciseLibrary.byId).firstWeek() }
    }
}

class SetsDoneTest {
    private val plan = Templates.upperLower4Day
    private val engine = MesocycleEngine(plan, ExerciseLibrary.byId)

    private fun records(rx: List<List<Prescription>>, setsFor: (Prescription) -> Int) = rx.mapIndexed { d, day ->
        SessionRecord(0, d, day, day.map { p -> ExerciseResult(p.exerciseId, List(setsFor(p)) { LoggedSet(50.0, 8) }) },
            plan.days[d].slots.associate { it.muscle to MuscleFeedback(Soreness.RECOVERED_ON_TIME, Pump.MODERATE, Workload.GOOD) })
    }

    @Test fun nextWeekStartsFromTheSetsDone() {
        val rx0 = engine.firstWeek()
        // Feedback that holds the sets. The user did one more set of bench than prescribed.
        val rx1 = engine.nextWeek(1, records(rx0) { p -> if (p.exerciseId == "bb-bench") p.sets + 1 else p.sets })
        val bench0 = rx0[0].first { it.exerciseId == "bb-bench" }.sets
        assertEquals(bench0 + 1, rx1[0].first { it.exerciseId == "bb-bench" }.sets)
        assertEquals(rx0[0].first { it.exerciseId == "cable-row" }.sets, rx1[0].first { it.exerciseId == "cable-row" }.sets)
    }

    @Test fun fewerSetsDoneMeansFewerSetsNextWeek() {
        val rx0 = engine.firstWeek()
        val rx1 = engine.nextWeek(1, records(rx0) { p -> if (p.exerciseId == "bb-bench") 2 else p.sets })
        assertEquals(2, rx1[0].first { it.exerciseId == "bb-bench" }.sets)
    }

    @Test fun noLoggedSetsUsesThePrescription() {
        val rx0 = engine.firstWeek()
        val rx1 = engine.nextWeek(1, records(rx0) { 0 })
        assertEquals(rx0.flatten().map { it.sets }, rx1.flatten().map { it.sets })
    }
}

class StartSetsTest {
    @Test fun slotStartSetsOverrideTheSplit() {
        val base = Templates.upperLower4Day
        val days = base.days.toMutableList()
        days[0] = days[0].copy(slots = days[0].slots.mapIndexed { i, s -> if (i == 0) s.copy(startSets = 3) else s })
        val week0 = MesocycleEngine(base.copy(days = days), ExerciseLibrary.byId).firstWeek()
        assertEquals(3, week0[0][0].sets) // Bench: its own start sets.
        // Incline press gets the rest of the chest start sets (8 - 3), at least 2.
        assertEquals(5, week0[2].first { it.exerciseId == "db-incline" }.sets)
    }
}

class SupersetTest {
    private val day = TrainingDay("Day", listOf("bb-bench", "cable-row", "db-lateral", "pushdown").map { ExerciseSlot(it, ExerciseLibrary.byId.getValue(it).primary) })

    @Test fun singlesAreOwnGroups() {
        assertEquals(listOf(listOf(0), listOf(1), listOf(2), listOf(3)), day.groups())
    }

    @Test fun linkAndSplit() {
        val linked = day.linkWithNext(0)
        assertEquals(listOf(listOf(0, 1), listOf(2), listOf(3)), linked.groups())
        assertTrue(linked.isInSuperset(1))
        val three = linked.linkWithNext(1)
        assertEquals(listOf(listOf(0, 1, 2), listOf(3)), three.groups())
        val split = three.split(2)
        assertEquals(day.groups(), split.groups())
        assertTrue(split.slots.all { it.supersetGroup == null })
    }

    @Test fun twoSupersetsStaySeparate() {
        val d = day.linkWithNext(0).linkWithNext(2)
        assertEquals(listOf(listOf(0, 1), listOf(2, 3)), d.groups())
        assertEquals(listOf(1, 1, 2, 2), d.slots.map { it.supersetGroup })
    }

    @Test fun normalizeFixesGroupsAfterAMove() {
        // Superset 0+1, then slot 1 moves to the end: the old group is no longer next to each other.
        val linked = day.linkWithNext(0)
        val moved = linked.copy(slots = listOf(linked.slots[0], linked.slots[2], linked.slots[3], linked.slots[1])).normalized()
        assertEquals(listOf(listOf(0), listOf(1), listOf(2), listOf(3)), moved.groups())
        assertTrue(moved.slots.all { it.supersetGroup == null })
    }

    @Test fun supersetSurvivesSerialization() {
        val plan = Templates.upperLower4Day.let { it.copy(days = listOf(it.days[0].linkWithNext(0)) + it.days.drop(1)) }
        val json = kotlinx.serialization.json.Json.encodeToString(MesocyclePlan.serializer(), plan)
        assertEquals(plan, kotlinx.serialization.json.Json.decodeFromString(MesocyclePlan.serializer(), json))
    }
}

class UnitsTest {
    private val bench = ExerciseLibrary.byId.getValue("bb-bench")
    private val lateral = ExerciseLibrary.byId.getValue("db-lateral")
    private val incline = ExerciseLibrary.byId.getValue("db-incline")

    @Test fun convertsBothWays() {
        assertEquals(220.462, app.openfitness.core.units.Units.toUnit(100.0, app.openfitness.core.units.WeightUnit.LB), 0.001)
        assertEquals(100.0, app.openfitness.core.units.Units.toKg(220.46226218, app.openfitness.core.units.WeightUnit.LB), 1e-6)
        assertEquals(80.0, app.openfitness.core.units.Units.toUnit(80.0, app.openfitness.core.units.WeightUnit.KG))
    }

    @Test fun lbStepsFollowTheEquipment() {
        val u = app.openfitness.core.units.Units
        val lb = app.openfitness.core.units.WeightUnit.LB
        assertEquals(5.0, u.loadStep(bench, lb))
        assertEquals(5.0, u.loadStep(incline, lb))
        assertEquals(2.5, u.loadStep(lateral, lb))
        assertEquals(2.5, u.loadStep(bench, app.openfitness.core.units.WeightUnit.KG))
    }

    @Test fun displayRoundsToTheStep() {
        val u = app.openfitness.core.units.Units
        val lb = app.openfitness.core.units.WeightUnit.LB
        val kg = app.openfitness.core.units.WeightUnit.KG
        assertEquals(185.0, u.display(u.toKg(185.0, lb), bench, lb)) // On a lb step: exact.
        assertEquals(84.0, u.display(u.toKg(185.0, lb), bench, kg)) // 83.9 kg: to the nearest 0.5 kg.
        assertEquals(182.0, u.display(82.5, bench, lb)) // 181.9 lb: to the nearest 1 lb.
        assertEquals(20.0, u.display(9.0, lateral, lb)) // 19.8 lb
        assertEquals(82.5, u.display(82.5, bench, kg))
    }

    @Test fun lbProgressionStaysOnLbSteps() {
        val u = app.openfitness.core.units.Units
        val lb = app.openfitness.core.units.WeightUnit.LB
        val benchLb = u.exercisesFor(lb, ExerciseLibrary.all).getValue("bb-bench")
        val start = u.toKg(185.0, lb)
        val t = LoadProgression.next(benchLb, listOf(LoggedSet(start, 10)), 2, 1, JointPain.NONE, deload = false)
        assertEquals(190.0, u.toUnit(t.weightKg!!, lb), 1e-9)
        val pain = LoadProgression.next(benchLb, listOf(LoggedSet(start, 8)), 2, 1, JointPain.MODERATE, deload = false)
        assertEquals(165.0, u.toUnit(pain.weightKg!!, lb), 1e-9) // 185 × 0.9 = 166.5 → 165
    }

    @Test fun formatHasNoTrailingZeros() {
        val u = app.openfitness.core.units.Units
        assertEquals("80", u.format(80.0))
        assertEquals("82.5", u.format(82.5))
        assertEquals("1.3", u.format(1.25)) // Never more than 1 decimal.
        assertEquals("83.9", u.format(83.91459))
    }

    @Test fun loggedWeightShowsExactlyInTheUnitItWasEnteredIn() {
        val u = app.openfitness.core.units.Units
        val lb = app.openfitness.core.units.WeightUnit.LB
        val kg = app.openfitness.core.units.WeightUnit.KG
        // Entered as 185 lb.
        val kg185 = u.toKg(185.0, lb)
        assertEquals(185.0, u.displayLogged(kg185, 185.0, lb, lb))
        assertEquals(84.0, u.displayLogged(kg185, 185.0, lb, kg))
        // Entered as 82.5 kg.
        assertEquals(82.5, u.displayLogged(82.5, 82.5, kg, kg))
        assertEquals(182.0, u.displayLogged(82.5, 82.5, kg, lb))
        // Old data: no entered unit, so it is kg.
        assertEquals(82.5, u.displayLogged(82.5, null, null, kg))
        assertEquals(182.0, u.displayLogged(82.5, null, null, lb))
    }

    @Test fun oldLoggedSetsLoadAsKg() {
        val json = """{"weightKg":80.0,"reps":8}"""
        val set = kotlinx.serialization.json.Json.decodeFromString(LoggedSet.serializer(), json)
        assertEquals(LoggedSet(80.0, 8), set)
        assertNull(set.enteredUnit)
        assertEquals(176.0, app.openfitness.core.units.Units.displayLogged(set.weightKg, set.enteredValue, set.enteredUnit, app.openfitness.core.units.WeightUnit.LB))
    }
}

class SlotCapTest {
    @Test fun firstWeekNeverGivesMoreThanTheSlotMaximum() {
        // Back starts at 10 sets. With one back slot, that slot gets the maximum, not 10.
        val plan = MesocyclePlan(
            "One slot",
            listOf(TrainingDay("Day 1", listOf(ExerciseSlot("cable-row", Muscle.BACK))), TrainingDay("Day 2", listOf(ExerciseSlot("bb-squat", Muscle.QUADS)))),
        )
        val week0 = MesocycleEngine(plan, ExerciseLibrary.byId).firstWeek()
        assertEquals(MesocycleEngine.MAX_SETS_PER_SLOT, week0[0][0].sets)
        assertTrue(week0.flatten().all { it.sets <= MesocycleEngine.MAX_SETS_PER_SLOT })
    }
}
