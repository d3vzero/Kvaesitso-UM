package id.kvplugin.unifiedmsg.overlay

import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.util.Log
import android.view.ContextThemeWrapper
import android.view.Gravity
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.VelocityTracker
import android.view.View
import android.view.ViewConfiguration
import android.view.ViewGroup
import android.view.WindowManager
import android.view.animation.DecelerateInterpolator
import android.view.inputmethod.InputMethodManager
import android.widget.FrameLayout
import androidx.compose.ui.platform.ComposeView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.google.android.libraries.launcherclient.ILauncherOverlayCallback
import id.kvplugin.unifiedmsg.ui.ConversationsApp
import id.kvplugin.unifiedmsg.ui.ConversationsState
import id.kvplugin.unifiedmsg.ui.PanelHost
import kotlin.math.abs

/**
 * Jendela panel yang menempel ke window Kvaesitso dan bergeser masuk dari kiri
 * mengikuti jari (progress 0 = tertutup, 1 = terbuka penuh).
 */
internal class OverlayPanel(
    private val service: Context,
    private val launcherToken: IBinder,
    private val callback: ILauncherOverlayCallback,
) : PanelHost {

    override val state = ConversationsState()

    private val ctx = ContextThemeWrapper(service, android.R.style.Theme_DeviceDefault_NoActionBar)
    private val wm = service.getSystemService(WindowManager::class.java)
    private val owner = PanelLifecycleOwner()
    private lateinit var root: SwipePanelLayout

    private var progress = 0f
    private var interactive = false
    private var attached = false
    private var animator: ValueAnimator? = null

    private val params = WindowManager.LayoutParams().apply {
        width = ViewGroup.LayoutParams.MATCH_PARENT
        height = ViewGroup.LayoutParams.MATCH_PARENT
        type = WindowManager.LayoutParams.TYPE_DRAWN_APPLICATION
        token = launcherToken
        format = PixelFormat.TRANSLUCENT
        gravity = Gravity.TOP or Gravity.START
        flags = WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
            WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS or
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
            WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
        softInputMode = WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) setFitInsetsTypes(0)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
        }
        title = "Conversations overlay"
    }

    @Suppress("DEPRECATION")
    fun attach() {
        root = SwipePanelLayout(
            ctx,
            onDrag = { dx -> dragTo(dx) },
            onRelease = { vx -> settle(vx) },
            onBack = { handleBack() },
        )
        val compose = ComposeView(ctx).apply { setContent { ConversationsApp(this@OverlayPanel) } }
        root.addView(compose, FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT
        ))
        root.setViewTreeLifecycleOwner(owner)
        root.setViewTreeSavedStateRegistryOwner(owner)
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
            root.systemUiVisibility = View.SYSTEM_UI_FLAG_LAYOUT_STABLE or
                View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or
                View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
        }
        root.visibility = View.INVISIBLE

        owner.create()
        try {
            wm.addView(root, params)
            attached = true
            owner.resume()
            callback.overlayStatusChanged(1) // beri tahu launcher: ada konten
        } catch (t: Throwable) {
            Log.e(TAG, "Gagal menempelkan panel ke window launcher", t)
            owner.destroy()
        }
    }

    fun detach() {
        animator?.cancel()
        owner.destroy()
        if (attached) runCatching { wm.removeViewImmediate(root) }
        attached = false
    }

    // ----- digerakkan oleh launcher -----

    fun startScroll() {
        animator?.cancel()
    }

    fun onScroll(p: Float) = applyProgress(p)

    fun endScroll() {
        animateTo(if (progress > 0.4f) 1f else 0f)
    }

    fun closeImmediately() {
        animator?.cancel()
        applyProgress(0f)
        setInteractive(false)
    }

    fun animateTo(target: Float) {
        if (!attached) return
        animator?.cancel()
        if (target < 1f) setInteractive(false)
        animator = ValueAnimator.ofFloat(progress, target).apply {
            duration = (220 + 180 * abs(target - progress)).toLong()
            interpolator = DecelerateInterpolator(1.6f)
            addUpdateListener { applyProgress(it.animatedValue as Float) }
            doOnEndCompat { if (target >= 1f) setInteractive(true) }
            start()
        }
    }

    // ----- digerakkan oleh jari di dalam panel (usap ke kiri untuk menutup) -----

    private fun dragTo(dx: Float) {
        animator?.cancel()
        val w = root.width.takeIf { it > 0 } ?: return
        applyProgress(1f + dx / w)
    }

    private fun settle(velocityX: Float) {
        val close = velocityX < -1200f || progress < 0.6f
        animateTo(if (close) 0f else 1f)
    }

    private fun handleBack(): Boolean {
        if (progress <= 0f) return false
        when {
            state.sheet != null -> state.sheet = null
            state.viewingImage != null -> state.viewingImage = null
            state.selected != null -> state.selected = null
            else -> animateTo(0f)
        }
        return true
    }

    // ----- PanelHost -----

    override fun close() = animateTo(0f)

    override fun launch(intent: Intent) {
        runCatching { service.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
        closeImmediately()
    }

    // ----- helper -----

    private fun applyProgress(p: Float) {
        progress = p.coerceIn(0f, 1f)
        if (!attached) return
        val w = root.width.takeIf { it > 0 } ?: ctx.resources.displayMetrics.widthPixels
        root.translationX = -w * (1f - progress)
        root.visibility = if (progress > 0f) View.VISIBLE else View.INVISIBLE
        runCatching { callback.overlayScrollChanged(progress) }
    }

    private fun setInteractive(value: Boolean) {
        if (!attached || interactive == value) return
        interactive = value
        val block = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
            WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
        params.flags = if (value) params.flags and block.inv() else params.flags or block
        if (!value) {
            root.findFocus()?.clearFocus()
            ctx.getSystemService(InputMethodManager::class.java)
                ?.hideSoftInputFromWindow(root.windowToken, 0)
        }
        runCatching { wm.updateViewLayout(root, params) }
    }

    private inline fun ValueAnimator.doOnEndCompat(crossinline block: () -> Unit) {
        addListener(object : android.animation.AnimatorListenerAdapter() {
            private var cancelled = false
            override fun onAnimationCancel(animation: android.animation.Animator) { cancelled = true }
            override fun onAnimationEnd(animation: android.animation.Animator) { if (!cancelled) block() }
        })
    }

    companion object {
        private const val TAG = "KvConversationsOverlay"
    }
}

