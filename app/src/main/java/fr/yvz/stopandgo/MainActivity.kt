package fr.yvz.stopandgo

import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.*
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.viewmodel.compose.viewModel
import fr.yvz.stopandgo.ui.PlayerView
import fr.yvz.stopandgo.ui.PlayerViewModel
import fr.yvz.stopandgo.ui.PlaylistView

class MainActivity : ComponentActivity() {
    private val notificationPermissionLauncher = registerForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.RequestPermission()
    ) { /* result ignored — best effort */ }

    private fun needsNotificationPermission(): Boolean {
        if (android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.TIRAMISU) return false
        return checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) !=
                android.content.pm.PackageManager.PERMISSION_GRANTED
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        // Install the splash screen before super.onCreate (Android 12+ API)
        installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Lock to portrait on phones; allow free rotation on tablets
        val isTabletDevice = resources.configuration.smallestScreenWidthDp >= 600
        requestedOrientation = if (isTabletDevice) {
            android.content.pm.ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        } else {
            android.content.pm.ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        }

        setContent {
            val viewModel: PlayerViewModel = viewModel()
            var showPermissionRationale by remember { mutableStateOf(needsNotificationPermission()) }

            // Rationale dialog explaining why we need notifications
            if (showPermissionRationale) {
                AlertDialog(
                    onDismissRequest = { showPermissionRationale = false },
                    title = { Text("Allow lock screen controls?") },
                    text = {
                        Text(
                            "Stop & Go uses notifications only to display playback controls " +
                            "(play, pause, next, previous) on your lock screen and in the " +
                            "notification panel — so you can control music without unlocking " +
                            "your phone.\n\nNo other notifications will ever be sent."
                        )
                    },
                    confirmButton = {
                        TextButton(onClick = {
                            showPermissionRationale = false
                            notificationPermissionLauncher.launch(
                                android.Manifest.permission.POST_NOTIFICATIONS
                            )
                        }) { Text("Continue") }
                    },
                    dismissButton = {
                        TextButton(onClick = { showPermissionRationale = false }) {
                            Text("Not now")
                        }
                    }
                )
            }

            val darkMode = viewModel.darkMode
            val colorScheme = if (darkMode) {
                darkColorScheme(
                    primary = Color(0xFFE0E0E0),
                    onPrimary = Color.Black,
                    primaryContainer = Color(0xFF424242),
                    onPrimaryContainer = Color.White,
                    secondary = Color(0xFFBDBDBD),
                    onSecondary = Color.Black,
                    tertiary = Color(0xFF9E9E9E),
                    onTertiary = Color.Black
                )
            } else {
                lightColorScheme(
                    primary = Color(0xFF424242),
                    onPrimary = Color.White,
                    primaryContainer = Color(0xFFE0E0E0),
                    onPrimaryContainer = Color.Black,
                    secondary = Color(0xFF616161),
                    onSecondary = Color.White,
                    tertiary = Color(0xFF757575),
                    onTertiary = Color.White
                )
            }

            MaterialTheme(colorScheme = colorScheme) {
                StopAndGoApp(viewModel = viewModel)
            }
        }
    }
}

@Composable
fun StopAndGoApp(viewModel: PlayerViewModel) {
    // Keep screen on when autoLock setting is enabled
    val context = LocalContext.current
    DisposableEffect(viewModel.autoLock) {
        val activity = context as? ComponentActivity
        if (viewModel.autoLock) {
            activity?.window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        } else {
            activity?.window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
        onDispose {
            activity?.window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }

    val configuration = androidx.compose.ui.platform.LocalConfiguration.current
    val isTablet = configuration.smallestScreenWidthDp >= 600

    if (isTablet) {
        // Tablet: side-by-side player + playlist
        Row(
            modifier = Modifier
                .fillMaxSize()
                .systemBarsPadding()
        ) {
            Box(modifier = Modifier.weight(1f).fillMaxHeight()) {
                PlayerView(viewModel = viewModel, isTablet = true)
            }
            // Wrap in Surface so children inherit the proper light/dark
            // background + content colors from the MaterialTheme.
            Surface(
                modifier = Modifier.weight(1f).fillMaxHeight(),
                color = MaterialTheme.colorScheme.background
            ) {
                PlaylistView(
                    viewModel = viewModel,
                    onDismiss = { /* no-op on tablet */ },
                    isTablet = true
                )
            }
        }
    } else {
        // Phone: player full-screen with playlist sliding up as overlay
        Box(
            modifier = Modifier
                .fillMaxSize()
                .systemBarsPadding()
        ) {
            PlayerView(viewModel = viewModel)

            AnimatedVisibility(
                visible = viewModel.showPlaylist,
                enter = slideInVertically(initialOffsetY = { it }),
                exit = slideOutVertically(targetOffsetY = { it })
            ) {
                Surface(modifier = Modifier.fillMaxSize()) {
                    PlaylistView(
                        viewModel = viewModel,
                        onDismiss = { viewModel.showPlaylist = false },
                        isTablet = false
                    )
                }
            }
        }
    }
}
