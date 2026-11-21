package app.openfitness.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.openfitness.app.data.Block
import app.openfitness.core.progression.RirSchedule

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OverviewScreen(
    block: Block,
    onWorkout: () -> Unit,
    onLibrary: () -> Unit,
    onSettings: () -> Unit,
    onEndBlock: () -> Unit,
) {
    var confirmEnd by remember { mutableStateOf(false) }
    val plan = block.plan
    val week = block.currentWeek

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(plan.name) },
                actions = {
                    TextButton(onClick = onLibrary) { Text("Exercises") }
                    TextButton(onClick = onSettings) { Text("Settings") }
                },
            )
        },
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            if (block.isComplete) {
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Block complete", style = MaterialTheme.typography.headlineSmall)
                        Text("You did all ${plan.totalWeeks} weeks, with the deload week. Start a new block when you are ready.")
                        Button(onClick = onEndBlock) { Text("Start a new block") }
                    }
                }
            } else {
                CurrentWeekCard(block, onWorkout)
            }

            SectionTitle("Weeks")
            WeekGrid(block)

            if (!block.isComplete) {
                OutlinedButton(onClick = { confirmEnd = true }, modifier = Modifier.fillMaxWidth()) { Text("End block") }
            }
        }
    }

    if (confirmEnd) {
        AlertDialog(
            onDismissRequest = { confirmEnd = false },
            title = { Text("End this block?") },
            text = { Text("The app deletes the data of this block. You cannot undo this.") },
            confirmButton = {
                TextButton(onClick = { confirmEnd = false; onEndBlock() }) { Text("End block") }
            },
            dismissButton = { TextButton(onClick = { confirmEnd = false }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun CurrentWeekCard(block: Block, onWorkout: () -> Unit) {
    val plan = block.plan
    val week = block.currentWeek
    val rir = block.weeks[week].firstOrNull()?.firstOrNull()?.targetRir ?: RirSchedule.targetRir(week, plan.accumulationWeeks)
    val deload = plan.isDeload(week)
    val active = block.active
    val nextDay = active?.dayIndex ?: block.nextDayIndex

    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("Week ${week + 1} of ${plan.totalWeeks}", style = MaterialTheme.typography.headlineSmall)
            Text(
                if (deload) "Deload week. Use light effort and fewer sets." else "Target: $rir reps in reserve (RIR $rir).",
                style = MaterialTheme.typography.bodyLarge,
            )
            if (nextDay != null) {
                Button(onClick = onWorkout, modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
                    Text(if (active != null) "Continue workout: ${plan.days[nextDay].name}" else "Start workout: ${plan.days[nextDay].name}")
                }
            }
        }
    }
}

@Composable
private fun WeekGrid(block: Block) {
    val plan = block.plan
    val cell = 64.dp
    var showAll by rememberSaveable { mutableStateOf(false) }
    val lastShown = if (showAll) plan.totalWeeks - 1 else block.currentWeek
    val hidden = plan.totalWeeks - 1 - block.currentWeek
    Row(Modifier.horizontalScroll(rememberScrollState())) {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Box(Modifier.width(cell))
                plan.days.forEach { day ->
                    Text(
                        day.name,
                        modifier = Modifier.width(cell),
                        style = MaterialTheme.typography.labelMedium,
                        textAlign = TextAlign.Center,
                        maxLines = 1,
                    )
                }
            }
            for (w in 0..lastShown) {
                val isCurrent = w == block.currentWeek
                val deload = plan.isDeload(w)
                val rir = RirSchedule.targetRir(w, plan.accumulationWeeks)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.width(cell)) {
                        Text(
                            "Week ${w + 1}",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Normal,
                        )
                        Text(
                            if (deload) "Deload" else "RIR $rir",
                            style = MaterialTheme.typography.labelSmall,
                            color = if (deload) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    plan.days.indices.forEach { d ->
                        val done = block.isDayDone(w, d)
                        val inProgress = block.active?.let { it.week == w && it.dayIndex == d } == true
                        val next = isCurrent && !inProgress && d == block.nextDayIndex && block.active == null
                        val colors = MaterialTheme.colorScheme
                        val bg = when {
                            done -> colors.primary
                            inProgress || next -> colors.secondaryContainer
                            deload -> colors.tertiaryContainer.copy(alpha = 0.5f)
                            else -> colors.surfaceContainerHigh
                        }
                        val label = when {
                            done -> "Done"
                            inProgress -> "Now"
                            next -> "Next"
                            else -> ""
                        }
                        Box(
                            Modifier.size(cell, 40.dp)
                                .background(bg, RoundedCornerShape(8.dp))
                                .then(if (isCurrent) Modifier.border(1.dp, colors.outline, RoundedCornerShape(8.dp)) else Modifier),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                label,
                                style = MaterialTheme.typography.labelMedium,
                                color = if (done) colors.onPrimary else colors.onSecondaryContainer,
                            )
                        }
                    }
                }
            }
        }
    }
    if (hidden > 0) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                if (showAll) "Week ${plan.totalWeeks} is the deload week."
                else "$hidden more weeks. Week ${plan.totalWeeks} is the deload week.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f),
            )
            TextButton(onClick = { showAll = !showAll }) { Text(if (showAll) "Show fewer weeks" else "Show all weeks") }
        }
    }
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
        Legend(MaterialTheme.colorScheme.primary, "Done")
        Legend(MaterialTheme.colorScheme.secondaryContainer, "Next")
        if (lastShown == plan.totalWeeks - 1) {
            Legend(MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.5f), "Deload")
        }
    }
}

@Composable
private fun Legend(color: Color, text: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(12.dp).background(color, RoundedCornerShape(3.dp)))
        Text(text, style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(start = 4.dp))
    }
}

