package app.openfitness.app.ui

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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
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
import app.openfitness.core.model.JointPain
import app.openfitness.core.model.LoadType
import app.openfitness.core.model.Muscle
import app.openfitness.core.model.Pump
import app.openfitness.core.model.Soreness
import app.openfitness.core.model.Workload
import kotlinx.coroutines.delay

private const val DEFAULT_REST_SECONDS = 120

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WorkoutScreen(
    block: Block,
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
                    if (restLeft > 0) {
                        Text("Rest ${formatSeconds(restLeft)}", style = MaterialTheme.typography.titleMedium)
                        TextButton(onClick = { restLeft += 30 }) { Text("+30 s") }
                        TextButton(onClick = { restLeft = 0 }) { Text("Stop") }
                    } else {
                        TextButton(onClick = { restLeft = DEFAULT_REST_SECONDS; restRun++ }) { Text("Start rest timer") }
                    }
                    Spacer(Modifier.weight(1f))
                    Button(onClick = {
                        if (workout.exercises.all { it.allSetsDone }) onFinish() else confirmFinish = true
                    }) { Text("Finish") }
                }
            }
        },
    ) { padding ->
        LazyColumn(
            Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            itemsIndexed(workout.exercises, key = { _, e -> "${e.slotIndex}-${e.exerciseId}" }) { i, entry ->
                ExerciseCard(
                    block = block,
                    index = i,
                    entry = entry,
                    onChange = onChange,
                    onSetDone = { restLeft = DEFAULT_REST_SECONDS; restRun++ },
                    onSwap = { onSwap(workout.dayIndex, entry.slotIndex) },
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

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ExerciseCard(
    block: Block,
    index: Int,
    entry: ExerciseEntry,
    onChange: ((Block) -> Block) -> Unit,
    onSetDone: () -> Unit,
    onSwap: () -> Unit,
) {
    val workout = block.active ?: return
    val exercise = ExerciseLibrary.byId.getValue(entry.exerciseId)
    val rx = block.weeks[workout.week][workout.dayIndex][entry.slotIndex]
    val muscle = Workflow.muscleOf(block, entry)
    val feedback = workout.feedback[muscle]

    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(exercise.name, style = MaterialTheme.typography.titleMedium)
                    Text(muscle.displayName, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                TextButton(onClick = onSwap, enabled = !entry.anySetDone) { Text("Swap") }
            }

            if (Workflow.asksSoreness(block, index)) {
                Prompt("How sore was your ${muscle.lower()} after the last session?") {
                    Options(
                        options = Soreness.entries,
                        selected = feedback?.soreness,
                        label = ::sorenessLabel,
                    ) { v -> onChange { Workflow.updateFeedback(it, muscle) { f -> f.copy(soreness = v) } } }
                }
            }

            Text(
                "${rx.sets} sets · ${repRangeText(exercise)} · ${rirText(rx.targetRir)}",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Text(suggestionText(rx), style = MaterialTheme.typography.bodyMedium)
            rx.note?.let {
                Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.tertiary)
            }

            HorizontalDivider()
            entry.sets.forEachIndexed { s, set ->
                SetRow(
                    number = s + 1,
                    key = "${entry.exerciseId}-$s",
                    usesWeight = exercise.loadType != LoadType.REPS_ONLY,
                    weightKg = set.weightKg,
                    reps = set.reps,
                    done = set.done,
                    onWeight = { kg -> onChange { Workflow.updateSet(it, index, s) { e -> e.copy(weightKg = kg) } } },
                    onReps = { r -> onChange { Workflow.updateSet(it, index, s) { e -> e.copy(reps = r) } } },
                    onDone = { d ->
                        onChange { Workflow.updateSet(it, index, s) { e -> e.copy(done = d) } }
                        if (d) onSetDone()
                    },
                )
            }

            if (Workflow.asksJointPain(block, index)) {
                Prompt("Did you have joint pain in this exercise?") {
                    Options(JointPain.entries, entry.jointPain, ::jointPainLabel) { v ->
                        onChange { Workflow.setJointPain(it, index, v) }
                    }
                }
            }
            if (Workflow.asksPumpAndWorkload(block, index)) {
                Prompt("How was the pump in your ${muscle.lower()}?") {
                    Options(Pump.entries, feedback?.pump, ::pumpLabel) { v ->
                        onChange { Workflow.updateFeedback(it, muscle) { f -> f.copy(pump = v) } }
                    }
                }
                Prompt("How was the work for your ${muscle.lower()} today?") {
                    Options(Workload.entries, feedback?.workload, ::workloadLabel) { v ->
                        onChange { Workflow.updateFeedback(it, muscle) { f -> f.copy(workload = v) } }
                    }
                }
            }
        }
    }
}

@Composable
private fun SetRow(
    number: Int,
    key: String,
    usesWeight: Boolean,
    weightKg: Double?,
    reps: Int?,
    done: Boolean,
    onWeight: (Double?) -> Unit,
    onReps: (Int?) -> Unit,
    onDone: (Boolean) -> Unit,
) {
    // Keep the text that the user types, so that "72." does not change while they type.
    var weightText by remember(key) { mutableStateOf(weightKg?.let(::formatKg) ?: "") }
    var repsText by remember(key) { mutableStateOf(reps?.toString() ?: "") }

    Row(verticalAlignment = Alignment.CenterVertically) {
        Text("Set $number", modifier = Modifier.width(52.dp), style = MaterialTheme.typography.labelLarge)
        if (usesWeight) {
            OutlinedTextField(
                value = weightText,
                onValueChange = { t ->
                    val clean = t.replace(',', '.')
                    if (clean.isEmpty() || clean.toDoubleOrNull() != null) {
                        weightText = clean
                        onWeight(clean.toDoubleOrNull())
                    }
                },
                label = { Text("kg") },
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
                if (t.isEmpty() || (t.length <= 3 && t.all(Char::isDigit))) {
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
        Checkbox(checked = done, onCheckedChange = onDone, enabled = done || reps != null)
    }
}

@Composable
private fun Prompt(question: String, content: @Composable () -> Unit) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)) {
        Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(question, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
            content()
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun <T> Options(options: List<T>, selected: T?, label: (T) -> String, onSelect: (T) -> Unit) {
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
