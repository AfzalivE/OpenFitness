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
import androidx.compose.foundation.layout.heightIn
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
            Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(Space.l),
            verticalArrangement = Arrangement.spacedBy(Space.l),
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

            Text("WEEKS", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
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

    Card(
        shape = RoundedCornerShape(Sizes.cardRadius),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
    ) {
        Column(Modifier.fillMaxWidth().padding(Sizes.cardPadding), verticalArrangement = Arrangement.spacedBy(Space.xs)) {
            Text(
                "WEEK ${week + 1} OF ${plan.totalWeeks}",
                style = MaterialTheme.typography.labelMedium,
                color = if (deload) statusColors.deload else MaterialTheme.colorScheme.primary,
            )
            Text(
                if (deload) "Deload week" else "Target RIR $rir",
                style = MaterialTheme.typography.headlineSmall,
            )
            Text(
                if (deload) "Use light effort and fewer sets." else "Stop each set with $rir reps in reserve.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (nextDay != null) {
                Button(onClick = onWorkout, modifier = Modifier.fillMaxWidth().padding(top = Space.s).heightIn(min = Sizes.touch)) {
                    Text(if (active != null) "Continue workout: ${plan.days[nextDay].name}" else "Start workout: ${plan.days[nextDay].name}")
                }
            }
        }
    }
}

@Composable
private fun WeekGrid(block: Block) {
    val plan = block.plan
    val cell = Sizes.gridCell
    val gap = Space.xs
    val status = statusColors
    var showAll by rememberSaveable { mutableStateOf(false) }
    val lastShown = if (showAll) plan.totalWeeks - 1 else block.currentWeek
    val hidden = plan.totalWeeks - 1 - block.currentWeek
    val labelWidth = 64.dp
    Card(shape = RoundedCornerShape(Sizes.cardRadius), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)) {
        Column(Modifier.padding(Sizes.cardPadding).horizontalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(gap)) {
            Row(horizontalArrangement = Arrangement.spacedBy(gap)) {
                Box(Modifier.width(labelWidth))
                plan.days.forEach { day ->
                    Text(
                        day.name,
                        modifier = Modifier.width(cell * 1.6f),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        maxLines = 1,
                    )
                }
            }
            for (w in 0..lastShown) {
                val isCurrent = w == block.currentWeek
                val deload = plan.isDeload(w)
                val rir = RirSchedule.targetRir(w, plan.accumulationWeeks)
                Row(horizontalArrangement = Arrangement.spacedBy(gap), verticalAlignment = Alignment.CenterVertically) {
                    Row(Modifier.width(labelWidth), verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            "W${w + 1}",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Normal,
                            color = if (isCurrent) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(
                            if (deload) " DL" else " $rir",
                            style = MaterialTheme.typography.labelSmall,
                            color = if (deload) status.deload else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    plan.days.indices.forEach { d ->
                        val done = block.isDayDone(w, d)
                        val inProgress = block.active?.let { it.week == w && it.dayIndex == d } == true
                        val next = isCurrent && !inProgress && d == block.nextDayIndex && block.active == null
                        val shape = RoundedCornerShape(6.dp)
                        val (bg, fg, label) = when {
                            done -> Triple(status.done, status.onDone, "✓")
                            inProgress -> Triple(status.current, status.onCurrent, "Now")
                            next -> Triple(status.current.copy(alpha = 0.18f), status.current, "Next")
                            deload -> Triple(status.deload.copy(alpha = 0.18f), status.deload, "")
                            else -> Triple(status.future, MaterialTheme.colorScheme.onSurfaceVariant, "")
                        }
                        Box(
                            Modifier.size(cell * 1.6f, cell)
                                .background(bg, shape)
                                .then(if (next) Modifier.border(1.dp, status.current, shape) else Modifier),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(label, style = MaterialTheme.typography.labelMedium, color = fg)
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
    Row(horizontalArrangement = Arrangement.spacedBy(Space.m), verticalAlignment = Alignment.CenterVertically) {
        Legend(status.done, "Done")
        Legend(status.current, "Next")
        Legend(status.deload, "Deload")
    }
}

@Composable
private fun Legend(color: Color, text: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(10.dp).background(color, RoundedCornerShape(3.dp)))
        Text(text, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(start = Space.xs))
    }
}

