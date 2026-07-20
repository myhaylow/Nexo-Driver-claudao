package br.com.nexo.driver.overlay

import android.content.Context
import android.graphics.PixelFormat
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.WindowManager
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import br.com.nexo.driver.ui.theme.ColorVisionScheme
import br.com.nexo.driver.ui.theme.DriverInteligenteTheme
import br.com.nexo.driver.ui.theme.DriverThemeMode
import br.com.nexo.driver.ui.theme.DriverVisualStyle
import br.com.nexo.driver.overlay.preferences.SharedPreferencesOverlayPositionStore

/**
 * Non-touchable overlay window. It deliberately cannot cover or activate controls from
 * another app; placement and touch customization are introduced after capture validation.
 * All methods must be called on the main thread.
 */
class WindowManagerOfferOverlay(context: Context) : AutoCloseable {
    private val appContext = context.applicationContext
    private val windowManager = context.getSystemService(WindowManager::class.java)
    private val positionStore = SharedPreferencesOverlayPositionStore.create(appContext)
    private val overlayModel = mutableStateOf<OfferOverlayUiModel?>(null)
    private val appearance = mutableStateOf(OverlayAppearance())
    // This handler is private to the overlay, so cancelling its callbacks cannot affect the app.
    private val autoDismissHandler = Handler(Looper.getMainLooper())
    private val autoDismiss = OverlayAutoDismissController(
        postDelayed = { delayMs, action -> autoDismissHandler.postDelayed(action, delayMs) },
        cancelPending = { autoDismissHandler.removeCallbacksAndMessages(null) },
        onTimeout = ::hide,
    )
    private var view: ComposeView? = null
    private var owners: OverlayViewTreeOwners? = null

    fun show(
        model: OfferOverlayUiModel,
        themeMode: DriverThemeMode = DriverThemeMode.SYSTEM,
        fontScale: Float = 1f,
        visualStyle: DriverVisualStyle = DriverVisualStyle.COCKPIT_PRO,
        colorVisionScheme: ColorVisionScheme = ColorVisionScheme.NORMAL,
        cardDurationMs: Long = OverlayAutoDismissController.DEFAULT_VISIBLE_DURATION_MS,
        /** Screen bounds of the ride app's window, used to place the card clear of its card. */
        appWindowBounds: OverlayWindowBounds? = null,
    ) {
        checkMainThread()
        require(fontScale > 0f) { "Overlay font scale must be positive." }
        overlayModel.value = model
        appearance.value = OverlayAppearance(themeMode, fontScale, visualStyle, colorVisionScheme)
        autoDismiss.timeoutMs = cardDurationMs.coerceIn(
            1L,
            OverlayAutoDismissController.MAX_VISIBLE_DURATION_MS,
        )
        if (view != null) {
            runCatching { windowManager.updateViewLayout(view, layoutParams(appWindowBounds)) }
            autoDismiss.restart()
            return
        }
        val viewTreeOwners = OverlayViewTreeOwners()
        val composeView = ComposeView(appContext).apply {
            setViewTreeLifecycleOwner(viewTreeOwners)
            setViewTreeSavedStateRegistryOwner(viewTreeOwners)
            setViewTreeViewModelStoreOwner(viewTreeOwners)
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setContent {
                val currentAppearance = appearance.value
                DriverInteligenteTheme(
                    mode = currentAppearance.themeMode,
                    fontScale = currentAppearance.fontScale,
                    visualStyle = currentAppearance.visualStyle,
                    colorVisionScheme = currentAppearance.colorVisionScheme,
                ) {
                    overlayModel.value?.let { OfferOverlayCard(it) }
                }
            }
        }
        try {
            windowManager.addView(composeView, layoutParams(appWindowBounds))
            view = composeView
            owners = viewTreeOwners
            viewTreeOwners.onAttached()
            autoDismiss.restart()
        } catch (failure: RuntimeException) {
            autoDismiss.cancel()
            view = null
            owners = null
            runCatching { windowManager.removeViewImmediate(composeView) }
            composeView.disposeComposition()
            viewTreeOwners.onDetached()
            overlayModel.value = null
            throw failure
        }
    }

