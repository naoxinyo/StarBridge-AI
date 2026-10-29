package com.dshbox.app.ui.settings

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Hub
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.dshbox.app.DshApp
import com.dshbox.app.R
import com.dshbox.app.models.ModelProviderPreset
import com.dshbox.app.models.ModelProviderProfile
import com.dshbox.app.models.ModelProviderStore
import com.dshbox.app.models.ModelProviders
import com.dshbox.app.service.SandboxService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ModelProvidersScreen(onBack: () -> Unit, backEnabled: Boolean, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val store = remember { ModelProviderStore(context) }
    var profiles by remember { mutableStateOf<List<ModelProviderProfile>>(emptyList()) }
    var keyed by remember { mutableStateOf<Set<String>>(emptySet()) }
    var revision by remember { mutableIntStateOf(0) }
    var editing by remember { mutableStateOf<ModelProviderPreset?>(null) }
    var message by remember { mutableStateOf<String?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var restartConfirm by remember { mutableStateOf(false) }
    BackHandler(enabled = backEnabled, onBack = onBack)
    LaunchedEffect(revision) {
        runCatching {
            withContext(Dispatchers.IO) {
                store.profiles() to ModelProviders.presets.filter { store.hasKey(it.id) }.map { it.id }.toSet()
            }
        }.onSuccess { (saved, keys) -> profiles = saved; keyed = keys }
            .onFailure { error = context.getString(R.string.models_load_failed) }
    }
    Scaffold(modifier = modifier, topBar = {
        TopAppBar(title = { Text(stringResource(R.string.models_title)) }, navigationIcon = {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.diagnostics_back)) }
        })
    }) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item {
                Column(Modifier.fillMaxWidth().background(
                    Brush.linearGradient(listOf(Color(0xFF10243A), Color(0xFF184C52))), RoundedCornerShape(22.dp)
                ).padding(22.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Icon(Icons.Default.Hub, null, Modifier.size(32.dp), tint = Color(0xFF86E4D3))
                    Text(stringResource(R.string.models_hero), style = MaterialTheme.typography.headlineSmall, color = Color.White)
                    Text(stringResource(R.string.models_hero_detail), style = MaterialTheme.typography.bodyMedium, color = Color(0xFFCFDFEA))
                }
            }
            item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(stringResource(R.string.models_apply_hint), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    OutlinedButton(onClick = { restartConfirm = true }, modifier = Modifier.fillMaxWidth()) {
                        Text(stringResource(R.string.models_apply))
                    }
                    message?.let { Text(it, color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.bodySmall) }
                    error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
                }
            }
            items(ModelProviders.presets, key = { it.id }) { preset ->
                val profile = profiles.find { it.id == preset.id }
                val ready = profile?.enabled == true && preset.id in keyed
                Card(modifier = Modifier.fillMaxWidth().clickable { editing = preset },
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)) {
                    Row(Modifier.fillMaxWidth().padding(18.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                            Text(preset.name, style = MaterialTheme.typography.titleMedium)
                            Text(preset.description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(stringResource(when {
                                ready -> R.string.models_configured
                                profile != null -> R.string.models_disabled
                                else -> R.string.models_not_configured
                            }), style = MaterialTheme.typography.labelMedium,
                                color = if (ready) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        if (ready) Icon(Icons.Default.CheckCircle, null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.width(8.dp))
                        Icon(Icons.Default.ChevronRight, stringResource(R.string.models_edit))
                    }
                }
            }
            item { Spacer(Modifier.height(24.dp)) }
        }
    }
    editing?.let { preset ->
        ProviderEditor(preset, profiles.find { it.id == preset.id }, preset.id in keyed, store,
            onDismiss = { editing = null }, onSaved = {
                editing = null; revision++
                message = context.getString(R.string.models_saved)
            })
    }
    if (restartConfirm) {
        AlertDialog(onDismissRequest = { restartConfirm = false },
            title = { Text(stringResource(R.string.models_apply)) },
            text = { Text(stringResource(R.string.models_restart_warning)) },
            confirmButton = { TextButton(onClick = {
                restartConfirm = false
                scope.launch {
                    runCatching { withContext(Dispatchers.IO) { store.refreshOverlay() } }
                        .onSuccess {
                            SandboxService.restartDsh(context)
                            message = context.getString(R.string.models_restarting)
                        }.onFailure { error = context.getString(R.string.models_save_failed) }
                }
            }) { Text(stringResource(R.string.action_restart)) } },
            dismissButton = { TextButton(onClick = { restartConfirm = false }) { Text(stringResource(android.R.string.cancel)) } })
    }
}

