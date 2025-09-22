package marcinlowercase.oo.browser

import kotlinx.serialization.Serializable
import androidx.compose.animation.core.Animatable
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
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.util.Patterns
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.webkit.ConsoleMessage
import android.webkit.GeolocationPermissions
import android.webkit.PermissionRequest
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.ManagedActivityResultLauncher
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.awaitTouchSlopOrCancellation
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.gestures.drag
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusManager
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.hapticfeedback.HapticFeedback
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.edit
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import marcinlowercase.oo.browser.ui.theme.BrowserTheme
import java.net.URISyntaxException
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import androidx.core.net.toUri
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.coroutines.coroutineContext


//region Global Variables
private lateinit var webView: CustomWebView
var databaseCurrentIndexHolder = -1
var realtimePreviousIndexHolder = 0
var pixel_9_corner_radius = 54.6f

const val default_url = "https://oo3.deno.dev/i"
//endregion

//region Global Functions

fun cornerRadiusForLayer(layer: Int, deviceCornerRadius: Float = 0f, padding: Float = 0f): Float {

    if (layer == 0) {
        return deviceCornerRadius
    }
    return (cornerRadiusForLayer(layer - 1, deviceCornerRadius, padding) - padding)
}

//endregion

//region Data Class

// A sealed interface to represent any type of JS Dialog
sealed interface JsDialogState

// Represents the "OK" button dialog from window.alert()
data class JsAlert(val message: String) : JsDialogState

// Represents the "OK" / "Cancel" dialog from window.confirm()
data class JsConfirm(val message: String, val onResult: (Boolean) -> Unit) : JsDialogState

// Represents the text input dialog from window.prompt()
data class JsPrompt(
    val message: String,
    val defaultValue: String,
    val onResult: (String?) -> Unit
) : JsDialogState

data class OptionItem(
    val iconRes: Int, // The drawable resource ID for the icon
    val contentDescription: String,
    val onClick: () -> Unit,
)

data class ColorScheme(
    val backgroundColor: Color,
    val foregroundColor: Color
)

data class BrowserSettings(
    val paddingDp: Float,
    val deviceCornerRadius: Float,
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

        Log.i("TabManager", "Loading tabs with url: $defaultUrl")
        Log.i("TabManager", "Loading tabs with json: $jsonString")
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

        Log.i("TabManager", "Creating default tabs with url: $defaultUrl")
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

    var onWebViewTouch: (() -> Unit)? = null

    /**
     * This method is called for every touch event on the WebView.
     */
    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent?): Boolean {
        // 2. We only care about the beginning of a touch gesture.
        if (event?.action == MotionEvent.ACTION_DOWN) {
            // 3. If the user starts touching the screen, invoke our callback.
            onWebViewTouch?.invoke()
        }
        // 4. IMPORTANT: We must call super to let the WebView handle scrolling,
        // clicking, and other gestures normally.
        return super.onTouchEvent(event)
    }
//
//
//    override fun startActionMode(
//        callback: ActionMode.Callback,
//        type: Int
//    ): ActionMode? {
//        // Create a custom callback that does just enough to keep the mode alive
//        // for text highlighting, but never shows a menu.
//        val customCallback = object : ActionMode.Callback {
//            /**
//             * MUST return true. This tells the system to create the ActionMode,
//             * which is what enables the text highlighting.
//             */
//            override fun onCreateActionMode(mode: ActionMode, menu: Menu): Boolean {
//                callback.onCreateActionMode(mode, menu)
//                return true
//            }
//
//            /**
//
//             * This is the key. By returning false, we tell the system "Don't
//             * prepare or show the menu UI". The mode stays active in the background,
//             * but the user never sees the floating toolbar.
//             */
//            override fun onPrepareActionMode(mode: ActionMode, menu: Menu): Boolean {
//                // Let the default callback prepare the menu.
//                callback.onPrepareActionMode(mode, menu)
//
//                // --- OUR CUSTOM LOGIC STARTS HERE ---
//
//                var translateItem: MenuItem? = null
//                var itemIndex = -1
//
//                // 1. Find the "Translate" item
//                for (i in 0 until menu.size()) {
//                    val item = menu[i]
//                    if (item.title.toString().equals("Translate", ignoreCase = true)) {
//                        translateItem = item
//                        itemIndex = i
//                        break // Stop searching once we've found it
//                    }
//                }
//
//                // 2. If we found it, move it to the front
//                if (translateItem != null) {
//                    // a. Store all of its original properties
//                    val originalTitle: CharSequence? = translateItem.title
//                    val originalIcon: Drawable? = translateItem.icon
//                    val originalIntent: Intent? = translateItem.intent
//                    val originalGroupId: Int = translateItem.groupId
//                    val originalItemId: Int = translateItem.itemId
//
//                    // b. Remove the item from its original position
//                    menu.removeItem(originalItemId)
//
//                    // c. Re-add the item at the very beginning of the menu
//                    val newTranslateItem = menu.add(
//                        originalGroupId,
//                        originalItemId,
//                        Menu.FIRST, // This is the key to forcing it to the front
//                        originalTitle
//                    )
//
//                    // d. Restore its original intent and icon
//                    newTranslateItem.intent = originalIntent
//                    newTranslateItem.icon = originalIcon
//                }
//
//                // --- OUR CUSTOM LOGIC ENDS HERE ---
//
//                // **CRUCIAL**: Return true to allow the system to draw the
//                // now-modified menu. Returning false would hide it.
//                return true
//            }
//
//            // These methods won't be called since there are no menu items,
//            // but we must implement them.
//            override fun onActionItemClicked(mode: ActionMode, item: MenuItem): Boolean {
//                return callback.onActionItemClicked(mode, item)
//            }
//
//            override fun onDestroyActionMode(mode: ActionMode) {
//                // No action needed here. Our JavaScript handles hiding the
//                // custom Compose menu when the user clicks away.
//                callback.onDestroyActionMode(mode)
//
//            }
//        }
//
//        // We start the action mode, but we pass OUR custom callback, not the original one.
//        return super.startActionMode(callback, type)
//    }
}
//endregion


//region Composable


