package marcinlowercase.oo.browser


import kotlinx.serialization.Serializable
import androidx.compose.animation.core.Animatable
import androidx.compose.ui.unit.IntOffset
import kotlin.math.roundToInt
import androidx.core.content.ContextCompat
import android.content.pm.PackageManager
import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.pm.ActivityInfo
import android.content.pm.ApplicationInfo
import android.graphics.Bitmap
import android.os.Bundle
import android.util.Log
import android.util.Patterns
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.webkit.ConsoleMessage
import android.webkit.GeolocationPermissions
import android.webkit.JavascriptInterface
import android.webkit.PermissionRequest
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.EaseIn
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.awaitTouchSlopOrCancellation
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.gestures.drag
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusManager
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.SoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.edit
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import marcinlowercase.oo.browser.ui.theme.BrowserTheme
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import kotlin.coroutines.coroutineContext

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            BrowserTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    BrowserScreen()
                }
            }
        }
    }
}

data class BrowserSettings(
    val paddingDp: Float,
    val cornerRadiusDp: Float,
    val isInteractable: Boolean,
    val defaultUrl: String,
    val animationSpeed: Int,
    val singleLineHeight: Int,
    val isDesktopMode: Boolean,
    val desktopModeWidth: Int,
)

enum class GestureNavAction {
    NONE, // The overlay is hidden
    BACK,
    REFRESH,
    FORWARD
}

data class CustomPermissionRequest(
    val title: String,
    val rationale: String,
    val iconResAllow: Int,
    val iconResDeny: Int,
    val permissionsToRequest: List<String>,
    val onResult: (Map<String, Boolean>) -> Unit
)

// This creates the "tunnel" that will provide our settings object.
// We provide a default value as a fallback.
val LocalBrowserSettings = compositionLocalOf {
    BrowserSettings(
        paddingDp = 8f,
        cornerRadiusDp = 24f,
        isInteractable = true,
        defaultUrl = "https://www.google.com/",
        animationSpeed = 300,
        singleLineHeight = 64,
        isDesktopMode = false,
        desktopModeWidth = 820,
    )
}

// The enum for the state of a tab
@Serializable // Marks this class as serializable
enum class TabState {
    ACTIVE,      // The tab currently visible to the user
    BACKGROUND,  // A tab that is loaded but not visible
    FROZEN       // A tab that needs to be reloaded when opened
}

// The data class for a single tab
// Serializable version of WebHistoryItem
@Serializable
data class SerializableHistoryItem(val url: String, val title: String)

// Serializable version of WebBackForwardList
@Serializable
data class SerializableBackForwardList(
    val items: List<SerializableHistoryItem>,
    val currentIndex: Int
)

@Serializable
data class Tab(
    val id: Long = System.currentTimeMillis(),
    var state: TabState = TabState.BACKGROUND,
    var historyState: SerializableBackForwardList? = null
) {
    // A convenient property to get the current URL from our saved state
    val currentUrl: String?
        get() = historyState?.items?.getOrNull(historyState!!.currentIndex)?.url
}

class TabManager(context: Context) {
    private val prefs = context.getSharedPreferences("BrowserTabs", Context.MODE_PRIVATE)
    private val json = Json { ignoreUnknownKeys = true } // Lenient JSON parser

    private val TABS_KEY = "tabs_list_json"

    fun saveTabs(tabs: List<Tab>) {
        // Convert the list of tabs into a single JSON string
        val jsonString = json.encodeToString(tabs)
        prefs.edit {
            putString(TABS_KEY, jsonString)
        }
        Log.d("TabManager", "Tabs saved.")
    }

    fun loadTabs(defaultUrl: String): MutableList<Tab> {
        val jsonString = prefs.getString(TABS_KEY, null)

        return if (jsonString != null) {
            try {
                // Try to decode the saved JSON string back into a list of tabs
                json.decodeFromString<MutableList<Tab>>(jsonString)
            } catch (e: Exception) {
                Log.e("TabManager", "Failed to decode tabs, creating default.", e)
                createDefaultTabs(defaultUrl)
            }
        } else {
            // If no saved data, create a default tab list
            createDefaultTabs(defaultUrl)
        }
    }

    private fun createDefaultTabs(defaultUrl: String): MutableList<Tab> {
        return mutableListOf(
            Tab(
                state = TabState.ACTIVE,
                // Create a default history state for the first launch
                historyState = SerializableBackForwardList(
                    items = listOf(SerializableHistoryItem(url = defaultUrl, title = "")),
                    currentIndex = 0
                )
            )
        )
    }
}

class CustomWebView(context: Context) : WebView(context) {
    var onUrlChangedListener: OnUrlChangedListener? = null
}

interface OnUrlChangedListener {
    fun onUrlChanged(newUrl: String?)
}


@Composable
fun rememberHasDisplayCutout(): State<Boolean> {
    // These are fine, as LocalConfiguration and LocalDensity are ambient Composable properties
    val configuration = LocalConfiguration.current
    val density = LocalDensity.current

    // Directly get the PaddingValues at the Composable level
    // WindowInsets.displayCutout here provides the current insets for the composition
    val displayCutoutPaddingValues =
        WindowInsets.displayCutout.asPaddingValues() // Pass density if needed, or rely on ambient if appropriate for the API version

    // Now, derivedStateOf can read from displayCutoutPaddingValues
    // We also key remember on configuration and density to re-evaluate if they change,
    // and on displayCutoutPaddingValues itself to re-calculate if the insets change.
    val hasCutout = remember(configuration, density, displayCutoutPaddingValues) {
        derivedStateOf {
            // Check if any of the cutout inset dimensions are greater than zero.
            (displayCutoutPaddingValues.calculateTopPadding() > 0.dp ||
                    displayCutoutPaddingValues.calculateLeftPadding(LayoutDirection.Ltr) > 0.dp ||
                    displayCutoutPaddingValues.calculateRightPadding(LayoutDirection.Ltr) > 0.dp)
            // Bottom cutouts are rare, so often omitted from this specific check
        }
    }
    return hasCutout
}

