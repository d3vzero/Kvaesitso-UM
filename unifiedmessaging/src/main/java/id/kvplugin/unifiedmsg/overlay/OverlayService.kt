package id.kvplugin.unifiedmsg.overlay

import android.app.Service
import android.content.Intent
import android.os.Binder
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.Process
import android.view.WindowManager
import androidx.core.os.BundleCompat
import com.google.android.libraries.launcherclient.ILauncherOverlay
import com.google.android.libraries.launcherclient.ILauncherOverlayCallback

/**
 * Penyedia "Feed" untuk Kvaesitso (protokol launcher overlay, sama seperti Google Discover).
 * Kvaesitso: Pengaturan → Gestur → Usap ke kanan → Feed → pilih "Conversations".
 */
class OverlayService : Service() {
    override fun onBind(intent: Intent?): IBinder = OverlayBinder(this)
}

private class OverlayBinder(private val service: Service) : ILauncherOverlay.Stub() {

    private val main = Handler(Looper.getMainLooper())
    private var panel: OverlayPanel? = null

    /** Hanya Kvaesitso (dan aplikasi ini sendiri) yang boleh menempelkan panel. */
    private fun callerAllowed(): Boolean {
        val uid = Binder.getCallingUid()
        if (uid == Process.myUid()) return true
        val pkgs = service.packageManager.getPackagesForUid(uid) ?: return false
        return pkgs.any { it.startsWith("de.mm20.launcher2") }
    }

    private fun attach(lp: WindowManager.LayoutParams?, cb: ILauncherOverlayCallback?) {
        if (!callerAllowed()) return
        val token = lp?.token ?: return
        cb ?: return
        main.post {
            panel?.detach()
            panel = OverlayPanel(service, token, cb).also { it.attach() }
        }
    }

    override fun windowAttached2(bundle: Bundle?, cb: ILauncherOverlayCallback?) {
        val lp = bundle?.let {
            BundleCompat.getParcelable(it, "layout_params", WindowManager.LayoutParams::class.java)
        }
        attach(lp, cb)
    }

    override fun windowAttached(lp: WindowManager.LayoutParams?, cb: ILauncherOverlayCallback?, flags: Int) =
        attach(lp, cb)

    override fun windowDetached(isChangingConfigurations: Boolean) {
        main.post { panel?.detach(); panel = null }
    }

    override fun startScroll() { main.post { panel?.startScroll() } }
    override fun onScroll(progress: Float) { main.post { panel?.onScroll(progress) } }
    override fun endScroll() { main.post { panel?.endScroll() } }
    override fun openOverlay(flags: Int) { main.post { panel?.animateTo(1f) } }
    override fun closeOverlay(flags: Int) { main.post { panel?.animateTo(0f) } }

    override fun setActivityState(flags: Int) {
        // bit 0 = launcher started. Kalau launcher tidak terlihat lagi, tutup panel.
        if (flags and 1 == 0) main.post { panel?.closeImmediately() }
    }

    override fun onPause() = Unit
    override fun onResume() = Unit
    override fun requestVoiceDetection(start: Boolean) = Unit
    override fun getVoiceSearchLanguage(): String = "en"
    override fun isVoiceDetectionRunning(): Boolean = false
    override fun hasOverlayContent(): Boolean = true
    override fun unusedMethod() = Unit
    override fun startSearch(data: ByteArray?, bundle: Bundle?): Boolean = false
}
