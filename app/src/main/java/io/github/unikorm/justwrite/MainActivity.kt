package io.github.unikorm.justwrite

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.drop
import java.io.File

class MainActivity : ComponentActivity() {

    private val noteFile: File by lazy { File(filesDir, "note.txt") }

    @OptIn(FlowPreview::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Cold start: append a fresh empty line so you can write immediately.
        val savedText = if (noteFile.exists()) noteFile.readText() else ""
        val initialText =
            if (savedText.isNotEmpty() && !savedText.endsWith("\n")) savedText + "\n"
            else savedText

        setContent {
            var value by remember {
                mutableStateOf(
                    TextFieldValue(
                        text = initialText,
                        selection = TextRange(initialText.length) // cursor at end
                    )
                )
            }
            val focusRequester = remember { FocusRequester() }
            val scrollState = rememberScrollState()

            // Debounced autosave: writes 500ms after you stop typing.
            LaunchedEffect(Unit) {
                snapshotFlow { value.text }
                    .drop(1)
                    .debounce(500)
                    .collect { text -> noteFile.writeText(text) }
            }

            // Open the keyboard immediately.
            LaunchedEffect(Unit) {
                focusRequester.requestFocus()
            }

            // Keep the last line visible whenever the cursor is at the end.
            // maxValue changes whenever content grows (Enter, typing past a
            // line) AND whenever the keyboard slides in (imePadding shrinks
            // the viewport). Following it keeps the cursor gliding just above
            // the keyboard with no delay hacks.
            LaunchedEffect(Unit) {
                snapshotFlow { scrollState.maxValue }
                    .collect { max ->
                        if (value.selection.end == value.text.length) {
                            scrollState.scrollTo(max)
                        }
                    }
            }

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color(0xFF111111))
                    .systemBarsPadding()
                    .imePadding()
                    .verticalScroll(scrollState)
            ) {
                BasicTextField(
                    value = value,
                    onValueChange = { value = it },
                    textStyle = TextStyle(
                        color = Color(0xFFFFF3B0),
                        fontFamily = FontFamily.Monospace,
                        fontSize = 18.sp,
                        lineHeight = 26.sp
                    ),
                    cursorBrush = SolidColor(Color(0xFFFFF3B0)),
                    modifier = Modifier
                        .fillMaxWidth()
                        // bottom = the breathing room between your current
                        // line and the keyboard. Tune 32.dp to taste.
                        .padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 32.dp)
                        .focusRequester(focusRequester)
                )
            }
        }
    }
}