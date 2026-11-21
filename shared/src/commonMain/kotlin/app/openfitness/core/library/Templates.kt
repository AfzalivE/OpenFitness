package app.openfitness.core.library

import app.openfitness.core.model.ExerciseSlot
import app.openfitness.core.model.MesocyclePlan
import app.openfitness.core.model.TrainingDay

/** Built-in plans that a user can start from. */
object Templates {
    private fun slot(id: String) = ExerciseSlot(id, ExerciseLibrary.byId.getValue(id).primary)

    val upperLower4Day = MesocyclePlan(
        name = "Upper / lower, 4 days",
        days = listOf(
            TrainingDay("Upper A", listOf("bb-bench", "cable-row", "db-lateral", "pushdown", "cable-curl").map(::slot)),
            TrainingDay("Lower A", listOf("bb-squat", "seated-curl", "leg-ext", "standing-calf").map(::slot)),
            TrainingDay("Upper B", listOf("db-incline", "lat-pulldown", "cable-lateral", "overhead-ext", "db-incline-curl").map(::slot)),
            TrainingDay("Lower B", listOf("rdl", "hack-squat", "lying-curl", "seated-calf").map(::slot)),
        ),
    )

    val fullBody3Day = MesocyclePlan(
        name = "Full body, 3 days",
        days = listOf(
            TrainingDay("Day 1", listOf("bb-squat", "bb-bench", "cable-row", "db-lateral", "cable-curl").map(::slot)),
            TrainingDay("Day 2", listOf("rdl", "db-shoulder-press", "lat-pulldown", "pushdown", "standing-calf").map(::slot)),
            TrainingDay("Day 3", listOf("leg-press", "db-incline", "chest-supported-row", "cable-lateral", "hammer-curl").map(::slot)),
        ),
    )

    val all = listOf(upperLower4Day, fullBody3Day)
}