@Composable
fun BrowserScreen(modifier: Modifier = Modifier) {

    /// VARIABLES
    val context = LocalContext.current
    val sharedPrefs =
        remember { context.getSharedPreferences("BrowserPrefs", Context.MODE_PRIVATE) }
    var browserSettings by remember {
        mutableStateOf(
            BrowserSettings(
                paddingDp = sharedPrefs.getFloat("padding_dp", 8f),
                cornerRadiusDp = sharedPrefs.getFloat("corner_radius_dp", 24f),
                isInteractable = sharedPrefs.getBoolean("is_interactable", true),
                defaultUrl = sharedPrefs.getString("default_url", "https://www.google.com/")
                    ?: "https://www.google.com/",
                animationSpeed = sharedPrefs.getInt("animation_speed", 300),
                singleLineHeight = sharedPrefs.getInt("single_line_height", 64),
                isDesktopMode = sharedPrefs.getBoolean("is_desktop_mode", false),
                desktopModeWidth = sharedPrefs.getInt("desktop_mode_width", 820),

                )
        )
    }

    val tabManager = remember { TabManager(context) }
    val tabs = remember {
        mutableStateListOf<Tab>().apply {
            addAll(tabManager.loadTabs(browserSettings.defaultUrl))
        }
    }
    val activeTabIndex = remember {
        mutableIntStateOf(tabs.indexOfFirst { it.state == TabState.ACTIVE }.coerceAtLeast(0))
    }
    val currentTab by remember {
        derivedStateOf { tabs.getOrNull(activeTabIndex.value) }
    }

    var initialLoadDone by rememberSaveable { mutableStateOf(false) }

    var saveTrigger by remember { mutableIntStateOf(0) }


    var textFieldValue by rememberSaveable(stateSaver = TextFieldValue.Saver) {
        mutableStateOf(TextFieldValue(currentTab?.currentUrl ?: "", TextRange(0)))
    }


    var isImmersiveMode by remember { mutableStateOf(false) }

    var isTraverseHistory by remember { mutableStateOf(false) }
    var isLoading by remember { mutableStateOf(false) }
    var isFocusOnTextField by remember { mutableStateOf(false) }

    val keyboardController = LocalSoftwareKeyboardController.current
    val focusManager = LocalFocusManager.current

    var textFieldHeightPx by remember { mutableIntStateOf(0) }
    // Density is needed to convert Px to Dp
    val density = LocalDensity.current
    val hapticFeedback = LocalHapticFeedback.current // <-- ADD THIS LINE


    // Convert the pixel height to Dp
    val textFieldHeightDp = with(density) { textFieldHeightPx.toDp() }

    var isUrlBarVisible by rememberSaveable { mutableStateOf(true) }


    var isOptionsPanelVisible by rememberSaveable { mutableStateOf(false) }

    val offsetY = remember { Animatable(0f) }
    var activeGestureAction by remember { mutableStateOf(GestureNavAction.NONE) }
    var overlayHeightPx by remember { mutableFloatStateOf(0f) }


    // Example: When overlay is visible -> 150 + 0 = 150 padding.
    val webViewPushDownOffset by remember {
        derivedStateOf {
            // We use coerceAtLeast(0f) to prevent any negative padding values
            // during animation overscrolls.
            with(density) {
                (overlayHeightPx + offsetY.value).coerceAtLeast(0f).toDp()
            }
        }
    }


    var backButtonRect by remember { mutableStateOf(Rect.Zero) }
    var refreshButtonRect by remember { mutableStateOf(Rect.Zero) }
    var forwardButtonRect by remember { mutableStateOf(Rect.Zero) }

    val hasDisplayCutout by rememberHasDisplayCutout()


    val animatedPadding by animateDpAsState(
        targetValue = if (!isImmersiveMode) browserSettings.paddingDp.dp else 0.dp,
        label = "Padding Animation",
    )

    val animatedCornerRadius by animateDpAsState(
        targetValue = if (!isImmersiveMode || hasDisplayCutout) browserSettings.cornerRadiusDp.dp else 0.dp,
        label = "Corner Radius Animation",
    )
    val isKeyboardVisibleForPadding =
        WindowInsets.ime.asPaddingValues().calculateBottomPadding() > 0.dp

    // 1. Get the raw cutout padding values.
    val cutoutPaddingValues = WindowInsets.displayCutout.asPaddingValues()
    val cutoutTop = cutoutPaddingValues.calculateTopPadding()
    val cutoutStart = cutoutPaddingValues.calculateLeftPadding(LayoutDirection.Ltr)
    val cutoutEnd = cutoutPaddingValues.calculateRightPadding(LayoutDirection.Ltr)
    val cutoutBottom = cutoutPaddingValues.calculateBottomPadding()

    // 2. Create animated states for each cutout dimension.
    //    They will animate to the cutout value ONLY when isUrlBarVisible is false.
    val animatedCutoutTop by animateDpAsState(
        targetValue = if (!isUrlBarVisible) cutoutTop else 0.dp,
        animationSpec = tween(browserSettings.animationSpeed),
        label = "Cutout Top Animation"
    )
    val animatedCutoutStart by animateDpAsState(
        targetValue = if (!isUrlBarVisible) cutoutStart else 0.dp,
        animationSpec = tween(browserSettings.animationSpeed),
        label = "Cutout Start Animation"
    )
    val animatedCutoutEnd by animateDpAsState(
        targetValue = if (!isUrlBarVisible) cutoutEnd else 0.dp,
        animationSpec = tween(browserSettings.animationSpeed),
        label = "Cutout End Animation"
    )
    val animatedCutoutBottom by animateDpAsState(
        targetValue = if (!isUrlBarVisible) cutoutBottom else 0.dp,
        animationSpec = tween(browserSettings.animationSpeed),
        label = "Cutout Bottom Animation"
    )

    var staticSystemBarBottom by remember { mutableStateOf(0.dp) }
    var staticSystemBarTop by remember { mutableStateOf(0.dp) }

    // Get the raw system bar padding values.
    val currentSystemBarInsets = WindowInsets.systemBars.asPaddingValues()
    val currentSystemBarTop = currentSystemBarInsets.calculateTopPadding()
    val currentSystemBarBottom = currentSystemBarInsets.calculateBottomPadding()

    if (staticSystemBarBottom == 0.dp && currentSystemBarBottom > 0.dp) {
        staticSystemBarBottom = currentSystemBarBottom
    }

    if (staticSystemBarTop == 0.dp && currentSystemBarTop > 0.dp) {
        staticSystemBarTop = currentSystemBarTop
    }

    // Create animated states for the system bar insets.
    val animatedSystemBarTop by animateDpAsState(
        targetValue = if (isUrlBarVisible) staticSystemBarTop else 0.dp,
        animationSpec = if (hasDisplayCutout) tween(browserSettings.animationSpeed) else snap(0), // Always animate smoothly for cutout and snap for full screen
        label = "SystemBar Top Animation"
    )
    val animatedSystemBarBottom by animateDpAsState(
        targetValue = if (!isImmersiveMode && !isKeyboardVisibleForPadding) staticSystemBarBottom else if (isKeyboardVisibleForPadding) browserSettings.paddingDp.dp else 0.dp,
        animationSpec = if (isImmersiveMode || !isKeyboardVisibleForPadding) tween(browserSettings.animationSpeed) else snap(
            0
        ), // Always animate smoothly
        label = "SystemBar Bottom Animation"
    )

    var customView by remember { mutableStateOf<View?>(null) }
    var customViewCallback by remember { mutableStateOf<WebChromeClient.CustomViewCallback?>(null) }


    // We only need the CustomViewCallback as state now.
    var originalOrientation by remember { mutableIntStateOf(ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED) }

    val activity = context as? Activity // Get the activity reference

    // Define your User Agent strings
    val mobileUserAgent =
        "Mozilla/5.0 (Linux; Android 14; Pixel 8 Pro) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/125.0.0.0 Mobile Safari/537.36"
    val desktopUserAgent =
        "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/125.0.0.0 Safari/537.36"


    var pendingPermissionRequest by remember {
        mutableStateOf<CustomPermissionRequest?>(null)
    }


    var colorScheme = ColorScheme(
        backgroundColor = if (isSystemInDarkTheme()) Color.Black else Color.White,
        foregroundColor = if (isSystemInDarkTheme()) Color.White else Color.Black
    )


    val canGoBack by remember {
        derivedStateOf { (currentTab?.historyState?.currentIndex ?: 0) > 0 }
    }
    val canGoForward by remember {
        derivedStateOf {
            val history = currentTab?.historyState
            if (history == null) false else history.currentIndex < history.items.lastIndex
        }
    }

    // --- 2. The Central Synchronizer Function ---
    // It's defined here in the main body of the Composable.
    fun synchronizeState(webView: CustomWebView) {

        val webViewHistory = webView.copyBackForwardList()


        currentTab?.let { tab ->
            val serializableItems = List(webViewHistory.size) { i ->
                val item = webViewHistory.getItemAtIndex(i)
                SerializableHistoryItem(url = item.url ?: "", title = item.title ?: "")
            }
            val webViewSerializableList = SerializableBackForwardList(
                items = serializableItems,
                currentIndex = webViewHistory.currentIndex
            )

            if (tab.historyState != webViewSerializableList) {
                val updatedTab = tab.copy(historyState = webViewSerializableList)
                tabs[activeTabIndex.value] = updatedTab
                saveTrigger++
            }

            Log.e("zzz", tabs[activeTabIndex.value].historyState?.items?.size.toString())
            Log.e("zzz", tabs[activeTabIndex.value].historyState.toString())
        }
    }

    val webView = remember {

        CustomWebView(context).apply {
            // Force WebView to be transparent so Compose can control the background
            setBackgroundColor(android.graphics.Color.TRANSPARENT)

            // The WebChromeClient handles UI-related browser events.
            webChromeClient = object : WebChromeClient() {

                private var fullscreenView: View? = null


                override fun onGeolocationPermissionsShowPrompt(
                    origin: String?,
                    callback: GeolocationPermissions.Callback?
                ) {
                    if (origin == null || callback == null) return

                    // Create a new generic permission request for this specific geolocation prompt.
                    pendingPermissionRequest = CustomPermissionRequest(
                        title = "Location Access Required",
                        rationale = "This website wants to use your device's location.",
                        iconResAllow = R.drawable.ic_location_on,
                        iconResDeny = R.drawable.ic_location_off,
                        permissionsToRequest = listOf(
                            Manifest.permission.ACCESS_FINE_LOCATION,
                            Manifest.permission.ACCESS_COARSE_LOCATION
                        ),
                        // This is the key: the onResult callback for this specific request
                        // knows how to talk back to the WebView's Geolocation callback.
                        onResult = { permissions ->
                            val isGranted =
                                permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
                                        permissions[Manifest.permission.ACCESS_COARSE_LOCATION] == true
                            callback.invoke(origin, isGranted, false)
                        }
                    )
                }


                override fun onPermissionRequest(request: PermissionRequest) {
                    Log.d(
                        "WebViewPermission",
                        "onPermissionRequest called for: ${request.resources.joinToString(", ")} from origin: ${request.origin}"
                    )

                    val requestedAndroidPermissions = mutableListOf<String>()
                    var title = "Permission Required" // Default title
                    var rationale =
                        "'${request.origin}' wants to use your device features." // Default rationale
                    var allowIcon = R.drawable.ic_bug // Default allow icon
                    var denyIcon = R.drawable.ic_bug   // Default deny icon

                    val requestsCamera =
                        request.resources.contains(PermissionRequest.RESOURCE_VIDEO_CAPTURE)
                    val requestsMicrophone =
                        request.resources.contains(PermissionRequest.RESOURCE_AUDIO_CAPTURE)

                    if (requestsCamera) {
                        requestedAndroidPermissions.add(Manifest.permission.CAMERA)
                        title = "Camera Access"
                        rationale = "Allow camera access for video recording."
                        allowIcon = R.drawable.ic_camera_on
                        denyIcon = R.drawable.ic_camera_off
                    } else if (requestsMicrophone) {
                        requestedAndroidPermissions.add(Manifest.permission.RECORD_AUDIO)
                        title = "Microphone Access"
                        rationale = "Allow microphone access for audio recording."
                        allowIcon = R.drawable.ic_mic_on
                        denyIcon = R.drawable.ic_mic_off
                    }

                    // Add other permission mappings if needed
                    if (request.resources.contains(PermissionRequest.RESOURCE_PROTECTED_MEDIA_ID)) {
                        // Handle protected media if needed
                        Log.d(
                            "WebViewPermission",
                            "Protected media ID requested - typically not mapped to runtime permissions"
                        )
                        // If no other Android permissions were added, you might want to deny or handle appropriately.
                        if (requestedAndroidPermissions.isEmpty()) {
                            Log.d(
                                "WebViewPermission",
                                "Protected media ID requested with no other mappable Android permissions; denying request."
                            )
                            request.deny()
                            return
                        }
                    }

                    if (requestedAndroidPermissions.isEmpty()) {
                        Log.d(
                            "WebViewPermission",
                            "No mappable Android permissions for the requested WebView resources; denying request."
                        )
                        request.deny()
                        return
                    }

                    // Check if we already have these permissions
                    val context = this@apply.context
                    val hasAllPermissions = requestedAndroidPermissions.all { permission ->
                        ContextCompat.checkSelfPermission(
                            context,
                            permission
                        ) == PackageManager.PERMISSION_GRANTED
                    }

                    if (hasAllPermissions) {
                        // If we already have permissions, grant them immediately
                        Log.d(
                            "WebViewPermission",
                            "Permissions already granted, granting to WebView"
                        )
                        request.grant(request.resources)
                        return
                    }

                    // Create the custom request
                    pendingPermissionRequest = CustomPermissionRequest(
                        title = title,
                        rationale = rationale,
                        iconResAllow = allowIcon,
                        iconResDeny = denyIcon,
                        permissionsToRequest = requestedAndroidPermissions,
                        onResult = { permissionsResult ->
                            activity?.runOnUiThread {
                                // Check which permissions were actually granted
                                val grantedPermissions = permissionsResult.filter { it.value }.keys

                                // Build a list of WebView resources to grant based on granted Android permissions
                                val resourcesToGrant = mutableListOf<String>()

                                if (grantedPermissions.contains(Manifest.permission.CAMERA) &&
                                    request.resources.contains(PermissionRequest.RESOURCE_VIDEO_CAPTURE)
                                ) {
                                    resourcesToGrant.add(PermissionRequest.RESOURCE_VIDEO_CAPTURE)
                                }

                                if (grantedPermissions.contains(Manifest.permission.RECORD_AUDIO) &&
                                    request.resources.contains(PermissionRequest.RESOURCE_AUDIO_CAPTURE)
                                ) {
                                    resourcesToGrant.add(PermissionRequest.RESOURCE_AUDIO_CAPTURE)
                                }

                                if (resourcesToGrant.isNotEmpty()) {
                                    Log.d(
                                        "WebViewPermission",
                                        "Granting resources: ${resourcesToGrant.joinToString()}"
                                    )
                                    request.grant(resourcesToGrant.toTypedArray())
                                } else {
                                    Log.d(
                                        "WebViewPermission",
                                        "No permissions granted; denying all resources."
                                    )
                                    request.deny()
                                }
                            }
                        }
                    )
                }

                override fun onShowCustomView(view: View?, callback: CustomViewCallback?) {
                    if (fullscreenView != null) {
                        callback?.onCustomViewHidden()
                        return
                    }


                    originalOrientation = activity?.requestedOrientation
                        ?: ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
                    customViewCallback = callback
                    fullscreenView = view

                    // B. Get the root view of the Activity and add our fullscreen view to it.
                    val decorView = activity?.window?.decorView as? ViewGroup
                    decorView?.addView(
                        fullscreenView,
                        ViewGroup.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.MATCH_PARENT
                        )
                    )

                    // C. Now, control the window
                    val insetsController = activity?.let {
                        WindowCompat.getInsetsController(
                            it.window,
                            it.window.decorView
                        )
                    }
                    insetsController?.hide(WindowInsetsCompat.Type.systemBars())
                    activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE

                    // Tell the WebView to resume, as it might have paused.
                    this@apply.onResume()
                }

                override fun onHideCustomView() {
                    val decorView = activity?.window?.decorView as? ViewGroup
                    decorView?.removeView(fullscreenView)
                    fullscreenView = null

                    val insetsController = activity?.let {
                        WindowCompat.getInsetsController(
                            it.window,
                            it.window.decorView
                        )
                    }
                    insetsController?.show(WindowInsetsCompat.Type.systemBars())
                    activity?.requestedOrientation = originalOrientation

                    customViewCallback?.onCustomViewHidden()
                    customViewCallback = null

                    this@apply.onResume()
                }

                override fun onProgressChanged(view: WebView?, newProgress: Int) {
                    super.onProgressChanged(view, newProgress)
                    // Inject our JavaScript helper as the page is loading.
                    val js =
                        "document.documentElement.style.setProperty('--vh', window.innerHeight + 'px');"
                    view?.evaluateJavascript(js, null)
                }

                override fun onConsoleMessage(consoleMessage: ConsoleMessage?): Boolean {
                    consoleMessage?.let {
                        Log.d(
                            "WebViewConsole",
                            "${it.message()} -- From line ${it.lineNumber()} of ${it.sourceId()}"
                        )
                    }
                    return true
                }

                override fun onReceivedTitle(view: WebView?, title: String?) {
                    super.onReceivedTitle(view, title)
                    // When the title changes (which also happens on pushState),
                    // get the current URL and notify our listener.
                    onUrlChangedListener?.onUrlChanged(view?.url)
                }

            }

            // The WebViewClient handles content loading events.
            webViewClient = object : WebViewClient() {


                override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                    super.onPageStarted(view, url, favicon)
                    isLoading = true

                }

                override fun onPageFinished(view: WebView?, currentUrlString: String?) {
                    super.onPageFinished(view, currentUrlString)
                    isLoading = false

                    if (currentUrlString != null) {
                        Log.w("zzz", "")

                        Log.w("zzz", "onPageFinished")
                        Log.w("zzz", "canGoForward: $canGoForward")
                        Log.w("zzz", "")
                        val webViewHistory = this@apply.copyBackForwardList()
                        Log.e("zzz", " ACTUAL WEBVIEW HISTORY ")
                        for (i in 0 until webViewHistory.size) {
                            Log.e("zzz", "$i : " + webViewHistory.getItemAtIndex(i).url)

                        }
                        Log.e("zzz", " ")

                        Log.i("zzz", "Current Items  :  : ${tabs[activeTabIndex.value].historyState?.items}")
                        Log.i("zzz", "Current Index  :  : ${tabs[activeTabIndex.value].historyState?.currentIndex}")
                        if (currentUrlString != tabs[activeTabIndex.value].historyState?.items[tabs[activeTabIndex.value].historyState?.currentIndex?: -1]?.url) {
                            Log.d("zzz", "++++++DIFFERENT")
                            Log.d("zzz", currentUrlString)
                            Log.d("zzz", tabs[activeTabIndex.value].historyState?.items[tabs[activeTabIndex.value].historyState?.currentIndex?: -1]?.url.toString())
                            synchronizeState(this@apply)
                        }
//                        if (isTraverseHistory) {
//                            Log.i("zzz", "isTraverseHistory")
//                            isTraverseHistory = false
//                        } else {
//
//                        }
                    }
                    if (!isFocusOnTextField) url?.let {
                        textFieldValue = TextFieldValue(it, TextRange(it.length))
                    }
                    // --- END OF LOGGING CODE ---


                }

//                override fun onPageFinished(view: WebView?, currentUrl: String?) {
//                    super.onPageFinished(view, currentUrl)
//                    isLoading = false
////                    canGoBack = view?.canGoBack() ?: false
////                    canGoForward = view?.canGoForward() ?: false
////                    currentUrl?.let {
////                        url = it
////                        if (!isFocusOnTextField) textFieldValue =
////                            TextFieldValue(it, TextRange(it.length))
////                    }
//                    // Force a scroll to the top to fix coordinate system bugs
//                    view?.scrollTo(0, 0)
//
//                    // Your JS script for getting the background color
//                    val jsScript =
//                        """"(function() { ... })();"""".trimIndent() // Keep your full script here
//                    view?.evaluateJavascript(jsScript, null)
//
//                    if (browserSettings.isDesktopMode) {
//                        // --- THIS IS THE FINAL, AGGRESSIVE SCRIPT ---
//                        view?.evaluateJavascript(
//                            """"
//            (function() {
//                // The function we want to run to enforce our viewport.
//                function enforceDesktopViewport() {
//                    console.log('Enforcing desktop viewport...');
//                    var meta = document.querySelector('meta[name=viewport]');
//                    if (!meta) {
//                        meta = document.createElement('meta');
//                        meta.setAttribute('name', 'viewport');
//                        document.getElementsByTagName('head')[0].appendChild(meta);
//                    }
//                    // Crucially, check if the content is already correct.
//                    // This prevents an infinite loop of observer callbacks.
//                    if (meta.getAttribute('content') !== 'width=${browserSettings.desktopModeWidth}') {
//                        console.log('Viewport was wrong, correcting to width=${browserSettings.desktopModeWidth}.');
//                        meta.setAttribute('content', 'width=${browserSettings.desktopModeWidth}');
//                    }
//                }
//
//                // 1. Enforce it immediately.
//                enforceDesktopViewport();
//
//                // 2. Create an observer to watch for any changes to the <head> element.
//                //    This will detect if the site's own JS tries to change the viewport.
//                var observer = new MutationObserver(function(mutations) {
//                    // When a change is detected, run our enforcement function again.
//                    enforceDesktopViewport();
//                });
//
//                // 3. Start observing. We watch for changes to child elements in the head.
//                var head = document.getElementsByTagName('head')[0];
//                if (head) {
//                    observer.observe(head, {
//                        childList: true,
//                        subtree: true
//                    });
//                }
//            })();
//            """".trimIndent(), null
//                        )
//                    }
//
//
//                }

                override fun shouldInterceptRequest(
                    view: WebView?,
                    request: WebResourceRequest?
                ): WebResourceResponse? {
                    request?.requestHeaders?.put("Origin", currentTab?.currentUrl)
                    return super.shouldInterceptRequest(view, request)
                }
            }


//            updateWebViewSettings(this, browserSettings.isDesktopMode)

            // Apply all your production-grade settings
            // --- This initial setup block should contain ALL static settings ---
            settings.apply {
                javaScriptEnabled = true
                domStorageEnabled = true
                allowFileAccess = true
                allowContentAccess = true
                mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
                javaScriptCanOpenWindowsAutomatically = true
                cacheMode = WebSettings.LOAD_CACHE_ELSE_NETWORK

                mediaPlaybackRequiresUserGesture = false


                // CRITICAL: Zoom must be supported for overview mode to work reliably.
                setSupportZoom(true)
                builtInZoomControls = true
                displayZoomControls = false // Hide the on-screen +/- buttons
            }

            // Enable remote debugging for debug builds
            if (0 != (context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE)) {
                WebView.setWebContentsDebuggingEnabled(true)
            }

            // Ensure hardware acceleration
            setLayerType(WebView.LAYER_TYPE_HARDWARE, null)

            // Add your JS interface
            addJavascriptInterface(WebAppInterface(), "Android")

        }
    }


    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions(),
        onResult = { permissions ->
            // When the system dialog returns a result, trigger the onResult
            // callback that we stored in our pendingPermissionRequest.
            pendingPermissionRequest?.onResult?.invoke(permissions)

            // Clear the request to hide the panel.
            pendingPermissionRequest = null
        }
    )

