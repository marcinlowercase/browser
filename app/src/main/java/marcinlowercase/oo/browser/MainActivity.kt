package marcinlowercase.oo.browser


import kotlinx.serialization.Serializable
import androidx.compose.animation.core.Animatable
import androidx.compose.ui.unit.IntOffset
import kotlin.math.roundToInt
import androidx.core.content.ContextCompat
import android.content.pm.PackageManager
import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.content.Intent
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
import android.webkit.PermissionRequest
import androidx.compose.ui.layout.onGloballyPositioned
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
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
//import androidx.compose.material3.value
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
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.SoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
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
import java.net.URISyntaxException
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import kotlin.coroutines.coroutineContext
import androidx.core.net.toUri


private lateinit var webView: CustomWebView
var databaseCurrentIndexHolder = -1
var realtimePreviousIndexHolder = 0

const val defaultUrl = "https://oo3.deno.dev/i"

class MainActivity : ComponentActivity() {
    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)


        webView = CustomWebView(this).apply {
            // Force WebView to be transparent so Compose can control the background
            setBackgroundColor(android.graphics.Color.TRANSPARENT)

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
//            addJavascriptInterface(WebAppInterface(), "Android")

        }
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
        defaultUrl = defaultUrl,
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
//    FROZEN       // A tab that needs to be reloaded when opened
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

    private val tabsKey = "tabs_list_json"

    fun saveTabs(tabs: List<Tab>) {
        // Convert the list of tabs into a single JSON string
        val jsonString = json.encodeToString(tabs)
        prefs.edit {
            putString(tabsKey, jsonString)
        }
        Log.d("TabManager", "Tabs saved.")
    }

    fun loadTabs(defaultUrl: String): MutableList<Tab> {
        val jsonString = prefs.getString(tabsKey, null)

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

class CustomWebView(context: Context) : WebView(context)


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
                defaultUrl = sharedPrefs.getString("default_url", defaultUrl)
                    ?: defaultUrl,
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

    var initialLoadDone by rememberSaveable { mutableStateOf(false) }

    var saveTrigger by remember { mutableIntStateOf(0) }


    var textFieldValue by rememberSaveable(stateSaver = TextFieldValue.Saver) {
        mutableStateOf(TextFieldValue(tabs[activeTabIndex.intValue].currentUrl ?: "", TextRange(0)))
    }


    var isImmersiveMode by remember { mutableStateOf(false) }

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
    var isNavigateInProgress by rememberSaveable { mutableStateOf(false) }


    var isOptionsPanelVisible by rememberSaveable { mutableStateOf(false) }

    val offsetY = remember { Animatable(0f) }
    var activeGestureAction by remember { mutableStateOf(GestureNavAction.NONE) }
    var overlayHeightPx by remember { mutableFloatStateOf(0f) }
    

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

    val isDarkTheme = isSystemInDarkTheme()
    val view = LocalView.current // Get the underlying view

    val colorScheme = ColorScheme(
        backgroundColor = if (isDarkTheme) Color.Black else Color.White,
        foregroundColor = if (isDarkTheme) Color.White else Color.Black
    )

    val canGoBack by remember {
        derivedStateOf {
            ((tabs[activeTabIndex.intValue].historyState?.currentIndex
                ?: 0) > 0) && !isNavigateInProgress
        }
    }
    val canGoForward by remember {
        derivedStateOf {
            val history = tabs[activeTabIndex.intValue].historyState
            if (history == null) false else (history.currentIndex < history.items.lastIndex) && !isNavigateInProgress
        }
    }

    LaunchedEffect(canGoBack, canGoForward) {
        Log.e("doUpdateVisitedHistory", "canGoBack: $canGoBack, canGoForward: $canGoForward")
    }

    databaseCurrentIndexHolder = tabs[activeTabIndex.intValue].historyState?.currentIndex ?: 0

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


    // FUNCTIONS


    // This function will be our single, safe way to update settings.
    val updateBrowserSettings = { newSettings: BrowserSettings ->
        browserSettings = newSettings
        Log.e("updateBrowserSettings", browserSettings.toString())
    }

    // LAUNCH EFFECTS
    //

    // This effect now ONLY handles the very first restoration of state.


    SideEffect {
        // The WebChromeClient handles UI-related browser events.
        webView.webChromeClient = object : WebChromeClient() {

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
                val context = webView.context
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
                webView.onResume()
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

                webView.onResume()
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


        }

        // The WebViewClient handles content loading events.
        webView.webViewClient = object : WebViewClient() {


            override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                super.onPageStarted(view, url, favicon)
                if (pendingPermissionRequest != null) pendingPermissionRequest = null
                isLoading = true

            }

            override fun onPageFinished(view: WebView?, currentUrlString: String?) {
                super.onPageFinished(view, currentUrlString)
                isLoading = false
            }

            override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                val url = request?.url ?: return false
                val urlString = url.toString()

                if (url.scheme == "http" || url.scheme == "https" ) {
                    return false // Let the WebView handle normal web links
                }

                if (url.scheme == "intent") {
                    try {
                        val intent = Intent.parseUri(urlString, Intent.URI_INTENT_SCHEME)
                        view?.context?.startActivity(intent)
                    } catch (e: Exception) {
                        Log.w("shouldOverrideUrlLoading", "Could not handle intent, trying fallback", e)
                        val packageName = try {
                            Intent.parseUri(urlString, Intent.URI_INTENT_SCHEME).`package`
                        } catch (parseEx: URISyntaxException) {
                            Log.e("shouldOverrideUrlLoading", "Could not get package name from intent", parseEx)
                            null
                        }

                        if (packageName != null) {
                            try {
                                val marketIntent = Intent(Intent.ACTION_VIEW, "market://details?id=$packageName".toUri())
                                view?.context?.startActivity(marketIntent)
                                view?.goBack()
                            } catch (marketError: Exception) {
                                Log.e("shouldOverrideUrlLoading", "Could not open Play Store for package: $packageName", marketError)
                            }
                        }
                    }
                    return true // We've handled the intent
                }

                // Handle other simple schemes like market://, mailto:// etc.
                try {
                    val intent = Intent(Intent.ACTION_VIEW, url)
                    // DO NOT add FLAG_ACTIVITY_NEW_TASK
                    view?.context?.startActivity(intent)

                    // Immediately go back to the previous page
                    view?.goBack()
                } catch (e: Exception) {
                    Log.w("WebView", "No app found to handle URL: $urlString", e)
                }
                return true
            }

            override fun doUpdateVisitedHistory(view: WebView?, url: String?, isReload: Boolean) {
                Log.i("doUpdateVisitedHistory", "<<<<<<<<<<<<<<<<")
                Log.i("doUpdateVisitedHistory", "<<<<<<<<<<<<<<<<")
                Log.i("doUpdateVisitedHistory", "URL updated: $url")
                Log.i("doUpdateVisitedHistory", "isReload: $isReload")
                if (!isFocusOnTextField) webView.url?.let {
                    textFieldValue = TextFieldValue(it, TextRange(it.length))
                }

                if (view == null || url == null) return


                tabs[activeTabIndex.intValue].let { tab ->

                    var databaseHistory = tabs[activeTabIndex.intValue].historyState
                    var updatedIndex = -99

                    if (databaseHistory == null) {
                        databaseHistory = SerializableBackForwardList(
                            items = emptyList(),
                            currentIndex = 0
                        )
                    } else {
                        updatedIndex = databaseHistory.currentIndex
                    }

                    val realtimeHistory = view.copyBackForwardList()
                    // LOG
                    Log.w("doUpdateVisitedHistory", "Realtime History:")
                    Log.i("doUpdateVisitedHistory", "Current Index ${realtimeHistory.currentIndex}")
                    for (i in 0 until realtimeHistory.size) {
                        val item = realtimeHistory.getItemAtIndex(i)
                        val marker = if (realtimeHistory.currentIndex == i) " << Current" else " "
                        Log.i("doUpdateVisitedHistory", "$i. URL: ${item.url} $marker")
                    }

                    Log.w("doUpdateVisitedHistory", "Database History:")
                    Log.i("doUpdateVisitedHistory", "Current Index ${databaseHistory.currentIndex}")

                    for (i in 0 until databaseHistory.items.size) {
                        val item = databaseHistory.items[i]
                        val marker = if (databaseHistory.currentIndex == i) " << Current" else " "
                        Log.i("doUpdateVisitedHistory", "$i. URL: ${item.url} $marker")
                    }
                    Log.i("doUpdateVisitedHistory", "")

                    val databaseCurrentItemUrl = databaseHistory.items[databaseHistory.currentIndex].url
                    val realtimeCurrentItemUrl = realtimeHistory.getItemAtIndex(realtimeHistory.currentIndex).url

                    var updatedHistoryItems = databaseHistory.items.toMutableList()

                    if (databaseCurrentItemUrl == realtimeCurrentItemUrl) {
                        Log.e("doUpdateVisitedHistory", "Same URl - Do Nothing")
                        isNavigateInProgress = false
                        return
                    } else {
                        var realtimePreviousItemUrl = " marcinlowercase "

                        if (realtimeHistory.currentIndex != 0) {
                            realtimePreviousItemUrl = realtimeHistory.getItemAtIndex(realtimeHistory.currentIndex - 1).url
                        }

                        if (databaseCurrentItemUrl == realtimePreviousItemUrl) {
                            Log.e("doUpdateVisitedHistory", "Add new url to database")
                            if (databaseHistory.currentIndex < databaseHistory.items.lastIndex) {
                                updatedHistoryItems = databaseHistory.items.subList(0, databaseHistory.currentIndex + 1).toMutableList()
                            }
                            updatedHistoryItems.add(
                                SerializableHistoryItem(
                                    url = realtimeCurrentItemUrl,
                                    title = realtimeHistory.currentItem?.title ?: ""
                                )
                            )
                            updatedIndex++
                            databaseCurrentIndexHolder = updatedIndex

                        } else {

                            Log.e("doUpdateVisitedHistory", "realTimePreviousItemUrl: $realtimePreviousIndexHolder")
                            Log.e("doUpdateVisitedHistory", "databaseCurrentItemUrl: ${realtimeHistory.currentIndex}")
                            if (realtimePreviousIndexHolder > realtimeHistory.currentIndex) {
                                Log.e("doUpdateVisitedHistory", "Back by Webview")

                                updatedIndex--
                            } else {
                                Log.e("doUpdateVisitedHistory", "Update existing url in database")
                                updatedHistoryItems[updatedIndex] = SerializableHistoryItem(
                                    url = realtimeCurrentItemUrl,
                                    title = realtimeHistory.currentItem?.title ?: ""
                                )
                            }


                        }
                        val updatedHistoryState = SerializableBackForwardList(
                            items = updatedHistoryItems,
                            currentIndex = updatedIndex
                        )
                        if (databaseHistory != updatedHistoryState) {

                            tabs[activeTabIndex.intValue] = tab.copy(historyState = updatedHistoryState)


                            val newDatabaseHistory = tabs[activeTabIndex.intValue].historyState
                            if (newDatabaseHistory == null) {
                                return
                            }
                            Log.w("doUpdateVisitedHistory", "NEW Database History:")
                            Log.i("doUpdateVisitedHistory", "Current Index ${newDatabaseHistory.currentIndex}")

                            for (i in 0 until newDatabaseHistory.items.size) {
                                val item = newDatabaseHistory.items[i]
                                val marker = if (newDatabaseHistory.currentIndex == i) " << Current" else " "
                                Log.i("doUpdateVisitedHistory", "$i. URL: ${item.url} $marker")
                            }
                            Log.i("doUpdateVisitedHistory", "")

                        }
                    }


                    Log.i("doUpdateVisitedHistory", ">>>>>>>>>>>>>>>")
                    Log.i("doUpdateVisitedHistory", "")
                    Log.i("doUpdateVisitedHistory", "")

                    realtimePreviousIndexHolder =  realtimeHistory.currentIndex
                }


                super.doUpdateVisitedHistory(view, url, isReload)




            }
        }

    }

    // This effect runs once and whenever isDarkTheme changes.
    LaunchedEffect(isDarkTheme) {
        val window = (view.context as Activity).window
        val insetsController = WindowCompat.getInsetsController(window, view)

        // true for light theme (dark icons), false for dark theme (light icons)
        insetsController.isAppearanceLightStatusBars = !isDarkTheme
        insetsController.isAppearanceLightNavigationBars = !isDarkTheme
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
        if (saveTrigger > 0) {
            tabManager.saveTabs(tabs)
        }
    }

    LaunchedEffect(Unit) {
        val urlToLoad = tabs[activeTabIndex.intValue].currentUrl ?: browserSettings.defaultUrl
        if (!initialLoadDone) {
            webView.loadUrl(urlToLoad)
            initialLoadDone = true
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
                tabs[activeTabIndex.intValue].historyState?.let { history ->
                    val newIndex =
                        history.currentIndex - 1
                    Log.i("GeckoHistoryLog", "NACK")
                    databaseCurrentIndexHolder = newIndex
                    history.items.getOrNull(newIndex)
                        ?.let { itemToLoad ->
                            isNavigateInProgress = true
                            webView.loadUrl(itemToLoad.url)
                            val updatedTab =
                                tabs[activeTabIndex.intValue].copy(
                                    historyState = history.copy(
                                        currentIndex = newIndex
                                    )
                                )
                            tabs[activeTabIndex.intValue] =
                                updatedTab
                            saveTrigger++
                        }
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
                                                        tabs[activeTabIndex.intValue].historyState?.let { history ->
                                                            val newIndex =
                                                                history.currentIndex - 1
                                                            Log.i("GeckoHistoryLog", "NACK")
                                                            databaseCurrentIndexHolder = newIndex
                                                            history.items.getOrNull(newIndex)
                                                                ?.let { itemToLoad ->
                                                                    isNavigateInProgress = true
                                                                    webView.loadUrl(itemToLoad.url)
                                                                    val updatedTab =
                                                                        tabs[activeTabIndex.intValue].copy(
                                                                            historyState = history.copy(
                                                                                currentIndex = newIndex
                                                                            )
                                                                        )
                                                                    tabs[activeTabIndex.intValue] =
                                                                        updatedTab
                                                                    saveTrigger++
                                                                }
                                                        }
                                                    }

                                                    GestureNavAction.REFRESH -> {

                                                        webView.reload()
                                                    }

                                                    GestureNavAction.FORWARD -> if (canGoForward) {
                                                        tabs[activeTabIndex.intValue].historyState?.let { history ->
                                                            val newIndex =
                                                                history.currentIndex + 1
                                                            Log.i("GeckoHistoryLog", "FORWARD")
                                                            databaseCurrentIndexHolder = newIndex
                                                            history.items.getOrNull(newIndex)
                                                                ?.let { itemToLoad ->
                                                                    isNavigateInProgress = true

                                                                    webView.loadUrl(itemToLoad.url)
                                                                    val updatedTab =
                                                                        tabs[activeTabIndex.intValue].copy(
                                                                            historyState = history.copy(
                                                                                currentIndex = newIndex
                                                                            )
                                                                        )
                                                                    tabs[activeTabIndex.intValue] =
                                                                        updatedTab
                                                                    saveTrigger++

                                                                }
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
                        activeTabIndex = activeTabIndex,
                        tabs = tabs,
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
            }
        )
    }


}

@Composable
fun BottomPanel(
    activeTabIndex: MutableState<Int>,
    tabs: List<Tab>,
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
                            val resetUrl = tabs[activeTabIndex.value].currentUrl ?: ""

                            if (input.isBlank()) {
                                changeTextFieldValue(
                                    TextFieldValue(
                                        resetUrl,
                                        TextRange(resetUrl.length)
                                    )
                                )
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
                            val resetUrl = tabs[activeTabIndex.value].currentUrl ?: ""
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
                                    val resetUrl = tabs[activeTabIndex.value].currentUrl ?: ""
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


//class WebAppInterface() {
//    @JavascriptInterface
//    fun logBackgroundColor(colorString: String) {
//        // We need a robust way to parse the "rgb(r, g, b)" or "rgba(r, g, b, a)" string.
//        try {
//
//            Log.e("WebViewBackground", "Detected web page background color: $colorString")
//
//
//        } catch (e: Exception) {
//            // If parsing fails for any reason, log it but don't crash.
//            Log.e("WebAppInterface", "Failed to parse color string: $colorString", e)
//        }
//    }
//}

