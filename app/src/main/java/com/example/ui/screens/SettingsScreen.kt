package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.automirrored.rounded.Logout
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.CloudSync
import androidx.compose.material.icons.rounded.DarkMode
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.DirectionsCar
import androidx.compose.material.icons.rounded.LightMode
import androidx.compose.material.icons.rounded.NewReleases
import androidx.compose.material.icons.rounded.OpenInBrowser
import androidx.compose.material.icons.rounded.SystemUpdate
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.credentials.CredentialManager
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.BuildConfig
import com.example.auth.AuthManager
import com.example.data.model.CurrencyCatalog
import com.example.data.model.DistanceUnit
import com.example.data.model.MapLayerType
import com.example.data.model.ThemeMode
import com.example.ui.KalyeViewModel
import com.example.ui.components.RigAvatarBadge
import com.example.ui.components.RigAvatarStudioSheet
import com.example.ui.components.VehicleRigIllustration
import com.example.update.AppUpdateChecker
import com.example.update.AppUpdateStatus
import java.util.Locale

@Composable
fun SettingsScreen(
    viewModel: KalyeViewModel,
    bottomBarPadding: PaddingValues,
    onSignedOut: () -> Unit,
    onOpenGarage: () -> Unit = {}
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val credentialManager = remember(context) { CredentialManager.create(context) }

    val preferences by viewModel.preferences.collectAsStateWithLifecycle()
    val categories by viewModel.categories.collectAsStateWithLifecycle()
    val activeVehicle by viewModel.activeVehicle.collectAsStateWithLifecycle()
    val vehicles by viewModel.vehicles.collectAsStateWithLifecycle()
    val updateStatus by viewModel.updateStatus.collectAsStateWithLifecycle()

    var showAddTagDialog by remember { mutableStateOf(false) }
    var showAvatarStudio by remember { mutableStateOf(false) }
    var newTagName by remember { mutableStateOf("") }
    var selectedTagColor by remember { mutableLongStateOf(0xFFB84A27L) }

    val avatarVehicle = remember(vehicles, activeVehicle, preferences.rigAvatar.vehicleId) {
        vehicles.firstOrNull { it.id == preferences.rigAvatar.vehicleId } ?: activeVehicle
    }

    val colorSwatches = remember {
        listOf(
            0xFFB84A27L,
            0xFF2A5C48L,
            0xFF1F6E8CL,
            0xFF9F6410L,
            0xFF5A4E7CL,
            0xFFC24343L
        )
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding()
            .padding(bottomBarPadding),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Column {
                Text(
                    text = "GARAGE & PROFILE",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = "Settings",
                    style = MaterialTheme.typography.headlineLarge
                )
            }
        }

        // Vehicle Garage Quick Access Card
        item {
            Surface(
                onClick = onOpenGarage,
                shape = RoundedCornerShape(22.dp),
                color = MaterialTheme.colorScheme.surface,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                tonalElevation = 1.dp,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("open_garage_button")
            ) {
                val currentRig = activeVehicle
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(width = 84.dp, height = 56.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant),
                        contentAlignment = Alignment.Center
                    ) {
                        if (currentRig != null) {
                            VehicleRigIllustration(
                                bodyStyle = currentRig.bodyStyle,
                                accentColor = Color(currentRig.accentColorHex),
                                rigGear = currentRig.rigGear,
                                modifier = Modifier
                                    .fillMaxWidth(0.88f)
                                    .height(48.dp)
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Rounded.DirectionsCar,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(28.dp)
                            )
                        }
                    }
                    Spacer(modifier = Modifier.width(14.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "VEHICLE GARAGE (${vehicles.size})",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = currentRig?.nickname ?: "No Rig Configured",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = if (currentRig != null) {
                                "${currentRig.makeModel} • ${currentRig.fuelType}"
                            } else {
                                "Tap to add your car or rig"
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Icon(
                        imageVector = Icons.AutoMirrored.Rounded.ArrowForward,
                        contentDescription = "Open Vehicle Garage",
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }

        // Google Account + Cloud Sync Card
        item {
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = MaterialTheme.colorScheme.surface,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                tonalElevation = 1.dp,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        RigAvatarBadge(
                            vehicle = avatarVehicle,
                            spec = preferences.rigAvatar,
                            size = 60.dp,
                            showEditPill = true,
                            onClick = { showAvatarStudio = true },
                            modifier = Modifier.testTag("settings_rig_avatar_badge")
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = viewModel.userDisplayName ?: "Overland Explorer",
                                style = MaterialTheme.typography.titleMedium
                            )
                            Text(
                                text = viewModel.userEmail ?: "Signed in with Google",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        TextButton(
                            onClick = { showAvatarStudio = true },
                            modifier = Modifier.testTag("settings_edit_avatar_button")
                        ) {
                            Icon(Icons.Rounded.AutoAwesome, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Avatar")
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        FilledTonalButton(
                            onClick = { viewModel.syncNow() },
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.weight(1f).testTag("sync_now_button")
                        ) {
                            Icon(Icons.Rounded.CloudSync, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Sync Cloud")
                        }
                        OutlinedButton(
                            onClick = {
                                AuthManager.signOut(credentialManager, scope, onSignedOut)
                            },
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.weight(1f).testTag("sign_out_button")
                        ) {
                            Icon(Icons.AutoMirrored.Rounded.Logout, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Sign Out")
                        }
                    }
                }
            }
        }

        // App Updates & Version Scanner Card
        item {
            AppUpdatesCard(
                repoSlug = viewModel.updateRepoSlug,
                status = updateStatus,
                onCheckUpdates = { viewModel.checkForUpdates(silent = false) },
                onDownloadRelease = { release ->
                    AppUpdateChecker.openUpdateInBrowser(context, release, preferDirectApk = true)
                },
                onViewReleaseNotes = { release ->
                    AppUpdateChecker.openUpdateInBrowser(context, release, preferDirectApk = false)
                }
            )
        }

        // Light / Dark Appearance & Preferences Card
        item {
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = MaterialTheme.colorScheme.surface,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                tonalElevation = 1.dp,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = "APPEARANCE (LIGHT / DARK)",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                    SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                        ThemeMode.entries.forEachIndexed { idx, mode ->
                            SegmentedButton(
                                selected = preferences.themeMode == mode,
                                onClick = { viewModel.setThemeMode(mode) },
                                shape = SegmentedButtonDefaults.itemShape(idx, ThemeMode.entries.size),
                                icon = {
                                    SegmentedButtonDefaults.Icon(active = preferences.themeMode == mode) {
                                        Icon(
                                            imageVector = if (mode == ThemeMode.DARK) Icons.Rounded.DarkMode else Icons.Rounded.LightMode,
                                            contentDescription = null,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                },
                                label = { Text(mode.label) },
                                modifier = Modifier.testTag("theme_mode_${mode.name.lowercase()}")
                            )
                        }
                    }

                    Text(
                        text = "DISTANCE & ELEVATION UNITS",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                    SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                        DistanceUnit.entries.forEachIndexed { idx, unit ->
                            SegmentedButton(
                                selected = preferences.distanceUnit == unit,
                                onClick = { viewModel.setDistanceUnit(unit) },
                                shape = SegmentedButtonDefaults.itemShape(idx, DistanceUnit.entries.size),
                                label = { Text("${unit.label} (${unit.shortLabel})") }
                            )
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "OFFLINE STREET TILES ONLY",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Text(
                                text = "Use cached muted street tiles without network requests",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Switch(
                            checked = preferences.mapLayer == MapLayerType.OFFLINE,
                            onCheckedChange = { offlineOnly ->
                                viewModel.setMapLayer(
                                    if (offlineOnly) MapLayerType.OFFLINE else MapLayerType.STREET
                                )
                            },
                            modifier = Modifier.testTag("offline_tiles_only_switch")
                        )
                    }

                    Text(
                        text = "CURRENCY",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        CurrencyCatalog.supported.forEach { curr ->
                            FilterChip(
                                selected = preferences.currencyCode == curr.code,
                                onClick = { viewModel.setCurrencyCode(curr.code) },
                                label = { Text("${curr.symbol} ${curr.code}") }
                            )
                        }
                    }
                }
            }
        }

        // Customizable Tag Categories Manager
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Custom Tag Categories (${categories.size})",
                    style = MaterialTheme.typography.titleLarge
                )
                FilledTonalButton(
                    onClick = { showAddTagDialog = true },
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.testTag("add_custom_tag_button")
                ) {
                    Icon(Icons.Rounded.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("New Tag")
                }
            }
        }

        items(categories, key = { it.id }) { cat ->
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surface,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                tonalElevation = 1.dp,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(16.dp)
                            .clip(CircleShape)
                            .background(Color(cat.colorHex))
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = cat.name,
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.weight(1f)
                    )
                    if (!cat.defaultTag) {
                        IconButton(onClick = { viewModel.deleteCategory(cat.id) }) {
                            Icon(Icons.Rounded.Delete, contentDescription = "Delete tag")
                        }
                    } else {
                        Text(
                            text = "DEFAULT",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }

    if (showAddTagDialog) {
        AlertDialog(
            onDismissRequest = { showAddTagDialog = false },
            title = { Text("Create Tag Category") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedTextField(
                        value = newTagName,
                        onValueChange = { newTagName = it },
                        label = { Text("Tag Name (e.g., Desert Run)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().testTag("custom_tag_name_input")
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        colorSwatches.forEach { hex ->
                            Box(
                                modifier = Modifier
                                    .size(32.dp)
                                    .clip(CircleShape)
                                    .background(Color(hex))
                                    .clickable { selectedTagColor = hex }
                                    .padding(4.dp)
                            ) {
                                if (selectedTagColor == hex) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .clip(CircleShape)
                                            .background(Color.White.copy(alpha = 0.45f))
                                    )
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.addCustomCategory(newTagName, selectedTagColor, "terrain")
                        newTagName = ""
                        showAddTagDialog = false
                    },
                    modifier = Modifier.testTag("save_custom_tag_button")
                ) { Text("Add Tag") }
            },
            dismissButton = {
                TextButton(onClick = { showAddTagDialog = false }) { Text("Cancel") }
            }
        )
    }

    if (showAvatarStudio) {
        RigAvatarStudioSheet(
            vehicles = vehicles,
            activeVehicle = activeVehicle,
            currentSpec = preferences.rigAvatar,
            onDismiss = { showAvatarStudio = false },
            onSaveAvatar = { spec, chosenVeh, newGear ->
                if (chosenVeh != null && newGear != null) {
                    viewModel.updateVehicle(
                        vehicleId = chosenVeh.id,
                        nickname = chosenVeh.nickname,
                        makeModel = chosenVeh.makeModel,
                        bodyStyle = chosenVeh.bodyStyle,
                        drivetrain = chosenVeh.drivetrain,
                        fuelType = chosenVeh.fuelType,
                        accentColorHex = chosenVeh.accentColorHex,
                        startingOdometerKm = chosenVeh.startingOdometerKm,
                        rigGear = newGear
                    )
                }
                viewModel.updateRigAvatar(spec)
            },
            onAddRigFirst = onOpenGarage
        )
    }
}

@Composable
private fun AppUpdatesCard(
    repoSlug: String,
    status: AppUpdateStatus,
    onCheckUpdates: () -> Unit,
    onDownloadRelease: (com.example.update.AppReleaseInfo) -> Unit,
    onViewReleaseNotes: (com.example.update.AppReleaseInfo) -> Unit
) {
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(
            width = if (status is AppUpdateStatus.UpdateAvailable) 1.5.dp else 1.dp,
            color = if (status is AppUpdateStatus.UpdateAvailable) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.outlineVariant
            }
        ),
        tonalElevation = 1.dp,
        modifier = Modifier
            .fillMaxWidth()
            .testTag("app_updates_card")
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "APP UPDATES & VERSION",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = "Kalye v${BuildConfig.VERSION_NAME}",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "Build ${BuildConfig.VERSION_CODE} • Repo: $repoSlug",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                when (status) {
                    is AppUpdateStatus.UpdateAvailable -> {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.primaryContainer
                        ) {
                            Text(
                                text = "${status.release.tagName} Ready",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                    }
                    is AppUpdateStatus.UpToDate -> {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.secondaryContainer
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.CheckCircle,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSecondaryContainer,
                                    modifier = Modifier.size(14.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = "Up to date",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSecondaryContainer
                                )
                            }
                        }
                    }
                    else -> Unit
                }
            }

            when (status) {
                is AppUpdateStatus.UpdateAvailable -> {
                    val rel = status.release
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Rounded.NewReleases,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "${rel.releaseName}${if (rel.publishedAt.isNotBlank()) " • ${rel.publishedAt}" else ""}",
                                    style = MaterialTheme.typography.titleSmall,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                            if (rel.releaseNotes.isNotBlank()) {
                                Text(
                                    text = rel.releaseNotes.take(260),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            if (!rel.apkAssetName.isNullOrBlank() && rel.apkSizeBytes > 0L) {
                                val mb = rel.apkSizeBytes / (1024.0 * 1024.0)
                                Text(
                                    text = String.format(Locale.US, "APK Asset: %s (%.1f MB)", rel.apkAssetName, mb),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.secondary
                                )
                            }
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = { onDownloadRelease(rel) },
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .weight(1.3f)
                                .height(48.dp)
                                .testTag("download_update_button")
                        ) {
                            Icon(Icons.Rounded.SystemUpdate, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Get ${rel.tagName}")
                        }
                        OutlinedButton(
                            onClick = { onViewReleaseNotes(rel) },
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp)
                                .testTag("view_release_notes_button")
                        ) {
                            Icon(Icons.Rounded.OpenInBrowser, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Release")
                        }
                    }
                }
                is AppUpdateStatus.Error -> {
                    Text(
                        text = status.message,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
                else -> Unit
            }

            FilledTonalButton(
                onClick = onCheckUpdates,
                enabled = status !is AppUpdateStatus.Checking,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .testTag("check_updates_button")
            ) {
                if (status is AppUpdateStatus.Checking) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Scanning Repo for Updates...")
                } else {
                    Icon(Icons.Rounded.SystemUpdate, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        if (status is AppUpdateStatus.Idle) "Check for Updates"
                        else "Scan Repo Again"
                    )
                }
            }
        }
    }
}
