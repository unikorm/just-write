package io.github.unikorm.justwrite

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.core.content.FileProvider

class ShareActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val prefs = Prefs(this)
        val noteFile = prefs.noteFile(this)
        if (noteFile.exists()) {
            val uri = FileProvider.getUriForFile(
                this, "$packageName.fileprovider", noteFile
            )
            val send = Intent(Intent.ACTION_SEND).apply {
                type = prefs.format.mime
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            startActivity(Intent.createChooser(send, null))
        }
        finish()
    }
}