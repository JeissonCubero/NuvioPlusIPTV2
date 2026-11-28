package com.nuvio.tv.livetv.mobile

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nuvio.tv.R
import com.nuvio.tv.livetv.model.EpgSource
import com.nuvio.tv.livetv.model.PlaylistSource
import com.nuvio.tv.livetv.ui.LiveTvSettingsViewModel

private val CardColor = Color(0xFF16161A)
private val Muted = Color(0xFFA8A8B0)

/**
 * Phone settings: your lists (M3U links and Xtream logins), guide (EPG) links, a few Live TV
 * options and app updates. The rest of Live TV's settings are on the TV.
 */
@Composable
fun MobileSettingsScreen(onCheckUpdates: () -> Unit, vm: LiveTvSettingsViewModel = hiltViewModel()) {
    val playlists by vm.playlists.collectAsStateWithLifecycle()
    val epgs by vm.epgSources.collectAsStateWithLifecycle()
    val settings by vm.settings.collectAsStateWithLifecycle()
    val status by vm.status.collectAsStateWithLifecycle()
    var addList by remember { mutableStateOf(false) }
    var addEpg by remember { mutableStateOf(false) }
    var editPrefixes by remember { mutableStateOf(false) }
    var editPin by remember { mutableStateOf(false) }
    var confirmRemove by remember { mutableStateOf<Pair<String, () -> Unit>?>(null) }

    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item {
            Text(stringResource(R.string.livetv_only_menu_settings), color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold)
            if (status.loading && !status.message.isNullOrBlank()) {
                Text(status.message.orEmpty(), color = Muted, fontSize = 13.sp, modifier = Modifier.padding(top = 4.dp))
            }
        }

        // ---------------------------------------------------------------- lists
        item { Section(stringResource(R.string.livetv_only_m_lists)) }
        if (playlists.isEmpty()) item { Text(stringResource(R.string.livetv_only_mobile_no_lists), color = Muted, fontSize = 14.sp) }
        items(playlists, key = { it.id }) { pl ->
            PlaylistCard(
                pl = pl,
                onEnabled = { vm.setPlaylistEnabled(pl.id, it) },
                onRefresh = { vm.updatePlaylistNow(pl) },
                onRemove = { confirmRemove = pl.name to { vm.removePlaylist(pl.id) } }
            )
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Button(onClick = { addList = true }, colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = Color.Black)) {
                    Icon(Icons.Default.Add, null); Spacer(Modifier.width(6.dp)); Text(stringResource(R.string.livetv_only_m_add_list))
                }
                OutlinedButton(onClick = { vm.refreshAll() }) {
                    Icon(Icons.Default.Refresh, null, tint = Color.White); Spacer(Modifier.width(6.dp))
                    Text(stringResource(R.string.livetv_only_m_update_all), color = Color.White)
                }
            }
        }

        // ---------------------------------------------------------------- guides
        item { Section(stringResource(R.string.livetv_only_m_guides)) }
        if (epgs.isEmpty()) item { Text(stringResource(R.string.livetv_only_m_no_guides), color = Muted, fontSize = 14.sp) }
        items(epgs, key = { it.id }) { e ->
            EpgCard(e, onEnabled = { vm.setEpgEnabled(e.id, it) }, onRemove = { confirmRemove = e.name to { vm.removeEpg(e.id) } })
        }
        item {
            OutlinedButton(onClick = { addEpg = true }) {
                Icon(Icons.Default.Add, null, tint = Color.White); Spacer(Modifier.width(6.dp))
                Text(stringResource(R.string.livetv_only_m_add_guide), color = Color.White)
            }
        }

        // ---------------------------------------------------------------- options
        item { Section(stringResource(R.string.livetv_only_m_options)) }
        item {
            OptionRow(
                stringResource(R.string.livetv_only_hide_groups_title),
                settings.hiddenGroupPrefixes.ifBlank { stringResource(R.string.livetv_only_hide_groups_none) }
            ) { editPrefixes = true }
        }
        item {
            OptionRow(
                stringResource(R.string.livetv_only_m_pin),
                if (settings.parentalPin.length >= 4) "••••" else stringResource(R.string.livetv_only_hide_groups_none)
            ) { editPin = true }
        }
        item {
            SwitchRow(stringResource(R.string.livetv_only_m_lock_adult), settings.lockAdultContent) { on ->
                vm.update { it.copy(lockAdultContent = on) }
            }
        }

        // ---------------------------------------------------------------- app
        item { Section(stringResource(R.string.livetv_only_m_app)) }
        item {
            OptionRow(
                stringResource(R.string.livetv_only_menu_updates),
                "Live TV 1.0.${com.nuvio.tv.livetv.only.AppUpdater.currentBuild}",
                onClick = onCheckUpdates
            )
        }
        item { Spacer(Modifier.height(24.dp)) }
    }

    if (addList) AddListDialog(
        onDismiss = { addList = false },
        onM3u = { name, url, ua -> vm.addPlaylist(name, url, ua); addList = false },
        onXtream = { name, server, user, pass -> vm.addXtream(name, server, user, pass); addList = false }
    )
    if (addEpg) TextFieldsDialog(
        title = stringResource(R.string.livetv_only_m_add_guide),
        fields = listOf(stringResource(R.string.livetv_only_m_name), stringResource(R.string.livetv_only_m_url)),
        onDismiss = { addEpg = false },
        onSave = { v -> if (v[1].isNotBlank()) { vm.addEpg(v[0].ifBlank { "EPG" }, v[1].trim()); addEpg = false } }
    )
    if (editPrefixes) TextFieldsDialog(
        title = stringResource(R.string.livetv_only_hide_groups_title),
        fields = listOf(stringResource(R.string.livetv_only_hide_groups_hint)),
        initial = listOf(settings.hiddenGroupPrefixes),
        note = stringResource(R.string.livetv_only_hide_groups_subtitle),
        onDismiss = { editPrefixes = false },
        onSave = { v ->
            val cleaned = v[0].split(',', ';').map { it.trim() }.filter { it.isNotEmpty() }.joinToString(", ")
            vm.update { it.copy(hiddenGroupPrefixes = cleaned) }; editPrefixes = false
        }
    )
    if (editPin) TextFieldsDialog(
        title = stringResource(R.string.livetv_only_m_pin),
        fields = listOf(stringResource(R.string.livetv_only_m_pin_hint)),
        numeric = true,
        note = stringResource(R.string.livetv_only_m_pin_note),
        onDismiss = { editPin = false },
        onSave = { v ->
            val pin = v[0].filter(Char::isDigit)
            if (pin.isEmpty() || pin.length >= 4) { vm.update { it.copy(parentalPin = pin) }; editPin = false }
        }
    )
    confirmRemove?.let { (name, action) ->
        AlertDialog(
            onDismissRequest = { confirmRemove = null },
            title = { Text(stringResource(R.string.livetv_only_m_remove_q, name)) },
            confirmButton = { TextButton(onClick = { action(); confirmRemove = null }) { Text(stringResource(R.string.livetv_only_m_remove)) } },
            dismissButton = { TextButton(onClick = { confirmRemove = null }) { Text(stringResource(android.R.string.cancel)) } }
        )
    }
}