//    val lifecycleOwner = LocalLifecycleOwner.current
//    DisposableEffect(lifecycleOwner) {
//        val observer = LifecycleEventObserver { _, event ->
//            when (event) {
//                Lifecycle.Event.ON_PAUSE -> {
//                    Log.d("WebViewLifecycle", "PAUSING WebView")
//                    webView.onPause() // Pauses JavaScript timers, etc.
//                }
//                Lifecycle.Event.ON_RESUME -> {
//                    Log.d("WebViewLifecycle", "RESUMING WebView")
//                    webView.onResume() // Resumes the WebView
//                }
//                else -> {} // No need to handle other events
//            }
//        }
//        lifecycleOwner.lifecycle.addObserver(observer)
//        onDispose {
//            lifecycleOwner.lifecycle.removeObserver(observer)
//        }
//    }


    // FUNCTIONS


    // This function will be our single, safe way to update settings.
    val updateBrowserSettings = { newSettings: BrowserSettings ->
        browserSettings = newSettings
        Log.e("updateBrowserSettings", browserSettings.toString())
    }

    // LAUNCH EFFECTS
    //

    // This effect handles SPA navigation by also calling our synchronizer
//    LaunchedEffect(webView) {
//        (webView as? CustomWebView)?.onUrlChangedListener = object : OnUrlChangedListener {
//            override fun onUrlChanged(newUrl: String?) {
//                if (newUrl != null) {
//                    synchronizeOnUncommandedNavigation(newUrl)
//                }
//                if (!isFocusOnTextField) newUrl?.let {
//                    textFieldValue = TextFieldValue(it, TextRange(it.length))
//                }
//            }
//        }
//    }

    // This effect now ONLY handles the very first restoration of state.

    LaunchedEffect(webView) {
        (webView as? CustomWebView)?.onUrlChangedListener = object : OnUrlChangedListener {
            override fun onUrlChanged(newUrl: String?) {
                Log.w("zzz", "")

                Log.w("zzz", "onUrlChanged")
                Log.w("zzz", "")

                if (newUrl != null) {

                    if (!isFocusOnTextField) {
                        textFieldValue = TextFieldValue(newUrl ?: "", TextRange((newUrl ?: "").length))
                    }
                    if (newUrl != tabs[activeTabIndex.value].historyState?.items[tabs[activeTabIndex.value].historyState?.currentIndex?: 0]?.url) {
                        synchronizeState(webView)
                    }
//                    synchronizeState(webView)


                    // When the URL changes, we run the EXACT SAME logic as onPageFinished.
                    // This keeps our state perfectly synchronized.
//                    currentTab?.let { tab ->
//                        Log.e("onUrlChanged", "Tab Before : " + tab.toString())
//
//                        if (tab.currentUrl != newUrl) {
//                            val newHistoryEndIndex = tab.currentUrlIndex + 1
//                            val newHistory = if (newHistoryEndIndex < tab.history.size) {
//                                tab.history.subList(0, newHistoryEndIndex)
//                            } else {
//                                tab.history
//                            }.toMutableList()
//
//                            newHistory.add(newUrl)
//
//                            val updatedTab = tab.copy(
//                                history = newHistory,
//                                currentUrlIndex = newHistory.lastIndex
//                            )
//
//                            tabs[activeTabIndex.value] = updatedTab
//                            saveTrigger++
//                            Log.e("onUrlChanged", "Tab after : " +    tabs[activeTabIndex.value].toString())
//                            Log.e("onUrlChanged", " " )
//
//
//
//                        }
//                    }
                }

                // --- NEW LOGGING CODE for copyBackForwardList() ---
//                val webViewHistoryList = webView.copyBackForwardList()
//
//                if (webViewHistoryList != null) {
//                    Log.d("WebViewHistory", "===================onUrlChanged====================")
//                    Log.d("WebViewHistory", "WebView.copyBackForwardList() Snapshot")
//                    Log.d("WebViewHistory", "New URL: $newUrl")
//                    Log.d("WebViewHistory", "List Size: ${webViewHistoryList.size}")
//                    Log.d("WebViewHistory", "Current Index: ${webViewHistoryList.currentIndex}")
//
//                    // Loop through and print each item in the WebView's history
//                    for (i in 0 until webViewHistoryList.size) {
//                        val item = webViewHistoryList.getItemAtIndex(i)
//                        val isCurrentMarker = if (i == webViewHistoryList.currentIndex) "<- CURRENT" else ""
//                        Log.d("WebViewHistory", "  [$i] ${item.url} ${isCurrentMarker}")
//                    }
//                    Log.d("WebViewHistory", "=======================================")
//                }
//                // --- END OF LOGGING CODE ---
            }
        }
    }

    LaunchedEffect(overlayHeightPx) {
        // We only want to act the first time the height is measured (it changes from 0f to a positive value).
        // The `offsetY.value == 0f` check is an extra safeguard to ensure we only do this once on startup.
        if (overlayHeightPx > 0f && offsetY.value == 0f) {
            // Instantly "snap" the overlay to its hidden position without any animation.
            // The hidden position is its full height negated, moving it off-screen upwards.
            offsetY.snapTo(-overlayHeightPx * 2)
        }
    }

    LaunchedEffect(browserSettings.isDesktopMode) {
        if (browserSettings.isDesktopMode) {
            webView.settings.userAgentString = desktopUserAgent
            webView.settings.useWideViewPort = true
            webView.settings.loadWithOverviewMode = true
        } else {
            webView.settings.userAgentString = mobileUserAgent
            webView.settings.useWideViewPort = false
            webView.settings.loadWithOverviewMode = false
        }

        // This reload is still essential to get the new HTML from the server.
        webView.reload()
    }

    LaunchedEffect(isUrlBarVisible, pendingPermissionRequest) {
        // Determine if the permission panel should be visible.
        val isPermissionPanelVisible = pendingPermissionRequest != null


        isImmersiveMode = if (isPermissionPanelVisible) {
            false
        } else {
            !isUrlBarVisible
        }
    }
    LaunchedEffect(isUrlBarVisible) {
        val window = (context as? Activity)?.window ?: return@LaunchedEffect
        val insetsController = WindowCompat.getInsetsController(window, window.decorView)
        if (isUrlBarVisible) {
            insetsController.show(WindowInsetsCompat.Type.systemBars())
        } else {
            insetsController.hide(WindowInsetsCompat.Type.systemBars())
            insetsController.systemBarsBehavior =
                WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
    }

    LaunchedEffect(saveTrigger) {
        // We add a check `if (saveTrigger > 0)` to prevent saving an empty
        // list on the very first composition.
        if (saveTrigger > 0) {
            tabManager.saveTabs(tabs)
        }
    }

    // This effect loads the URL when the active tab changes
    LaunchedEffect(activeTabIndex, initialLoadDone) {
        Log.e("zzz", "Change Tab")
        // Get the URL that SHOULD be loaded for the current tab.
        val urlToLoad = currentTab?.currentUrl

        if (urlToLoad != null) {
            if (!initialLoadDone) {
                // --- SCENARIO 1: First time app is opened ---
                // If the initial load hasn't happened yet, load the URL.
                webView.loadUrl(urlToLoad)
                // Set the flag to true so this block never runs again.
                initialLoadDone = true
            }
//            else {
//                // --- SCENARIO 2: User switches to a different tab ---
//                // If the initial load IS done, this effect is running because
//                // currentTab changed. Load the new tab's URL.
//                if (webView.url != urlToLoad) {
//                    webView.loadUrl(urlToLoad)
//                }
//            }
        }
    }

    // The LaunchedEffect now saves the entire settings object (or individual fields)
    LaunchedEffect(browserSettings) {
        sharedPrefs.edit {
            putFloat("padding_dp", browserSettings.paddingDp)
            putFloat("corner_radius_dp", browserSettings.cornerRadiusDp)
            putBoolean("is_interactable", browserSettings.isInteractable)
            putString("default_url", browserSettings.defaultUrl)
            putInt("animation_speed", browserSettings.animationSpeed)
            putInt("single_line_height", browserSettings.singleLineHeight)
            putInt("desktop_mode_width", browserSettings.desktopModeWidth)

        }
    }

    LaunchedEffect(animatedSystemBarBottom) {
        Log.e("animatedSystemBarBottom", animatedSystemBarBottom.toString())
    }

    LaunchedEffect(Unit) {
        focusManager.clearFocus()
    }


    // This effect runs whenever the isDesktopMode flag changes.
    LaunchedEffect(browserSettings.isDesktopMode) {
        val newAgent = if (browserSettings.isDesktopMode) desktopUserAgent else mobileUserAgent
        if (webView.settings.userAgentString != newAgent) {
            webView.settings.userAgentString = newAgent
            // Reload the page to apply the new User Agent
            webView.reload()
        }
    }

    // This effect will re-launch whenever the animatedPadding value changes (i.e., every frame).
    LaunchedEffect(animatedPadding) {
        // We now have a hook that runs on every animation frame.
        // We can command our WebView to update its layout.
        webView.requestLayout()
    }
//
//    LaunchedEffect(url) {
//        if (webView.url != url) {
//            webView.loadUrl(url)
//        }
//    }

    BackHandler(enabled = !isUrlBarVisible || canGoBack) {
        when {
            // Priority 1: Exit fullscreen video if it's active.
            customView != null -> {
                customViewCallback?.onCustomViewHidden()

            }
            // Priority 2: Exit main browser's immersive mode.
            !isUrlBarVisible -> {
                isUrlBarVisible = true
                updateBrowserSettings(browserSettings.copy(isInteractable = false))
            }
            // Priority 3: Navigate back in the WebView.
            canGoBack -> {
                currentTab?.let { tab ->
                    val updatedTab = tab.copy(historyState = tab.historyState?.copy(currentIndex = tab.historyState!!.currentIndex - 1))
                    tabs[activeTabIndex.value] = updatedTab
//                    updatedTab.currentUrl?.let { webView.loadUrl(it) }
                    webView.goBack()
                    saveTrigger++
                }
            }

            else -> {
            }
        }
    }


    //
    //
    //
    // LAYOUT
    //
    //
    Box(modifier = Modifier.fillMaxSize()) {
        CompositionLocalProvider(LocalBrowserSettings provides browserSettings) {
            Box(
                modifier = modifier
                    .fillMaxSize()
                    .padding(top = animatedSystemBarTop, bottom = animatedSystemBarBottom)
            ) {
                Column(
                    modifier = modifier
                        .fillMaxSize()
                        .windowInsetsPadding(WindowInsets.ime)

                ) {


                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                            .padding(animatedPadding)
                            .padding(
                                top = animatedCutoutTop,
                                start = animatedCutoutStart,
                                end = animatedCutoutEnd,
                                bottom = animatedCutoutBottom
                            )
                            .clip(RoundedCornerShape(animatedCornerRadius))
                            .testTag("WebViewContainer")

                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()

                        ) {
                            AndroidView(
                                factory = {
                                    FrameLayout(it).apply {
                                        // If the WebView still has a parent from a previous composition, remove it.
                                        (webView.parent as? ViewGroup)?.removeView(webView)

                                        // Add our singleton WebView to it.
                                        addView(
                                            webView,
                                            FrameLayout.LayoutParams(
                                                FrameLayout.LayoutParams.MATCH_PARENT,
                                                FrameLayout.LayoutParams.MATCH_PARENT
                                            ).apply {
                                                gravity = Gravity.CENTER
                                            }
                                        )
                                    }
                                },
                                modifier = Modifier.fillMaxSize()
                            )
                        }

                        if (!browserSettings.isInteractable) {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .pointerInput(Unit) {
                                        val coroutineScope = CoroutineScope(coroutineContext)
                                        val verticalDragThreshold =
                                            with(density) { overlayHeightPx * 2 }
                                        val horizontalDragThreshold = with(density) { 40.dp.toPx() }

                                        awaitEachGesture {
                                            val down = awaitFirstDown(requireUnconsumed = false)

                                            var isTap = true
                                            val drag =
                                                awaitTouchSlopOrCancellation(down.id) { change, _ ->
                                                    isTap = false
                                                    change.consume()
                                                }

                                            if (isTap) {
                                                isUrlBarVisible = false
                                                updateBrowserSettings(
                                                    browserSettings.copy(
                                                        isInteractable = true
                                                    )
                                                )
                                            } else if (drag != null) {
                                                var horizontalDragAccumulator = 0f
                                                // --- HAPTIC FEEDBACK STATE ---
                                                var previousAction = GestureNavAction.NONE
                                                var commitHapticPlayed = false
                                                // ---

                                                drag(drag.id) { change ->
                                                    change.consume()
                                                    val verticalDragDistance =
                                                        change.position.y - down.position.y
                                                    val dragAmount =
                                                        change.position.y - change.previousPosition.y
                                                    val newOffset =
                                                        (offsetY.value + dragAmount).coerceIn(
                                                            -overlayHeightPx * 2,
                                                            0f
                                                        )
                                                    coroutineScope.launch { offsetY.snapTo(newOffset) }

                                                    if (verticalDragDistance > verticalDragThreshold) {
                                                        // --- HAPTIC 1: Play a "pop" when the gesture first commits ---
                                                        if (!commitHapticPlayed) {
                                                            hapticFeedback.performHapticFeedback(
                                                                HapticFeedbackType.LongPress
                                                            )
                                                            commitHapticPlayed = true
                                                        }
                                                        // ---

                                                        horizontalDragAccumulator += change.position.x - change.previousPosition.x

                                                        val newAction = when {
                                                            horizontalDragAccumulator < -horizontalDragThreshold -> GestureNavAction.BACK
                                                            horizontalDragAccumulator > horizontalDragThreshold -> GestureNavAction.FORWARD
                                                            else -> GestureNavAction.REFRESH
                                                        }

                                                        // --- HAPTIC 2: Play a "tick" when the selected action changes ---
                                                        if (newAction != previousAction) {
                                                            hapticFeedback.performHapticFeedback(
                                                                HapticFeedbackType.LongPress
                                                            )
                                                            previousAction = newAction
                                                        }
                                                        // ---

                                                        activeGestureAction = newAction

                                                    } else {
                                                        // If finger moves back up, cancel the action and reset everything
                                                        activeGestureAction = GestureNavAction.NONE
                                                        horizontalDragAccumulator = 0f
                                                        commitHapticPlayed =
                                                            false // Reset the commit haptic flag
                                                        previousAction = GestureNavAction.NONE
                                                    }
                                                }

                                                when (activeGestureAction) {
                                                    GestureNavAction.BACK -> if (canGoBack) {
                                                        isTraverseHistory = true
                                                        currentTab?.let { tab ->
                                                            val updatedTab = tab.copy(
                                                                historyState = tab.historyState?.copy(
                                                                    currentIndex = tab.historyState!!.currentIndex - 1
                                                                )
                                                            )
                                                            tabs[activeTabIndex.value] = updatedTab
                                                            saveTrigger++

                                                            webView.goBack()
                                                        }
                                                    }

                                                    GestureNavAction.REFRESH -> {
                                                        isTraverseHistory = true

                                                        webView.reload()
                                                    }

                                                    GestureNavAction.FORWARD -> if (canGoForward) {
                                                        isTraverseHistory = true

                                                        currentTab?.let { tab ->
                                                            val updatedTab = tab.copy(
                                                                historyState = tab.historyState?.copy(
                                                                    currentIndex = tab.historyState!!.currentIndex + 1
                                                                )
                                                            )
                                                            tabs[activeTabIndex.value] = updatedTab

                                                            webView.goForward()
                                                            saveTrigger++
                                                        }
                                                    }

                                                    GestureNavAction.NONE -> { /* Do nothing */
                                                    }
                                                }
                                                // Animate the overlay back to its hidden position.
                                                coroutineScope.launch {
                                                    offsetY.animateTo(
                                                        targetValue = -overlayHeightPx * 2,
                                                        animationSpec = tween(durationMillis = 200)
                                                    )
                                                }
                                            }
                                            // Always reset the highlighted action for the next gesture.
                                            activeGestureAction = GestureNavAction.NONE
                                        }
                                    }


//                                    .pointerInput(Unit) {
//                                        awaitEachGesture {
//                                            // 1. At the start of each new gesture, reset our flag.
//                                            var isDrag = false
//
//                                            // 2. Wait for the initial press.
//                                            val down = awaitFirstDown(requireUnconsumed = false)
//
//                                            // 3. Use awaitTouchSlopOrCancellation. We are most interested
//                                            //    in its onSlopCrossed lambda.
//                                            val dragOrTap =
//                                                awaitTouchSlopOrCancellation(down.id) { _, _ ->
//                                                    // THIS IS THE KEY: This lambda is called the *moment* a
//                                                    // drag is detected. We set our flag here. This happens
//                                                    // before the WebView can fully "steal" the gesture,
//                                                    // making our flag a reliable source of truth.
//                                                    isDrag = true
//                                                    // We don't need to do anything with the change object itself.
//                                                }
//
//                                            // 4. AFTER the gesture is over, we check OUR flag, not the
//                                            //    unreliable return value of dragOrTap.
//                                            if (!isDrag) {
//                                                // If our flag is still false, it means onSlopCrossed was
//                                                // never called. Therefore, it must be a tap.
//                                                isUrlBarVisible = false
//                                                updateBrowserSettings(
//                                                    browserSettings.copy(
//                                                        isInteractable = true
//                                                    )
//                                                )
//                                            }
//                                        }
//                                    }
                            )

                        }

                        LoadingOverlay(isLoading = isLoading, colorScheme = colorScheme)
                    }



                    PermissionPanel(
                        colorScheme = colorScheme,
                        browserSettings = browserSettings,
                        request = pendingPermissionRequest,
                        onAllow = {
                            // When user clicks allow, launch the system dialog with the permissions
                            // stored in our request object.

                            pendingPermissionRequest?.let {
                                permissionLauncher.launch(it.permissionsToRequest.toTypedArray())
                            }
                        },
                        onDeny = {
                            // When user clicks deny, immediately invoke the stored onResult callback
                            // with an empty map (signifying denial) and clear the request.
                            pendingPermissionRequest?.onResult?.invoke(emptyMap())
                            pendingPermissionRequest = null
                        }
                    )
                    BottomPanel(

                        currentTab = currentTab,
                        colorScheme = colorScheme,
                        isImmersiveMode = isImmersiveMode,
                        isUrlBarVisible = isUrlBarVisible,
                        isOptionsPanelVisible = isOptionsPanelVisible,
                        browserSettings = browserSettings,
                        updateBrowserSettings = updateBrowserSettings,
                        textFieldValue = textFieldValue,
//                        url = url,
                        focusManager = focusManager,
                        keyboardController = keyboardController,
                        textFieldHeightDp = textFieldHeightDp,
                        toggleOptionsPanel = { isOptionsPanelVisible = it },
                        changeTextFieldValue = { textFieldValue = it },
                        onNewUrl = { newUrl ->
                            webView.loadUrl(newUrl)
//                            }
                        },
                        toggleUrlBar = { isUrlBarVisible = it },
                        setTextFieldHeightPx = { textFieldHeightPx = it },
                        setIsFocusOnTextField = { isFocusOnTextField = it },


                        )


                }
            }
        }
        // This appears on top of everything when customView is not null.
        if (customView != null) {
            AndroidView(
                factory = { customView!! as ViewGroup },
                // This onRelease block is the KEY to preventing the crash.
                // When this view is removed from composition (because customView becomes null),
                // it guarantees the view is detached from its parent.
                onRelease = { view ->
                    (view.parent as? ViewGroup)?.removeView(view)
                },
                modifier = Modifier.fillMaxSize()
            )
        }

        GestureNavigationOverlay(
            colorScheme = colorScheme,
            staticSystemBarTop = staticSystemBarTop,
            offsetY = offsetY,
            activeAction = activeGestureAction,
            canGoBack = canGoBack,
            canGoForward = canGoForward,
            onHeightMeasured = { measuredHeight ->
                // Only set the initial height once to avoid recomposition loops
                if (overlayHeightPx == 0f && measuredHeight > 0) {
                    overlayHeightPx = measuredHeight
                }
            },
            onBackButtonBoundsChanged = { backButtonRect = it },
            onRefreshButtonBoundsChanged = { refreshButtonRect = it },
            onForwardButtonBoundsChanged = { forwardButtonRect = it }
        )
    }


}

