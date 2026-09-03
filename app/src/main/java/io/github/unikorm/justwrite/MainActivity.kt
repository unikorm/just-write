package io.github.unikorm.justwrite

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
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

private const val CMD_SHOW = "/sysctl -w"
private const val CMD_HIDE = "/sysctl -x"

class MainActivity : ComponentActivity() {

    private lateinit var prefs: Prefs

    @OptIn(FlowPreview::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        prefs = Prefs(this)

        val savedText = prefs.noteFile(this).takeIf { it.exists() }?.readText() ?: ""
        val initialText =
            if (savedText.isNotEmpty() && !savedText.endsWith("\n")) savedText + "\n"
            else savedText

        setContent {
            // ---- settings (compose state, written through to Prefs) ----
            var theme by remember { mutableStateOf(prefs.theme) }
            var textSize by remember { mutableIntStateOf(prefs.textSize) }
            var format by remember { mutableStateOf(prefs.format) }
            var menuVisible by remember { mutableStateOf(prefs.menuVisible) }
            var menuOpen by remember { mutableStateOf(false) }

            val systemDark = isSystemInDarkTheme()
            val dark = when (theme) {
                ThemeMode.SYSTEM -> systemDark
                ThemeMode.LIGHT -> false
                ThemeMode.DARK -> true
            }
            val bgColor = if (dark) Color(0xFF111111) else Color(0xFFFFF3B0)
            val fgColor = if (dark) Color(0xFFFFF3B0) else Color(0xFF111111)

            // ---- editor state ----
            var value by remember {
                mutableStateOf(
                    TextFieldValue(initialText, TextRange(initialText.length))
                )
            }
            val focusRequester = remember { FocusRequester() }
            val scrollState = rememberScrollState()
            var textLayout by remember { mutableStateOf<TextLayoutResult?>(null) }
            var viewportHeight by remember { mutableIntStateOf(0) }
            val density = LocalDensity.current
            val topPadPx = with(density) { 16.dp.toPx() }
            val gapPx = with(density) { 32.dp.toPx() }

            // Debounced autosave (file depends on current format).
            LaunchedEffect(Unit) {
                snapshotFlow { value.text }
                    .drop(1)
                    .debounce(500)
                    .collect { text -> prefs.noteFile(this@MainActivity).writeText(text) }
            }

            LaunchedEffect(Unit) { focusRequester.requestFocus() }

            // Keep the cursor visible (strict on growth/resize, lenient on cursor moves).
            LaunchedEffect(Unit) {
                var lastMax = -1
                var lastVp = -1
                snapshotFlow {
                    Triple(scrollState.maxValue, viewportHeight, value.selection.end)
                }.collect { (max, vp, selEnd) ->
                    val layout = textLayout ?: return@collect
                    val strict = max != lastMax || vp != lastVp
                    lastMax = max; lastVp = vp
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

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(bgColor)
                    .systemBarsPadding()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .imePadding()
                        .onGloballyPositioned { viewportHeight = it.size.height }
                        .verticalScroll(scrollState)
                ) {
                    BasicTextField(
                        value = value,
                        onValueChange = { new ->
                            value = consumeCommands(value, new) { show ->
                                menuVisible = show
                                prefs.menuVisible = show
                                if (!show) menuOpen = false
                            }
                        },
                        onTextLayout = { textLayout = it },
                        textStyle = TextStyle(
                            color = fgColor,
                            fontFamily = FontFamily.Monospace,
                            fontSize = textSize.sp,
                            lineHeight = (textSize + 8).sp
                        ),
                        cursorBrush = SolidColor(fgColor),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 32.dp)
                            .focusRequester(focusRequester)
                    )
                }

                // ---- hidden control plane: wedjat + three cycling options ----
                if (menuVisible) {
                    Column(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(top = 4.dp, end = 8.dp),
                        horizontalAlignment = Alignment.End
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.ic_wedjat),
                            contentDescription = "options",
                            tint = fgColor,
                            modifier = Modifier
                                .size(40.dp)
                                .clickable { menuOpen = !menuOpen }
                                .padding(6.dp)
                        )
                        if (menuOpen) {
                            MenuItem("theme: ${theme.label}", fgColor, bgColor) {
                                theme = theme.next(); prefs.theme = theme
                            }
                            MenuItem("size: $textSize", fgColor, bgColor) {
                                textSize = nextTextSize(textSize); prefs.textSize = textSize
                            }
                            MenuItem("file: .${format.ext}", fgColor, bgColor) {
                                val next = format.next()
                                val newFile = File(filesDir, "just-write.${next.ext}")
                                newFile.writeText(value.text)
                                prefs.noteFile(this@MainActivity).delete()
                                format = next; prefs.format = next
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * Fire only when Enter is pressed at the end of a sysctl command line.
 * The command line and its newline are consumed.
 */
private fun consumeCommands(
    old: TextFieldValue,
    new: TextFieldValue,
    onToggle: (Boolean) -> Unit
): TextFieldValue {
    val text = new.text
    val cursor = new.selection.end
    // Enter was just typed if exactly one char was added and it's '\n' before the cursor.
    val enterPressed = text.length == old.text.length + 1 &&
            cursor > 0 && text[cursor - 1] == '\n'
    if (!enterPressed) return new

    // The line that was completed sits before that newline.
    val lineEnd = cursor - 1
    val lineStart = text.lastIndexOf('\n', lineEnd - 1) + 1
    val show = when (text.substring(lineStart, lineEnd).trim()) {
        CMD_SHOW -> true
        CMD_HIDE -> false
        else -> return new
    }
    onToggle(show)
    // Remove the command line AND its newline.
    return TextFieldValue(text.removeRange(lineStart, cursor), TextRange(lineStart))
}

@Composable
private fun MenuItem(label: String, fg: Color, bg: Color, onClick: () -> Unit) {
    Text(
        text = label,
        color = fg,
        fontFamily = FontFamily.Monospace,
        fontSize = 14.sp,
        modifier = Modifier
            .background(bg)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 8.dp)
    )
}