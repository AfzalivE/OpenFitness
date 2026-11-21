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