@Composable
fun BottomPanel(
    currentTab: Tab?,
    colorScheme: ColorScheme,
    isImmersiveMode: Boolean,
    isUrlBarVisible: Boolean,
    isOptionsPanelVisible: Boolean,
    browserSettings: BrowserSettings,
    updateBrowserSettings: (BrowserSettings) -> Int,
    textFieldValue: TextFieldValue,
//    url: String,
    focusManager: FocusManager,
    keyboardController: SoftwareKeyboardController?,
    textFieldHeightDp: Dp,
    toggleOptionsPanel: (Boolean) -> Unit = {},
    changeTextFieldValue: (TextFieldValue) -> Unit = {},
    onNewUrl: (String) -> Unit = {},
    toggleUrlBar: (Boolean) -> Unit = {},
    setTextFieldHeightPx: (Int) -> Unit = {},
    setIsFocusOnTextField: (Boolean) -> Unit = {},

    ) {
    AnimatedVisibility(
        visible = isUrlBarVisible,
        enter = expandVertically(tween(browserSettings.animationSpeed)),
        exit = shrinkVertically(tween(browserSettings.animationSpeed))
    ) {
        Column {


            // URL BAR
            Row(
                modifier = Modifier
                    .pointerInput(Unit) {
                        detectVerticalDragGestures(
                            onVerticalDrag = { change, dragAmount ->
                                // dragAmount is the change in the Y-axis.
                                // A negative value means the finger has moved UP.
                                if (dragAmount < 0) {
                                    toggleOptionsPanel(true)
                                }
                                // A positive value means the finger has moved DOWN.
                                else if (dragAmount > 0) {
                                    toggleOptionsPanel(false)
                                }
                            })
                    }
                    .padding(
                        horizontal = browserSettings.paddingDp.dp,
                        vertical = browserSettings.paddingDp.dp / 2
                    ),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = textFieldValue.text,
                    onValueChange = { newValue ->
                        changeTextFieldValue(
                            TextFieldValue(
                                newValue,
                                selection = TextRange(newValue.length)
                            )
                        )
                    },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Go),
                    keyboardActions = KeyboardActions(
                        onGo = {
                            val input = textFieldValue.text.trim()
                            val resetUrl = currentTab?.currentUrl ?: ""

                            if (input.isBlank()) {
                                changeTextFieldValue(
                                    TextFieldValue(
                                        resetUrl,
                                        TextRange(resetUrl.length)
                                    )
                                )
//                                changeTextFieldValue(TextFieldValue(url, TextRange(url.length)))
                                focusManager.clearFocus()
                                keyboardController?.hide()
                                return@KeyboardActions
                            }
                            val isUrl = try {
                                Patterns.WEB_URL.matcher(input).matches() ||
                                        (input.contains(".") && !input.contains(" "))
                            } catch (_: Exception) {
                                false
                            }

                            val finalUrl = if (isUrl) {
                                if (input.startsWith("http://") || input.startsWith("https://")) {
                                    input
                                } else {
                                    "https://$input"
                                }
                            } else {
                                val encodedQuery =
                                    URLEncoder.encode(input, StandardCharsets.UTF_8.toString())
                                "https://www.google.com/search?q=$encodedQuery"
                            }

                            onNewUrl(finalUrl)

                            focusManager.clearFocus()
                            keyboardController?.hide()
                            if (!browserSettings.isInteractable) {
                                toggleUrlBar(false)
                                updateBrowserSettings(browserSettings.copy(isInteractable = true))
                            }
                        }
                    ),
                    modifier = Modifier
                        .weight(1f)
                        .height(browserSettings.singleLineHeight.dp)
                        .onSizeChanged { size ->
                            setTextFieldHeightPx(size.height)
                        }
                        .fillMaxWidth()
                        //                            .padding(horizontal = browserSettings.paddingDp.dp, vertical = browserSettings.paddingDp.dp / 2)
                        .onFocusChanged {
                            val resetUrl = currentTab?.currentUrl ?: ""
                            setIsFocusOnTextField(it.isFocused)
                            if (it.isFocused) {

                                if (textFieldValue.text == resetUrl) {

                                    changeTextFieldValue(TextFieldValue("", TextRange(0)))
                                }
                            } else {

                                if (textFieldValue.text.isBlank()) {
                                    changeTextFieldValue(
                                        TextFieldValue(
                                            resetUrl,
                                            TextRange(resetUrl.length)
                                        )
                                    )
                                }
                            }
                        }
                        .pointerInput(Unit) {
                            detectHorizontalDragGestures { _, dragAmount ->
                                if (dragAmount > 0) {
                                    val resetUrl = currentTab?.currentUrl ?: ""
                                    changeTextFieldValue(
                                        TextFieldValue(
                                            resetUrl,
                                            selection = TextRange(resetUrl.length)
                                        )
                                    )
                                }
                            }
                        },
                    shape = RoundedCornerShape(browserSettings.cornerRadiusDp.dp),
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = colorScheme.backgroundColor, // Background when focused
                        unfocusedContainerColor = colorScheme.backgroundColor, // Background when unfocused
                        disabledContainerColor = colorScheme.foregroundColor, // Background when disabled
                        errorContainerColor = Color.Red // Background when in error state
                    )
                )
                IconButton(
                    onClick = { updateBrowserSettings(browserSettings.copy(isInteractable = !browserSettings.isInteractable)) },
                    modifier = Modifier
                        .padding(start = browserSettings.paddingDp.dp)
                        .then(if (textFieldHeightDp > 0.dp) Modifier.size(textFieldHeightDp) else Modifier),
                    colors = IconButtonDefaults.iconButtonColors(
                        containerColor = colorScheme.foregroundColor
                    )

                ) {
                    Icon(
                        painter = if (browserSettings.isInteractable) painterResource(id = R.drawable.ic_transparent) else painterResource(
                            id = R.drawable.ic_immersive
                        ),
                        contentDescription = "Toggle Interactable",
//                        tint = MaterialTheme.colorScheme.onPrimary
                        tint = colorScheme.backgroundColor
                    )
                }
            }


            // SETTING OPTIONS
            OptionsPanel(
                colorScheme = colorScheme,
                isImmersiveMode = isImmersiveMode,
                isOptionsPanelVisible = isOptionsPanelVisible,
                toggleOptionsPanel = toggleOptionsPanel,
                updateBrowserSettings = updateBrowserSettings,
                browserSettings = browserSettings,
            )
        }
    }
}

