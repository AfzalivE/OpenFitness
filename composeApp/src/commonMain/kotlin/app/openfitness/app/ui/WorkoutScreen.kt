package app.openfitness.app.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import app.openfitness.app.data.Block
import app.openfitness.app.data.ExerciseEntry
import app.openfitness.app.data.Workflow
import app.openfitness.core.library.ExerciseLibrary
import app.openfitness.core.model.Exercise
import app.openfitness.core.model.JointPain
import app.openfitness.core.model.LoadType
import app.openfitness.core.model.Muscle
import app.openfitness.core.model.Prescription
import app.openfitness.core.model.Pump
import app.openfitness.core.model.Soreness
import app.openfitness.core.model.Workload
import app.openfitness.core.units.Units
import app.openfitness.core.units.WeightUnit
import kotlinx.coroutines.delay

private const val DEFAULT_REST_SECONDS = 120

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WorkoutScreen(
    block: Block,
    unit: WeightUnit,
    onChange: ((Block) -> Block) -> Unit,
    onSwap: (dayIndex: Int, slotIndex: Int) -> Unit,
    onFinish: () -> Unit,
    onBack: () -> Unit,
) {
    val workout = block.active ?: return
    val day = block.plan.days[workout.dayIndex]
    var restLeft by rememberSaveable { mutableIntStateOf(0) }
    var restRun by rememberSaveable { mutableIntStateOf(0) } // Changes to restart the timer.
    var confirmFinish by remember { mutableStateOf(false) }

    LaunchedEffect(restRun) {
        while (restLeft > 0) {
            delay(1000)
            restLeft--
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Week ${workout.week + 1}: ${day.name}") },
                navigationIcon = { TextButton(onClick = onBack) { Text("Back") } },
            )
        },
        bottomBar = {
            Surface(tonalElevation = 3.dp) {
                Row(
                    Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    // The rest timer starts when a set (or a superset round) is done. It shows only while it runs.
                    if (restLeft > 0) {
                        Text("Rest ${formatSeconds(restLeft)}", style = MaterialTheme.typography.titleMedium)
                        TextButton(onClick = { restLeft += 30 }) { Text("+30 s") }
                        TextButton(onClick = { restLeft = 0 }) { Text("Skip") }
                    } else {
                        Text(
                            "${workout.exercises.sumOf { e -> e.sets.count { it.done } }} of ${workout.exercises.sumOf { it.sets.size }} sets done",
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                    Spacer(Modifier.weight(1f))
                    Button(onClick = {
                        if (workout.exercises.all { it.allSetsDone }) onFinish() else confirmFinish = true
                    }) { Text("Finish") }
                }
            }
        },
    ) { padding ->
        val groups = Workflow.workoutGroups(block)
        // The current exercise is the first one with sets that are not done.
        // Its group and the groups before it are open. The next groups are closed.
        // A tap on a card header opens or closes that card.
        val current = workout.exercises.indexOfFirst { !it.allSetsDone }.let { if (it < 0) workout.exercises.lastIndex else it }
        var toggled by remember { mutableStateOf(mapOf<Int, Boolean>()) }
        LazyColumn(
            Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            items(groups, key = { g -> g.joinToString("-") { "${workout.exercises[it].slotIndex}:${workout.exercises[it].exerciseId}" } }) { group ->
                val key = workout.exercises[group.first()].slotIndex
                val default = group.min() <= current
                GroupCard(
                    block = block,
                    group = group,
                    isLastGroup = group == groups.last(),
                    unit = unit,
                    expanded = toggled[key] ?: default,
                    onToggle = { toggled = toggled + (key to !(toggled[key] ?: default)) },
                    onChange = onChange,
                    onSetDone = { i, s ->
                        val after = Workflow.updateSet(block, i, s) { it.copy(done = true) }
                        if (Workflow.startsRest(after, i, s)) {
                            restLeft = DEFAULT_REST_SECONDS
                            restRun++
                        }
                    },
                    onSwap = { slot -> onSwap(workout.dayIndex, slot) },
                )
            }
        }
    }

    if (confirmFinish) {
        AlertDialog(
            onDismissRequest = { confirmFinish = false },
            title = { Text("Finish the workout?") },
            text = { Text("Some sets are not done. The app saves only the sets that are done.") },
            confirmButton = { TextButton(onClick = { confirmFinish = false; onFinish() }) { Text("Finish") } },
            dismissButton = { TextButton(onClick = { confirmFinish = false }) { Text("Cancel") } },
        )
    }
}

/** One card for a single exercise or for a superset. */
@Composable
private fun GroupCard(
    block: Block,
    group: List<Int>,
    isLastGroup: Boolean,
    unit: WeightUnit,
    expanded: Boolean,
    onToggle: () -> Unit,
    onChange: ((Block) -> Block) -> Unit,
    onSetDone: (exerciseIndex: Int, setIndex: Int) -> Unit,
    onSwap: (slotIndex: Int) -> Unit,
) {
    val workout = block.active ?: return
    val superset = group.size > 1
    val entries = group.map { workout.exercises[it] }
    val exercises = entries.map { ExerciseLibrary.byId.getValue(it.exerciseId) }
    val prescriptions = entries.map { block.weeks[workout.week][workout.dayIndex][it.slotIndex] }
    val letters = group.indices.map { ('A' + it).toString() }
    val done = entries.sumOf { e -> e.sets.count { it.done } }
    val total = entries.sumOf { it.sets.size }
    val complete = group.all { Workflow.isExerciseComplete(block, it) }

    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        border = if (superset) BorderStroke(2.dp, MaterialTheme.colorScheme.primary) else null,
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            // Header: tap to open or close.
            Row(Modifier.fillMaxWidth().clickable(onClick = onToggle), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    if (superset) {
                        Text("Superset", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                        exercises.forEachIndexed { i, ex -> Text("${letters[i]}  ${ex.name}", style = MaterialTheme.typography.titleMedium) }
                    } else {
                        Text(exercises[0].name, style = MaterialTheme.typography.titleMedium)
                    }
                    val summary = when {
                        expanded && !superset -> Workflow.muscleOf(block, entries[0]).displayName
                        expanded -> entries.joinToString(" · ") { Workflow.muscleOf(block, it).displayName }
                        complete -> "Done: $done of $total sets"
                        done > 0 -> "$done of $total sets done"
                        superset -> "$total sets in rounds"
                        else -> "${prescriptions[0].sets} sets · ${suggestionText(prescriptions[0], exercises[0], unit)}"
                    }
                    Text(
                        summary,
                        style = MaterialTheme.typography.bodySmall,
                        color = if (complete && !expanded) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (expanded && !superset && !entries[0].anySetDone) {
                    TextButton(onClick = { onSwap(entries[0].slotIndex) }) { Text("Swap") }
                }
                Text(
                    if (expanded) "▴" else "▾",
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 8.dp),
                )
            }
            if (!expanded) return@Column

            // Facts for each exercise.
            group.forEachIndexed { m, index ->
                if (superset) HorizontalDivider()
                ExerciseFacts(
                    block = block,
                    index = index,
                    entry = entries[m],
                    exercise = exercises[m],
                    rx = prescriptions[m],
                    letter = if (superset) letters[m] else null,
                    unit = unit,
                    onChange = onChange,
                    onSwap = if (superset && !entries[m].anySetDone) ({ onSwap(entries[m].slotIndex) }) else null,
                )
            }

            HorizontalDivider()
            // Set rows. In a superset the order goes A1, B1, A2, B2, ...
            val rounds = entries.maxOf { it.sets.size }
            for (r in 0 until rounds) {
                group.forEachIndexed { m, index ->
                    val set = entries[m].sets.getOrNull(r) ?: return@forEachIndexed
                    SetRow(
                        label = if (superset) "${letters[m]}${r + 1}" else "Set ${r + 1}",
                        key = "${entries[m].exerciseId}-$r-$unit",
                        exercise = exercises[m],
                        unit = unit,
                        weight = set.weightIn(unit),
                        reps = set.reps,
                        done = set.done,
                        onWeight = { v -> onChange { Workflow.updateSet(it, index, r) { e -> e.withWeight(v, unit) } } },
                        onReps = { v -> onChange { Workflow.updateSet(it, index, r) { e -> e.copy(reps = v) } } },
                        canComplete = Workflow.canComplete(set, exercises[m]),
                        onDone = { d ->
                            if (d) {
                                onChange { Workflow.completeSet(it, index, r) }
                                onSetDone(index, r)
                            } else {
                                onChange { Workflow.updateSet(it, index, r) { e -> e.copy(done = false) } }
                            }
                        },
                    )
                }
            }

            // Questions that show only after the sets are done.
            group.forEachIndexed { m, index ->
                FeedbackPrompts(block, index, entries[m], if (superset) exercises[m].name else null, onChange)
            }

            // Superset actions. They change the plan for this day in the next weeks too.
            Row {
                val firstSlot = entries.first().slotIndex
                if (superset) {
                    TextButton(onClick = { onChange { Workflow.splitSuperset(it, workout.dayIndex, firstSlot) } }) {
                        Text("Split superset")
                    }
                } else if (!isLastGroup) {
                    TextButton(onClick = { onChange { Workflow.linkWithNext(it, workout.dayIndex, entries.last().slotIndex) } }) {
                        Text("Superset with next")
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ExerciseFacts(
    block: Block,
    index: Int,
    entry: ExerciseEntry,
    exercise: Exercise,
    rx: Prescription,
    letter: String?,
    unit: WeightUnit,
    onChange: ((Block) -> Block) -> Unit,
    onSwap: (() -> Unit)?,
) {
    val muscle = Workflow.muscleOf(block, entry)
    val feedback = block.active?.feedback?.get(muscle)
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        if (letter != null) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("$letter  ${exercise.name}", style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                if (onSwap != null) TextButton(onClick = onSwap) { Text("Swap") }
            }
        }
        if (Workflow.asksSoreness(block, index)) {
            Prompt(
                question = "How sore was your ${muscle.lower()} after the last session?",
                answer = feedback?.soreness?.let { "${muscle.displayName} soreness: ${sorenessLabel(it).lowercase()}" },
                options = Soreness.entries,
                selected = feedback?.soreness,
                label = ::sorenessLabel,
            ) { v -> onChange { Workflow.updateFeedback(it, muscle) { f -> f.copy(soreness = v) } } }
        }
        Text(
            "${rx.sets} sets · ${repRangeText(exercise)} · ${rirText(rx.targetRir)}",
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
        )
        Text(suggestionText(rx, exercise, unit), style = MaterialTheme.typography.bodyMedium)
        rx.note?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.tertiary) }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "${entry.sets.size} sets today",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f),
            )
            TextButton(
                onClick = { onChange { Workflow.removeSet(it, index) } },
                enabled = entry.sets.size > 1 && entry.sets.any { !it.done },
            ) { Text("− Set") }
            TextButton(
                onClick = { onChange { Workflow.addSet(it, index) } },
                enabled = entry.sets.size < Workflow.MAX_SETS_IN_WORKOUT,
            ) { Text("+ Set") }
        }
    }
}

