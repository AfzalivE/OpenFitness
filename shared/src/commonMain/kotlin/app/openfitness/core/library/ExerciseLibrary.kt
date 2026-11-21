package app.openfitness.core.library

import app.openfitness.core.model.Equipment.BARBELL
import app.openfitness.core.model.Equipment.BODYWEIGHT
import app.openfitness.core.model.Equipment.CABLE
import app.openfitness.core.model.Equipment.DUMBBELL
import app.openfitness.core.model.Equipment.MACHINE
import app.openfitness.core.model.Equipment.SMITH_MACHINE
import app.openfitness.core.model.Exercise
import app.openfitness.core.model.LoadType
import app.openfitness.core.model.Muscle
import app.openfitness.core.model.Muscle.ABS
import app.openfitness.core.model.Muscle.BACK
import app.openfitness.core.model.Muscle.BICEPS
import app.openfitness.core.model.Muscle.CALVES
import app.openfitness.core.model.Muscle.CHEST
import app.openfitness.core.model.Muscle.FOREARMS
import app.openfitness.core.model.Muscle.FRONT_DELTS
import app.openfitness.core.model.Muscle.GLUTES
import app.openfitness.core.model.Muscle.HAMSTRINGS
import app.openfitness.core.model.Muscle.QUADS
import app.openfitness.core.model.Muscle.REAR_DELTS
import app.openfitness.core.model.Muscle.SIDE_DELTS
import app.openfitness.core.model.Muscle.TRAPS
import app.openfitness.core.model.Muscle.TRICEPS