// --- REPLACE THE ENTIRE OLD PermissionPanel WITH THIS ---

@Composable
fun PermissionPanel(
    colorScheme: ColorScheme,
    browserSettings: BrowserSettings,
    // The pending request, which also controls visibility. Null means hidden.
    request: CustomPermissionRequest?,
    // Event for when the user clicks "Allow" on our panel.
    onAllow: () -> Unit,
    // Event for when the user clicks "Deny" on our panel.
    onDeny: () -> Unit
) {
    val isVisible = request != null

    AnimatedVisibility(
        visible = isVisible,
        enter = expandVertically(animationSpec = tween(browserSettings.animationSpeed)),
        exit = shrinkVertically(animationSpec = tween(browserSettings.animationSpeed))
    ) {
        // We need a non-null request to proceed, which is safe inside this
        // AnimatedVisibility block.
        if (request == null) return@AnimatedVisibility

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = Color.Transparent
            ),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = browserSettings.paddingDp.dp)
                    .padding(bottom = browserSettings.paddingDp.dp / 2),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {


                // Action buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(browserSettings.paddingDp.dp)
                ) {
                    // --- Deny Button ---
                    IconButton(
                        onClick = onDeny,
                        modifier = Modifier
                            .weight(1f)
                            .height(browserSettings.singleLineHeight.dp),
                        colors = IconButtonDefaults.iconButtonColors(
                            containerColor = colorScheme.foregroundColor.copy(alpha = 0.1f)
                        )
                    ) {
                        Icon(
                            painter = painterResource(id = request.iconResDeny), // You can make this icon generic too
                            contentDescription = "Deny Permission",
                            tint = colorScheme.foregroundColor
                        )
                    }

                    // --- Allow Button ---
                    IconButton(
                        onClick = onAllow,
                        modifier = Modifier
                            .weight(1f)
                            .height(browserSettings.singleLineHeight.dp),
                        colors = IconButtonDefaults.iconButtonColors(
                            containerColor = colorScheme.foregroundColor
                        )
                    ) {
                        Icon(
                            painter = painterResource(id = request.iconResAllow), // You can make this icon generic too
                            contentDescription = "Allow Permission",
                            tint = colorScheme.backgroundColor
                        )
                    }
                }
            }
        }
    }
}