@Composable
private fun FeedbackPrompts(
    block: Block,
    index: Int,
    entry: ExerciseEntry,
    name: String?,
    onChange: ((Block) -> Block) -> Unit,
) {
    val muscle = Workflow.muscleOf(block, entry)
    val feedback = block.active?.feedback?.get(muscle)
    if (Workflow.asksJointPain(block, index)) {
        Prompt(
            question = if (name != null) "Did you have joint pain in $name?" else "Did you have joint pain in this exercise?",
            answer = entry.jointPain?.let { "${name?.let { n -> "$n joint pain" } ?: "Joint pain"}: ${jointPainLabel(it).lowercase()}" },
            options = JointPain.entries,
            selected = entry.jointPain,
            label = ::jointPainLabel,
        ) { v -> onChange { Workflow.setJointPain(it, index, v) } }
    }
    if (Workflow.asksPumpAndWorkload(block, index)) {
        Prompt(
            question = "How was the pump in your ${muscle.lower()}?",
            answer = feedback?.pump?.let { "${muscle.displayName} pump: ${pumpLabel(it).lowercase()}" },
            options = Pump.entries,
            selected = feedback?.pump,
            label = ::pumpLabel,
        ) { v -> onChange { Workflow.updateFeedback(it, muscle) { f -> f.copy(pump = v) } } }
        Prompt(
            question = "How was the work for your ${muscle.lower()} today?",
            answer = feedback?.workload?.let { "${muscle.displayName} work: ${workloadLabel(it).lowercase()}" },
            options = Workload.entries,
            selected = feedback?.workload,
            label = ::workloadLabel,
        ) { v -> onChange { Workflow.updateFeedback(it, muscle) { f -> f.copy(workload = v) } } }
    }
}

