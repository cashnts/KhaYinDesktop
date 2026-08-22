package com.nuvio.app.features.license

import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Build
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nuvio.app.features.settings.AppBrandWordmark
import kotlinx.coroutines.launch

@Composable
fun MaintenanceModeScreen(
    onCheckAgain: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scope = rememberCoroutineScope()
    var isChecking by remember { mutableStateOf(false) }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFF0D0D11)),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .size(600.dp)
                .background(
                    Brush.radialGradient(
                        colors = listOf(
                            Color(0x22FF5252),
                            Color.Transparent,
                        ),
                    ),
                ),
        )

        Column(
            modifier = Modifier
                .widthIn(max = 500.dp)
                .fillMaxWidth()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            AppBrandWordmark(
                modifier = Modifier
                    .height(90.dp)
                    .padding(bottom = 14.dp),
            )

            Icon(
                imageVector = Icons.Rounded.Build,
                contentDescription = null,
                tint = Color(0xFFFF5252),
                modifier = Modifier.size(56.dp),
            )

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = "Maintenance Mode",
                style = MaterialTheme.typography.headlineMedium.copy(
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                ),
                textAlign = TextAlign.Center,
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "KhaYin is currently undergoing scheduled maintenance. All client services and streaming are temporarily paused. Please check back shortly.",
                style = MaterialTheme.typography.bodyMedium.copy(
                    color = Color(0xFF9E9EA7),
                ),
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 16.dp),
            )

            Spacer(modifier = Modifier.height(28.dp))

            Button(
                onClick = {
                    if (isChecking) return@Button
                    isChecking = true
                    scope.launch {
                        AdminControlRepository.fetchConfig()
                        isChecking = false
                        onCheckAgain()
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp),
                shape = RoundedCornerShape(10.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFF00E699),
                    contentColor = Color.Black,
                ),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Rounded.Refresh,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = if (isChecking) "Checking Status..." else "Check Server Status",
                        style = TextStyle(fontWeight = FontWeight.Bold, fontSize = 15.sp),
                    )
                }
            }
        }
    }
}
