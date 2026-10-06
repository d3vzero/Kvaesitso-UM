package id.kvplugin.unifiedmsg.ui

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge

/**
 * Versi activity dari panel Conversations.
 * Dipakai kalau tidak memakai mode Feed, mis. gestur "usap kiri → buka aplikasi".
 */
class ConversationsActivity : ComponentActivity(), PanelHost {

    override val state = ConversationsState()

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                when {
                    state.sheet != null -> state.sheet = null
                    state.viewingImage != null -> state.viewingImage = null
                    state.selected != null -> state.selected = null
                    else -> close()
                }
            }
        })
        setContent { ConversationsApp(this) }
    }

    @Suppress("DEPRECATION")
    override fun close() {
        finish()
        overridePendingTransition(android.R.anim.fade_in, android.R.anim.slide_out_right)
    }

    override fun launch(intent: Intent) {
        startActivity(intent)
    }
}