class MainActivity : ComponentActivity() {
    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        webView = CustomWebView(this).apply {
            // Force WebView to be transparent so Compose can control the background
            setBackgroundColor(android.graphics.Color.TRANSPARENT)

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
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    // Use the modern, non-deprecated API on Android 13+
                    isAlgorithmicDarkeningAllowed = false
                } else {
                    // Use the deprecated API for older versions, suppressing the warning
                    @Suppress("DEPRECATION")
                    forceDark = WebSettings.FORCE_DARK_OFF
                }

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


    //region Variables
    val context = LocalContext.current
    val sharedPrefs =
        remember { context.getSharedPreferences("BrowserPrefs", Context.MODE_PRIVATE) }
    var browserSettings by remember {
        mutableStateOf(
            BrowserSettings(
                paddingDp = sharedPrefs.getFloat("padding_dp", 8f),
                deviceCornerRadius = sharedPrefs.getFloat(
                    "corner_radius_dp",
                    pixel_9_corner_radius
                ),
                defaultUrl = sharedPrefs.getString("default_url", default_url)
                    ?: default_url,
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

    // Density is needed to convert Px to Dp


    var isUrlBarVisible by rememberSaveable { mutableStateOf(true) }
    var isPermissionPanelVisible by rememberSaveable { mutableStateOf(false) }
    var isBottomPanelVisible by rememberSaveable { mutableStateOf(true) }
    var isPromptPanelVisible by rememberSaveable { mutableStateOf(false) }


    var isNavPanelVisible by remember { mutableStateOf(false) }
    var activeNavAction by remember { mutableStateOf(GestureNavAction.REFRESH) }


    val hapticFeedback = LocalHapticFeedback.current

    var isNavigateInProgress by rememberSaveable { mutableStateOf(false) }


    var isOptionsPanelVisible by rememberSaveable { mutableStateOf(false) }

    val offsetY = remember { Animatable(0f) }
    var overlayHeightPx by remember { mutableFloatStateOf(0f) }


    val hasDisplayCutout by rememberHasDisplayCutout()


    val animatedPadding by animateDpAsState(
        targetValue = if (!isImmersiveMode) browserSettings.paddingDp.dp else 0.dp,
        label = "Padding Animation",
    )

    val animatedCornerRadius by animateDpAsState(
        targetValue = if (hasDisplayCutout) browserSettings.deviceCornerRadius.dp else 0.dp,
        label = "Corner Radius Animation",
    )

    // 1. Get the raw cutout padding values.
    val cutoutPaddingValues = WindowInsets.displayCutout.asPaddingValues()
    val cutoutTop = cutoutPaddingValues.calculateTopPadding()
    val cutoutBottom = cutoutPaddingValues.calculateBottomPadding()


    var pendingPermissionRequest by remember {
        mutableStateOf<CustomPermissionRequest?>(null)
    }
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

    var squareAlignment by remember { mutableStateOf(Alignment.BottomEnd) }
    val squareAlpha = remember { Animatable(0f) }

    //  hold the currently active dialog
    var jsDialogState by remember { mutableStateOf<JsDialogState?>(null) }
    var promptComponentDisplayState by remember { mutableStateOf<JsDialogState?>(null) }

    //endregion
    // FUNCTIONS


    //region Functions
    // This function will be our single, safe way to update settings.
    val updateBrowserSettings = { newSettings: BrowserSettings ->
        browserSettings = newSettings
        Log.e("updateBrowserSettings", browserSettings.toString())
    }

    fun navigateWebView() {
        when (activeNavAction) {
            GestureNavAction.BACK -> if (canGoBack) {
                tabs[activeTabIndex.intValue].historyState?.let { history ->
                    val newIndex =
                        history.currentIndex - 1
                    Log.i("Web View Navigation", "Back")

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
                isNavigateInProgress = true

                webView.reload()
            }

            GestureNavAction.FORWARD -> if (canGoForward) {
                tabs[activeTabIndex.intValue].historyState?.let { history ->
                    val newIndex =
                        history.currentIndex + 1
                    Log.i("Web View Navigation", "FORWARD")
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
    }


    //endregion


    // This effect now ONLY handles the very first restoration of state.

    SideEffect {

        webView.onWebViewTouch = {
            // Only hide the panel if it's currently visible.
            if (isUrlBarVisible) {
                isUrlBarVisible = false
            }
        }

        // The WebChromeClient handles UI-related browser events.
        webView.webChromeClient = object : WebChromeClient() {

            private var fullscreenView: View? = null

            // Handles window.alert()
            override fun onJsAlert(
                view: WebView?,
                url: String?,
                message: String?,
                result: android.webkit.JsResult?
            ): Boolean {
                jsDialogState = JsAlert(message ?: "")
                // We consume the result here and will handle it in our Compose Dialog
                result?.confirm()
                return true // Return true to indicate we've handled it.
            }

            // Handles window.confirm()
            override fun onJsConfirm(
                view: WebView?,
                url: String?,
                message: String?,
                result: android.webkit.JsResult?
            ): Boolean {
                jsDialogState = JsConfirm(message ?: "") { confirmed ->
                    if (confirmed) result?.confirm() else result?.cancel()
                }
                return true
            }

            // Handles window.prompt()
            override fun onJsPrompt(
                view: WebView?,
                url: String?,
                message: String?,
                defaultValue: String?,
                result: android.webkit.JsPromptResult?
            ): Boolean {
                jsDialogState = JsPrompt(message ?: "", defaultValue ?: "") { inputText ->
                    if (inputText != null) {
                        result?.confirm(inputText)
                    } else {
                        result?.cancel()
                    }
                }
                return true
            }

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

            override fun shouldOverrideUrlLoading(
                view: WebView?,
                request: WebResourceRequest?
            ): Boolean {
                val url = request?.url ?: return false
                val urlString = url.toString()

                if (url.scheme == "http" || url.scheme == "https") {
                    return false // Let the WebView handle normal web links
                }

                if (url.scheme == "intent") {
                    try {
                        val intent = Intent.parseUri(urlString, Intent.URI_INTENT_SCHEME)
                        view?.context?.startActivity(intent)
                    } catch (e: Exception) {
                        Log.w(
                            "shouldOverrideUrlLoading",
                            "Could not handle intent, trying fallback",
                            e
                        )
                        val packageName = try {
                            Intent.parseUri(urlString, Intent.URI_INTENT_SCHEME).`package`
                        } catch (parseEx: URISyntaxException) {
                            Log.e(
                                "shouldOverrideUrlLoading",
                                "Could not get package name from intent",
                                parseEx
                            )
                            null
                        }

                        if (packageName != null) {
                            try {
                                val marketIntent = Intent(
                                    Intent.ACTION_VIEW,
                                    "market://details?id=$packageName".toUri()
                                )
                                view?.context?.startActivity(marketIntent)
                                view?.goBack()
                            } catch (marketError: Exception) {
                                Log.e(
                                    "shouldOverrideUrlLoading",
                                    "Could not open Play Store for package: $packageName",
                                    marketError
                                )
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

                    if (databaseHistory == null) {
                        val items =
                            List(1) { SerializableHistoryItem(browserSettings.defaultUrl, "") }
                        databaseHistory = SerializableBackForwardList(
                            items = items,
                            currentIndex = 0
                        )
                    }
                    var updatedIndex: Int = databaseHistory.currentIndex


                    val realtimeHistory = view.copyBackForwardList()

                    if (realtimeHistory.size <= 1 && databaseHistory.items.size == 1 &&
                        (realtimeHistory.currentItem?.url == null || realtimeHistory.currentItem?.url == "about:blank")
                    ) {
                        Log.d("doUpdateVisitedHistory", "Ignoring initial empty history update.")
                        return
                    }

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

                    val databaseCurrentItemUrl =
                        databaseHistory.items[databaseHistory.currentIndex].url
                    val realtimeCurrentItemUrl =
                        realtimeHistory.getItemAtIndex(realtimeHistory.currentIndex).url

                    var updatedHistoryItems = databaseHistory.items.toMutableList()

                    if (databaseCurrentItemUrl == realtimeCurrentItemUrl) {
                        Log.e("doUpdateVisitedHistory", "Same URl - Do Nothing")
                        isNavigateInProgress = false
                        return
                    } else {
                        var realtimePreviousItemUrl = " marcinlowercase "

                        if (realtimeHistory.currentIndex != 0) {
                            realtimePreviousItemUrl =
                                realtimeHistory.getItemAtIndex(realtimeHistory.currentIndex - 1).url
                        }

                        if (databaseCurrentItemUrl == realtimePreviousItemUrl) {
                            Log.e("doUpdateVisitedHistory", "Add new url to database")
                            if (databaseHistory.currentIndex < databaseHistory.items.lastIndex) {
                                updatedHistoryItems = databaseHistory.items.subList(
                                    0,
                                    databaseHistory.currentIndex + 1
                                ).toMutableList()
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

                            Log.e(
                                "doUpdateVisitedHistory",
                                "realTimePreviousItemUrl: $realtimePreviousIndexHolder"
                            )
                            Log.e(
                                "doUpdateVisitedHistory",
                                "databaseCurrentItemUrl: ${realtimeHistory.currentIndex}"
                            )
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

                            tabs[activeTabIndex.intValue] =
                                tab.copy(historyState = updatedHistoryState)
                            saveTrigger++


                            val newDatabaseHistory = tabs[activeTabIndex.intValue].historyState
                            if (newDatabaseHistory == null) {
                                return
                            }
                            Log.w("doUpdateVisitedHistory", "NEW Database History:")
                            Log.i(
                                "doUpdateVisitedHistory",
                                "Current Index ${newDatabaseHistory.currentIndex}"
                            )

                            for (i in 0 until newDatabaseHistory.items.size) {
                                val item = newDatabaseHistory.items[i]
                                val marker =
                                    if (newDatabaseHistory.currentIndex == i) " << Current" else " "
                                Log.i("doUpdateVisitedHistory", "$i. URL: ${item.url} $marker")
                            }
                            Log.i("doUpdateVisitedHistory", "")

                        }
                    }


                    Log.i("doUpdateVisitedHistory", ">>>>>>>>>>>>>>>")
                    Log.i("doUpdateVisitedHistory", "")
                    Log.i("doUpdateVisitedHistory", "")

                    realtimePreviousIndexHolder = realtimeHistory.currentIndex
                }


                super.doUpdateVisitedHistory(view, url, isReload)


            }
        }

    }


    //region LaunchedEffect

    LaunchedEffect(jsDialogState) {
        if (jsDialogState != null) {
            promptComponentDisplayState = jsDialogState
        }
    }
    LaunchedEffect(isUrlBarVisible) {
        if (!isUrlBarVisible) isOptionsPanelVisible = false
    }
    LaunchedEffect(jsDialogState) {
        isPromptPanelVisible = jsDialogState != null
    }

    LaunchedEffect(isUrlBarVisible, isPermissionPanelVisible, isPromptPanelVisible) {
        isBottomPanelVisible = isUrlBarVisible || isPermissionPanelVisible || isPromptPanelVisible
//        Log.i("VisibleState", "isBottomPanelVisible: $isBottomPanelVisible")
    }


    LaunchedEffect(pendingPermissionRequest) {
        isPermissionPanelVisible = pendingPermissionRequest != null
    }
    // This effect will re-launch whenever isBottomPanelVisible changes.
    LaunchedEffect(isBottomPanelVisible, squareAlignment) {
        if (!isBottomPanelVisible) {
            // -- The URL bar has just been hidden. Start the "show and blink" sequence. --

            // a. Instantly appear with 0.6 opacity.
            squareAlpha.snapTo(0.7f)

            // b. Wait a moment so the user can see it before it blinks.
            delay(400)

            // c. Blink twice.
            repeat(2) {
                // Fade out
                squareAlpha.animateTo(0f, animationSpec = tween(durationMillis = 300))
                // Fade back in
                squareAlpha.animateTo(0.7f, animationSpec = tween(durationMillis = 300))
            }

            // d. After blinking, fade out completely.
            squareAlpha.animateTo(0f, animationSpec = tween(durationMillis = 400))
        } else {
            // -- The URL bar is visible. Ensure the square is fully transparent. --
            squareAlpha.snapTo(0f)
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
    LaunchedEffect(Unit) {
        val window = (context as? Activity)?.window ?: return@LaunchedEffect
        val insetsController = WindowCompat.getInsetsController(window, window.decorView)

        // Hide the system bars permanently
        insetsController.hide(WindowInsetsCompat.Type.systemBars())

        // Configure the swipe-to-reveal behavior
        insetsController.systemBarsBehavior =
            WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
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
            putFloat("corner_radius_dp", browserSettings.deviceCornerRadius)
            putString("default_url", browserSettings.defaultUrl)
            putInt("animation_speed", browserSettings.animationSpeed)
            putInt("single_line_height", browserSettings.singleLineHeight)
            putInt("desktop_mode_width", browserSettings.desktopModeWidth)

        }
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

    LaunchedEffect(canGoBack, canGoForward) {
        Log.i("Web View Navigation", "canGoBack $canGoBack")
        Log.i("Web View Navigation", "canGoForward $canGoForward")
    }

    //endregion
    BackHandler(enabled = !isBottomPanelVisible || canGoBack) {
        when {
            // Priority 1: Exit fullscreen video if it's active.
            customView != null -> {
                customViewCallback?.onCustomViewHidden()

            }
            // Priority 2: Navigate back in the WebView.
            canGoBack -> {
                tabs[activeTabIndex.intValue].historyState?.let { history ->
                    val newIndex =
                        history.currentIndex - 1
                    Log.i("Web View Navigation", "NACK")
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
    // LAYOUT
    //
    //
    Box(modifier = Modifier.fillMaxSize()) {
        Box(
            modifier = modifier
                .fillMaxSize()
                .padding(top = cutoutTop, bottom = cutoutBottom)
        ) {
            Column(
                modifier = modifier
                    .fillMaxSize()
                    .padding(
                        bottom = cutoutBottom
                    )
            ) {


                // Webview Container
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
//                            .padding(animatedPadding)
//                            .padding(
////                                top = animatedCutoutTop,
//                                start = animatedCutoutStart,
//                                end = animatedCutoutEnd,
//                                bottom = animatedCutoutBottom
//                            )
//                        .padding(all = (if (pendingPermissionRequest != null) browserSettings.paddingDp.dp else 0.dp))

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
                    LoadingOverlay(isLoading = isLoading, colorScheme = colorScheme)
                }
            }

            BottomPanel(
                navigateWebView = {
                    navigateWebView()
                },
                hapticFeedback = hapticFeedback,
                setIsNavPanelVisible = { isNavPanelVisible = it },
                setActiveNavAction = { activeNavAction = it },

                canGoBack = canGoBack,
                canGoForward = canGoForward,
                isNavPanelVisible = isNavPanelVisible,
                activeNavAction = activeNavAction,

                state = if (jsDialogState != null) jsDialogState!! else null,
                promptComponentDisplayState = if (promptComponentDisplayState != null) promptComponentDisplayState else null,
                onDismiss = { jsDialogState = null },
                isPromptPanelVisible = isPromptPanelVisible,

                isPermissionPanelVisible = isPermissionPanelVisible,
                permissionLauncher = permissionLauncher,
                pendingPermissionRequest = pendingPermissionRequest,
                modifier = Modifier
                    // This aligns the panel to the bottom center of the Box
                    .windowInsetsPadding(WindowInsets.ime)
                    .align(Alignment.BottomCenter),
                activeTabIndex = activeTabIndex,
                tabs = tabs,
                isImmersiveMode = isImmersiveMode,
                isUrlBarVisible = isUrlBarVisible,
                isBottomPanelVisible = isBottomPanelVisible,
                isOptionsPanelVisible = isOptionsPanelVisible,
                browserSettings = browserSettings,
                updateBrowserSettings = updateBrowserSettings,
                textFieldValue = textFieldValue,
//                        url = url,
                focusManager = focusManager,
                keyboardController = keyboardController,
                toggleOptionsPanel = { isOptionsPanelVisible = it },
                changeTextFieldValue = { textFieldValue = it },
                setPendingPermissionRequest = { pendingPermissionRequest = it },
                onNewUrl = { newUrl ->
                    webView.loadUrl(newUrl)
//                            }
                },
                setIsFocusOnTextField = { isFocusOnTextField = it },


                )


            // BackSquare
            AnimatedVisibility(
                visible = !isBottomPanelVisible,
                modifier = Modifier.align(squareAlignment), // Align to bottom-right corner
                enter = fadeIn(animationSpec = tween(browserSettings.animationSpeed)),
                exit = fadeOut(animationSpec = tween(browserSettings.animationSpeed))
            ) {


                Box(
                    modifier = Modifier
                        .height(
                            cornerRadiusForLayer(
                                1,
                                browserSettings.deviceCornerRadius,
                                browserSettings.paddingDp
                            ).dp * 2
                        )
                        .fillMaxWidth(0.45f)
                        .graphicsLayer {
                            alpha = squareAlpha.value
                        }
                        .pointerInput(Unit) {
                            // 1. Get the CoroutineScope at the top level of pointerInput
                            detectDragGestures(
                                onDragStart = {
                                    // This is called once the drag passes the touch slop
                                },
                                onDragEnd = {
                                    // This is called when the user lifts their finger
                                },
                                onDrag = { change, dragAmount ->
                                    // This is called for every movement during the drag
                                    change.consume()

                                    val (dx, dy) = dragAmount // Destructure for clarity (delta x, delta y)

                                    // 2. Compare the horizontal and vertical movement
                                    if (kotlin.math.abs(dx) > kotlin.math.abs(dy)) {
                                        // -- HORIZONTAL DRAG IS DOMINANT --
                                        squareAlignment = if (dx < 0) { // Dragging left
                                            Alignment.BottomStart
                                        } else { // Dragging right
                                            Alignment.BottomEnd
                                        }
                                    } else {
                                        // -- VERTICAL DRAG IS DOMINANT --
                                        // We only need to trigger this once per gesture to show the bar
                                        if (!isUrlBarVisible) {
                                            isUrlBarVisible = true
                                        }
                                    }
                                }
                            )
                        }
                        .padding(
                            end = browserSettings.paddingDp.dp,
                            start = browserSettings.paddingDp.dp, // Add start padding for when it's on the left
                            bottom = browserSettings.paddingDp.dp
                        )
                        .clip(
                            RoundedCornerShape(
                                cornerRadiusForLayer(
                                    1,
                                    browserSettings.deviceCornerRadius,
                                    browserSettings.paddingDp
                                ).dp
                            )
                        )
                        .background(Color.Black.copy(alpha = 0.4f))
                        .border(
                            2.dp,
                            Color.White.copy(alpha = 0.5f),
                            shape = RoundedCornerShape(
                                cornerRadiusForLayer(
                                    1,
                                    browserSettings.deviceCornerRadius,
                                    browserSettings.paddingDp
                                ).dp
                            )
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        painter = painterResource(R.drawable.ic_expand_circle_up),
                        contentDescription = "Back",
                        tint = Color.White
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
    }


}

@Composable
fun BottomPanel(
    navigateWebView: () -> Unit,
    hapticFeedback: HapticFeedback,
    setActiveNavAction: (GestureNavAction) -> Unit,
    setIsNavPanelVisible: (Boolean) -> Unit,
    canGoBack: Boolean,
    canGoForward: Boolean,
    isNavPanelVisible: Boolean,
    activeNavAction: GestureNavAction,
    isPromptPanelVisible: Boolean = false,
    state: JsDialogState?,
    promptComponentDisplayState: JsDialogState?,
    onDismiss: () -> Unit,
    isPermissionPanelVisible: Boolean = false,
    permissionLauncher: ManagedActivityResultLauncher<Array<String>, Map<String, @JvmSuppressWildcards Boolean>>,
    pendingPermissionRequest: CustomPermissionRequest?,
    modifier: Modifier,
    activeTabIndex: MutableState<Int>,
    tabs: List<Tab>,
    isImmersiveMode: Boolean,
    isUrlBarVisible: Boolean,
    isBottomPanelVisible: Boolean,
    isOptionsPanelVisible: Boolean,
    browserSettings: BrowserSettings,
    updateBrowserSettings: (BrowserSettings) -> Int,
    textFieldValue: TextFieldValue,
//    url: String,
    focusManager: FocusManager,
    keyboardController: SoftwareKeyboardController?,
    toggleOptionsPanel: (Boolean) -> Unit = {},
    changeTextFieldValue: (TextFieldValue) -> Unit = {},
    onNewUrl: (String) -> Unit = {},
    setTextFieldHeightPx: (Int) -> Unit = {},
    setIsFocusOnTextField: (Boolean) -> Unit = {},
    setPendingPermissionRequest: (CustomPermissionRequest?) -> Unit = {},

    ) {
    AnimatedVisibility(
        modifier = modifier,
        visible = isBottomPanelVisible,
        enter = fadeIn(animationSpec = tween(browserSettings.animationSpeed)),
        exit = fadeOut(animationSpec = tween(browserSettings.animationSpeed))
//        enter = expandVertically(tween(browserSettings.animationSpeed)),
//        exit = shrinkVertically(tween(browserSettings.animationSpeed))
    ) {

        Column(
            modifier = Modifier
                .padding(browserSettings.paddingDp.dp)
                .background(
                    Color.Black.copy(alpha = 0.5f),
                    shape = RoundedCornerShape(
                        cornerRadiusForLayer(
                            1,
                            browserSettings.deviceCornerRadius,
                            browserSettings.paddingDp
                        ).dp
                    )
                )
        ) {

            PromptPanel(
                browserSettings = browserSettings,
//                modifier = modifier,
                isPromptPanelVisible = isPromptPanelVisible,
                onDismiss = onDismiss,
                state = state,
                promptComponentDisplayState = promptComponentDisplayState,

                )

            AnimatedVisibility(visible = isNavPanelVisible) {
                NavigationPanel(
                    browserSettings = browserSettings,
                    activeAction = activeNavAction,
                    canGoBack = canGoBack, // Make sure to pass these down from BrowserScreen
                    canGoForward = canGoForward // And this one too
                )
            }

            PermissionPanel(
                isPermissionPanelVisible = isPermissionPanelVisible,
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
                    setPendingPermissionRequest(null)
//                    pendingPermissionRequest = null
                }
            )


            // URL BAR
            AnimatedVisibility(
                modifier = modifier
                    .pointerInput(Unit) {
                        // The long press on the UrlBar will activate the gesture

                    },
                visible = isUrlBarVisible,
//                enter = fadeIn(animationSpec = tween(browserSettings.animationSpeed)),
//                exit = fadeOut(animationSpec = tween(browserSettings.animationSpeed))
                enter = expandVertically(tween(browserSettings.animationSpeed)) + fadeIn(
                    tween(
                        browserSettings.animationSpeed
                    )
                ),
                exit = shrinkVertically(tween(browserSettings.animationSpeed)) + fadeOut(
                    tween(
                        browserSettings.animationSpeed
                    )
                )
            ) {
                Box(
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
                ) {
                    val focusRequester = remember { FocusRequester() }

                    OutlinedTextField(
                        modifier = Modifier
                            .height(
                                cornerRadiusForLayer(
                                    1,
                                    browserSettings.deviceCornerRadius,
                                    browserSettings.paddingDp
                                ).dp * 2
                            )
                            .onSizeChanged { size ->
                                setTextFieldHeightPx(size.height)
                            }
                            .fillMaxWidth()
                            .focusRequester(focusRequester)
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
                                        val resetUrl =
                                            tabs[activeTabIndex.value].currentUrl ?: ""
                                        changeTextFieldValue(
                                            TextFieldValue(
                                                resetUrl,
                                                selection = TextRange(resetUrl.length)
                                            )
                                        )
                                    }
                                }
                            },
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
                                        URLEncoder.encode(
                                            input,
                                            StandardCharsets.UTF_8.toString()
                                        )
                                    "https://www.google.com/search?q=$encodedQuery"
                                }

                                onNewUrl(finalUrl)

                                focusManager.clearFocus()
                                keyboardController?.hide()

                            }
                        ),
                        shape = RoundedCornerShape(
                            cornerRadiusForLayer(
                                1,
                                browserSettings.deviceCornerRadius,
                                browserSettings.paddingDp
                            ).dp
                        ),
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor = Color.Black, // Background when focused
                            unfocusedContainerColor = Color.Black.copy(0.8f), // Background when unfocused
                            cursorColor = Color.White,
                            disabledContainerColor = Color.White, // Background when disabled
                            errorContainerColor = Color.Red, // Background when in error state.
                            focusedIndicatorColor = Color.White.copy(0.95f),      // Outline color when focused
                            unfocusedIndicatorColor = Color.White.copy(0.8f),    // Outline color when unfocused
                            disabledIndicatorColor = Color.White, // Outline color when disabled
                            errorIndicatorColor = Color.Red,          // Outline color on error
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White.copy(0.8f),
                        )
                    )

                    Box(
                        modifier = Modifier
                            .background(
                                Color.Transparent, shape = RoundedCornerShape(
                                    cornerRadiusForLayer(
                                        1,
                                        browserSettings.deviceCornerRadius,
                                        browserSettings.paddingDp
                                    ).dp
                                )
                            )
                            .clip(
                                RoundedCornerShape(
                                    cornerRadiusForLayer(
                                        1,
                                        browserSettings.deviceCornerRadius,
                                        browserSettings.paddingDp
                                    ).dp
                                )
                            )
                            .matchParentSize()
                            .pointerInput(Unit, canGoBack, canGoForward) {
                                // 1. CAPTURE the CoroutineScope provided by pointerInput
                                val coroutineScope = CoroutineScope(coroutineContext)
                                awaitEachGesture {
                                    val down = awaitFirstDown(requireUnconsumed = false)

                                    // 2. USE the captured scope to launch the long press job
                                    val longPressJob = coroutineScope.launch {
                                        delay(viewConfiguration.longPressTimeoutMillis)

                                        // LONG PRESS CONFIRMED
                                        hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
                                        focusManager.clearFocus(true)
                                        setIsNavPanelVisible(true)
                                        setActiveNavAction(GestureNavAction.NONE)

                                    }

                                    val drag =
                                        awaitTouchSlopOrCancellation(down.id) { change, _ ->
                                            if (longPressJob.isActive) {
                                                longPressJob.cancel()
                                            }
                                            change.consume()
                                        }

                                    if (longPressJob.isCompleted && !longPressJob.isCancelled) {
                                        if (drag != null) {
                                            var horizontalDragAccumulator = 0f
                                            var verticalDragAccumulator = 0f
                                            var previousAction = GestureNavAction.REFRESH
                                            val horizontalDragThreshold =
                                                with(density) { 40.dp.toPx() }

                                            val verticalCancelThreshold =
                                                with(density) { -40.dp.toPx() }


                                            drag(drag.id) { change ->
                                                change.consume()
                                                horizontalDragAccumulator += change.position.x - change.previousPosition.x
                                                verticalDragAccumulator += change.position.y - change.previousPosition.y

                                                val newAction = when {
                                                    verticalDragAccumulator < verticalCancelThreshold -> GestureNavAction.REFRESH
                                                    horizontalDragAccumulator < -horizontalDragThreshold -> if (canGoBack) GestureNavAction.BACK else GestureNavAction.NONE
                                                    horizontalDragAccumulator > horizontalDragThreshold -> if (canGoForward) GestureNavAction.FORWARD else GestureNavAction.NONE
                                                    else -> GestureNavAction.NONE
                                                }

                                                if (newAction != previousAction) {
                                                    hapticFeedback.performHapticFeedback(
                                                        HapticFeedbackType.TextHandleMove
                                                    )
                                                    previousAction = newAction
                                                }
//                                                    activeNavAction = newAction
                                                setActiveNavAction(newAction)
                                            }

                                            navigateWebView()
                                        }
                                    } else {
                                        if (drag != null) {
                                            // IT'S A VERTICAL SWIPE to open OptionsPanel
                                            var vAccumulator = 0f
                                            drag(drag.id) { change ->
                                                vAccumulator += change.position.y - change.previousPosition.y
                                            }
                                            // Check the final drag direction
                                            if (vAccumulator < 0) toggleOptionsPanel(true) // Swipe Up
                                            else toggleOptionsPanel(false) // Swipe Down

                                        } else {
                                            // Gesture is fully over
                                            if (longPressJob.isActive) {
                                                longPressJob.cancel()
                                                // This was a tap
                                                focusRequester.requestFocus()
                                            }
                                        }
                                    }

//                                        // Gesture is fully over
//                                        if (longPressJob.isActive) {
//                                            longPressJob.cancel()
//                                            // This was a tap
//                                            focusRequester.requestFocus()
//                                        }

                                    // Reset the UI state
//                                        isNavPanelVisible = false
                                    setIsNavPanelVisible(false)
//                                        activeNavAction = GestureNavAction.NONE
                                    setActiveNavAction(GestureNavAction.NONE)
                                }
                            }
                    )
                    {

                    }
                }
            }

            // SETTING OPTIONS
            OptionsPanel(
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
    browserSettings: BrowserSettings,
    // The pending request, which also controls visibility. Null means hidden.
    request: CustomPermissionRequest?,
    // Event for when the user clicks "Allow" on our panel.
    onAllow: () -> Unit,
    // Event for when the user clicks "Deny" on our panel.
    onDeny: () -> Unit,
    isPermissionPanelVisible: Boolean = false,

    ) {
    var requestToShow by remember { mutableStateOf(request) }

    LaunchedEffect(request) {
        if (request != null) {
            // If there's a new request, update immediately.
            requestToShow = request
        }
    }

    AnimatedVisibility(
        visible = isPermissionPanelVisible,
        enter = expandVertically(animationSpec = tween(browserSettings.animationSpeed)) + fadeIn(tween(browserSettings.animationSpeed)),
        exit = shrinkVertically(animationSpec = tween(browserSettings.animationSpeed))  + fadeOut(tween(browserSettings.animationSpeed))
    ) {

        val currentRequest = requestToShow
        if (currentRequest == null) return@AnimatedVisibility

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = Color.Transparent
            ),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(browserSettings.paddingDp.dp),
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
                            .height(
                                cornerRadiusForLayer(
                                    2,
                                    browserSettings.deviceCornerRadius,
                                    browserSettings.paddingDp
                                ).dp * 2
                            )
                            .border(
                                4.dp, Color.White, shape = RoundedCornerShape(
                                    cornerRadiusForLayer(
                                        2,
                                        browserSettings.deviceCornerRadius,
                                        browserSettings.paddingDp
                                    ).dp
                                )
                            ),

                        colors = IconButtonDefaults.iconButtonColors(
                            containerColor = Color.Black.copy(alpha = 0.5f)
                        ),
                    ) {
                        Icon(
                            painter = painterResource(id = currentRequest.iconResDeny), // You can make this icon generic too
                            contentDescription = "Deny Permission",
                            tint = Color.White
                        )
                    }

                    // --- Allow Button ---
                    IconButton(
                        onClick = onAllow,
                        modifier = Modifier
                            .weight(1f)
                            .height(
                                cornerRadiusForLayer(
                                    2,
                                    browserSettings.deviceCornerRadius,
                                    browserSettings.paddingDp
                                ).dp * 2
                            ),
                        colors = IconButtonDefaults.iconButtonColors(
                            containerColor = Color.Black
                        )
                    ) {
                        Icon(
                            painter = painterResource(id = currentRequest.iconResAllow), // You can make this icon generic too
                            contentDescription = "Allow Permission",
                            tint = Color.White
                        )
                    }
                }
            }
        }
    }
}


@Composable
fun OptionsPanel(
    isImmersiveMode: Boolean,
    isOptionsPanelVisible: Boolean = false,
    toggleOptionsPanel: (Boolean) -> Unit = {},
    updateBrowserSettings: (BrowserSettings) -> Int,
    browserSettings: BrowserSettings,
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
        enter = expandVertically(tween(browserSettings.animationSpeed)) + fadeIn(
            tween(
                browserSettings.animationSpeed
            )
        ),
        exit = shrinkVertically(tween(browserSettings.animationSpeed)) + fadeOut(
            tween(
                browserSettings.animationSpeed
            )
        ),
    ) {
        Box(
            modifier = Modifier
                .padding(browserSettings.paddingDp.dp)
                .fillMaxWidth()
                .clip(
                    RoundedCornerShape(
                        cornerRadiusForLayer(
                            2,
                            browserSettings.deviceCornerRadius,
                            browserSettings.paddingDp
                        ).dp
                    )
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
                        .background(
                            Color.Black.copy(alpha = 0.4f),
                            shape = RoundedCornerShape(
                                cornerRadiusForLayer(
                                    2,
                                    browserSettings.deviceCornerRadius,
                                    browserSettings.paddingDp
                                ).dp
                            )
                        ),

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
                                .height(
                                    cornerRadiusForLayer(
                                        2,
                                        browserSettings.deviceCornerRadius,
                                        browserSettings.paddingDp
                                    ).dp * 2
                                )
                                .background(
                                    Color.Black.copy(alpha = 0.8f),
                                    shape = RoundedCornerShape(
                                        cornerRadiusForLayer(
                                            2,
                                            browserSettings.deviceCornerRadius,
                                            browserSettings.paddingDp
                                        ).dp
                                    )
                                ),
//                                .border(
//                                    1.dp,
//                                    colorScheme.backgroundColor,
//                                    RoundedCornerShape(browserSettings.deviceCornerRadius.dp)
//                                )


                        ) {
                            Icon(
                                painter = painterResource(id = option.iconRes),
                                contentDescription = option.contentDescription,
                                tint = Color.White
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
fun PromptPanel(
    browserSettings: BrowserSettings,
    isPromptPanelVisible: Boolean,
    state: JsDialogState?,
    promptComponentDisplayState: JsDialogState?,
    onDismiss: () -> Unit,
) {
    AnimatedVisibility(
//        modifier = modifier,
        visible = isPromptPanelVisible,
        enter = fadeIn(tween(browserSettings.animationSpeed)),
        exit = shrinkVertically(tween(browserSettings.animationSpeed)) + fadeOut(
            tween(
                browserSettings.animationSpeed
            )
        )
    ) {
        var textInput by remember(state) {
            mutableStateOf(if (state is JsPrompt) state.defaultValue else "")
        }
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(browserSettings.paddingDp.dp)

        ) {

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = browserSettings.paddingDp.dp)
                    .padding(horizontal = browserSettings.paddingDp.dp * 3)
                    .background(
                        Color.Black,
                        shape = RoundedCornerShape(
                            cornerRadiusForLayer(
                                2,
                                browserSettings.deviceCornerRadius,
                                browserSettings.paddingDp
                            ).dp
                        )
                    ),
                verticalAlignment = Alignment.CenterVertically // Keeps text aligned nicely
            ) {
                // "from" Text - Fixed Size
                Row(
                    modifier = Modifier
                        .padding(browserSettings.paddingDp.dp)
                ) {
                    Text(
                        text = "from ", // Added a space for better readability
                        color = Color.White.copy(alpha = 0.7f), // Subtly de-emphasize
                        maxLines = 1, // Ensure it doesn't wrap
                    )

                    // URL Text - Scrollable and takes up remaining space
                    Box(
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(
                            text = webView.url ?: "the current page", // Safely handle null URL
                            color = Color.White,
                            maxLines = 1, // Crucial for horizontal scrolling
                            overflow = TextOverflow.Ellipsis, // Good practice, though scrolling will hide it
                            modifier = Modifier
//                                .weight(1f) // Takes all available remaining space
                                .horizontalScroll(rememberScrollState()) // THIS MAKES IT SCROLLABLE
                        )
                    }
                }
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        Color.Black,
                        shape = RoundedCornerShape(
                            cornerRadiusForLayer(
                                2,
                                browserSettings.deviceCornerRadius,
                                browserSettings.paddingDp
                            ).dp
                        )
                    )
            )
            {

                val textModifier = Modifier
                    .padding(browserSettings.paddingDp.dp)
                Column(
                    modifier = Modifier
                        .padding(browserSettings.paddingDp.dp)
                        .background(
                            color = Color.Transparent,
                            shape = RoundedCornerShape(
                                cornerRadiusForLayer(
                                    3,
                                    browserSettings.deviceCornerRadius,
                                    browserSettings.paddingDp
                                ).dp
                            )
                        )
                ) {
                    when (promptComponentDisplayState) {
                        is JsAlert -> Text(
                            text = promptComponentDisplayState.message,
                            modifier = textModifier
                        )

                        is JsConfirm -> Text(
                            text = promptComponentDisplayState.message,
                            modifier = textModifier
                        )

                        is JsPrompt -> {
                            Text(
                                text = promptComponentDisplayState.message,
                                modifier = textModifier
                            )
                            Spacer(Modifier.height(browserSettings.paddingDp.dp))
                            OutlinedTextField(
                                value = textInput,
                                onValueChange = { textInput = it },
                                maxLines = 6, //hardcode
                                modifier = Modifier
                                    .height(IntrinsicSize.Min)
                                    .fillMaxWidth(),
                                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Default),
                                keyboardActions = KeyboardActions(
                                    onDone = {
                                        webView.requestFocus()
                                        promptComponentDisplayState.onResult(textInput)
                                        onDismiss()
                                    }
                                ),
                                shape = RoundedCornerShape(
                                    cornerRadiusForLayer(
                                        3,
                                        browserSettings.deviceCornerRadius,
                                        browserSettings.paddingDp
                                    ).dp
                                ),
                                colors = TextFieldDefaults.colors(
                                    focusedContainerColor = Color.Black.copy(0.95f), // Background when focused
                                    unfocusedContainerColor = Color.Black.copy(0.8f), // Background when unfocused
                                    cursorColor = Color.White,
                                    disabledContainerColor = Color.White, // Background when disabled
                                    errorContainerColor = Color.Red, // Background when in error state.
                                    focusedIndicatorColor = Color.White.copy(0.95f),      // Outline color when focused
                                    unfocusedIndicatorColor = Color.White.copy(0.8f),    // Outline color when unfocused
                                    disabledIndicatorColor = Color.White, // Outline color when disabled
                                    errorIndicatorColor = Color.Red,          // Outline color on error
                                    focusedTextColor = Color.White,
                                    unfocusedTextColor = Color.White.copy(0.8f),
                                )
                            )
                        }

                        null -> {

                        }
                    }
                }

                // Action buttons
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(browserSettings.paddingDp.dp),
                    horizontalArrangement = Arrangement.spacedBy(
                        browserSettings.paddingDp.dp
                    ),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val buttonModifier = Modifier
                        .height(
                            cornerRadiusForLayer(
                                3,
                                browserSettings.deviceCornerRadius,
                                browserSettings.paddingDp
                            ).dp * 2
                        )
                        .weight(1f)

                    // Dismiss/Cancel Button (only for confirm/prompt)
                    if (promptComponentDisplayState is JsConfirm || promptComponentDisplayState is JsPrompt) {
                        Button(
                            modifier = buttonModifier
                                .border(
                                    4.dp, Color.Black, shape = RoundedCornerShape(
                                        cornerRadiusForLayer(
                                            3,
                                            browserSettings.deviceCornerRadius,
                                            browserSettings.paddingDp,
                                        ).dp
                                    )
                                ),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color.White.copy(alpha = 0.5f)
                            ),
                            shape = RoundedCornerShape(
                                cornerRadiusForLayer(
                                    3,
                                    browserSettings.deviceCornerRadius,
                                    browserSettings.paddingDp,
                                ).dp
                            ),
                            onClick = {
                                webView.requestFocus()
                                when (state) {
                                    is JsConfirm -> state.onResult(false)
                                    is JsPrompt -> state.onResult(null)
                                    else -> {}
                                }
                                onDismiss()
                            },

                            ) {
                            Icon(
                                painter = painterResource(id = R.drawable.ic_close),
                                contentDescription = "Dismiss",
                            )
                        }
                    }


                    // Confirm Button
                    Button(
                        modifier = buttonModifier
                            .background(
                                Color.White, shape = RoundedCornerShape(
                                    cornerRadiusForLayer(
                                        3,
                                        browserSettings.deviceCornerRadius,
                                        browserSettings.paddingDp
                                    ).dp
                                )
                            ),
                        shape = RoundedCornerShape(
                            cornerRadiusForLayer(
                                2,
                                browserSettings.deviceCornerRadius,
                                browserSettings.paddingDp,
                            ).dp
                        ),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color.White
                        ),
                        onClick = {
                            webView.requestFocus()
                            when (state) {
                                is JsAlert -> { /* Just dismiss */
                                }

                                is JsConfirm -> state.onResult(true)
                                is JsPrompt -> state.onResult(textInput)
                                null -> {

                                }
                            }
                            onDismiss()
                        },
                    ) {
                        Icon(
                            painter = painterResource(id = R.drawable.ic_check),
                            contentDescription = "Confirm",
                        )
                    }
                }


            }


        }

    }
}



@Composable
fun NavigationPanel(
    modifier: Modifier = Modifier,
    browserSettings: BrowserSettings,
    activeAction: GestureNavAction,
    canGoBack: Boolean,
    canGoForward: Boolean
) {
    Box(
        modifier = Modifier
            .padding(browserSettings.paddingDp.dp)
    ) {
        Column(
            modifier = modifier

                .clip(
                    RoundedCornerShape(
                        cornerRadiusForLayer(
                            2,
                            browserSettings.deviceCornerRadius,
                            browserSettings.paddingDp
                        ).dp
                    )
                )
                .background(Color.Black.copy(0.3f)),

            ) {
            Row(
                modifier = modifier
                    .fillMaxWidth()
                    .height(browserSettings.singleLineHeight.dp)
                    .padding(browserSettings.paddingDp.dp),


                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Refresh Icon
                NavigationItem(
                    modifier = Modifier.weight(1f),
                    activeAction = activeAction,
                    gestureNavAction = GestureNavAction.REFRESH,
                    actionIcon = painterResource(R.drawable.ic_refresh)
                )
            }
            Row(
                modifier = modifier
                    .fillMaxWidth()
                    .height(browserSettings.singleLineHeight.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Back Icon
                NavigationItem(
                    modifier = Modifier.weight(1f),
                    activeAction = activeAction,
                    gestureNavAction = GestureNavAction.BACK,
                    actionIcon = painterResource(R.drawable.ic_arrow_back),
                    visibility = canGoBack
                )

                // Cancel Icon
                NavigationItem(
                    modifier = Modifier.weight(1f),
                    activeAction = activeAction,
                    gestureNavAction = GestureNavAction.NONE,
                    actionIcon = painterResource(R.drawable.ic_close)
                )

                // Forward Icon
                // Back Icon
                NavigationItem(
                    modifier = Modifier.weight(1f),
                    activeAction = activeAction,
                    gestureNavAction = GestureNavAction.FORWARD,
                    actionIcon = painterResource(R.drawable.ic_arrow_forward),
                    visibility = canGoForward
                )
            }
        }
    }
}


@Composable
fun NavigationItem(
    modifier: Modifier,
    activeAction: GestureNavAction,
    gestureNavAction: GestureNavAction,
    actionIcon: Painter,
    visibility: Boolean = true,
) {
    // Cancel Icon
    val refreshColor by animateColorAsState(if (activeAction == gestureNavAction) Color.White else Color.Transparent)
    Box(
        modifier = modifier
            .fillMaxHeight()
            .clip(RoundedCornerShape(16.dp))
            .background(refreshColor)
    ) {
        if (visibility) {
            Icon(
                actionIcon,
                "Refresh",
                Modifier.align(Alignment.Center),
                tint = if (activeAction == gestureNavAction) Color.Black else Color.White
            )
        }

    }

}

//endregion