@Composable
private fun ProviderEditor(
    preset: ModelProviderPreset, profile: ModelProviderProfile?, hasKey: Boolean,
    store: ModelProviderStore, onDismiss: () -> Unit, onSaved: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    var endpoint by remember { mutableStateOf(profile?.baseUrl ?: preset.baseUrl) }
    var protocol by remember { mutableStateOf(profile?.protocol ?: preset.protocol) }
    var models by remember { mutableStateOf(profile?.models?.joinToString("\n") ?: "") }
    var secret by remember { mutableStateOf("") }
    var enabled by remember { mutableStateOf(profile?.enabled ?: true) }
    var clearKey by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var deleting by remember { mutableStateOf(false) }
    AlertDialog(onDismissRequest = { if (!busy) onDismiss() }, title = { Text(preset.name) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(R.string.models_enable), Modifier.weight(1f))
                    Switch(enabled, onCheckedChange = { enabled = it }, enabled = !busy)
                }
                OutlinedTextField(endpoint, { endpoint = it }, Modifier.fillMaxWidth(),
                    label = { Text(stringResource(R.string.models_endpoint)) }, singleLine = true, enabled = !busy,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri))
                Text(stringResource(R.string.models_protocol), style = MaterialTheme.typography.labelLarge)
                ModelProviders.protocols.forEach { value ->
                    FilterChip(selected = protocol == value, onClick = { protocol = value }, label = { Text(value) }, enabled = !busy)
                }
                OutlinedTextField(models, { models = it }, Modifier.fillMaxWidth(), enabled = !busy,
                    label = { Text(stringResource(R.string.models_ids)) }, minLines = 2,
                    supportingText = { Text(stringResource(if (preset.catalog) R.string.models_catalog_hint else R.string.models_custom_hint)) })
                OutlinedTextField(secret, { secret = it }, Modifier.fillMaxWidth(), enabled = !busy,
                    label = { Text(stringResource(if (hasKey) R.string.models_replace_key else R.string.models_key)) },
                    singleLine = true, visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password))
                Text(stringResource(R.string.models_key_hint), style = MaterialTheme.typography.bodySmall)
                if (hasKey) Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(clearKey, { clearKey = it }, enabled = !busy)
                    Text(stringResource(R.string.models_clear_key), style = MaterialTheme.typography.bodySmall)
                }
                error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                if (profile != null) TextButton(onClick = { deleting = true }, enabled = !busy) {
                    Text(stringResource(R.string.models_remove), color = MaterialTheme.colorScheme.error)
                }
            }
        },
        confirmButton = { Button(enabled = !busy, onClick = {
            busy = true; error = null
            scope.launch {
                val result = runCatching { withContext(Dispatchers.IO) {
                    store.save(ModelProviderProfile(preset.id, enabled, endpoint.trim().trimEnd('/'), protocol,
                        models.lines().map(String::trim).filter(String::isNotEmpty).distinct()), secret.trim(), clearKey)
                } }
                busy = false
                result.onSuccess { secret = ""; onSaved() }.onFailure { error = it.message ?: context.getString(R.string.models_save_failed) }
            }
        }) { Text(stringResource(if (busy) R.string.models_saving else R.string.models_save)) } },
        dismissButton = { TextButton(onClick = onDismiss, enabled = !busy) { Text(stringResource(android.R.string.cancel)) } })
    if (deleting) AlertDialog(onDismissRequest = { deleting = false },
        title = { Text(stringResource(R.string.models_remove)) }, text = { Text(stringResource(R.string.models_remove_warning)) },
        confirmButton = { TextButton(onClick = {
            deleting = false; busy = true
            scope.launch {
                val result = runCatching { withContext(Dispatchers.IO) { store.remove(preset.id) } }
                busy = false
                result.onSuccess { onSaved() }.onFailure { error = context.getString(R.string.models_save_failed) }
            }
        }) { Text(stringResource(R.string.models_remove)) } },
        dismissButton = { TextButton(onClick = { deleting = false }) { Text(stringResource(android.R.string.cancel)) } })
}