@Composable
private fun Section(title: String) {
    Text(title, color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 12.dp))
}

@Composable
private fun PlaylistCard(pl: PlaylistSource, onEnabled: (Boolean) -> Unit, onRefresh: () -> Unit, onRemove: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().background(CardColor, RoundedCornerShape(12.dp)).padding(start = 14.dp, end = 4.dp, top = 10.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(pl.name, color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            val kind = when { pl.isStalker -> "Stalker"; pl.isXtream -> "Xtream"; else -> "M3U" }
            Text("$kind · ${pl.channelCount} ch", color = Muted, fontSize = 12.sp)
            pl.lastError?.takeIf { it.isNotBlank() }?.let { Text(it, color = Color(0xFFFF8A80), fontSize = 12.sp, maxLines = 2) }
        }
        Switch(checked = pl.enabled, onCheckedChange = onEnabled, colors = SwitchDefaults.colors(checkedTrackColor = Color.White, checkedThumbColor = Color.Black))
        IconButton(onClick = onRefresh) { Icon(Icons.Default.Refresh, null, tint = Color.White) }
        IconButton(onClick = onRemove) { Icon(Icons.Default.Delete, null, tint = Muted) }
    }
}

@Composable
private fun EpgCard(e: EpgSource, onEnabled: (Boolean) -> Unit, onRemove: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().background(CardColor, RoundedCornerShape(12.dp)).padding(start = 14.dp, end = 4.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(e.name, color = Color.White, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(e.url, color = Muted, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Switch(checked = e.enabled, onCheckedChange = onEnabled, colors = SwitchDefaults.colors(checkedTrackColor = Color.White, checkedThumbColor = Color.Black))
        IconButton(onClick = onRemove) { Icon(Icons.Default.Delete, null, tint = Muted) }
    }
}

@Composable
private fun OptionRow(title: String, value: String, onClick: () -> Unit) {
    Column(
        Modifier.fillMaxWidth().background(CardColor, RoundedCornerShape(12.dp)).clickable(onClick = onClick).padding(horizontal = 14.dp, vertical = 12.dp)
    ) {
        Text(title, color = Color.White, fontSize = 15.sp)
        Text(value, color = Muted, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun SwitchRow(title: String, on: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().background(CardColor, RoundedCornerShape(12.dp)).clickable { onChange(!on) }.padding(horizontal = 14.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(title, color = Color.White, fontSize = 15.sp, modifier = Modifier.weight(1f))
        Switch(checked = on, onCheckedChange = onChange, colors = SwitchDefaults.colors(checkedTrackColor = Color.White, checkedThumbColor = Color.Black))
    }
}

@Composable
private fun field(label: String, value: String, onChange: (String) -> Unit, password: Boolean = false, numeric: Boolean = false) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(label) },
        singleLine = true,
        visualTransformation = if (password) PasswordVisualTransformation() else androidx.compose.ui.text.input.VisualTransformation.None,
        keyboardOptions = KeyboardOptions(
            keyboardType = when {
                numeric -> KeyboardType.NumberPassword
                password -> KeyboardType.Password
                else -> KeyboardType.Uri
            }
        ),
        colors = OutlinedTextFieldDefaults.colors(focusedTextColor = Color.White, unfocusedTextColor = Color.White, focusedBorderColor = Color.White),
        modifier = Modifier.fillMaxWidth()
    )
}

@Composable
private fun AddListDialog(
    onDismiss: () -> Unit,
    onM3u: (String, String, String) -> Unit,
    onXtream: (String, String, String, String) -> Unit
) {
    var xtream by remember { mutableStateOf(false) }
    var name by remember { mutableStateOf("") }
    var url by remember { mutableStateOf("") }
    var ua by remember { mutableStateOf("") }
    var server by remember { mutableStateOf("") }
    var user by remember { mutableStateOf("") }
    var pass by remember { mutableStateOf("") }
    val nameLabel = stringResource(R.string.livetv_only_m_name)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.livetv_only_m_add_list)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                    SegmentedButton(selected = !xtream, onClick = { xtream = false }, shape = SegmentedButtonDefaults.itemShape(0, 2)) { Text("M3U") }
                    SegmentedButton(selected = xtream, onClick = { xtream = true }, shape = SegmentedButtonDefaults.itemShape(1, 2)) { Text("Xtream") }
                }
                field(nameLabel, name, { name = it })
                if (!xtream) {
                    field(stringResource(R.string.livetv_only_m_url), url, { url = it })
                    field(stringResource(R.string.livetv_only_m_user_agent), ua, { ua = it })
                } else {
                    field(stringResource(R.string.livetv_only_m_server), server, { server = it })
                    field(stringResource(R.string.livetv_only_m_user), user, { user = it })
                    field(stringResource(R.string.livetv_only_m_password), pass, { pass = it }, password = true)
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                if (!xtream && url.isNotBlank()) onM3u(name.ifBlank { "Playlist" }, url.trim(), ua.trim())
                if (xtream && server.isNotBlank() && user.isNotBlank()) onXtream(name.ifBlank { "Xtream" }, server.trim(), user.trim(), pass)
            }) { Text(stringResource(R.string.livetv_only_m_add)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(android.R.string.cancel)) } }
    )
}

@Composable
private fun TextFieldsDialog(
    title: String,
    fields: List<String>,
    onDismiss: () -> Unit,
    onSave: (List<String>) -> Unit,
    initial: List<String> = emptyList(),
    note: String? = null,
    numeric: Boolean = false
) {
    var values by remember { mutableStateOf(fields.indices.map { initial.getOrNull(it).orEmpty() }) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                note?.let { Text(it, fontSize = 13.sp, color = Muted) }
                fields.forEachIndexed { i, label ->
                    field(label, values[i], { v -> values = values.toMutableList().also { it[i] = v } }, numeric = numeric)
                }
            }
        },
        confirmButton = { TextButton(onClick = { onSave(values) }) { Text(stringResource(R.string.action_save)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(android.R.string.cancel)) } }
    )
}
