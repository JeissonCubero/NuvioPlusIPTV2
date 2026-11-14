package com.nuvio.tv.livetv.only

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Text
import com.nuvio.tv.livetv.ui.LiveTvSearchBridge
import com.nuvio.tv.livetv.ui.LiveTvSearchPreview
import com.nuvio.tv.livetv.ui.LiveTvSearchRow
import com.nuvio.tv.livetv.ui.rememberLiveTvSearchResults
import com.nuvio.tv.ui.theme.NuvioTheme

/**
 * Live TV only build: search (it used to be Nuvio's search page, with a Live TV row in it).
 * Finds channels and programmes; OK plays one behind the results, OK again goes full screen.
 */
@Composable
fun LiveTvSearchScreen(onOpenLiveTv: () -> Unit) {
    var text by remember { mutableStateOf(LiveTvSearchBridge.consume().orEmpty()) }
    var submitted by remember { mutableStateOf(text.trim()) }
    var fieldFocused by remember { mutableStateOf(false) }
    val fieldFocus = remember { FocusRequester() }
    val resultsFocus = remember { FocusRequester() }

    // A title sent from the guide ("Find…") while this page is already open.
    LaunchedEffect(Unit) {
        LiveTvSearchBridge.pending.collect { title ->
            if (title != null) {
                LiveTvSearchBridge.consume()
                text = title
                submitted = title.trim()
            }
        }
    }
    LaunchedEffect(Unit) { runCatching { fieldFocus.requestFocus() } }

    val results = rememberLiveTvSearchResults(query = submitted, onOpened = onOpenLiveTv)

    Box(Modifier.fillMaxSize().background(NuvioTheme.colors.Background)) {
        LiveTvSearchPreview(results, Modifier.fillMaxSize())
        Column(Modifier.fillMaxSize().padding(horizontal = 48.dp, vertical = 32.dp)) {
            val shape = RoundedCornerShape(14.dp)
            BasicTextField(
                value = text,
                onValueChange = { text = it },
                singleLine = true,
                textStyle = TextStyle(color = Color.White, fontSize = 20.sp),
                cursorBrush = SolidColor(Color.White),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = {
                    submitted = text.trim()
                    runCatching { resultsFocus.requestFocus() }
                }),
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRequester(fieldFocus)
                    .onFocusChanged { fieldFocused = it.isFocused }
                    .background(Color.White.copy(alpha = 0.08f), shape)
                    .border(
                        if (fieldFocused) 2.dp else 0.dp,
                        if (fieldFocused) NuvioTheme.colors.FocusRing else Color.Transparent,
                        shape
                    )
                    .padding(horizontal = 20.dp, vertical = 16.dp),
                decorationBox = { inner ->
                    Box {
                        if (text.isEmpty()) {
                            Text("Search channels and shows", color = NuvioTheme.colors.TextSecondary, fontSize = 20.sp)
                        }
                        inner()
                    }
                }
            )
            Spacer(Modifier.height(24.dp))
            if (submitted.length >= 2 && results.hits.isEmpty()) {
                Text("Nothing found for \"$submitted\"", color = NuvioTheme.colors.TextSecondary, fontSize = 16.sp)
            }
            LiveTvSearchRow(
                results = results,
                entryFocusRequester = resultsFocus,
                upFocusRequester = fieldFocus
            )
        }
    }
}
