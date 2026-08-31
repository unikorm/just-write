package io.github.unikorm.justwrite

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.core.content.FileProvider
import java.io.File

class ShareActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val noteFile = File(filesDir, "just-write.txt")
        if (noteFile.exists()) {
            val uri = FileProvider.getUriForFile(
                this, "$packageName.fileprovider", noteFile
            )
            val send = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            startActivity(Intent.createChooser(send, null))
        }
        finish()
    }
}