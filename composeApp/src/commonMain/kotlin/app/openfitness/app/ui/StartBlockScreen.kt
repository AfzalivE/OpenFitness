package app.openfitness.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.openfitness.app.data.Workflow
import app.openfitness.core.library.Templates
import app.openfitness.core.model.MesocyclePlan

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StartBlockScreen(
    customPlans: List<MesocyclePlan>,
    draft: MesocyclePlan?,
    onContinueDraft: () -> Unit,
    onDeleteDraft: () -> Unit,
    onStart: (MesocyclePlan, Int) -> Unit,
    onNewPlan: () -> Unit,
    onEditPlan: (plan: MesocyclePlan, isTemplate: Boolean) -> Unit,
    onDeletePlan: (MesocyclePlan) -> Unit,
    onSettings: () -> Unit,
) {
    val plans = Templates.all.map { it to true } + customPlans.map { it to false }
    // Null until the user selects a plan. Then the default is the newest plan that the user saved.
    var selectedName by rememberSaveable { mutableStateOf<String?>(null) }
    val selected = plans.firstOrNull { it.first.name == selectedName }?.first
        ?: customPlans.lastOrNull()
        ?: Templates.all.first()
    var hardWeeks by remember(selected.name) { mutableIntStateOf(selected.accumulationWeeks) }
    // A plan to edit, when the user must first say yes to replacing the unsaved plan.
    var pendingEdit by remember { mutableStateOf<Pair<MesocyclePlan, Boolean>?>(null) }
    val edit: (MesocyclePlan, Boolean) -> Unit = { plan, isTemplate ->
        if (draft != null) pendingEdit = plan to isTemplate else onEditPlan(plan, isTemplate)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Start a block") },
                actions = { TextButton(onClick = onSettings) { Text("Settings") } },
            )
        },
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                "A block has hard weeks and then one deload week. Each week, the target RIR goes down.",
                style = MaterialTheme.typography.bodyLarge,
            )

            if (draft != null) {
                // A plan that the user did not save yet. It stays when the app closes.
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer)) {
                    Column(Modifier.fillMaxWidth().padding(16.dp)) {
                        Text("Plan not saved: ${draft.name.ifBlank { "No name" }}", style = MaterialTheme.typography.titleMedium)
                        Text("You did not save this plan yet.", style = MaterialTheme.typography.bodyMedium)
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                            TextButton(onClick = onDeleteDraft) { Text("Delete") }
                            TextButton(onClick = onContinueDraft) { Text("Continue") }
                        }
                    }
                }
            }

            SectionTitle("1. Select a plan")
            if (customPlans.isNotEmpty()) {
                Text("My plans", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                customPlans.forEach { plan ->
                    PlanCard(plan, plan.name == selected.name, { selectedName = plan.name }) {
                        TextButton(onClick = { edit(plan, false) }) { Text("Edit") }
                        TextButton(onClick = { onDeletePlan(plan) }) { Text("Delete") }
                    }
                }
                Text("Templates", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
            }
            Templates.all.forEach { plan ->
                PlanCard(plan, plan.name == selected.name, { selectedName = plan.name }) {
                    TextButton(onClick = { edit(plan, true) }) { Text("Copy and edit") }
                }
            }
            if (draft == null) {
                OutlinedButton(onClick = onNewPlan, modifier = Modifier.fillMaxWidth()) { Text("Make a new plan") }
            }

            SectionTitle("2. Set the number of hard weeks")
            Stepper(
                value = hardWeeks,
                onChange = { hardWeeks = it },
                min = Workflow.MIN_HARD_WEEKS,
                max = Workflow.MAX_HARD_WEEKS,
            )
            Text(
                "You can set ${Workflow.MIN_HARD_WEEKS} to ${Workflow.MAX_HARD_WEEKS} hard weeks. " +
                    "Week ${hardWeeks + 1} is the deload week. The block has ${hardWeeks + 1} weeks.",
                style = MaterialTheme.typography.bodyMedium,
            )

            Button(onClick = { onStart(selected, hardWeeks) }, modifier = Modifier.fillMaxWidth()) { Text("Start block") }
        }
    }

    pendingEdit?.let { (plan, isTemplate) ->
        ReplaceDraftDialog(
            draftName = draft?.name?.ifBlank { "No name" } ?: "",
            onReplace = { pendingEdit = null; onEditPlan(plan, isTemplate) },
            onCancel = { pendingEdit = null },
        )
    }
}

@Composable
private fun ReplaceDraftDialog(draftName: String, onReplace: () -> Unit, onCancel: () -> Unit) {
    AlertDialog(
        onDismissRequest = onCancel,
        title = { Text("Replace the plan that is not saved?") },
        text = { Text("You did not save \"$draftName\". If you continue, the app deletes it.") },
        confirmButton = { TextButton(onClick = onReplace) { Text("Replace") } },
        dismissButton = { TextButton(onClick = onCancel) { Text("Cancel") } },
    )
}

@Composable
private fun PlanCard(
    plan: MesocyclePlan,
    selected: Boolean,
    onSelect: () -> Unit,
    actions: @Composable () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth().selectable(selected = selected, role = Role.RadioButton, onClick = onSelect),
        colors = CardDefaults.cardColors(
            containerColor = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainer,
        ),
    ) {
        Row(Modifier.padding(start = 12.dp, top = 12.dp, end = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            RadioButton(selected = selected, onClick = null)
            Spacer(Modifier.width(12.dp))
            Column {
                Text(plan.name, style = MaterialTheme.typography.titleMedium)
                Text(
                    "${plan.days.size} days each week: ${plan.days.joinToString { it.name }}",
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
        Row(Modifier.fillMaxWidth().padding(end = 4.dp), horizontalArrangement = Arrangement.End) { actions() }
    }
}

/** A number with − and + buttons. */
@Composable
fun Stepper(value: Int, onChange: (Int) -> Unit, min: Int, max: Int, modifier: Modifier = Modifier, text: String = "$value") {
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        FilledTonalIconButton(onClick = { onChange(value - 1) }, enabled = value > min) {
            Text("−", style = MaterialTheme.typography.titleLarge)
        }
        Text(text, style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(horizontal = 16.dp))
        FilledTonalIconButton(onClick = { onChange(value + 1) }, enabled = value < max) {
            Text("+", style = MaterialTheme.typography.titleLarge)
        }
    }
}

@Composable
fun SectionTitle(text: String, modifier: Modifier = Modifier) {
    Text(text, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, modifier = modifier)
}
