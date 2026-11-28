package com.nuvio.tv.livetv.mobile

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nuvio.tv.R
import com.nuvio.tv.livetv.model.LiveChannel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

/** Phone search: type a channel's name (or number, or a show on now) and tap to watch. */
@Composable
fun MobileSearchScreen(onPlay: () -> Unit) {
    val vm = mobileLiveTvViewModel()
    val ui by vm.uiState.collectAsStateWithLifecycle()
    val user by vm.userState.collectAsStateWithLifecycle()
    val now by vm.now.collectAsStateWithLifecycle()
    val programs by vm.programs.collectAsStateWithLifecycle()
    var text by rememberSaveable { mutableStateOf("") }
    val focus = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    val context = LocalContext.current

    LaunchedEffect(Unit) {
        if (vm.playback.state.value.channelKey != null) vm.playback.stop()
        if (text.isEmpty()) { delay(150); runCatching { focus.requestFocus() } }
    }

    val q = text.trim()
    val hits by produceState(emptyList<LiveChannel>(), q, ui.allVisibleChannels, programs, now) {
        if (q.length < 2) { value = emptyList(); return@produceState }
        delay(250)
        value = withContext(Dispatchers.Default) {
            val byName = ui.allVisibleChannels.filter { it.name.contains(q, true) || it.number.toString() == q }
                .sortedBy { c ->
                    when {
                        c.number.toString() == q || c.name.equals(q, true) -> 0
                        c.name.startsWith(q, true) -> 1
                        else -> 2
                    }
                }
            val seen = byName.mapTo(HashSet()) { it.key }
            val byShow = ui.allVisibleChannels.filter { c ->
                c.key !in seen && programs[c.key]?.any { now >= it.startMs && now < it.stopMs && it.title.contains(q, true) } == true
            }
            (byName + byShow).take(200)
        }
    }

    Column(Modifier.fillMaxSize()) {
        OutlinedTextField(
            value = text,
            onValueChange = { text = it },
            singleLine = true,
            placeholder = { Text(context.getString(R.string.livetv_only_menu_search)) },
            leadingIcon = { Icon(Icons.Default.Search, null, tint = Color.White) },
            trailingIcon = if (text.isNotEmpty()) ({ IconButton(onClick = { text = "" }) { Icon(Icons.Default.Clear, null, tint = Color.White) } }) else null,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { keyboard?.hide() }),
            colors = OutlinedTextFieldDefaults.colors(focusedTextColor = Color.White, unfocusedTextColor = Color.White, focusedBorderColor = Color.White),
            modifier = Modifier.fillMaxWidth().padding(12.dp).focusRequester(focus)
        )
        if (q.length >= 2 && hits.isEmpty()) EmptyNote("—")
        LazyColumn(contentPadding = PaddingValues(bottom = 16.dp)) {
            items(hits, key = { it.key }) { ch ->
                val program = remember(programs, now, ch.key) { vm.currentProgram(ch.key, now) }
                ChannelRow(
                    channel = ch,
                    programTitle = program?.title,
                    progress = program?.let { ((now - it.startMs).toFloat() / (it.stopMs - it.startMs).coerceAtLeast(1)).coerceIn(0f, 1f) },
                    favorite = ch.key in user.favorites,
                    playing = false,
                    onClick = {
                        keyboard?.hide()
                        vm.preview(ch)
                        onPlay()
                    },
                    onLongClick = { vm.toggleFavorite(ch) }
                )
            }
        }
    }
}
