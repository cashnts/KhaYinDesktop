package com.nuvio.app.features.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nuvio.app.core.auth.AuthRepository
import com.nuvio.app.core.auth.AuthState
import com.nuvio.app.core.build.AppFeaturePolicy
import com.nuvio.app.core.ui.NuvioPrimaryButton
import com.nuvio.app.core.ui.NuvioStatusModal
import com.nuvio.app.core.ui.NuvioSurfaceCard
import com.nuvio.app.core.ui.NuvioTokens
import com.nuvio.app.core.ui.nuvio
import kotlinx.coroutines.launch
import nuvio.composeapp.generated.resources.Res
import nuvio.composeapp.generated.resources.action_cancel
import nuvio.composeapp.generated.resources.compose_settings_page_account
import nuvio.composeapp.generated.resources.auth_account_deletion_failed
import nuvio.composeapp.generated.resources.settings_account_delete_account
import nuvio.composeapp.generated.resources.settings_account_delete_account_description
import nuvio.composeapp.generated.resources.settings_account_delete_confirm_message
import nuvio.composeapp.generated.resources.settings_account_delete_confirm_title
import nuvio.composeapp.generated.resources.settings_account_email
import nuvio.composeapp.generated.resources.settings_account_not_signed_in
import nuvio.composeapp.generated.resources.settings_account_sign_out
import nuvio.composeapp.generated.resources.settings_account_sign_out_confirm_message
import nuvio.composeapp.generated.resources.settings_account_sign_out_confirm_title
import nuvio.composeapp.generated.resources.settings_account_status
import nuvio.composeapp.generated.resources.settings_account_status_anonymous
import nuvio.composeapp.generated.resources.settings_account_status_signed_in
import nuvio.composeapp.generated.resources.settings_subscription_active_plan_badge
import nuvio.composeapp.generated.resources.settings_subscription_and_license
import nuvio.composeapp.generated.resources.settings_subscription_copy_license_key
import nuvio.composeapp.generated.resources.settings_subscription_customize_profile
import nuvio.composeapp.generated.resources.settings_subscription_devices
import nuvio.composeapp.generated.resources.settings_subscription_devices_active
import nuvio.composeapp.generated.resources.settings_subscription_expires_on
import nuvio.composeapp.generated.resources.settings_subscription_feature_4k_hdr
import nuvio.composeapp.generated.resources.settings_subscription_feature_early_access
import nuvio.composeapp.generated.resources.settings_subscription_feature_en_zh_subs
import nuvio.composeapp.generated.resources.settings_subscription_feature_everything_in_standard
import nuvio.composeapp.generated.resources.settings_subscription_feature_exclusive_mm_subs
import nuvio.composeapp.generated.resources.settings_subscription_feature_ondemand_streaming
import nuvio.composeapp.generated.resources.settings_subscription_feature_priority_support
import nuvio.composeapp.generated.resources.settings_subscription_feature_standard_support
import nuvio.composeapp.generated.resources.settings_subscription_feature_unlimited_downloads
import nuvio.composeapp.generated.resources.settings_subscription_feature_up_to_3_devices
import nuvio.composeapp.generated.resources.settings_subscription_feature_up_to_5_downloads
import nuvio.composeapp.generated.resources.settings_subscription_feature_up_to_6_devices
import nuvio.composeapp.generated.resources.settings_subscription_license_key
import nuvio.composeapp.generated.resources.settings_subscription_licensed_to
import nuvio.composeapp.generated.resources.settings_subscription_lifetime_access
import nuvio.composeapp.generated.resources.settings_subscription_logout
import nuvio.composeapp.generated.resources.settings_subscription_logout_confirm_message
import nuvio.composeapp.generated.resources.settings_subscription_member_default
import nuvio.composeapp.generated.resources.settings_subscription_no_license
import nuvio.composeapp.generated.resources.settings_subscription_package_tier
import nuvio.composeapp.generated.resources.settings_subscription_plan_plus
import nuvio.composeapp.generated.resources.settings_subscription_plan_standard
import nuvio.composeapp.generated.resources.settings_subscription_profile_appearance_desc
import nuvio.composeapp.generated.resources.settings_subscription_profile_appearance_title
import nuvio.composeapp.generated.resources.settings_subscription_profile_name
import nuvio.composeapp.generated.resources.settings_subscription_recommended_badge
import nuvio.composeapp.generated.resources.settings_subscription_status
import nuvio.composeapp.generated.resources.settings_subscription_status_active
import nuvio.composeapp.generated.resources.settings_subscription_status_expired
import nuvio.composeapp.generated.resources.settings_subscription_status_revoked
import nuvio.composeapp.generated.resources.settings_subscription_tier_plus
import nuvio.composeapp.generated.resources.settings_subscription_tier_standard
import nuvio.composeapp.generated.resources.settings_subscription_tiers_desc
import nuvio.composeapp.generated.resources.settings_subscription_tiers_title
import org.jetbrains.compose.resources.stringResource
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Key
import androidx.compose.material.icons.rounded.Verified
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import com.nuvio.app.features.license.LicenseInfo
import com.nuvio.app.features.license.LicenseRepository
import com.nuvio.app.features.license.LicenseState
import com.nuvio.app.features.license.activeInfo
import com.nuvio.app.features.license.isPlus

