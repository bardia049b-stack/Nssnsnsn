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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
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
import app.nebulabox.util.ShareLinkParser
import kotlinx.coroutines.launch

private object Route {
    const val SERVERS = "servers"
    const val SETTINGS = "settings"
    const val LOGS = "logs"
}

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
                modifier = Modifier.fillMaxWidth(0.76f),
                drawerContainerColor = MaterialTheme.colorScheme.surface,
            ) {
                Column(
                    modifier = Modifier.verticalScroll(rememberScrollState()),
                ) {
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 20.dp),
                        shape = RoundedCornerShape(20.dp),
                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f),
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 18.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(14.dp),
                        ) {
                            Surface(
                                shape = RoundedCornerShape(14.dp),
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(48.dp),
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        painter = painterResource(id = R.drawable.ic_javid_logo),
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onPrimary,
                                        modifier = Modifier.size(26.dp),
                                    )
                                }
                            }
                            Column {
                                Text(
                                    text = stringResource(R.string.app_name),
                                    style = MaterialTheme.typography.titleLarge,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface,
                                )
                                Spacer(Modifier.height(2.dp))
                                Text(
                                    text = viewModel.activeEngine?.implementationName ?: "JavidTun Core",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }

                    NavigationDrawerItem(
                        label = { Text("Servers") },
                        selected = currentRoute == Route.SERVERS,
                        onClick = {
                            scope.launch { drawerState.close() }
                            navController.goTo(Route.SERVERS)
                        },
                        icon = {
                            Icon(
                                painter = painterResource(id = R.drawable.ic_qu_switch_24dp),
                                contentDescription = null,
                            )
                        },
                        modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding),
                    )

                    NavigationDrawerItem(
                        label = { Text("Subscriptions") },
                        selected = false,
                        onClick = {
                            scope.launch { drawerState.close() }
                            navController.goTo(Route.SERVERS)
                            openSubscriptionsModal = true
                        },
                        icon = {
                            Icon(
                                painter = painterResource(id = R.drawable.ic_subscriptions_24dp),
                                contentDescription = null,
                            )
                        },
                        modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding),
                    )

                    NavigationDrawerItem(
                        label = { Text("Per-App Proxy") },
                        selected = false,
                        onClick = {
                            scope.launch { drawerState.close() }
                            navController.goTo(Route.SETTINGS)
                        },
                        icon = {
                            Icon(
                                painter = painterResource(id = R.drawable.ic_per_apps_24dp),
                                contentDescription = null,
                            )
                        },
                        modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding),
                    )

                    NavigationDrawerItem(
                        label = { Text("Routing & Fragment") },
                        selected = false,
                        onClick = {
                            scope.launch { drawerState.close() }
                            navController.goTo(Route.SETTINGS)
                        },
                        icon = {
                            Icon(
                                painter = painterResource(id = R.drawable.ic_routing_24dp),
                                contentDescription = null,
                            )
                        },
                        modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding),
                    )

                    NavigationDrawerItem(
                        label = { Text(stringResource(R.string.tab_settings)) },
                        selected = currentRoute == Route.SETTINGS,
                        onClick = {
                            scope.launch { drawerState.close() }
                            navController.goTo(Route.SETTINGS)
                        },
                        icon = {
                            Icon(
                                painter = painterResource(id = R.drawable.ic_settings_24dp),
                                contentDescription = null,
                            )
                        },
                        modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding),
                    )

                    Spacer(Modifier.height(8.dp))
                    AppDivider(modifier = Modifier.padding(horizontal = 16.dp))
                    Spacer(Modifier.height(8.dp))

                    NavigationDrawerItem(
                        label = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text("Logs & Diagnostics")
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
                        icon = {
                            Icon(
                                painter = painterResource(id = R.drawable.ic_logcat_24dp),
                                contentDescription = null,
                            )
                        },
                        modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding),
                    )
                }
            }
        },
    ) {
        Scaffold(
            snackbarHost = { SnackbarHost(snackbarHostState) },
            containerColor = MaterialTheme.colorScheme.background,
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
                                viewModel.draftProfile = Profile(
                                    id = ShareLinkParser.newId(),
                                    name = "",
                                    protocol = protocol,
                                )
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
                            title = "Logs & Diagnostics",
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
    onBack: () -> Unit,
    content: @Composable () -> Unit,
) {
    Column(Modifier.fillMaxSize()) {
        TopAppBar(
            title = {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                )
            },
            navigationIcon = {
                IconButton(onClick = onBack) {
                    Icon(
                        painter = painterResource(id = R.drawable.ic_arrow_back_24dp),
                        contentDescription = "Back",
                    )
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = MaterialTheme.colorScheme.background,
                titleContentColor = MaterialTheme.colorScheme.onBackground,
                navigationIconContentColor = MaterialTheme.colorScheme.onBackground,
            ),
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
