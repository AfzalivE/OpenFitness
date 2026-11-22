package app.openfitness.app

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import app.openfitness.app.data.AppStore
import app.openfitness.app.data.Planner
import app.openfitness.app.data.Workflow
import app.openfitness.app.ui.LibraryMode
import app.openfitness.app.ui.LibraryScreen
import app.openfitness.app.ui.OpenFitnessTheme
import app.openfitness.app.ui.OverviewScreen
import app.openfitness.app.ui.PlannerScreen
import app.openfitness.app.ui.SettingsScreen
import app.openfitness.app.ui.StartBlockScreen
import app.openfitness.app.ui.WorkoutScreen
import app.openfitness.core.library.ExerciseLibrary
import app.openfitness.core.model.MesocyclePlan
import app.openfitness.core.units.Units
import kotlinx.serialization.Serializable

/** Screens of the app. */
object Routes {
    @Serializable data object Start
    @Serializable data object Overview
    @Serializable data object Workout
    @Serializable data object Planner
    @Serializable data object Settings

    /**
     * The exercise library.
     * With [swapDay] and [swapSlot], the user picks an exercise to swap in.
     * With [addDay], the user picks an exercise to add to that day of the planner draft.
     */
    @Serializable data class Library(val swapDay: Int = -1, val swapSlot: Int = -1, val addDay: Int = -1)
}

@Composable
fun App(store: AppStore) {
    OpenFitnessTheme {
        val data by store.state.collectAsState()
        val nav = rememberNavController()
        val start: Any = remember { if (store.state.value.block != null) Routes.Overview else Routes.Start }
        // After the app starts with a workout in progress, go to that workout.
        // The overview stays under it, so Back goes to the overview. Saved, so a rotation does not do it again.
        var openedWorkout by rememberSaveable { mutableStateOf(false) }
        LaunchedEffect(Unit) {
            if (!openedWorkout && store.state.value.block?.active != null) nav.navigate(Routes.Workout)
            openedWorkout = true
        }
        val unit = data.settings.unit
        val exercises = remember(unit) { Units.exercisesFor(unit, ExerciseLibrary.all) }

        fun startBlock(plan: MesocyclePlan, weeks: Int) {
            store.update { it.copy(block = Workflow.startBlock(plan, weeks), draft = null) }
            nav.navigate(Routes.Overview) { popUpTo(nav.graph.id) { inclusive = true } }
        }

        NavHost(navController = nav, startDestination = start) {
            composable<Routes.Start> {
                StartBlockScreen(
                    customPlans = data.customPlans,
                    draft = data.draft,
                    onContinueDraft = { nav.navigate(Routes.Planner) },
                    onDeleteDraft = { store.update { it.copy(draft = null) } },
                    onStart = ::startBlock,
                    onNewPlan = {
                        store.update { it.copy(draft = Planner.newPlan()) }
                        nav.navigate(Routes.Planner)
                    },
                    onEditPlan = { plan, isTemplate ->
                        // This replaces a draft that was not saved. The Start screen shows the draft, so the user knows.
                        store.update { it.copy(draft = if (isTemplate) Planner.fromTemplate(plan) else plan) }
                        nav.navigate(Routes.Planner)
                    },
                    onDeletePlan = { plan -> store.update { Planner.delete(it, plan.name) } },
                    onSettings = { nav.navigate(Routes.Settings) },
                )
            }
            composable<Routes.Planner> {
                val draft = data.draft
                if (draft != null) {
                    PlannerScreen(
                        plan = draft,
                        onChange = { change -> store.update { d -> d.draft?.let { d.copy(draft = change(it)) } ?: d } },
                        onAddExercise = { day -> nav.navigate(Routes.Library(addDay = day)) },
                        onSave = {
                            store.update { Planner.save(it, draft).copy(draft = null) }
                            nav.popBackStack()
                        },
                        onStart = {
                            store.update { Planner.save(it, draft) }
                            startBlock(draft, draft.accumulationWeeks)
                        },
                        onBack = { nav.popBackStack() },
                    )
                }
            }
            composable<Routes.Settings> {
                SettingsScreen(
                    settings = data.settings,
                    onUnit = { u -> store.update { it.copy(settings = it.settings.copy(unit = u)) } },
                    onBack = { nav.popBackStack() },
                )
            }
            composable<Routes.Overview> {
                val block = data.block
                if (block == null) {
                    LaunchedEffect(Unit) { nav.goToStart() }
                } else {
                    OverviewScreen(
                        block = block,
                        onWorkout = {
                            store.updateBlock { Workflow.startWorkout(it, unit, exercises) }
                            nav.navigate(Routes.Workout) { launchSingleTop = true }
                        },
                        onLibrary = { nav.navigate(Routes.Library()) },
                        onSettings = { nav.navigate(Routes.Settings) },
                        onEndBlock = { store.update { it.copy(block = null) } },
                    )
                }
            }
            composable<Routes.Workout> {
                val block = data.block
                // The workout can be gone for a moment while the screen closes after "Finish".
                if (block?.active != null) {
                    WorkoutScreen(
                        block = block,
                        unit = unit,
                        onChange = store::updateBlock,
                        onSwap = { day, slot -> nav.navigate(Routes.Library(swapDay = day, swapSlot = slot)) },
                        onFinish = {
                            store.updateBlock { Workflow.finishWorkout(it, exercises) }
                            nav.popBackStack(Routes.Overview, inclusive = false)
                        },
                        onBack = { nav.popBackStack() },
                    )
                }
            }
            composable<Routes.Library> { entry ->
                val route = entry.toRoute<Routes.Library>()
                val block = data.block
                val draft = data.draft
                val mode = when {
                    route.swapDay >= 0 && block != null -> {
                        val day = block.plan.days[route.swapDay]
                        val slot = day.slots[route.swapSlot]
                        LibraryMode.Swap(day.name, slot.muscle, slot.exerciseId, day.slots.map { it.exerciseId }.toSet())
                    }
                    route.addDay >= 0 && draft != null -> {
                        val day = draft.days[route.addDay]
                        LibraryMode.Add(day.name, day.slots.map { it.exerciseId }.toSet())
                    }
                    else -> LibraryMode.Browse
                }
                LibraryScreen(
                    mode = mode,
                    onPick = { id ->
                        when (mode) {
                            is LibraryMode.Swap -> store.updateBlock { Workflow.swapExercise(it, route.swapDay, route.swapSlot, id) }
                            is LibraryMode.Add -> store.update { d -> d.draft?.let { d.copy(draft = Planner.addExercise(it, route.addDay, id)) } ?: d }
                            LibraryMode.Browse -> Unit
                        }
                        nav.popBackStack()
                    },
                    onBack = { nav.popBackStack() },
                )
            }
        }
    }
}

private fun NavHostController.goToStart() {
    navigate(Routes.Start) { popUpTo(graph.id) { inclusive = true } }
}