/** The built-in exercises. Users can add their own exercises later. */
object ExerciseLibrary {
    val all: List<Exercise> = listOf(
        // Chest
        Exercise("bb-bench", "Barbell bench press", CHEST, setOf(TRICEPS, FRONT_DELTS), BARBELL, repRange = 6..10),
        Exercise("db-incline", "Incline dumbbell press", CHEST, setOf(FRONT_DELTS, TRICEPS), DUMBBELL, loadStepKg = 2.0),
        Exercise("machine-chest", "Machine chest press", CHEST, setOf(TRICEPS), MACHINE, loadStepKg = 5.0),
        Exercise("cable-fly", "Cable fly", CHEST, equipment = CABLE, loadStepKg = 2.5, repRange = 10..15),
        Exercise("dip", "Dip", CHEST, setOf(TRICEPS), BODYWEIGHT, LoadType.BODYWEIGHT_PLUS, repRange = 6..15),
        Exercise("push-up", "Push-up", CHEST, setOf(TRICEPS), BODYWEIGHT, LoadType.REPS_ONLY, repRange = 10..30),
        // Back
        Exercise("pull-up", "Pull-up", BACK, setOf(BICEPS), BODYWEIGHT, LoadType.BODYWEIGHT_PLUS, repRange = 5..12),
        Exercise("lat-pulldown", "Lat pulldown", BACK, setOf(BICEPS), CABLE, loadStepKg = 5.0),
        Exercise("bb-row", "Barbell row", BACK, setOf(BICEPS, REAR_DELTS), BARBELL, repRange = 6..10),
        Exercise("cable-row", "Seated cable row", BACK, setOf(BICEPS), CABLE, loadStepKg = 5.0),
        Exercise("db-row", "One-arm dumbbell row", BACK, setOf(BICEPS), DUMBBELL, loadStepKg = 2.0),
        Exercise("chest-supported-row", "Chest-supported machine row", BACK, setOf(REAR_DELTS), MACHINE, loadStepKg = 5.0),
        // Shoulders
        Exercise("ohp", "Standing overhead press", FRONT_DELTS, setOf(TRICEPS), BARBELL, repRange = 6..10),
        Exercise("db-shoulder-press", "Seated dumbbell shoulder press", FRONT_DELTS, setOf(TRICEPS), DUMBBELL, loadStepKg = 2.0),
        Exercise("db-lateral", "Dumbbell lateral raise", SIDE_DELTS, equipment = DUMBBELL, loadStepKg = 1.0, repRange = 12..20),
        Exercise("cable-lateral", "Cable lateral raise", SIDE_DELTS, equipment = CABLE, loadStepKg = 1.25, repRange = 12..20),
        Exercise("reverse-fly", "Reverse machine fly", REAR_DELTS, equipment = MACHINE, loadStepKg = 2.5, repRange = 12..20),
        Exercise("face-pull", "Cable face pull", REAR_DELTS, setOf(TRAPS), CABLE, loadStepKg = 2.5, repRange = 12..20),
        // Arms
        Exercise("bb-curl", "Barbell curl", BICEPS, setOf(FOREARMS), BARBELL, loadStepKg = 2.5),
        Exercise("db-incline-curl", "Incline dumbbell curl", BICEPS, equipment = DUMBBELL, loadStepKg = 1.0, repRange = 10..15),
        Exercise("cable-curl", "Cable curl", BICEPS, equipment = CABLE, loadStepKg = 2.5, repRange = 10..15),
        Exercise("hammer-curl", "Hammer curl", BICEPS, setOf(FOREARMS), DUMBBELL, loadStepKg = 1.0),
        Exercise("pushdown", "Cable triceps pushdown", TRICEPS, equipment = CABLE, loadStepKg = 2.5, repRange = 10..15),
        Exercise("overhead-ext", "Overhead cable triceps extension", TRICEPS, equipment = CABLE, loadStepKg = 2.5, repRange = 10..15),
        Exercise("skull-crusher", "EZ-bar skull crusher", TRICEPS, equipment = BARBELL, loadStepKg = 2.5),
        Exercise("wrist-curl", "Dumbbell wrist curl", FOREARMS, equipment = DUMBBELL, loadStepKg = 1.0, repRange = 12..20),
        // Legs
        Exercise("bb-squat", "Barbell back squat", QUADS, setOf(GLUTES), BARBELL, repRange = 5..10),
        Exercise("hack-squat", "Hack squat", QUADS, setOf(GLUTES), MACHINE, loadStepKg = 5.0),
        Exercise("leg-press", "Leg press", QUADS, setOf(GLUTES), MACHINE, loadStepKg = 10.0, repRange = 8..15),
        Exercise("leg-ext", "Leg extension", QUADS, equipment = MACHINE, loadStepKg = 5.0, repRange = 10..15),
        Exercise("smith-squat", "Smith machine squat", QUADS, setOf(GLUTES), SMITH_MACHINE),
        Exercise("rdl", "Romanian deadlift", HAMSTRINGS, setOf(GLUTES, BACK), BARBELL, repRange = 6..10),
        Exercise("seated-curl", "Seated leg curl", HAMSTRINGS, equipment = MACHINE, loadStepKg = 5.0, repRange = 10..15),
        Exercise("lying-curl", "Lying leg curl", HAMSTRINGS, equipment = MACHINE, loadStepKg = 5.0, repRange = 10..15),
        Exercise("hip-thrust", "Barbell hip thrust", GLUTES, setOf(HAMSTRINGS), BARBELL, loadStepKg = 5.0),
        Exercise("split-squat", "Dumbbell split squat", QUADS, setOf(GLUTES), DUMBBELL, loadStepKg = 2.0),
        Exercise("standing-calf", "Standing calf raise", CALVES, equipment = MACHINE, loadStepKg = 5.0, repRange = 10..20),
        Exercise("seated-calf", "Seated calf raise", CALVES, equipment = MACHINE, loadStepKg = 5.0, repRange = 12..20),
        // Other
        Exercise("db-shrug", "Dumbbell shrug", TRAPS, equipment = DUMBBELL, loadStepKg = 2.0, repRange = 10..20),
        Exercise("cable-crunch", "Cable crunch", ABS, equipment = CABLE, loadStepKg = 5.0, repRange = 10..20),
        Exercise("hanging-leg-raise", "Hanging leg raise", ABS, equipment = BODYWEIGHT, loadType = LoadType.REPS_ONLY, repRange = 8..20),
    )

    val byId: Map<String, Exercise> = all.associateBy { it.id }

    fun forMuscle(muscle: Muscle): List<Exercise> = all.filter { it.primary == muscle }
}
