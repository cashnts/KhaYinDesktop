package com.nuvio.app.features.license

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AdminPanelSettings
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.ContentPaste
import androidx.compose.material.icons.rounded.Key
import androidx.compose.material.icons.rounded.Warning
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nuvio.app.core.build.AppFeaturePolicy
import com.nuvio.app.core.ui.NuvioLoadingIndicator
import com.nuvio.app.features.settings.AppBrandWordmark
import kotlinx.coroutines.launch

@Composable
fun LicenseActivationScreen(
    onActivated: (LicenseInfo) -> Unit,
    onOpenAdminPanel: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    var keyInput by remember { mutableStateOf("") }
    var isSubmitting by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val focusManager = LocalFocusManager.current
    val clipboardManager = LocalClipboardManager.current
    val errorFromRepo by LicenseRepository.error.collectAsStateWithLifecycle()

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFF0D0D11)),
        contentAlignment = Alignment.Center,
    ) {
        // Subtle ambient background gradient
        Box(
            modifier = Modifier
                .size(600.dp)
                .background(
                    Brush.radialGradient(
                        colors = listOf(
                            Color(0x1A00E699),
                            Color(0x087B2CBF),
                            Color.Transparent,
                        ),
                    ),
                ),
        )

        Column(
            modifier = Modifier
                .widthIn(max = 480.dp)
                .fillMaxWidth()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            AppBrandWordmark(
                modifier = Modifier
                    .height(100.dp)
                    .padding(bottom = 12.dp),
            )

            Text(
                text = "Welcome to KhaYin",
                style = MaterialTheme.typography.headlineMedium.copy(
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                ),
                textAlign = TextAlign.Center,
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "Enter your client license key to activate your profile, streams, and full media hub access.",
                style = MaterialTheme.typography.bodyMedium.copy(
                    color = Color(0xFF9E9EA7),
                ),
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 16.dp),
            )

            Spacer(modifier = Modifier.height(32.dp))

            // License Key Input Card
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(Color(0xFF16161E))
                    .border(1.dp, Color(0xFF262633), RoundedCornerShape(16.dp))
                    .padding(20.dp),
            ) {
                Text(
                    text = "LICENSE KEY",
                    style = MaterialTheme.typography.labelSmall.copy(
                        color = Color(0xFF00E699),
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp,
                    ),
                )

                Spacer(modifier = Modifier.height(10.dp))

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(Color(0xFF0F0F16))
                        .border(1.dp, Color(0xFF323244), RoundedCornerShape(10.dp))
                        .padding(horizontal = 14.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Key,
                        contentDescription = null,
                        tint = Color(0xFF7A7A8C),
                        modifier = Modifier.size(20.dp),
                    )

                    Spacer(modifier = Modifier.width(10.dp))

                    BasicTextField(
                        value = keyInput,
                        onValueChange = { raw ->
                            val clean = raw.uppercase().filter { it.isLetterOrDigit() || it == '-' }.take(24)
                            keyInput = clean
                            errorMessage = null
                        },
                        modifier = Modifier.weight(1f),
                        textStyle = TextStyle(
                            color = Color.White,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.SemiBold,
                            fontFamily = FontFamily.Monospace,
                        ),
                        cursorBrush = SolidColor(Color(0xFF00E699)),
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(
                            capitalization = KeyboardCapitalization.Characters,
                            imeAction = ImeAction.Done,
                        ),
                        keyboardActions = KeyboardActions(
                            onDone = {
                                focusManager.clearFocus()
                                if (keyInput.isNotBlank() && !isSubmitting) {
                                    isSubmitting = true
                                    scope.launch {
                                        LicenseRepository.activate(keyInput).fold(
                                            onSuccess = { info ->
                                                isSubmitting = false
                                                onActivated(info)
                                            },
                                            onFailure = { err ->
                                                isSubmitting = false
                                                errorMessage = err.message
                                            },
                                        )
                                    }
                                }
                            },
                        ),
                        decorationBox = { innerTextField ->
                            if (keyInput.isEmpty()) {
                                Text(
                                    text = "KY-XXXX-XXXX-XXXX",
                                    style = TextStyle(
                                        color = Color(0xFF555566),
                                        fontSize = 15.sp,
                                        fontFamily = FontFamily.Monospace,
                                    ),
                                )
                            }
                            innerTextField()
                        },
                    )

                    // Paste Button
                    Icon(
                        imageVector = Icons.Rounded.ContentPaste,
                        contentDescription = "Paste",
                        tint = Color(0xFF7A7A8C),
                        modifier = Modifier
                            .size(20.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .clickable {
                                clipboardManager.getText()?.text?.let { pasted ->
                                    keyInput = pasted.trim().uppercase()
                                    errorMessage = null
                                }
                            },
                    )
                }

                val activeError = errorMessage ?: errorFromRepo
                AnimatedVisibility(
                    visible = activeError != null,
                    enter = fadeIn(),
                    exit = fadeOut(),
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Warning,
                            contentDescription = null,
                            tint = Color(0xFFFF5252),
                            modifier = Modifier.size(16.dp),
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = activeError.orEmpty(),
                            style = MaterialTheme.typography.bodySmall.copy(
                                color = Color(0xFFFF5252),
                                fontSize = 12.sp,
                            ),
                        )
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))

                Button(
                    onClick = {
                        focusManager.clearFocus()
                        if (keyInput.isBlank()) {
                            errorMessage = "Please enter your license key"
                            return@Button
                        }
                        isSubmitting = true
                        scope.launch {
                            LicenseRepository.activate(keyInput).fold(
                                onSuccess = { info ->
                                    isSubmitting = false
                                    onActivated(info)
                                },
                                onFailure = { err ->
                                    isSubmitting = false
                                    errorMessage = err.message ?: "Failed to activate"
                                },
                            )
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
                    enabled = !isSubmitting,
                ) {
                    if (isSubmitting) {
                        NuvioLoadingIndicator(
                            modifier = Modifier.size(20.dp),
                            color = Color.Black,
                        )
                    } else {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Rounded.CheckCircle,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp),
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Activate License",
                                style = TextStyle(fontWeight = FontWeight.Bold, fontSize = 15.sp),
                            )
                        }
                    }
                }
            }
        }
    }
}
