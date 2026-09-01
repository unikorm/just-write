package io.github.unikorm.justwrite

import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
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
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextLayoutResult
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

    private val noteFile: File by lazy { File(filesDir, "just-write.txt") }

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
            // System theme: dark = your palette, light = inverted.
            val dark = isSystemInDarkTheme()
            val bgColor = if (dark) Color(0xFF111111) else Color(0xFFFFF3B0)
            val fgColor = if (dark) Color(0xFFFFF3B0) else Color(0xFF111111)

            var value by remember {
                mutableStateOf(
                    TextFieldValue(
                        text = initialText,
                        selection = TextRange(initialText.length)
                    )
                )
            }
            val focusRequester = remember { FocusRequester() }
            val scrollState = rememberScrollState()

            var textLayout by remember { mutableStateOf<TextLayoutResult?>(null) }
            var viewportHeight by remember { mutableIntStateOf(0) }
            val density = LocalDensity.current
            val topPadPx = with(density) { 16.dp.toPx() }
            val gapPx = with(density) { 32.dp.toPx() }

            // Debounced autosave.
            LaunchedEffect(Unit) {
                snapshotFlow { value.text }
                    .drop(1)
                    .debounce(500)
                    .collect { text -> noteFile.writeText(text) }
            }

            LaunchedEffect(Unit) {
                focusRequester.requestFocus()
            }

            // Keep the cursor visible.
// Strict (full 32dp gap) when content grows or the keyboard resizes;
// lenient (only if actually hidden) when just the cursor moved,
// so taps/typing at wrap boundaries don't nudge the text.
            LaunchedEffect(Unit) {
                var lastMax = -1
                var lastVp = -1
                snapshotFlow {
                    Triple(scrollState.maxValue, viewportHeight, value.selection.end)
                }.collect { (max, vp, selEnd) ->
                    val layout = textLayout ?: return@collect
                    val strict = max != lastMax || vp != lastVp
                    lastMax = max
                    lastVp = vp

                    val offset = selEnd.coerceIn(0, layout.layoutInput.text.length)
                    val rect = layout.getCursorRect(offset)
                    val cursorTop = rect.top + topPadPx
                    val cursorBottom = rect.bottom + topPadPx
                    val visibleBottom = scrollState.value + vp

                    val hiddenBelow =
                        if (strict) cursorBottom + gapPx > visibleBottom
                        else cursorBottom > visibleBottom

                    when {
                        hiddenBelow -> scrollState.scrollTo(
                            (cursorBottom + gapPx - vp).toInt().coerceIn(0, max)
                        )
                        cursorTop < scrollState.value -> scrollState.scrollTo(
                            cursorTop.toInt().coerceAtLeast(0)
                        )
                    }
                }
            }

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .background(bgColor)
                    .systemBarsPadding()
                    .imePadding()
                    .onGloballyPositioned { viewportHeight = it.size.height }
                    .verticalScroll(scrollState)
            ) {
                BasicTextField(
                    value = value,
                    onValueChange = { value = it },
                    onTextLayout = { textLayout = it },
                    textStyle = TextStyle(
                        color = fgColor,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 18.sp,
                        lineHeight = 26.sp
                    ),
                    cursorBrush = SolidColor(fgColor),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 32.dp)
                        .focusRequester(focusRequester)
                )
            }
        }
    }
}