data class OptionItem(
    val iconRes: Int, // The drawable resource ID for the icon
    val contentDescription: String,
    val onClick: () -> Unit,
)

data class ColorScheme(
    val backgroundColor: Color,
    val foregroundColor: Color
)

@Composable
fun OptionsPanel(
    colorScheme: ColorScheme,
    isImmersiveMode: Boolean,
    isOptionsPanelVisible: Boolean = false,
    toggleOptionsPanel: (Boolean) -> Unit = {},
    updateBrowserSettings: (BrowserSettings) -> Int,
    browserSettings: BrowserSettings = LocalBrowserSettings.current,
) {


    // This remains the same
    val allOptions = remember(browserSettings) {
        listOf(
            OptionItem(
                if (browserSettings.isDesktopMode) R.drawable.ic_mobile else R.drawable.ic_desktop,
                "Desktop layout"
            ) {
                updateBrowserSettings(browserSettings.copy(isDesktopMode = !browserSettings.isDesktopMode))
            },

            OptionItem(R.drawable.ic_bug, "logBrowserSettings") {
                Log.e("BROWSER SETTINGS", browserSettings.toString())
                Log.e("isImmersiveMode", isImmersiveMode.toString())
            },
            OptionItem(R.drawable.ic_fullscreen, "Button 4") { /* ... */ },
            OptionItem(R.drawable.ic_fullscreen, "Button 5") { /* ... */ },
            OptionItem(R.drawable.ic_fullscreen, "Button 6") { /* ... */ },
            OptionItem(R.drawable.ic_fullscreen, "Button 7") { /* ... */ },
            OptionItem(R.drawable.ic_fullscreen, "Button 8") { /* ... */ }
        )
    }

// --- NEW: Group the options into pages of 4 ---
    val optionPages = remember(allOptions) {
        allOptions.chunked(4)
    }

    // --- Pager State ---
    // The pagerState remembers the current page and handles scroll animations.
    val pagerState = rememberPagerState(pageCount = { optionPages.size })

    AnimatedVisibility(
        visible = isOptionsPanelVisible,
        enter = expandVertically(tween(browserSettings.animationSpeed)),
        exit = shrinkVertically(tween(browserSettings.animationSpeed)),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(
                    horizontal = browserSettings.paddingDp.dp,
                    vertical = browserSettings.paddingDp.dp / 2
                )
                .pointerInput(Unit) {
                    detectVerticalDragGestures(
                        onVerticalDrag = { change, dragAmount ->
                            // dragAmount is the change in the Y-axis.
                            // A negative value means the finger has moved UP.
                            if (dragAmount < 0) {
                                toggleOptionsPanel(true)
                            }
                            // A positive value means the finger has moved DOWN.
                            else if (dragAmount > 0) {
                                toggleOptionsPanel(false)
                            }
                        })
                }

        ) {

            HorizontalPager(
                state = pagerState,
                modifier = Modifier.fillMaxWidth()
            ) { pageIndex ->
                // This composable block is called for each page.

                // A Row holds the 4 buttons for the current page.
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = browserSettings.paddingDp.dp), // Add some inner padding
                    horizontalArrangement = Arrangement.spacedBy(browserSettings.paddingDp.dp)
                ) {
                    // Get the options for the current page
                    val pageOptions = optionPages[pageIndex]

                    // Create an IconButton for each option on the page
                    pageOptions.forEach { option ->
                        IconButton(
                            onClick = option.onClick,
                            // Use weight to make the buttons share space equally
                            modifier = Modifier
                                .weight(1f)
                                .height(browserSettings.singleLineHeight.dp),
                            colors = IconButtonDefaults.iconButtonColors(
                                containerColor = colorScheme.foregroundColor.copy(alpha = 0.05f)
                            ),

                            ) {
                            Icon(
                                painter = painterResource(id = option.iconRes),
                                contentDescription = option.contentDescription,
                                tint = colorScheme.foregroundColor
                            )
                        }
                    }

                    // If a page has fewer than 4 items, we add spacers to keep the layout consistent.
                    repeat(4 - pageOptions.size) {
                        Spacer(modifier = Modifier.weight(1f))
                    }
                }
            }

        }
    }
}