/** Menangkap usapan horizontal ke kiri (menutup panel) dan tombol back. */
@SuppressLint("ViewConstructor")
internal class SwipePanelLayout(
    context: Context,
    private val onDrag: (Float) -> Unit,
    private val onRelease: (Float) -> Unit,
    private val onBack: () -> Boolean,
) : FrameLayout(context) {

    private val slop = ViewConfiguration.get(context).scaledTouchSlop
    private var downX = 0f
    private var downY = 0f
    private var dragging = false
    private var tracker: VelocityTracker? = null

    override fun onInterceptTouchEvent(ev: MotionEvent): Boolean {
        when (ev.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                downX = ev.rawX; downY = ev.rawY; dragging = false
                tracker?.recycle()
                tracker = VelocityTracker.obtain().also { it.addMovement(ev) }
            }
            MotionEvent.ACTION_MOVE -> {
                tracker?.addMovement(ev)
                val dx = ev.rawX - downX
                val dy = ev.rawY - downY
                if (dx < -slop * 2 && abs(dx) > abs(dy) * 1.5f) {
                    dragging = true
                    return true
                }
            }
        }
        return false
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(ev: MotionEvent): Boolean {
        if (!dragging) return false
        tracker?.addMovement(ev)
        when (ev.actionMasked) {
            MotionEvent.ACTION_MOVE -> onDrag((ev.rawX - downX).coerceAtMost(0f))
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                tracker?.computeCurrentVelocity(1000)
                onRelease(tracker?.xVelocity ?: 0f)
                tracker?.recycle(); tracker = null
                dragging = false
            }
        }
        return true
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (event.keyCode == KeyEvent.KEYCODE_BACK) {
            if (event.action == KeyEvent.ACTION_UP) onBack()
            return true
        }
        return super.dispatchKeyEvent(event)
    }
}

/** Lifecycle minimal agar Compose bisa berjalan di jendela non-Activity. */
internal class PanelLifecycleOwner : LifecycleOwner, SavedStateRegistryOwner {
    private val registry = LifecycleRegistry(this)
    private val savedState = SavedStateRegistryController.create(this)

    override val lifecycle: Lifecycle get() = registry
    override val savedStateRegistry: SavedStateRegistry get() = savedState.savedStateRegistry

    fun create() {
        savedState.performRestore(null)
        registry.currentState = Lifecycle.State.CREATED
    }

    fun resume() {
        registry.currentState = Lifecycle.State.RESUMED
    }

    fun destroy() {
        if (registry.currentState != Lifecycle.State.INITIALIZED) {
            registry.currentState = Lifecycle.State.DESTROYED
        }
    }
}
