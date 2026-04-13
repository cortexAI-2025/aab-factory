package com.aabfactory.app.presentation.subscription

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.aabfactory.app.data.model.Plan
import com.aabfactory.app.presentation.theme.BrandPurple
import com.aabfactory.app.presentation.theme.BrandTeal
import com.aabfactory.app.presentation.theme.ProBadgeGold
import com.aabfactory.app.presentation.theme.Success

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SubscriptionScreen(
    onBack: () -> Unit,
    viewModel: SubscriptionViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsState()
    val uriHandler = LocalUriHandler.current

    // Open Stripe checkout URL when it arrives
    LaunchedEffect(state.checkoutUrl) {
        if (state.checkoutUrl.isNotEmpty()) {
            uriHandler.openUri(state.checkoutUrl)
            viewModel.clearCheckoutUrl()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Upgrade", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, "Back") }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
                modifier = Modifier.statusBarsPadding()
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .navigationBarsPadding(),
            contentPadding = PaddingValues(24.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            item {
                // Header
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                    Box(
                        modifier = Modifier
                            .size(72.dp)
                            .clip(RoundedCornerShape(20.dp))
                            .background(Brush.linearGradient(listOf(BrandPurple, BrandTeal))),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Default.Star, null, tint = Color.White, modifier = Modifier.size(36.dp))
                    }
                    Spacer(Modifier.height(16.dp))
                    Text("Unlock Full Power", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
                    Text("Build unlimited Android apps with AI", style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
                }
            }

            // Current plan indicator
            if (state.userPlan.plan == Plan.PRO) {
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = Success.copy(alpha = 0.1f)),
                        border = androidx.compose.foundation.BorderStroke(1.dp, Success)
                    ) {
                        Row(
                            modifier = Modifier.padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.Check, null, tint = Success)
                            Spacer(Modifier.width(8.dp))
                            Column {
                                Text("You're on Pro!", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = Success)
                                Text("Enjoy unlimited builds and all features", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
            }

            // Free Plan card
            item {
                PlanCard(
                    title = "Free",
                    price = "$0",
                    period = "forever",
                    features = listOf(
                        "2 templates (Business, Blog)",
                        "2 AAB builds per month",
                        "Watermark on output",
                        "Community support"
                    ),
                    isCurrent = state.userPlan.plan == Plan.FREE,
                    isHighlighted = false,
                    ctaText = if (state.userPlan.plan == Plan.FREE) "Current Plan" else "Downgrade",
                    ctaEnabled = false,
                    onCta = {}
                )
            }

            // Pro Plan card
            item {
                PlanCard(
                    title = "Pro",
                    price = "$29",
                    period = "per month",
                    features = listOf(
                        "All 5 templates",
                        "Unlimited AAB builds",
                        "No watermark",
                        "Priority build queue",
                        "AI generation priority",
                        "Email support"
                    ),
                    isCurrent = state.userPlan.plan == Plan.PRO,
                    isHighlighted = true,
                    ctaText = if (state.userPlan.plan == Plan.PRO) "Current Plan" else "Subscribe Now",
                    ctaEnabled = state.userPlan.plan == Plan.FREE && !state.isProcessingPayment,
                    onCta = viewModel::subscribePro,
                    isLoading = state.isProcessingPayment
                )
            }

            // One-time Build Pack
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
                ) {
                    Column(modifier = Modifier.padding(20.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Build Pack", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                                Text("5 extra builds — one-time purchase", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Text("$9", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Black, color = BrandTeal)
                        }
                        Spacer(Modifier.height(12.dp))
                        OutlinedButton(
                            onClick = viewModel::purchaseBuildPack,
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp),
                            enabled = !state.isProcessingPayment
                        ) {
                            Text("Buy 5 Builds", fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            }

            item {
                Text(
                    "Payments powered by Stripe • Cancel anytime • No hidden fees",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            state.error?.let {
                item {
                    Text(it, color = com.aabfactory.app.presentation.theme.Error,
                        style = MaterialTheme.typography.bodySmall, textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth())
                }
            }
        }
    }
}

@Composable
private fun PlanCard(
    title: String,
    price: String,
    period: String,
    features: List<String>,
    isCurrent: Boolean,
    isHighlighted: Boolean,
    ctaText: String,
    ctaEnabled: Boolean,
    onCta: () -> Unit,
    isLoading: Boolean = false
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isHighlighted) BrandPurple.copy(alpha = 0.08f)
            else MaterialTheme.colorScheme.surface
        ),
        border = if (isHighlighted) {
            androidx.compose.foundation.BorderStroke(2.dp, BrandPurple)
        } else {
            androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
        }
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            if (isHighlighted) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.width(8.dp))
                    Box(
                        modifier = Modifier.clip(RoundedCornerShape(20.dp)).background(ProBadgeGold).padding(horizontal = 8.dp, vertical = 2.dp)
                    ) {
                        Text("POPULAR", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = Color.Black)
                    }
                }
            } else {
                Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            }

            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.Bottom) {
                Text(price, style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.Black,
                    color = if (isHighlighted) BrandPurple else MaterialTheme.colorScheme.onSurface)
                Spacer(Modifier.width(4.dp))
                Text("/$period", style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 4.dp))
            }

            Spacer(Modifier.height(16.dp))
            features.forEach { feature ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(bottom = 8.dp)
                ) {
                    Icon(Icons.Default.Check, null, tint = if (isHighlighted) BrandPurple else Success,
                        modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(feature, style = MaterialTheme.typography.bodySmall)
                }
            }

            Spacer(Modifier.height(12.dp))
            Button(
                onClick = onCta,
                modifier = Modifier.fillMaxWidth().height(50.dp),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (isHighlighted) BrandPurple else MaterialTheme.colorScheme.surfaceVariant,
                    disabledContainerColor = if (isCurrent) Success.copy(alpha = 0.2f) else MaterialTheme.colorScheme.surfaceVariant
                ),
                enabled = ctaEnabled
            ) {
                if (isLoading) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp, color = Color.White)
                } else {
                    Text(ctaText, fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}
