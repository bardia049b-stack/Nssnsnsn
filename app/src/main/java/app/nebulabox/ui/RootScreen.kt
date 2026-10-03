package app.nebulabox.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import app.nebulabox.R
import app.nebulabox.data.Profile
import app.nebulabox.ui.screens.LogsScreen
import app.nebulabox.ui.screens.PerAppProxyScreen
import app.nebulabox.ui.screens.ProfileEditSheet
import app.nebulabox.ui.screens.ProfilesScreen
import app.nebulabox.ui.screens.RoutingFragmentScreen
import app.nebulabox.ui.screens.SettingsScreen
import app.nebulabox.util.AppLogger
import app.nebulabox.util.ShareLinkParser

private object Route {
    const val SERVERS = "servers"
    const val ROUTING = "routing"
    const val PER_APP = "per_app"
    const val LOGS = "logs"
    const val SETTINGS = "settings"
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RootScreen(
    viewModel: NebulaViewModel,
    onRequestVpnPermission: (android.content.Intent) -> Unit,
) {
    val navController = rememberNavController()
    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current
    val crashes by AppLogger.crashes.collectAsState()

    var showEditor by rememberSaveable { mutableStateOf(false) }
    var openSubscriptionsModal by rememberSaveable { mutableStateOf(false) }
    val backStack by navController.currentBackStackEntryAsState()
    val currentRoute = backStack?.destination?.route ?: Route.SERVERS

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
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        bottomBar = {
            NavigationBar(
                containerColor = MaterialTheme.colorScheme.surface,
                tonalElevation = 3.dp,
            ) {
                NavigationBarItem(
                    selected = currentRoute == Route.SERVERS,
                    onClick = { navController.goTo(Route.SERVERS) },
                    icon = {
                        Icon(
                            painter = painterResource(id = R.drawable.ic_qu_switch_24dp),
                            contentDescription = "Servers",
                        )
                    },
                    label = { Text("Servers", maxLines = 1, overflow = TextOverflow.Ellipsis) },
                )

                NavigationBarItem(
                    selected = currentRoute == Route.ROUTING,
                    onClick = { navController.goTo(Route.ROUTING) },
                    icon = {
                        Icon(
                            painter = painterResource(id = R.drawable.ic_routing_24dp),
                            contentDescription = "Routing",
                        )
                    },
                    label = { Text("Routing", maxLines = 1, overflow = TextOverflow.Ellipsis) },
                )

                NavigationBarItem(
                    selected = currentRoute == Route.PER_APP,
                    onClick = { navController.goTo(Route.PER_APP) },
                    icon = {
                        Icon(
                            painter = painterResource(id = R.drawable.ic_per_apps_24dp),
                            contentDescription = "Per-App",
                        )
                    },
                    label = { Text("Per-App", maxLines = 1, overflow = TextOverflow.Ellipsis) },
                )

                NavigationBarItem(
                    selected = currentRoute == Route.LOGS,
                    onClick = { navController.goTo(Route.LOGS) },
                    icon = {
                        BadgedBox(
                            badge = {
                                if (crashes.isNotEmpty()) {
                                    Badge(containerColor = MaterialTheme.colorScheme.error) {
                                        Text(crashes.size.toString())
                                    }
                                }
                            },
                        ) {
                            Icon(
                                painter = painterResource(id = R.drawable.ic_logcat_24dp),
                                contentDescription = "Logs",
                            )
                        }
                    },
                    label = { Text("Logs", maxLines = 1, overflow = TextOverflow.Ellipsis) },
                )

                NavigationBarItem(
                    selected = currentRoute == Route.SETTINGS,
                    onClick = { navController.goTo(Route.SETTINGS) },
                    icon = {
                        Icon(
                            painter = painterResource(id = R.drawable.ic_settings_24dp),
                            contentDescription = "Settings",
                        )
                    },
                    label = { Text("Settings", maxLines = 1, overflow = TextOverflow.Ellipsis) },
                )
            }
        },
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(bottom = padding.calculateBottomPadding()),
        ) {
            NavHost(
                navController = navController,
                startDestination = Route.SERVERS,
            ) {
                composable(Route.SERVERS) {
                    ProfilesScreen(
                        viewModel = viewModel,
                        onEdit = { profile ->
                            viewModel.draftProfile = profile
                            showEditor = true
                        },
                        onNewWithProtocol = { protocol ->
                            viewModel.draftProfile = Profile(
                                id = ShareLinkParser.newId(),
                                name = "",
                                protocol = protocol,
                            )
                            showEditor = true
                        },
                        showSubscriptionsInit = openSubscriptionsModal,
                        onSubscriptionsDismissed = { openSubscriptionsModal = false },
                    )
                }

                composable(Route.ROUTING) {
                    SubScreenScaffold(title = "Routing & Fragment") {
                        RoutingFragmentScreen(viewModel)
                    }
                }

                composable(Route.PER_APP) {
                    SubScreenScaffold(title = "Per-App Proxy") {
                        PerAppProxyScreen(viewModel)
                    }
                }

                composable(Route.LOGS) {
                    SubScreenScaffold(title = "Logs & Diagnostics") {
                        LogsScreen(viewModel)
                    }
                }

                composable(Route.SETTINGS) {
                    SubScreenScaffold(title = stringResource(R.string.tab_settings)) {
                        SettingsScreen(viewModel)
                    }
                }
            }
        }
    }

    if (showEditor) {
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ModalBottomSheet(
            onDismissRequest = { showEditor = false },
            sheetState = sheetState,
            containerColor = MaterialTheme.colorScheme.surface,
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SubScreenScaffold(
    title: String,
    content: @Composable () -> Unit,
) {
    Column(Modifier.fillMaxSize()) {
        TopAppBar(
            title = {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold,
                )
            },
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = MaterialTheme.colorScheme.surface,
                titleContentColor = MaterialTheme.colorScheme.onSurface,
            ),
        )
        HorizontalDivider(
            thickness = 0.5.dp,
            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f),
        )
        Box(Modifier.fillMaxSize()) {
            content()
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
