package app.openfitness.app.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.openfitness.app.data.Block
import app.openfitness.app.data.Workflow
import app.openfitness.app.data.Workflow.Question
import app.openfitness.app.data.Workflow.QuestionKind
import app.openfitness.core.library.ExerciseLibrary
import app.openfitness.core.model.Exercise
import app.openfitness.core.model.JointPain
import app.openfitness.core.model.LoadType
import app.openfitness.core.model.Muscle
import app.openfitness.core.model.Pump
import app.openfitness.core.model.Soreness
import app.openfitness.core.model.Workload
import app.openfitness.core.units.Units
import app.openfitness.core.units.WeightUnit
import kotlinx.coroutines.delay

private const val DEFAULT_REST_SECONDS = 120

/** Saves the open or closed state of the cards as text, for example "0:1,3:0". */
private val ToggleSaver = Saver<Map<Int, Boolean>, String>(
    save = { m -> m.entries.joinToString(",") { "${it.key}:${if (it.value) 1 else 0}" } },
    restore = { t ->
        t.split(',').filter { it.contains(':') }.associate { e -> e.substringBefore(':').toInt() to (e.substringAfter(':') == "1") }
    },
)

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
    val rir = block.weeks[workout.week][workout.dayIndex].firstOrNull()?.targetRir
    var restLeft by rememberSaveable { mutableIntStateOf(0) }
    var restRun by rememberSaveable { mutableIntStateOf(0) } // Changes to restart the timer.
    var confirmFinish by remember { mutableStateOf(false) }
    // Questions that the user closed without an answer. They stay as chips on the card.
    var skipped by remember { mutableStateOf(setOf<Question>()) }
    // A question that the user opened from a chip.
    var editing by remember { mutableStateOf<Question?>(null) }

    LaunchedEffect(restRun) {
        while (restLeft > 0) {
            delay(1000)
            restLeft--
        }
    }

    val doneSets = workout.exercises.sumOf { e -> e.sets.count { it.done } }
    val allSets = workout.exercises.sumOf { it.sets.size }

    Scaffold(
        topBar = {
            Column {
                TopAppBar(
                    title = {
                        Column {
                            Text(day.name, style = MaterialTheme.typography.titleLarge)
                            Text(
                                "Week ${workout.week + 1} · " + if (block.plan.isDeload(workout.week)) "Deload week" else "Target RIR ${rir ?: "-"}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    },
                    navigationIcon = { TextButton(onClick = onBack) { Text("Back") } },
                )
                LinearProgressIndicator(
                    progress = { if (allSets == 0) 0f else doneSets.toFloat() / allSets },
                    modifier = Modifier.fillMaxWidth().height(Sizes.progressBar).semantics { contentDescription = "$doneSets of $allSets sets done" },
                    color = statusColors.done,
                    trackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                    drawStopIndicator = {},
                    gapSize = 0.dp,
                )
            }
        },
        bottomBar = {
            Surface(color = MaterialTheme.colorScheme.surfaceContainer) {
                Row(
                    Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = Space.l, vertical = Space.s),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    // The rest timer starts when a set (or a superset round) is done. It shows only while it runs.
                    if (restLeft > 0) {
                        Text("Rest ${formatSeconds(restLeft)}", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
                        TextButton(onClick = { restLeft += 30 }) { Text("+30 s") }
                        TextButton(onClick = { restLeft = 0 }) { Text("Skip") }
                    } else {
                        Text("$doneSets of $allSets sets done", style = MaterialTheme.typography.bodyMedium)
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
        // A tap on a card header opens or closes that card. Saved, so a rotation keeps it.
        val current = workout.exercises.indexOfFirst { !it.allSetsDone }.let { if (it < 0) workout.exercises.lastIndex else it }
        var toggled by rememberSaveable(stateSaver = ToggleSaver) { mutableStateOf(mapOf<Int, Boolean>()) }
        val questions = Workflow.questions(block)
        LazyColumn(
            Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(horizontal = Space.m, vertical = Space.m),
            verticalArrangement = Arrangement.spacedBy(Space.s),
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
                    questions = questions.filter { it.exerciseIndex in group },
                    skipped = skipped,
                    onOpenQuestion = { editing = it },
                    onToggle = { toggled = toggled + (key to !(toggled[key] ?: default)) },
                    onChange = onChange,
                    onSetDone = { i, s ->
                        val after = Workflow.completeSet(block, i, s)
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

    // Feedback questions open in a bottom sheet at their trigger, one at a time.
    val asked = editing ?: Workflow.nextQuestion(block, skipped)
    if (asked != null) {
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ModalBottomSheet(
            onDismissRequest = {
                if (editing != null) editing = null else skipped = skipped + asked
            },
            sheetState = sheetState,
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        ) {
            QuestionSheet(block, asked) { answer ->
                onChange(answer)
                editing = null
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

/** The content of the feedback sheet: the question and its answers. */
@Composable
private fun QuestionSheet(block: Block, q: Question, onAnswer: ((Block) -> Block) -> Unit) {
    val w = block.active ?: return
    val entry = w.exercises[q.exerciseIndex]
    val exercise = ExerciseLibrary.byId.getValue(entry.exerciseId)
    val fb = w.feedback[q.muscle]
    val m = q.muscle.lower()
    Column(Modifier.fillMaxWidth().padding(start = Space.l, end = Space.l, bottom = Space.l), verticalArrangement = Arrangement.spacedBy(Space.m)) {
        Text(exercise.name, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        when (q.kind) {
            QuestionKind.SORENESS -> {
                Text("How sore was your $m after the last session?", style = MaterialTheme.typography.titleMedium)
                Options(Soreness.entries, fb?.soreness, ::sorenessLabel) { v -> onAnswer { Workflow.updateFeedback(it, q.muscle) { f -> f.copy(soreness = v) } } }
            }
            QuestionKind.JOINT_PAIN -> {
                Text("Did you have joint pain in this exercise?", style = MaterialTheme.typography.titleMedium)
                Options(JointPain.entries, entry.jointPain, ::jointPainLabel) { v -> onAnswer { Workflow.setJointPain(it, q.exerciseIndex, v) } }
            }
            QuestionKind.PUMP -> {
                Text("How was the pump in your $m?", style = MaterialTheme.typography.titleMedium)
                Options(Pump.entries, fb?.pump, ::pumpLabel) { v -> onAnswer { Workflow.updateFeedback(it, q.muscle) { f -> f.copy(pump = v) } } }
            }
            QuestionKind.WORKLOAD -> {
                Text("How was the work for your $m today?", style = MaterialTheme.typography.titleMedium)
                Options(Workload.entries, fb?.workload, ::workloadLabel) { v -> onAnswer { Workflow.updateFeedback(it, q.muscle) { f -> f.copy(workload = v) } } }
            }
        }
        Text(
            "Close this to answer later from the card.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** The short text of an answer, for the chip, or null if there is no answer. */
private fun answerText(block: Block, q: Question): String? {
    val w = block.active ?: return null
    val fb = w.feedback[q.muscle]
    val m = q.muscle.displayName
    return when (q.kind) {
        QuestionKind.SORENESS -> fb?.soreness?.let { "$m soreness: ${sorenessLabel(it).lowercase()}" }
        QuestionKind.JOINT_PAIN -> w.exercises[q.exerciseIndex].jointPain?.let { "Joint pain: ${jointPainLabel(it).lowercase()}" }
        QuestionKind.PUMP -> fb?.pump?.let { "$m pump: ${pumpLabel(it).lowercase()}" }
        QuestionKind.WORKLOAD -> fb?.workload?.let { "$m work: ${workloadLabel(it).lowercase()}" }
    }
}

private fun questionName(q: Question): String = when (q.kind) {
    QuestionKind.SORENESS -> "${q.muscle.displayName} soreness"
    QuestionKind.JOINT_PAIN -> "Joint pain"
    QuestionKind.PUMP -> "${q.muscle.displayName} pump"
    QuestionKind.WORKLOAD -> "${q.muscle.displayName} work"
}

/** One card for a single exercise or for a superset. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun GroupCard(
    block: Block,
    group: List<Int>,
    isLastGroup: Boolean,
    unit: WeightUnit,
    expanded: Boolean,
    questions: List<Question>,
    skipped: Set<Question>,
    onOpenQuestion: (Question) -> Unit,
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
    val firstSlot = entries.first().slotIndex

    Card(
        shape = RoundedCornerShape(Sizes.cardRadius),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        border = if (superset) BorderStroke(1.dp, MaterialTheme.colorScheme.primary) else null,
    ) {
        Column(Modifier.padding(Sizes.cardPadding), verticalArrangement = Arrangement.spacedBy(Space.s)) {
            // Header: tap to open or close.
            Row(Modifier.fillMaxWidth().heightIn(min = Sizes.touch).clickable(onClick = onToggle), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    if (superset) {
                        Text("SUPERSET", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                        Text(exercises.joinToString(" + ") { it.name }, style = MaterialTheme.typography.titleMedium)
                    } else {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(exercises[0].name, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f, fill = false))
                            Spacer(Modifier.width(Space.s))
                            MuscleTag(Workflow.muscleOf(block, entries[0]))
                        }
                    }
                    if (!expanded) {
                        Text(
                            when {
                                complete -> "Done · $done of $total sets"
                                done > 0 -> "$done of $total sets done"
                                superset -> "$total sets in rounds"
                                prescriptions[0].weightKg == null && prescriptions[0].targetReps == null -> "${prescriptions[0].sets} sets"
                                else -> "${prescriptions[0].sets} sets · ${suggestionText(prescriptions[0], exercises[0], unit)}"
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = if (complete) statusColors.done else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                if (!superset) {
                    ExerciseMenu(
                        canSwap = !entries[0].anySetDone,
                        canAdd = entries[0].sets.size < Workflow.MAX_SETS_IN_WORKOUT,
                        canRemove = entries[0].sets.size > 1 && entries[0].sets.any { !it.done },
                        linkLabel = if (!isLastGroup) "Superset with next" else null,
                        onSwap = { onSwap(entries[0].slotIndex) },
                        onAdd = { onChange { Workflow.addSet(it, group[0]) } },
                        onRemove = { onChange { Workflow.removeSet(it, group[0]) } },
                        onLink = { onChange { Workflow.linkWithNext(it, workout.dayIndex, entries[0].slotIndex) } },
                    )
                } else {
                    GroupMenu(onSplit = { onChange { Workflow.splitSuperset(it, workout.dayIndex, firstSlot) } })
                }
                Text(
                    if (expanded) "▴" else "▾",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (!expanded) return@Column

            // What to do, for each exercise.
            group.forEachIndexed { m, index ->
                if (superset) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("${letters[m]}  ${exercises[m].name}", style = MaterialTheme.typography.titleSmall)
                        Spacer(Modifier.width(Space.s))
                        MuscleTag(Workflow.muscleOf(block, entries[m]))
                        Spacer(Modifier.weight(1f))
                        ExerciseMenu(
                            canSwap = !entries[m].anySetDone,
                            canAdd = entries[m].sets.size < Workflow.MAX_SETS_IN_WORKOUT,
                            canRemove = entries[m].sets.size > 1 && entries[m].sets.any { !it.done },
                            linkLabel = null,
                            onSwap = { onSwap(entries[m].slotIndex) },
                            onAdd = { onChange { Workflow.addSet(it, index) } },
                            onRemove = { onChange { Workflow.removeSet(it, index) } },
                            onLink = {},
                        )
                    }
                }
                Text(
                    "${suggestionText(prescriptions[m], exercises[m], unit)} · ${repRangeText(exercises[m])}",
                    style = MaterialTheme.typography.bodyMedium,
                )
                prescriptions[m].note?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = statusColors.warning) }
            }

            // The set table. In a superset the order goes A1, B1, A2, B2, ...
            SetTableHeader(unit)
            val rounds = entries.maxOf { it.sets.size }
            for (r in 0 until rounds) {
                group.forEachIndexed { m, index ->
                    val set = entries[m].sets.getOrNull(r) ?: return@forEachIndexed
                    SetRow(
                        label = if (superset) "${letters[m]}${r + 1}" else "${r + 1}",
                        key = "${entries[m].exerciseId}-$r-$unit",
                        exercise = exercises[m],
                        unit = unit,
                        weight = set.weightIn(unit),
                        reps = set.reps,
                        rir = prescriptions[m].targetRir,
                        done = set.done,
                        canComplete = Workflow.canComplete(set, exercises[m]),
                        onWeight = { v -> onChange { Workflow.updateSet(it, index, r) { e -> e.withWeight(v, unit) } } },
                        onReps = { v -> onChange { Workflow.updateSet(it, index, r) { e -> e.copy(reps = v) } } },
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

            // Answers as small chips. A question that the user closed shows as a chip to answer later.
            if (questions.isNotEmpty()) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(Space.xs)) {
                    questions.forEach { q ->
                        val answer = answerText(block, q)
                        when {
                            answer != null -> SmallChip("✓ $answer") { onOpenQuestion(q) }
                            q in skipped -> SmallChip("Answer: ${questionName(q).lowercase()}", warning = true) { onOpenQuestion(q) }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SmallChip(text: String, warning: Boolean = false, onClick: () -> Unit) {
    AssistChip(
        onClick = onClick,
        label = { Text(text, style = MaterialTheme.typography.labelMedium) },
        colors = AssistChipDefaults.assistChipColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            labelColor = if (warning) statusColors.warning else MaterialTheme.colorScheme.onSurface,
        ),
        border = null,
    )
}

@Composable
private fun MuscleTag(muscle: Muscle) {
    Text(
        muscle.displayName.uppercase(),
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier
            .background(MaterialTheme.colorScheme.surfaceContainerHighest, RoundedCornerShape(4.dp))
            .padding(horizontal = 6.dp, vertical = 2.dp),
    )
}

/** The ⋮ menu of one exercise. */
@Composable
private fun ExerciseMenu(
    canSwap: Boolean,
    canAdd: Boolean,
    canRemove: Boolean,
    linkLabel: String?,
    onSwap: () -> Unit,
    onAdd: () -> Unit,
    onRemove: () -> Unit,
    onLink: () -> Unit,
) {
    var open by remember { mutableStateOf(false) }
    Box {
        MenuButton { open = true }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            DropdownMenuItem(text = { Text("Add a set") }, enabled = canAdd, onClick = { open = false; onAdd() })
            DropdownMenuItem(text = { Text("Remove a set") }, enabled = canRemove, onClick = { open = false; onRemove() })
            DropdownMenuItem(text = { Text("Swap exercise") }, enabled = canSwap, onClick = { open = false; onSwap() })
            if (linkLabel != null) DropdownMenuItem(text = { Text(linkLabel) }, onClick = { open = false; onLink() })
        }
    }
}

/** The ⋮ menu of a superset. */
@Composable
private fun GroupMenu(onSplit: () -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        MenuButton(description = "Superset menu") { open = true }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            DropdownMenuItem(text = { Text("Split superset") }, onClick = { open = false; onSplit() })
        }
    }
}

@Composable
private fun MenuButton(description: String = "Exercise menu", onClick: () -> Unit) {
    Box(
        Modifier.size(Sizes.touch).clickable(onClick = onClick).semantics { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) {
        Text("⋮", style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

private val SetCol = 36.dp
private val RirCol = 44.dp

@Composable
private fun SetTableHeader(unit: WeightUnit) {
    val style = MaterialTheme.typography.labelSmall
    val color = MaterialTheme.colorScheme.onSurfaceVariant
    Row(Modifier.fillMaxWidth().padding(top = Space.xs), verticalAlignment = Alignment.CenterVertically) {
        Text("SET", style = style, color = color, modifier = Modifier.width(SetCol))
        Text(unit.label.uppercase(), style = style, color = color, textAlign = TextAlign.Center, modifier = Modifier.weight(1f))
        Text("REPS", style = style, color = color, textAlign = TextAlign.Center, modifier = Modifier.weight(1f))
        Text("RIR", style = style, color = color, textAlign = TextAlign.Center, modifier = Modifier.width(RirCol))
        Spacer(Modifier.width(Sizes.touch))
    }
    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
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
    rir: Int,
    done: Boolean,
    /** True when the set has the values that it needs to be marked done. */
    canComplete: Boolean,
    /** A weight in [unit]. */
    onWeight: (Double?) -> Unit,
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

    Row(Modifier.fillMaxWidth().heightIn(min = Sizes.setRow), verticalAlignment = Alignment.CenterVertically) {
        Text(
            label,
            modifier = Modifier.width(SetCol),
            style = MaterialTheme.typography.labelLarge,
            color = if (done) statusColors.done else MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
            if (usesWeight) {
                NumberField(
                    value = weightText,
                    description = "Weight for set $label in ${unit.label}",
                    enabled = !done,
                    keyboardType = KeyboardType.Decimal,
                ) { t ->
                    val clean = t.replace(',', '.')
                    val v = clean.toDoubleOrNull()
                    val max = Units.toUnit(Workflow.MAX_WEIGHT_KG, unit)
                    // At most 1 decimal.
                    if (clean.isEmpty() || (v != null && v <= max && clean.substringAfter('.', "").length <= 1)) {
                        weightText = clean
                        onWeight(v)
                    }
                }
            } else {
                Text("—", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
            NumberField(
                value = repsText,
                description = "Reps for set $label",
                enabled = !done,
                keyboardType = KeyboardType.Number,
            ) { t ->
                if (t.isEmpty() || (t.all(Char::isDigit) && (t.toIntOrNull() ?: Int.MAX_VALUE) <= Workflow.MAX_REPS)) {
                    repsText = t
                    onReps(t.toIntOrNull())
                }
            }
        }
        Text(
            rirText(rir).removePrefix("RIR "),
            modifier = Modifier.width(RirCol),
            textAlign = TextAlign.Center,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        DoneButton(done = done, enabled = done || canComplete, label = label, onDone = onDone)
    }
}

/** A compact filled number field: 40 dp high, numbers in the center, in a 48 dp touch area. */
@Composable
private fun NumberField(value: String, description: String, enabled: Boolean, keyboardType: KeyboardType, onValue: (String) -> Unit) {
    val colors = MaterialTheme.colorScheme
    BasicTextField(
        value = value,
        onValueChange = onValue,
        enabled = enabled,
        singleLine = true,
        textStyle = MaterialTheme.typography.bodyLarge.copy(
            textAlign = TextAlign.Center,
            fontWeight = FontWeight.Medium,
            color = if (enabled) colors.onSurface else colors.onSurfaceVariant,
        ),
        cursorBrush = SolidColor(colors.primary),
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
        modifier = Modifier
            .width(Sizes.fieldWidth)
            .heightIn(min = Sizes.touch)
            .semantics { contentDescription = description },
        decorationBox = { inner ->
            Box(Modifier.heightIn(min = Sizes.touch), contentAlignment = Alignment.Center) {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(Sizes.field)
                        .background(if (enabled) colors.surfaceContainerHighest else colors.surfaceContainerHigh, RoundedCornerShape(Sizes.fieldRadius)),
                    contentAlignment = Alignment.Center,
                ) {
                    if (value.isEmpty()) Text("–", color = colors.onSurfaceVariant, style = MaterialTheme.typography.bodyLarge)
                    inner()
                }
            }
        },
    )
}

/** A round done button in a 48 dp touch target. It acts as a checkbox for accessibility. */
@Composable
private fun DoneButton(done: Boolean, enabled: Boolean, label: String, onDone: (Boolean) -> Unit) {
    val colors = MaterialTheme.colorScheme
    Box(
        Modifier
            .size(Sizes.touch)
            .toggleable(value = done, enabled = enabled, role = Role.Checkbox, onValueChange = onDone)
            .semantics { contentDescription = "Set $label done" },
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier
                .size(Sizes.doneButton)
                .then(
                    if (done) Modifier.background(statusColors.done, CircleShape)
                    else Modifier.border(2.dp, if (enabled) colors.onSurfaceVariant else colors.outlineVariant, CircleShape),
                ),
            contentAlignment = Alignment.Center,
        ) {
            if (done) Text("✓", color = statusColors.onDone, style = MaterialTheme.typography.titleSmall)
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun <T> Options(options: List<T>, selected: T?, label: (T) -> String, onSelect: (T) -> Unit) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(Space.s), verticalArrangement = Arrangement.spacedBy(Space.xs)) {
        options.forEach { option ->
            FilterChip(
                selected = option == selected,
                onClick = { onSelect(option) },
                label = { Text(label(option), style = MaterialTheme.typography.labelLarge) },
                modifier = Modifier.heightIn(min = Sizes.touch),
                colors = FilterChipDefaults.filterChipColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                    labelColor = MaterialTheme.colorScheme.onSurface,
                    selectedContainerColor = MaterialTheme.colorScheme.primary,
                    selectedLabelColor = MaterialTheme.colorScheme.onPrimary,
                ),
                border = null,
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