/**
 * A semi-transparent overlay with a circular progress indicator that appears
 * on top of other content.
 *
 * @param isLoading Controls the visibility of the overlay.
 * @param modifier The modifier to be applied to the overlay.
 */
@Composable
fun LoadingOverlay(isLoading: Boolean, modifier: Modifier = Modifier, colorScheme: ColorScheme) {
    // Animate the appearance and disappearance of the overlay.
    AnimatedVisibility(
        visible = isLoading,
        modifier = modifier,
        enter = fadeIn(animationSpec = tween(300)),
        exit = fadeOut(animationSpec = tween(300))
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                // Use a theme-aware scrim color for a professional look.
                .background(colorScheme.backgroundColor.copy(alpha = 0.7f))
                // CRITICAL: This consumes all touch events, preventing the user
                // from interacting with the WebView while it's loading.
                .pointerInput(Unit) {},
            contentAlignment = Alignment.Center
        ) {
            CircularProgressIndicator(
                modifier = Modifier.size(64.dp),
                // Use a contrasting color that works well on the dark scrim.
                color = colorScheme.foregroundColor,
                strokeWidth = 6.dp
            )
        }
    }
}


@Composable
fun GestureNavigationOverlay(
    colorScheme: ColorScheme,
    staticSystemBarTop: Dp,
    offsetY: Animatable<Float, *>,
    activeAction: GestureNavAction,
    canGoBack: Boolean,
    canGoForward: Boolean,
    onHeightMeasured: (Float) -> Unit,
    onBackButtonBoundsChanged: (Rect) -> Unit,
    onRefreshButtonBoundsChanged: (Rect) -> Unit,
    onForwardButtonBoundsChanged: (Rect) -> Unit
) {
    val browserSettings = LocalBrowserSettings.current

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .onGloballyPositioned {
                onHeightMeasured(it.size.height.toFloat())
            }
            .offset { IntOffset(0, offsetY.value.roundToInt()) }
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = staticSystemBarTop + browserSettings.paddingDp.dp)
                .padding(horizontal = browserSettings.paddingDp.dp)
                .height(browserSettings.singleLineHeight.dp * 1.5f)
        ) {
            val backgroundColor by animateColorAsState(
                targetValue = if (activeAction != GestureNavAction.NONE) colorScheme.foregroundColor.copy(
                    alpha = 0.9f
                ) else colorScheme.foregroundColor.copy(alpha = 0.5f),
                label = "BackgroundColor"
            )
            val containerBorderRadius by animateDpAsState(
                targetValue = if (activeAction != GestureNavAction.NONE) browserSettings.singleLineHeight.dp * 1.5f else browserSettings.cornerRadiusDp.dp,
                animationSpec = tween(
                    durationMillis = 100, // Set custom duration in milliseconds
                    easing = EaseIn // Optional: customize easing
                ),
                label = "ContainerBorderRadius"
            )

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clip(RoundedCornerShape(containerBorderRadius))
                    .background(backgroundColor)
                    .blur(10.dp)
            )

            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(
                        vertical = browserSettings.paddingDp.dp,
                        horizontal = browserSettings.singleLineHeight.dp * 1f
                    ),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // --- DYNAMIC WEIGHT ANIMATION ---
                // Animate the weight of each button based on the active action.
                val backWeight by animateFloatAsState(
                    targetValue = if (activeAction == GestureNavAction.BACK) 2f else 1f,
                    label = "BackWeight"
                )
                val refreshWeight by animateFloatAsState(
                    targetValue = if (activeAction == GestureNavAction.REFRESH) 2f else 1f,
                    label = "RefreshWeight"
                )
                val forwardWeight by animateFloatAsState(
                    targetValue = if (activeAction == GestureNavAction.FORWARD) 2f else 1f,
                    label = "ForwardWeight"
                )
                // ---

                // --- Back Button ---
                val backColor by animateColorAsState(
                    targetValue = if (activeAction == GestureNavAction.BACK && canGoBack) colorScheme.backgroundColor else Color.Transparent,
                    label = "BackColor"
                )
                Box(
                    modifier = Modifier
                        .weight(backWeight) // Use the animated weight
                        .fillMaxHeight()
                        .onGloballyPositioned { onBackButtonBoundsChanged(it.boundsInRoot()) }
                        .clip(RoundedCornerShape(browserSettings.cornerRadiusDp.dp))
                        .background(backColor)
                ) {
                    if (canGoBack) {
                        Icon(
                            painter = painterResource(id = R.drawable.ic_arrow_back),
                            contentDescription = "Go Back",
                            tint = if (activeAction == GestureNavAction.BACK) colorScheme.foregroundColor else colorScheme.backgroundColor,
                            modifier = Modifier.align(Alignment.Center)
                        )
                    }
                }

                Spacer(modifier = Modifier.width(browserSettings.paddingDp.dp))

                // --- Refresh Button ---
                val refreshColor by animateColorAsState(
                    targetValue = if (activeAction == GestureNavAction.REFRESH) colorScheme.backgroundColor else Color.Transparent,
                    label = "RefreshColor"
                )
                Box(
                    modifier = Modifier
                        .weight(refreshWeight) // Use the animated weight
                        .fillMaxHeight()
                        .onGloballyPositioned { onRefreshButtonBoundsChanged(it.boundsInRoot()) }
                        .clip(RoundedCornerShape(browserSettings.cornerRadiusDp.dp))
                        .background(refreshColor)
                ) {
                    Icon(
                        painter = painterResource(id = R.drawable.ic_refresh),
                        contentDescription = "Refresh",
                        tint = if (activeAction == GestureNavAction.REFRESH) colorScheme.foregroundColor else colorScheme.backgroundColor,
                        modifier = Modifier.align(Alignment.Center)
                    )
                }

                Spacer(modifier = Modifier.width(browserSettings.paddingDp.dp))

                // --- Forward Button ---
                val forwardColor by animateColorAsState(
                    targetValue = if (activeAction == GestureNavAction.FORWARD && canGoForward) colorScheme.backgroundColor else Color.Transparent,
                    label = "ForwardColor"
                )
                Box(
                    modifier = Modifier
                        .weight(forwardWeight) // Use the animated weight
                        .fillMaxHeight()
                        .onGloballyPositioned { onForwardButtonBoundsChanged(it.boundsInRoot()) }
                        .clip(RoundedCornerShape(browserSettings.cornerRadiusDp.dp))
                        .background(forwardColor)
                ) {
                    if (canGoForward) {
                        Icon(
                            painter = painterResource(id = R.drawable.ic_arrow_forward),
                            contentDescription = "Go Forward",
                            tint = if (activeAction == GestureNavAction.FORWARD) colorScheme.foregroundColor else colorScheme.backgroundColor,
                            modifier = Modifier.align(Alignment.Center)
                        )
                    }
                }
            }
        }
    }
}

@Composable
@Preview(showBackground = true)
fun BrowserScreenPreview() {
    BrowserTheme {
        BrowserScreen()
    }
}


class WebAppInterface() {
    @JavascriptInterface
    fun logBackgroundColor(colorString: String) {
        // We need a robust way to parse the "rgb(r, g, b)" or "rgba(r, g, b, a)" string.
        try {

            Log.e("WebViewBackground", "Detected web page background color: $colorString")


        } catch (e: Exception) {
            // If parsing fails for any reason, log it but don't crash.
            Log.e("WebAppInterface", "Failed to parse color string: $colorString", e)
        }
    }
}