@Composable
private fun SetRow(
    label: String,
    key: String,
    exercise: Exercise,
    unit: WeightUnit,
    /** The weight in [unit]. */
    weight: Double?,
    reps: Int?,
    done: Boolean,
    /** A weight in [unit]. */
    onWeight: (Double?) -> Unit,
    /** True when the set has the values that it needs to be marked done. */
    canComplete: Boolean,
    onReps: (Int?) -> Unit,
    onDone: (Boolean) -> Unit,
) {
    val usesWeight = exercise.loadType != LoadType.REPS_ONLY
    // Keep the text that the user types, so that "72." does not change while they type.
    var weightText by remember(key) { mutableStateOf(weight?.let { Units.format(it) } ?: "") }
    var repsText by remember(key) { mutableStateOf(reps?.toString() ?: "") }
    // When the app fills an empty field (from the set before), show the value.
    LaunchedEffect(weight) { if (weightText.isEmpty() && weight != null) weightText = Units.format(weight) }
    LaunchedEffect(reps) { if (repsText.isEmpty() && reps != null) repsText = reps.toString() }

    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(label, modifier = Modifier.width(52.dp), style = MaterialTheme.typography.labelLarge)
        if (usesWeight) {
            OutlinedTextField(
                value = weightText,
                onValueChange = { t ->
                    val clean = t.replace(',', '.')
                    // At most 1 decimal.
                    val v = clean.toDoubleOrNull()
                    val max = Units.toUnit(Workflow.MAX_WEIGHT_KG, unit)
                    if (clean.isEmpty() || (v != null && v <= max && clean.substringAfter('.', "").length <= 1)) {
                        weightText = clean
                        onWeight(clean.toDoubleOrNull())
                    }
                },
                label = { Text(unit.label) },
                singleLine = true,
                enabled = !done,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier.weight(1f),
            )
            Spacer(Modifier.width(8.dp))
        }
        OutlinedTextField(
            value = repsText,
            onValueChange = { t ->
                if (t.isEmpty() || (t.all(Char::isDigit) && (t.toIntOrNull() ?: Int.MAX_VALUE) <= Workflow.MAX_REPS)) {
                    repsText = t
                    onReps(t.toIntOrNull())
                }
            },
            label = { Text("Reps") },
            singleLine = true,
            enabled = !done,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier.weight(1f),
        )
        Checkbox(checked = done, onCheckedChange = onDone, enabled = done || canComplete)
    }
}

