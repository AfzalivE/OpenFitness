package app.openfitness.app.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.openfitness.app.data.Block
import app.openfitness.app.data.Workflow
import app.openfitness.core.library.ExerciseLibrary
import app.openfitness.core.model.Equipment
import app.openfitness.core.model.Exercise
import app.openfitness.core.model.Muscle

/**
 * Shows the exercise library by muscle. When [dayIndex] and [slotIndex] are set,
 * the screen shows only the exercises that can go in that slot, and a tap swaps it.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibraryScreen(
    block: Block?,
    dayIndex: Int?,
    slotIndex: Int?,
    onPick: (String) -> Unit,
    onBack: () -> Unit,
) {
    val swap = block != null && dayIndex != null && slotIndex != null
    val slot = if (swap) block.plan.days[dayIndex].slots[slotIndex] else null
    val dayIds = if (swap) block.plan.days[dayIndex].slots.map { it.exerciseId }.toSet() else emptySet()
    val exercises = if (swap) Workflow.swapOptions(block, dayIndex, slotIndex) else ExerciseLibrary.all
    val groups: Map<Muscle, List<Exercise>> = Muscle.entries
        .associateWith { m -> exercises.filter { it.primary == m } }
        .filterValues { it.isNotEmpty() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (swap) "Swap exercise" else "Exercises") },
                navigationIcon = { TextButton(onClick = onBack) { Text("Back") } },
            )
        },
    ) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding)) {
            if (slot != null) {
                item {
                    Text(
                        "Select a ${slot.muscle.displayName.lowercase()} exercise for ${block!!.plan.days[dayIndex!!].name}. " +
                            "The change applies to this day and to the next weeks.",
                        modifier = Modifier.padding(16.dp),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
            groups.forEach { (muscle, list) ->
                item(key = "h-$muscle") {
                    Text(
                        muscle.displayName,
                        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 4.dp),
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
                items(list, key = { it.id }) { ex ->
                    val current = slot?.exerciseId == ex.id
                    val usedOnDay = swap && !current && ex.id in dayIds
                    val enabled = swap && !current && !usedOnDay
                    ListItem(
                        headlineContent = { Text(ex.name) },
                        supportingContent = {
                            Column {
                                Text("${equipmentLabel(ex.equipment)} · ${repRangeText(ex)}")
                                if (ex.secondary.isNotEmpty()) {
                                    Text("Also: ${ex.secondary.joinToString { it.displayName.lowercase() }}")
                                }
                            }
                        },
                        trailingContent = when {
                            current -> ({ Text("Now", color = MaterialTheme.colorScheme.primary) })
                            usedOnDay -> ({ Text("On this day") })
                            else -> null
                        },
                        colors = ListItemDefaults.colors(
                            containerColor = if (current) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
                        ),
                        modifier = if (enabled) Modifier.clickable { onPick(ex.id) } else Modifier,
                    )
                    HorizontalDivider()
                }
            }
        }
    }
}

fun equipmentLabel(e: Equipment) = when (e) {
    Equipment.BARBELL -> "Barbell"
    Equipment.DUMBBELL -> "Dumbbell"
    Equipment.MACHINE -> "Machine"
    Equipment.CABLE -> "Cable"
    Equipment.BODYWEIGHT -> "Bodyweight"
    Equipment.SMITH_MACHINE -> "Smith machine"
    Equipment.BAND -> "Band"
}
