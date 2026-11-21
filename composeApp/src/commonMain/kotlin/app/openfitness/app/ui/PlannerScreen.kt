package app.openfitness.app.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.openfitness.app.data.Planner
import app.openfitness.app.data.Workflow
import app.openfitness.core.library.ExerciseLibrary
import app.openfitness.core.model.MesocyclePlan
import app.openfitness.core.model.groupOf
import app.openfitness.core.model.groups

/**
 * The planner: the user makes or changes a plan. Each change goes to [onChange]
 * at once, so the draft is saved while the user works.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlannerScreen(
    plan: MesocyclePlan,
    onChange: ((MesocyclePlan) -> MesocyclePlan) -> Unit,
    onAddExercise: (day: Int) -> Unit,
    onSave: () -> Unit,
    onStart: () -> Unit,
    onBack: () -> Unit,
) {
    val problems = Planner.problems(plan)
    val slotSets = Planner.startSetsPerSlot(plan)

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Plan a block") },
                navigationIcon = { TextButton(onClick = onBack) { Text("Back") } },
            )
        },
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            OutlinedTextField(
                value = plan.name,
                onValueChange = { v -> onChange { Planner.rename(it, v) } },
                label = { Text("Plan name") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    SectionTitle("Hard weeks")
                    Text("Then 1 deload week.", style = MaterialTheme.typography.bodySmall)
                }
                Stepper(plan.accumulationWeeks, { n -> onChange { Planner.setHardWeeks(it, n) } }, Workflow.MIN_HARD_WEEKS, Workflow.MAX_HARD_WEEKS)
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    SectionTitle("Training days")
                    Text("Each week.", style = MaterialTheme.typography.bodySmall)
                }
                Stepper(plan.days.size, { n -> onChange { Planner.setDayCount(it, n) } }, Planner.MIN_DAYS, Planner.MAX_DAYS)
            }

            plan.days.forEachIndexed { d, day ->
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)) {
                    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = day.name,
                            onValueChange = { v -> onChange { Planner.renameDay(it, d, v) } },
                            label = { Text("Day ${d + 1} name") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        if (day.slots.isEmpty()) {
                            Text("No exercises yet.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error)
                        }
                        val groups = day.groups()
                        groups.forEach { group ->
                            val content = @Composable {
                                Column(Modifier.padding(if (group.size > 1) 8.dp else 0.dp)) {
                                    if (group.size > 1) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Text("Superset", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary, modifier = Modifier.weight(1f))
                                            TextButton(onClick = { onChange { Planner.splitSuperset(it, d, group.first()) } }) { Text("Split") }
                                        }
                                    }
                                    group.forEach { s ->
                                        SlotRow(
                                            plan = plan,
                                            day = d,
                                            slot = s,
                                            letter = if (group.size > 1) ('A' + group.indexOf(s)).toString() else null,
                                            weekOneSets = slotSets.getOrNull(d)?.getOrNull(s),
                                            onChange = onChange,
                                        )
                                    }
                                }
                            }
                            if (group.size > 1) {
                                OutlinedCard(border = BorderStroke(2.dp, MaterialTheme.colorScheme.primary)) { content() }
                            } else {
                                content()
                            }
                        }
                        OutlinedButton(onClick = { onAddExercise(d) }, modifier = Modifier.fillMaxWidth()) { Text("Add exercise") }
                    }
                }
            }

            MuscleVolumeCard(plan, onChange)

            if (problems.isNotEmpty()) {
                problems.forEach { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium) }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedButton(onClick = onSave, enabled = problems.isEmpty(), modifier = Modifier.weight(1f)) { Text("Save plan") }
                Button(onClick = onStart, enabled = problems.isEmpty(), modifier = Modifier.weight(1f)) { Text("Start block") }
            }
        }
    }
}

/** One exercise of a day. A tap shows the actions for it. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SlotRow(
    plan: MesocyclePlan,
    day: Int,
    slot: Int,
    letter: String?,
    weekOneSets: Int?,
    onChange: ((MesocyclePlan) -> MesocyclePlan) -> Unit,
) {
    val d = plan.days[day]
    val s = d.slots[slot]
    val ex = ExerciseLibrary.byId.getValue(s.exerciseId)
    val last = d.slots.lastIndex
    var open by remember(day, s.exerciseId) { mutableStateOf(false) }
    val linkedToNext = slot < last && d.groupOf(slot).contains(slot + 1)
    Column(Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth().clickable { open = !open }.padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text((letter?.let { "$it  " } ?: "") + ex.name, style = MaterialTheme.typography.titleSmall)
                Text(
                    "${s.muscle.displayName} · ${weekOneSets ?: "-"} sets in week 1" + if (s.startSets != null) " (set by you)" else "",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text(if (open) "▴" else "▾", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (open) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Start sets", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                if (s.startSets == null) {
                    Text("Auto", style = MaterialTheme.typography.bodyMedium)
                    TextButton(onClick = { onChange { Planner.setSlotStartSets(it, day, slot, weekOneSets ?: 2) } }) { Text("Change") }
                } else {
                    Stepper(s.startSets!!, { n -> onChange { Planner.setSlotStartSets(it, day, slot, n) } }, 1, Planner.MAX_START_SETS)
                    TextButton(onClick = { onChange { Planner.setSlotStartSets(it, day, slot, null) } }) { Text("Auto") }
                }
            }
            FlowRow {
                TextButton(onClick = { onChange { Planner.moveExercise(it, day, slot, -1) } }, enabled = slot > 0) { Text("Move up") }
                TextButton(onClick = { onChange { Planner.moveExercise(it, day, slot, 1) } }, enabled = slot < last) { Text("Move down") }
                if (slot < last && !linkedToNext) {
                    TextButton(onClick = { onChange { Planner.linkWithNext(it, day, slot) } }) { Text("Superset with next") }
                }
                TextButton(onClick = { onChange { Planner.removeExercise(it, day, slot) } }) {
                    Text("Remove", color = MaterialTheme.colorScheme.error)
                }
            }
        }
    }
}

@Composable
private fun MuscleVolumeCard(plan: MesocyclePlan, onChange: ((MesocyclePlan) -> MesocyclePlan) -> Unit) {
    val muscles = Planner.musclesInPlan(plan)
    if (muscles.isEmpty()) return
    val weekOne = Planner.startSetsPerMuscle(plan)
    var open by rememberSaveable { mutableStateOf(false) }
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(Modifier.fillMaxWidth().clickable { open = !open }, verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    SectionTitle("Weekly sets for each muscle")
                    Text(
                        "Week 1: ${weekOne.values.sum()} sets for ${weekOne.size} muscles",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Text(if (open) "▴" else "▾", style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (!open) return@Column
            Text(
                "Week 1 shows the sets that this plan gives. Start is the target for week 1. Max is the highest weekly sets that the app will give.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            muscles.forEach { m ->
                val v = Planner.volume(plan, m)
                HorizontalDivider()
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(m.displayName, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                    Text("Week 1: ${weekOne[m] ?: 0} sets", fontWeight = FontWeight.SemiBold)
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Start", modifier = Modifier.weight(1f))
                    Stepper(v.startSets, { n -> onChange { Planner.setMuscleStartSets(it, m, n) } }, 0, 40)
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Max", modifier = Modifier.weight(1f))
                    Stepper(v.maxSets, { n -> onChange { Planner.setMuscleMaxSets(it, m, n) } }, 1, 40)
                }
            }
        }
    }
}
