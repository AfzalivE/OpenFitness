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
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
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
fun StartBlockScreen(onStart: (MesocyclePlan, Int) -> Unit) {
    var templateIndex by remember { mutableIntStateOf(0) }
    var hardWeeks by remember { mutableIntStateOf(4) }

    Scaffold(topBar = { TopAppBar(title = { Text("Start a block") }) }) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                "A block has hard weeks and then one deload week. Each week, the target RIR goes down.",
                style = MaterialTheme.typography.bodyLarge,
            )

            SectionTitle("1. Select a plan")
            Templates.all.forEachIndexed { i, plan ->
                val selected = i == templateIndex
                Card(
                    modifier = Modifier.fillMaxWidth()
                        .selectable(selected = selected, role = Role.RadioButton) { templateIndex = i },
                    colors = CardDefaults.cardColors(
                        containerColor = if (selected) MaterialTheme.colorScheme.primaryContainer
                        else MaterialTheme.colorScheme.surfaceContainer,
                    ),
                ) {
                    Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
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
                }
            }

            SectionTitle("2. Set the number of hard weeks")
            Row(verticalAlignment = Alignment.CenterVertically) {
                FilledTonalIconButton(
                    onClick = { hardWeeks-- },
                    enabled = hardWeeks > Workflow.MIN_HARD_WEEKS,
                ) { Text("−", style = MaterialTheme.typography.titleLarge) }
                Text(
                    "$hardWeeks",
                    style = MaterialTheme.typography.headlineMedium,
                    modifier = Modifier.padding(horizontal = 24.dp),
                )
                FilledTonalIconButton(
                    onClick = { hardWeeks++ },
                    enabled = hardWeeks < Workflow.MAX_HARD_WEEKS,
                ) { Text("+", style = MaterialTheme.typography.titleLarge) }
            }
            Text(
                "You can set ${Workflow.MIN_HARD_WEEKS} to ${Workflow.MAX_HARD_WEEKS} hard weeks. " +
                    "Week ${hardWeeks + 1} is the deload week. The block has ${hardWeeks + 1} weeks.",
                style = MaterialTheme.typography.bodyMedium,
            )

            Button(
                onClick = { onStart(Templates.all[templateIndex], hardWeeks) },
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Start block") }
        }
    }
}

@Composable
fun SectionTitle(text: String, modifier: Modifier = Modifier) {
    Text(text, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, modifier = modifier)
}
