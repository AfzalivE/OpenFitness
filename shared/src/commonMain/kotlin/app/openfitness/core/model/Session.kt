package app.openfitness.core.model

/** What the user did in one training day of one week, with feedback. */
data class SessionRecord(
    val week: Int,
    val dayIndex: Int,
    val prescriptions: List<Prescription>,
    val results: List<ExerciseResult>,
    val feedback: Map<Muscle, MuscleFeedback> = emptyMap(),
) {
    fun resultFor(exerciseId: String): ExerciseResult? = results.firstOrNull { it.exerciseId == exerciseId }
    fun prescriptionFor(exerciseId: String): Prescription? = prescriptions.firstOrNull { it.exerciseId == exerciseId }
}
