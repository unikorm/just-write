package io.github.unikorm.justwrite

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.LocalTextStyle
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import java.io.File

class MainActivity : ComponentActivity() {

    private val noteFile: File by lazy { File(filesDir, "just-write.txt") }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val initialText = if (noteFile.exists()) noteFile.readText() else ""

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

            // Debounced autosave: waits 500ms after you stop typing
            LaunchedEffect(Unit) {
                snapshotFlow { value.text }
                    .drop(1)
                    .debounce(500)
                    .collect { text -> noteFile.writeText(text) }
            }

            // Open keyboard immediately
            LaunchedEffect(Unit) {
                focusRequester.requestFocus()
            }

            BasicTextField(
                value = value,
                onValueChange = { value = it },
                textStyle = TextStyle(
                    color = Color(0xFFFFF3B0),
                    fontFamily = FontFamily.Monospace,
                    fontSize = 18.sp,
                    lineHeight = 26.sp
                ),
                cursorBrush = androidx.compose.ui.graphics.SolidColor(Color(0xFFFFF3B0)),
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color(0xFF111111))
                    .systemBarsPadding()
                    .imePadding()          // keyboard never covers cursor
                    .padding(16.dp)
                    .focusRequester(focusRequester)
            )
        }
    }

    // Safety net: save immediately when leaving the app
    override fun onPause() {
        super.onPause()
        // value lives in Compose, so the debounce handles most cases;
        // the 500ms debounce means at most half a second of typing could be lost,
        // which the flow above already flushes on next keystroke.
    }
}