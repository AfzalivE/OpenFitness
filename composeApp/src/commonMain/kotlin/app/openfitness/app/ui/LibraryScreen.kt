package app.openfitness.app.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.openfitness.core.library.ExerciseLibrary
import app.openfitness.core.model.Equipment
import app.openfitness.core.model.Exercise
import app.openfitness.core.model.Muscle

/** What the library screen is for. */
sealed interface LibraryMode {
    /** Look at the exercises. */
    data object Browse : LibraryMode

    /** Swap the exercise in a slot. Only exercises for [muscle] can go in. */
    data class Swap(val dayName: String, val muscle: Muscle, val currentId: String, val dayIds: Set<String>) : LibraryMode

    /** Add an exercise to a day in the planner. */
    data class Add(val dayName: String, val dayIds: Set<String>) : LibraryMode
}

/** Shows the exercise library by muscle. The user can filter by muscle (not in swap mode). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibraryScreen(
    mode: LibraryMode,
    onPick: (String) -> Unit,
    onBack: () -> Unit,
) {
    var filter by rememberSaveable { mutableStateOf<Muscle?>(null) }
    val muscleFilter = if (mode is LibraryMode.Swap) mode.muscle else filter
    val groups: Map<Muscle, List<Exercise>> = Muscle.entries
        .filter { muscleFilter == null || it == muscleFilter }
        .associateWith { m -> ExerciseLibrary.all.filter { it.primary == m } }
        .filterValues { it.isNotEmpty() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        when (mode) {
                            LibraryMode.Browse -> "Exercises"
                            is LibraryMode.Swap -> "Swap exercise"
                            is LibraryMode.Add -> "Add exercise"
                        },
                    )
                },
                navigationIcon = { TextButton(onClick = onBack) { Text("Back") } },
            )
        },
    ) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding)) {
            item {
                Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                    when (mode) {
                        is LibraryMode.Swap -> Text(
                            "Select a ${mode.muscle.displayName.lowercase()} exercise for ${mode.dayName}. " +
                                "The change applies to this day and to the next weeks.",
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        is LibraryMode.Add -> Text("Select an exercise to add to ${mode.dayName}.", style = MaterialTheme.typography.bodyMedium)
                        LibraryMode.Browse -> Unit
                    }
                    if (mode !is LibraryMode.Swap) {
                        Row(Modifier.horizontalScroll(rememberScrollState()).padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            FilterChip(selected = filter == null, onClick = { filter = null }, label = { Text("All") })
                            Muscle.entries.filter { m -> ExerciseLibrary.all.any { it.primary == m } }.forEach { m ->
                                FilterChip(selected = filter == m, onClick = { filter = m }, label = { Text(m.displayName) })
                            }
                        }
                    }
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
                    val current = mode is LibraryMode.Swap && mode.currentId == ex.id
                    val usedOnDay = !current && when (mode) {
                        is LibraryMode.Swap -> ex.id in mode.dayIds
                        is LibraryMode.Add -> ex.id in mode.dayIds
                        LibraryMode.Browse -> false
                    }
                    val enabled = mode != LibraryMode.Browse && !current && !usedOnDay
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
                            mode is LibraryMode.Add -> ({ Text("Add", color = MaterialTheme.colorScheme.primary) })
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
