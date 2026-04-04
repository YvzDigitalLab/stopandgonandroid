package com.example.stopgomusicplayer

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.*
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.stopgomusicplayer.ui.PlayerView
import com.example.stopgomusicplayer.ui.PlayerViewModel
import com.example.stopgomusicplayer.ui.PlaylistView

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            val viewModel: PlayerViewModel = viewModel()

            val darkMode = viewModel.darkMode
            val colorScheme = if (darkMode) darkColorScheme() else lightColorScheme()

            MaterialTheme(colorScheme = colorScheme) {
                StopAndGoApp(viewModel = viewModel)
            }
        }
    }
}

@Composable
fun StopAndGoApp(viewModel: PlayerViewModel) {
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
                    onDismiss = { viewModel.showPlaylist = false }
                )
            }
        }
    }
}
