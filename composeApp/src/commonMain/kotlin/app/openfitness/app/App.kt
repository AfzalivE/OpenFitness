package app.openfitness.app

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import app.openfitness.app.data.AppStore
import app.openfitness.app.data.Workflow
import app.openfitness.app.ui.LibraryScreen
import app.openfitness.app.ui.OpenFitnessTheme
import app.openfitness.app.ui.OverviewScreen
import app.openfitness.app.ui.StartBlockScreen
import app.openfitness.app.ui.WorkoutScreen
import kotlinx.serialization.Serializable

/** Screens of the app. */
object Routes {
    @Serializable data object Start
    @Serializable data object Overview
    @Serializable data object Workout

    /** The exercise library. With a day and a slot, the user picks an exercise to swap in. */
    @Serializable data class Library(val dayIndex: Int = -1, val slotIndex: Int = -1) {
        val isSwap: Boolean get() = dayIndex >= 0 && slotIndex >= 0
    }
}

@Composable
fun App(store: AppStore) {
    OpenFitnessTheme {
        val data by store.state.collectAsState()
        val nav = rememberNavController()
        val start: Any = remember { if (store.state.value.block != null) Routes.Overview else Routes.Start }

        NavHost(navController = nav, startDestination = start) {
            composable<Routes.Start> {
                StartBlockScreen(
                    onStart = { template, weeks ->
                        store.update { it.copy(block = Workflow.startBlock(template, weeks)) }
                        nav.navigate(Routes.Overview) { popUpTo(nav.graph.id) { inclusive = true } }
                    },
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
                            store.updateBlock(Workflow::startWorkout)
                            nav.navigate(Routes.Workout) { launchSingleTop = true }
                        },
                        onLibrary = { nav.navigate(Routes.Library()) },
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
                        onChange = store::updateBlock,
                        onSwap = { day, slot -> nav.navigate(Routes.Library(day, slot)) },
                        onFinish = {
                            store.updateBlock { Workflow.finishWorkout(it) }
                            nav.popBackStack(Routes.Overview, inclusive = false)
                        },
                        onBack = { nav.popBackStack() },
                    )
                }
            }
            composable<Routes.Library> { entry ->
                val route = entry.toRoute<Routes.Library>()
                LibraryScreen(
                    block = data.block,
                    dayIndex = route.dayIndex.takeIf { route.isSwap },
                    slotIndex = route.slotIndex.takeIf { route.isSwap },
                    onPick = { id ->
                        store.updateBlock { Workflow.swapExercise(it, route.dayIndex, route.slotIndex, id) }
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
