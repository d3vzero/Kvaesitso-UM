package id.kvplugin.unifiedmsg

import android.app.Activity
import android.os.Bundle
import android.widget.Toast

/** Trampoline untuk hasil pencarian: kvmsg://open?key=...  /  kvmsg://number?d=... */
class OpenChatActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val data = intent?.data
        runCatching {
            when (data?.host) {
                "open" -> data.getQueryParameter("key")?.let {
                    if (!ChatActions.openChatAnyApp(this, it)) {
                        Toast.makeText(this, R.string.um_fallback_open_app, Toast.LENGTH_SHORT).show()
                    }
                }
                "number" -> data.getQueryParameter("d")?.let { ChatActions.openNumber(this, it) }
            }
        }
        finish()
    }
}