internal fun LazyListScope.accountSettingsContent(
    isTablet: Boolean,
    onCustomizeProfile: (() -> Unit)? = null,
) {
    item {
        if (AppFeaturePolicy.isUserClient) {
            SubscriptionSettingsBody(
                isTablet = isTablet,
                onCustomizeProfile = onCustomizeProfile,
            )
        } else {
            AccountSettingsBody(isTablet = isTablet)
        }
    }
}

@Composable
private fun SubscriptionSettingsBody(
    isTablet: Boolean,
    onCustomizeProfile: (() -> Unit)? = null,
) {
    val licenseState by LicenseRepository.state.collectAsStateWithLifecycle()
    val licenseInfo = licenseState.activeInfo
    val profileState by com.nuvio.app.features.profiles.ProfileRepository.state.collectAsStateWithLifecycle()
    val activeProfile = profileState.activeProfile
    val clipboardManager = LocalClipboardManager.current
    var showDeactivateConfirm by remember { mutableStateOf(false) }
    var copiedKey by remember { mutableStateOf(false) }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        NuvioSurfaceCard {
            Text(
                text = stringResource(Res.string.settings_subscription_profile_appearance_title),
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = stringResource(Res.string.settings_subscription_profile_appearance_desc),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(modifier = Modifier.height(14.dp))
            AccountInfoRow(
                label = stringResource(Res.string.settings_subscription_profile_name),
                value = activeProfile?.name?.takeIf { it.isNotBlank() } ?: licenseInfo?.customerName ?: stringResource(Res.string.settings_subscription_member_default),
                valueColor = MaterialTheme.colorScheme.primary,
            )
            if (onCustomizeProfile != null) {
                Spacer(modifier = Modifier.height(14.dp))
                NuvioPrimaryButton(
                    text = stringResource(Res.string.settings_subscription_customize_profile),
                    onClick = onCustomizeProfile,
                )
            }
        }

        NuvioSurfaceCard {
            Text(
                text = stringResource(Res.string.settings_subscription_and_license),
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(modifier = Modifier.height(14.dp))

            if (licenseInfo != null) {
                val (statusText, statusColor) = when {
                    licenseInfo.status.equals("revoked", ignoreCase = true) -> stringResource(Res.string.settings_subscription_status_revoked) to MaterialTheme.colorScheme.error
                    licenseInfo.status.equals("active", ignoreCase = true) -> stringResource(Res.string.settings_subscription_status_active) to MaterialTheme.colorScheme.primary
                    else -> stringResource(Res.string.settings_subscription_status_expired) to Color(0xFFFFB300)
                }
                AccountInfoRow(
                    label = stringResource(Res.string.settings_subscription_status),
                    value = statusText,
                    valueColor = statusColor,
                )
                Spacer(modifier = Modifier.height(10.dp))

                val tierText = if (licenseInfo.isPlus) stringResource(Res.string.settings_subscription_tier_plus) else stringResource(Res.string.settings_subscription_tier_standard)
                AccountInfoRow(
                    label = stringResource(Res.string.settings_subscription_package_tier),
                    value = tierText,
                    valueColor = MaterialTheme.colorScheme.primary,
                )
                Spacer(modifier = Modifier.height(10.dp))

                val expiryText = if (licenseInfo.expiresAt.isNullOrBlank()) {
                    stringResource(Res.string.settings_subscription_lifetime_access)
                } else {
                    licenseInfo.expiresAt.take(10)
                }
                AccountInfoRow(
                    label = stringResource(Res.string.settings_subscription_expires_on),
                    value = expiryText,
                    valueColor = if (licenseInfo.expiresAt.isNullOrBlank()) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                )
                Spacer(modifier = Modifier.height(10.dp))

                AccountInfoRow(
                    label = stringResource(Res.string.settings_subscription_devices),
                    value = stringResource(Res.string.settings_subscription_devices_active, licenseInfo.activeDevices, licenseInfo.maxDevices),
                )

                val activeProfileName = activeProfile?.name?.takeIf { it.isNotBlank() }
                val licensedToName = licenseInfo.customerName?.takeIf { it.isNotBlank() } ?: activeProfileName
                if (licensedToName != null) {
                    Spacer(modifier = Modifier.height(10.dp))
                    AccountInfoRow(
                        label = stringResource(Res.string.settings_subscription_licensed_to),
                        value = licensedToName,
                    )
                }

                Spacer(modifier = Modifier.height(14.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = stringResource(Res.string.settings_subscription_license_key),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(
                            text = licenseInfo.key,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                    }
                    IconButton(
                        onClick = {
                            clipboardManager.setText(AnnotatedString(licenseInfo.key))
                            copiedKey = true
                        },
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.ContentCopy,
                            contentDescription = stringResource(Res.string.settings_subscription_copy_license_key),
                            tint = if (copiedKey) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            } else {
                Text(
                    text = stringResource(Res.string.settings_subscription_no_license),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        SubscriptionPlansCard(
            isPlusActive = licenseInfo?.isPlus == true,
            isTablet = isTablet,
        )

        NuvioPrimaryButton(
            text = stringResource(Res.string.settings_subscription_logout),
            onClick = { showDeactivateConfirm = true },
        )
    }

    NuvioStatusModal(
        title = stringResource(Res.string.settings_subscription_logout),
        message = stringResource(Res.string.settings_subscription_logout_confirm_message),
        isVisible = showDeactivateConfirm,
        confirmText = stringResource(Res.string.settings_subscription_logout),
        dismissText = stringResource(Res.string.action_cancel),
        onConfirm = {
            showDeactivateConfirm = false
            LicenseRepository.deactivate()
        },
        onDismiss = { showDeactivateConfirm = false },
    )
}

@Composable
private fun AccountSettingsBody(
    isTablet: Boolean,
) {
    val authState by AuthRepository.state.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    var showSignOutConfirm by remember { mutableStateOf(false) }
    var showDeleteConfirm by remember { mutableStateOf(false) }
    var isDeletingAccount by remember { mutableStateOf(false) }
    var deleteErrorMessage by remember { mutableStateOf<String?>(null) }
    val deleteAccountFallbackMessage = stringResource(Res.string.auth_account_deletion_failed)
    val canDeleteAccount = AppFeaturePolicy.accountDeletionEnabled && authState is AuthState.Authenticated

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        NuvioSurfaceCard {
            Text(
                text = stringResource(Res.string.compose_settings_page_account),
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(modifier = Modifier.height(14.dp))

            when (val state = authState) {
                is AuthState.Authenticated -> {
                    state.email?.takeUnless { state.isAnonymous }?.let { email ->
                        AccountInfoRow(
                            label = stringResource(Res.string.settings_account_email),
                            value = email,
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                    }
                    AccountInfoRow(
                        label = stringResource(Res.string.settings_account_status),
                        value = if (state.isAnonymous) {
                            stringResource(Res.string.settings_account_status_anonymous)
                        } else {
                            stringResource(Res.string.settings_account_status_signed_in)
                        },
                        valueColor = MaterialTheme.colorScheme.primary,
                    )
                }
                else -> {
                    Text(
                        text = stringResource(Res.string.settings_account_not_signed_in),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        NuvioPrimaryButton(
            text = stringResource(Res.string.settings_account_sign_out),
            onClick = { showSignOutConfirm = true },
        )

        if (canDeleteAccount) {
            DeleteAccountCard(
                errorMessage = deleteErrorMessage,
                onDeleteClick = {
                    deleteErrorMessage = null
                    showDeleteConfirm = true
                },
            )
        }
    }

    NuvioStatusModal(
        title = stringResource(Res.string.settings_account_sign_out_confirm_title),
        message = stringResource(Res.string.settings_account_sign_out_confirm_message),
        isVisible = showSignOutConfirm,
        confirmText = stringResource(Res.string.settings_account_sign_out),
        dismissText = stringResource(Res.string.action_cancel),
        onConfirm = {
            showSignOutConfirm = false
            scope.launch { AuthRepository.signOut() }
        },
        onDismiss = { showSignOutConfirm = false },
    )

    NuvioStatusModal(
        title = stringResource(Res.string.settings_account_delete_confirm_title),
        message = stringResource(Res.string.settings_account_delete_confirm_message),
        isVisible = showDeleteConfirm,
        isBusy = isDeletingAccount,
        confirmText = stringResource(Res.string.settings_account_delete_account),
        dismissText = stringResource(Res.string.action_cancel),
        onConfirm = {
            if (isDeletingAccount) return@NuvioStatusModal
            isDeletingAccount = true
            scope.launch {
                val result = AuthRepository.deleteAccount()
                isDeletingAccount = false
                showDeleteConfirm = false
                deleteErrorMessage = if (result.isSuccess) {
                    null
                } else {
                    AuthRepository.error.value
                        ?: result.exceptionOrNull()?.message
                        ?: deleteAccountFallbackMessage
                }
            }
        },
        onDismiss = {
            if (!isDeletingAccount) {
                showDeleteConfirm = false
            }
        },
    )
}

@Composable
private fun DeleteAccountCard(
    errorMessage: String?,
    onDeleteClick: () -> Unit,
) {
    val tokens = MaterialTheme.nuvio

    NuvioSurfaceCard {
        Text(
            text = stringResource(Res.string.settings_account_delete_account),
            style = MaterialTheme.typography.titleMedium,
            color = tokens.colors.textPrimary,
            fontWeight = FontWeight.SemiBold,
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = stringResource(Res.string.settings_account_delete_account_description),
            style = MaterialTheme.typography.bodyMedium,
            color = tokens.colors.textMuted,
        )
        errorMessage?.let { message ->
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = message,
                style = MaterialTheme.typography.bodySmall,
                color = tokens.colors.danger,
            )
        }
        Spacer(modifier = Modifier.height(14.dp))
        Button(
            onClick = onDeleteClick,
            modifier = Modifier
                .fillMaxWidth()
                .height(NuvioTokens.Space.s48 + NuvioTokens.Space.s4),
            shape = tokens.shapes.button,
            colors = ButtonDefaults.buttonColors(
                containerColor = tokens.colors.danger,
                contentColor = tokens.colors.textInverse,
            ),
        ) {
            Text(
                text = stringResource(Res.string.settings_account_delete_account),
                style = MaterialTheme.typography.titleMedium,
                textAlign = TextAlign.Center,
            )
        }
    }
}

@Composable
private fun AccountInfoRow(
    label: String,
    value: String,
    valueColor: Color = MaterialTheme.colorScheme.onSurface,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = value,
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.bodyLarge,
            color = valueColor,
            fontWeight = FontWeight.Medium,
            textAlign = TextAlign.End,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun SubscriptionPlansCard(
    isPlusActive: Boolean,
    isTablet: Boolean,
) {
    val standardTitle = stringResource(Res.string.settings_subscription_plan_standard)
    val plusTitle = stringResource(Res.string.settings_subscription_plan_plus)
    val recommendedBadge = stringResource(Res.string.settings_subscription_recommended_badge)

    val standardFeatures = listOf(
        stringResource(Res.string.settings_subscription_feature_en_zh_subs) to true,
        stringResource(Res.string.settings_subscription_feature_ondemand_streaming) to true,
        stringResource(Res.string.settings_subscription_feature_4k_hdr) to true,
        stringResource(Res.string.settings_subscription_feature_standard_support) to true,
        stringResource(Res.string.settings_subscription_feature_up_to_3_devices) to true,
        stringResource(Res.string.settings_subscription_feature_up_to_5_downloads) to true,
    )
    val plusFeatures = listOf(
        stringResource(Res.string.settings_subscription_feature_everything_in_standard) to true,
        stringResource(Res.string.settings_subscription_feature_exclusive_mm_subs) to true,
        stringResource(Res.string.settings_subscription_feature_priority_support) to true,
        stringResource(Res.string.settings_subscription_feature_up_to_6_devices) to true,
        stringResource(Res.string.settings_subscription_feature_unlimited_downloads) to true,
        stringResource(Res.string.settings_subscription_feature_early_access) to true,
    )

    NuvioSurfaceCard {
        Text(
            text = stringResource(Res.string.settings_subscription_tiers_title),
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onSurface,
            fontWeight = FontWeight.SemiBold,
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = stringResource(Res.string.settings_subscription_tiers_desc),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Spacer(modifier = Modifier.height(16.dp))

        if (isTablet) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(androidx.compose.foundation.layout.IntrinsicSize.Max),
                horizontalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                PlanTierCard(
                    title = standardTitle,
                    isCurrentPlan = !isPlusActive,
                    accentColor = Color(0xFF9E9EA7),
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight(),
                    features = standardFeatures,
                )
                PlanTierCard(
                    title = plusTitle,
                    isCurrentPlan = isPlusActive,
                    accentColor = MaterialTheme.colorScheme.primary,
                    highlightBadge = recommendedBadge,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight(),
                    features = plusFeatures,
                )
            }
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                PlanTierCard(
                    title = standardTitle,
                    isCurrentPlan = !isPlusActive,
                    accentColor = Color(0xFF9E9EA7),
                    features = standardFeatures,
                )
                PlanTierCard(
                    title = plusTitle,
                    isCurrentPlan = isPlusActive,
                    accentColor = MaterialTheme.colorScheme.primary,
                    highlightBadge = recommendedBadge,
                    features = plusFeatures,
                )
            }
        }
    }
}

@Composable
private fun PlanTierCard(
    title: String,
    isCurrentPlan: Boolean,
    accentColor: Color,
    modifier: Modifier = Modifier,
    highlightBadge: String? = null,
    features: List<Pair<String, Boolean>>,
) {
    val borderColor = if (isCurrentPlan) accentColor else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)
    val bgColor = if (isCurrentPlan) accentColor.copy(alpha = 0.08f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(bgColor)
            .border(
                width = if (isCurrentPlan) 1.5.dp else 1.dp,
                color = borderColor,
                shape = RoundedCornerShape(14.dp),
            )
            .padding(16.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = if (isCurrentPlan) accentColor else MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.Bold,
            )

            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                if (highlightBadge != null && !isCurrentPlan) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .background(accentColor.copy(alpha = 0.15f))
                            .padding(horizontal = 6.dp, vertical = 2.dp),
                    ) {
                        Text(
                            text = highlightBadge,
                            style = MaterialTheme.typography.labelSmall,
                            color = accentColor,
                            fontWeight = FontWeight.Bold,
                            fontSize = 10.sp,
                        )
                    }
                }
                if (isCurrentPlan) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(accentColor.copy(alpha = 0.2f))
                            .border(1.dp, accentColor, RoundedCornerShape(6.dp))
                            .padding(horizontal = 8.dp, vertical = 3.dp),
                    ) {
                        Text(
                            text = stringResource(Res.string.settings_subscription_active_plan_badge),
                            style = TextStyle(
                                color = accentColor,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                            ),
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            features.forEach { (feature, isIncluded) ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Icon(
                        imageVector = if (isIncluded) Icons.Rounded.CheckCircle else Icons.Rounded.Info,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = if (isIncluded) accentColor else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                    )
                    Text(
                        text = feature,
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (isIncluded) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 13.sp,
                    )
                }
            }
        }
    }
}
