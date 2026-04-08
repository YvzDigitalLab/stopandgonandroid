package fr.yvz.stopandgo.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.activity.ComponentActivity

@Composable
fun UpgradeView(
    viewModel: PlayerViewModel,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val productDetails by viewModel.billing.productDetails.collectAsState()
    val isPremium by viewModel.billing.isPremium.collectAsState()

    // Auto-dismiss when purchase succeeds
    LaunchedEffect(isPremium) {
        if (isPremium) onDismiss()
    }

    val priceText = productDetails?.oneTimePurchaseOfferDetails?.formattedPrice ?: ""

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Top bar
            Surface(tonalElevation = 2.dp, modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "Close")
                    }
                    Spacer(Modifier.weight(1f))
                }
            }

            // Content
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // App logo / title
                androidx.compose.foundation.Image(
                    painter = androidx.compose.ui.res.painterResource(fr.yvz.stopandgo.R.drawable.ic_plus_logo),
                    contentDescription = "Stop & Go +",
                    modifier = Modifier
                        .size(96.dp)
                        .clip(CircleShape)
                )
                Spacer(Modifier.height(16.dp))
                Text(
                    text = "Stop & Go +",
                    fontSize = 32.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "Lifetime upgrade",
                    fontSize = 16.sp,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                )

                Spacer(Modifier.height(32.dp))

                // Feature list
                FeatureRow(
                    icon = Icons.Default.AllInclusive,
                    title = "Unlimited songs",
                    description = "Add as many songs as you want to your playlists"
                )
                FeatureRow(
                    icon = Icons.Default.LibraryMusic,
                    title = "Unlimited playlists",
                    description = "Create and manage as many playlists as you need"
                )
                FeatureRow(
                    icon = Icons.Default.Favorite,
                    title = "Support development",
                    description = "Help keep the app ad-free and updated"
                )

                Spacer(Modifier.height(32.dp))

                // Buy button
                Button(
                    onClick = {
                        val activity = context as? ComponentActivity ?: return@Button
                        viewModel.billing.launchPurchaseFlow(activity)
                    },
                    enabled = productDetails != null,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF4CAF50),
                        contentColor = Color.White
                    ),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp)
                ) {
                    Text(
                        text = if (priceText.isNotEmpty()) "Upgrade for $priceText" else "Upgrade",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }

                Spacer(Modifier.height(8.dp))

                TextButton(
                    onClick = { viewModel.billing.restorePurchases() },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Restore previous purchase")
                }

                if (productDetails == null) {
                    Spacer(Modifier.height(16.dp))
                    Text(
                        text = "Connecting to Google Play...",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }
    }
}

@Composable
private fun FeatureRow(
    icon: ImageVector,
    title: String,
    description: String
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = Color(0xFF4CAF50),
            modifier = Modifier.size(32.dp)
        )
        Spacer(Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = description,
                fontSize = 14.sp,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
            )
        }
    }
}
