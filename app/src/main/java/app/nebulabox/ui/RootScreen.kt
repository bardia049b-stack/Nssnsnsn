package app.nebulabox.ui

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.navigation.NavController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import app.nebulabox.R
import app.nebulabox.ui.screens.GroupsScreen
import app.nebulabox.ui.screens.HomeScreen
import app.nebulabox.ui.screens.LogsScreen
import app.nebulabox.ui.screens.ProfileEditSheet
import app.nebulabox.ui.screens.ProfilesScreen
import app.nebulabox.ui.screens.SettingsScreen

private object Tab {
    const val HOME = "home"
    const val PROFILES = "profiles"
    const val GROUPS = "groups"
    const val LOGS = "logs"
    const val SETTINGS = "settings"
}

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun RootScreen(
    viewModel: NebulaViewModel,
    onRequestVpnPermission: (android.content.Intent) -> Unit,
) {
    val navController = rememberNavController()
    val snackbarHostState = remember { SnackbarHostState() }
    var showEditor by rememberSaveable { mutableStateOf(false) }
    val context = LocalContext.current

    LaunchedEffect(Unit) {
        viewModel.snacks.collect { snackbarHostState.showSnackbar(it) }
    }

    LaunchedEffect(Unit) {
        viewModel.importResults.collect { result ->
            val message = if (result.count > 0) {
                context.resources.getQuantityString(
                    R.plurals.profiles_imported,
                    result.count,
                    result.count,
                )
            } else {
                context.getString(R.string.import_failed)
            }
            snackbarHostState.showSnackbar(message)
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        bottomBar = {
            val backStack by navController.currentBackStackEntryAsState()
            val currentRoute = backStack?.destination?.route
            NavigationBar {
                NavigationBarItem(
                    selected = currentRoute == Tab.HOME,
                    onClick = { navController.goTo(Tab.HOME) },
                    icon = { Icon(Icons.Outlined.Home, null) },
                    label = { Text(stringResource(R.string.tab_home)) },
                )
                NavigationBarItem(
                    selected = currentRoute == Tab.PROFILES,
                    onClick = { navController.goTo(Tab.PROFILES) },
                    icon = { Icon(Icons.AutoMirrored.Filled.List, null) },
                    label = { Text(stringResource(R.string.tab_profiles)) },
                )
                NavigationBarItem(
                    selected = currentRoute == Tab.LOGS,
                    onClick = { navController.goTo(Tab.LOGS) },
                    icon = { Icon(Icons.Filled.Terminal, null) },
                    label = { Text(stringResource(R.string.tab_logs)) },
                )
                NavigationBarItem(
                    selected = currentRoute == Tab.SETTINGS,
                    onClick = { navController.goTo(Tab.SETTINGS) },
                    icon = { Icon(Icons.Filled.Settings, null) },
                    label = { Text(stringResource(R.string.tab_settings)) },
                )
            }
        },
    ) { padding ->
        NavHost(
            navController = navController,
            startDestination = Tab.HOME,
            modifier = Modifier.padding(padding),
        ) {
            composable(Tab.HOME) {
                HomeScreen(
                    viewModel = viewModel,
                    onOpenProfiles = { navController.goTo(Tab.PROFILES) },
                    onOpenGroups = { navController.goTo(Tab.GROUPS) },
                )
            }
            composable(Tab.PROFILES) {
                ProfilesScreen(
                    viewModel = viewModel,
                    onEdit = { profile ->
                        viewModel.draftProfile = profile
                        showEditor = true
                    },
                    onNew = {
                        viewModel.draftProfile = null
                        showEditor = true
                    },
                )
            }
            composable(Tab.GROUPS) { GroupsScreen(viewModel) }
            composable(Tab.LOGS) { LogsScreen(viewModel) }
            composable(Tab.SETTINGS) { SettingsScreen(viewModel) }
        }
    }

    if (showEditor) {
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ModalBottomSheet(
            onDismissRequest = { showEditor = false },
            sheetState = sheetState,
        ) {
            ProfileEditSheet(
                initial = viewModel.draftProfile,
                onDismiss = { showEditor = false },
                onSave = { profile ->
                    viewModel.saveProfile(profile)
                    showEditor = false
                },
            )
        }
    }
}

private fun NavController.goTo(route: String) {
    navigate(route) {
        popUpTo(graph.startDestinationId) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}
