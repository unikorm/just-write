package io.github.unikorm.justwrite

import android.content.Context
import java.io.File

enum class ThemeMode(val label: String) {
    LIGHT("light"), DARK("dark"), SYSTEM("system");
    fun next() = entries[(ordinal + 1) % entries.size]
}

enum class FileFormat(val ext: String, val mime: String) {
    TXT("txt", "text/plain"), MD("md", "text/markdown");
    fun next() = entries[(ordinal + 1) % entries.size]
}

val TEXT_SIZES = listOf(16, 18, 20)
fun nextTextSize(current: Int) = TEXT_SIZES[(TEXT_SIZES.indexOf(current) + 1) % TEXT_SIZES.size]

/** Tiny persistence layer: four settings in app-private SharedPreferences. */
class Prefs(context: Context) {
    private val sp = context.getSharedPreferences("justwrite", Context.MODE_PRIVATE)

    var menuVisible: Boolean
        get() = sp.getBoolean("menuVisible", false)
        set(v) = sp.edit().putBoolean("menuVisible", v).apply()

    var theme: ThemeMode
        get() = ThemeMode.valueOf(sp.getString("theme", ThemeMode.SYSTEM.name)!!)
        set(v) = sp.edit().putString("theme", v.name).apply()

    var textSize: Int
        get() = sp.getInt("textSize", 18)
        set(v) = sp.edit().putInt("textSize", v).apply()

    var format: FileFormat
        get() = FileFormat.valueOf(sp.getString("format", FileFormat.TXT.name)!!)
        set(v) = sp.edit().putString("format", v.name).apply()

    fun noteFile(context: Context): File = File(context.filesDir, "just-write.${format.ext}")
}