    /**
     * Replaces a visible card's content without extending its eight-second display interval.
     * This is used when a late, read-only enrichment (such as destination/GPS) refines an offer
     * that has already been shown to the driver.
     */
    fun update(
        model: OfferOverlayUiModel,
        themeMode: DriverThemeMode = DriverThemeMode.SYSTEM,
        fontScale: Float = 1f,
        visualStyle: DriverVisualStyle = DriverVisualStyle.COCKPIT_PRO,
        colorVisionScheme: ColorVisionScheme = ColorVisionScheme.NORMAL,
    ) {
        checkMainThread()
        require(fontScale > 0f) { "Overlay font scale must be positive." }
        if (view == null) return
        overlayModel.value = model
        appearance.value = OverlayAppearance(themeMode, fontScale, visualStyle, colorVisionScheme)
    }

    fun hide() {
        checkMainThread()
        autoDismiss.cancel()
        val attachedView = view
        view = null
        if (attachedView != null) {
            runCatching { windowManager.removeViewImmediate(attachedView) }
            attachedView.disposeComposition()
        }
        owners?.onDetached()
        owners = null
        overlayModel.value = null
    }

    override fun close() = hide()

    private fun checkMainThread() {
        check(Looper.myLooper() == Looper.getMainLooper()) {
            "Overlay windows must be managed from the main thread."
        }
    }

    private fun layoutParams(appWindowBounds: OverlayWindowBounds?) = WindowManager.LayoutParams(
        WindowManager.LayoutParams.MATCH_PARENT,
        WindowManager.LayoutParams.WRAP_CONTENT,
        WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
            WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
            // The offer-analysis surface must never be fed back into our own MediaProjection OCR.
            WindowManager.LayoutParams.FLAG_SECURE or
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
        PixelFormat.TRANSLUCENT,
    ).apply {
        val preferred = positionStore.load()
        val metrics = appContext.resources.displayMetrics
        val placement = OverlayPlacement.resolve(
            preferred = preferred,
            appWindow = appWindowBounds,
            screenWidth = metrics.widthPixels,
            screenHeight = metrics.heightPixels,
        )
        gravity = placement.gravity
        y = (placement.verticalOffsetDp * metrics.density).toInt()
        horizontalMargin = placement.horizontalMargin
        title = "DriverInteligenteOfferOverlay"
    }

    /** Supplies the owners normally inherited from an Activity's decor view. */
    private class OverlayViewTreeOwners :
        LifecycleOwner,
        SavedStateRegistryOwner,
        ViewModelStoreOwner {
        private val lifecycleRegistry = LifecycleRegistry(this)
        private val savedStateController = SavedStateRegistryController.create(this)

        override val lifecycle: Lifecycle = lifecycleRegistry
        override val savedStateRegistry: SavedStateRegistry = savedStateController.savedStateRegistry
        override val viewModelStore = ViewModelStore()

        init {
            savedStateController.performAttach()
            savedStateController.performRestore(null)
            lifecycleRegistry.currentState = Lifecycle.State.CREATED
        }

        fun onAttached() {
            lifecycleRegistry.currentState = Lifecycle.State.RESUMED
        }

        fun onDetached() {
            if (lifecycleRegistry.currentState != Lifecycle.State.DESTROYED) {
                lifecycleRegistry.currentState = Lifecycle.State.DESTROYED
                viewModelStore.clear()
            }
        }
    }

    private data class OverlayAppearance(
        val themeMode: DriverThemeMode = DriverThemeMode.SYSTEM,
        val fontScale: Float = 1f,
        val visualStyle: DriverVisualStyle = DriverVisualStyle.COCKPIT_PRO,
        val colorVisionScheme: ColorVisionScheme = ColorVisionScheme.NORMAL,
    )
}
