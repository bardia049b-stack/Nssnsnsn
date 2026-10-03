package app.nebulabox.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.FolderSpecial
import androidx.compose.material.icons.filled.Route
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material3.Badge
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.NavigationDrawerItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberDrawerState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import app.nebulabox.R
import app.nebulabox.data.Profile
import app.nebulabox.ui.screens.LogsScreen
import app.nebulabox.ui.screens.ProfileEditSheet
import app.nebulabox.ui.screens.ProfilesScreen
import app.nebulabox.ui.screens.SettingsScreen
import app.nebulabox.util.AppLogger
import kotlinx.coroutines.launch

private object Route {
    const val SERVERS = "servers"
    const val SETTINGS = "settings"
    const val LOGS = "logs"
}

/**
 * Root navigation modeled directly on `v2rayNG 2.3.10` (`MainScreen.kt` + `MainDrawer.kt`):
 *  - Main screen is the unified Server List (`ProfilesScreen`) with `MainTopBar` and `MainBottomBar`
 *  - Left slide-out `ModalNavigationDrawer` provides instant access to Subscriptions, Per-App Proxy,
 *    Routing, Settings, and Logcat
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RootScreen(
    viewModel: NebulaViewModel,
    onRequestVpnPermission: (android.content.Intent) -> Unit,
) {
    val navController = rememberNavController()
    val snackbarHostState = remember { SnackbarHostState() }
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()
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

    ModalNavigationDrawer(
        drawerState = drawerState,
        gesturesEnabled = currentRoute == Route.SERVERS,
        drawerContent = {
            ModalDrawerSheet(
                drawerState = drawerState,
                modifier = Modifier.fillMaxWidth(0.75f),
                drawerContainerColor = MaterialTheme.colorScheme.surface,
            ) {
                Column(
                    modifier = Modifier.verticalScroll(rememberScrollState()),
                ) {
                    // Drawer header (exact v2rayNG MainDrawerContent style)
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(168.dp),
                        color = MaterialTheme.colorScheme.surface,
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(16.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center,
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Shield,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.secondary,
                                modifier = Modifier.size(64.dp),
                            )
                            Spacer(Modifier.height(10.dp))
                            Text(
                                text = stringResource(R.string.app_name),
                                style = MaterialTheme.typography.titleLarge,
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                            Text(
                                text = viewModel.engineName,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }

                    AppDivider()
                    Spacer(Modifier.height(8.dp))

                    NavigationDrawerItem(
                        label = { Text("Servers") },
                        selected = currentRoute == Route.SERVERS,
                        onClick = {
                            scope.launch { drawerState.close() }
                            navController.goTo(Route.SERVERS)
                        },
                        icon = { Icon(Icons.AutoMirrored.Filled.List, contentDescription = null) },
                        modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding),
                    )

                    NavigationDrawerItem(
                        label = { Text("Subscription group setting") },
                        selected = false,
                        onClick = {
                            scope.launch { drawerState.close() }
                            navController.goTo(Route.SERVERS)
                            openSubscriptionsModal = true
                        },
                        icon = { Icon(Icons.Filled.FolderSpecial, contentDescription = null) },
                        modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding),
                    )

                    NavigationDrawerItem(
                        label = { Text("Per-app proxy") },
                        selected = false,
                        onClick = {
                            scope.launch { drawerState.close() }
                            navController.goTo(Route.SETTINGS)
                        },
                        icon = { Icon(Icons.Filled.Apps, contentDescription = null) },
                        modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding),
                    )

                    NavigationDrawerItem(
                        label = { Text("Routing & Fragment setting") },
                        selected = false,
                        onClick = {
                            scope.launch { drawerState.close() }
                            navController.goTo(Route.SETTINGS)
                        },
                        icon = { Icon(Icons.Filled.Route, contentDescription = null) },
                        modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding),
                    )

                    NavigationDrawerItem(
                        label = { Text(stringResource(R.string.tab_settings)) },
                        selected = currentRoute == Route.SETTINGS,
                        onClick = {
                            scope.launch { drawerState.close() }
                            navController.goTo(Route.SETTINGS)
                        },
                        icon = { Icon(Icons.Filled.Settings, contentDescription = null) },
                        modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding),
                    )

                    Spacer(Modifier.height(8.dp))
                    AppDivider()
                    Spacer(Modifier.height(8.dp))

                    NavigationDrawerItem(
                        label = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text("Logcat & Crash Center")
                                if (crashes.isNotEmpty()) {
                                    Spacer(Modifier.width(8.dp))
                                    Badge(containerColor = MaterialTheme.colorScheme.error) {
                                        Text(crashes.size.toString())
                                    }
                                }
                            }
                        },
                        selected = currentRoute == Route.LOGS,
                        onClick = {
                            scope.launch { drawerState.close() }
                            navController.goTo(Route.LOGS)
                        },
                        icon = { Icon(Icons.Filled.Terminal, contentDescription = null) },
                        modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding),
                    )
                }
            }
        },
    ) {
        Scaffold(
            snackbarHost = { SnackbarHost(snackbarHostState) },
        ) { padding ->
            Box(Modifier.padding(padding)) {
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
                                viewModel.draftProfile = Profile(protocol = protocol)
                                showEditor = true
                            },
                            onOpenDrawer = { scope.launch { drawerState.open() } },
                            showSubscriptionsInit = openSubscriptionsModal,
                            onSubscriptionsDismissed = { openSubscriptionsModal = false },
                        )
                    }

                    composable(Route.SETTINGS) {
                        SubScreenScaffold(
                            title = stringResource(R.string.tab_settings),
                            onBack = { navController.popBackStack() },
                        ) {
                            SettingsScreen(viewModel)
                        }
                    }

                    composable(Route.LOGS) {
                        SubScreenScaffold(
                            title = "Logcat",
                            onBack = { navController.popBackStack() },
                        ) {
                            LogsScreen(viewModel)
                        }
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
    onBack: () -> Unit,
    content: @Composable () -> Unit,
) {
    Column(Modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text(title) },
            navigationIcon = {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = MaterialTheme.colorScheme.surface,
                titleContentColor = MaterialTheme.colorScheme.onSurface,
                navigationIconContentColor = MaterialTheme.colorScheme.onSurface,
            ),
        )
        AppDivider()
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