@Composable
private fun <T> Prompt(
    question: String,
    answer: String?,
    options: List<T>,
    selected: T?,
    label: (T) -> String,
    onSelect: (T) -> Unit,
) {
    var editing by remember { mutableStateOf(false) }
    if (answer != null && !editing) {
        // A small chip. Tap it to change the answer.
        AssistChip(onClick = { editing = true }, label = { Text("✓ $answer") })
        return
    }
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
        shape = RoundedCornerShape(16.dp),
    ) {
        Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(question, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
            Options(options, selected, label) { editing = false; onSelect(it) }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun <T> Options(options: List<T>, selected: T?, label: (T) -> String, onSelect: (T) -> Unit) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        options.forEach { option ->
            FilterChip(
                selected = option == selected,
                onClick = { onSelect(option) },
                label = { Text(label(option)) },
                colors = FilterChipDefaults.filterChipColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    selectedContainerColor = MaterialTheme.colorScheme.primary,
                    selectedLabelColor = MaterialTheme.colorScheme.onPrimary,
                ),
            )
        }
    }
}

private fun Muscle.lower() = displayName.lowercase()

fun sorenessLabel(v: Soreness) = when (v) {
    Soreness.NONE -> "Not sore"
    Soreness.RECOVERED_EARLY -> "Healed early"
    Soreness.RECOVERED_ON_TIME -> "Healed just in time"
    Soreness.STILL_SORE -> "Still sore"
}

fun pumpLabel(v: Pump) = when (v) {
    Pump.LOW -> "Low"
    Pump.MODERATE -> "Medium"
    Pump.HIGH -> "High"
}

fun workloadLabel(v: Workload) = when (v) {
    Workload.EASY -> "Easy"
    Workload.GOOD -> "Good"
    Workload.HARD -> "Hard"
    Workload.TOO_MUCH -> "Too much"
}

fun jointPainLabel(v: JointPain) = when (v) {
    JointPain.NONE -> "No pain"
    JointPain.MILD -> "Mild"
    JointPain.MODERATE -> "Moderate"
    JointPain.SEVERE -> "Severe"
}
