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
import android.content.res.AssetManager
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
import org.mozilla.geckoview.GeckoRuntime
import org.mozilla.geckoview.GeckoRuntimeSettings
import org.mozilla.geckoview.GeckoSession
import org.mozilla.geckoview.GeckoSessionSettings
import org.mozilla.geckoview.GeckoView
import org.mozilla.geckoview.WebExtensionController
import java.io.IOException
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import kotlin.collections.get
import kotlin.coroutines.coroutineContext
import kotlin.text.get


private lateinit var geckoView: GeckoView
private lateinit var session: GeckoSession
private lateinit var runtime: GeckoRuntime

var currentIndexValue = 0

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        // 1. Initialize GeckoRuntime (MUST happen before GeckoSession is created)
        // Ensure you have the necessary dependency in your build.gradle:
        // implementation "org.mozilla.geckoview:geckoview-nightly:..."
        if (!::runtime.isInitialized) {
            runtime = GeckoRuntime.create(this, GeckoRuntimeSettings.Builder()
                .extensionsProcessEnabled(true)
                .build())
        }

        installUblockOrigin()


        // 2. Initialize GeckoView and GeckoSession
        geckoView = GeckoView(this)
        session = GeckoSession()


        // 3. Open the session with the runtime
        session.open(runtime)
        geckoView.setSession(session)



        session.settings.apply {
            allowJavascript = true
        }



        setContent {
            BrowserTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    BrowserScreen()
                }
            }
        }

    }
    private fun installUblockOrigin() {
        // Get the controller from the runtime.
        val extensionController = runtime.webExtensionController

        // Define the extension's official ID and its path within our assets.
        val uBlockId = "uBlock0@raymondhill.net"
        val uBlockAssetPath = "resource://android/assets/extensions/ublock_origin.xpi"

        Log.d("ExtensionManager", "Attempting to install uBlock Origin from: $uBlockAssetPath")

        // Pre-flight check to make sure the file is bundled correctly.
        try {
            assets.open("extensions/ublock_origin.xpi").close()
        } catch (e: IOException) {
            Log.e("ExtensionManager", "CRITICAL ERROR: uBlock Origin .xpi file not found in assets/extensions/. Installation aborted.", e)
            return
        }

        // Install the extension. GeckoView handles cases where it's already installed.
        val installResult = extensionController.install(uBlockAssetPath, uBlockId)

        // Log the result of the asynchronous installation.
        installResult.accept(
            { extension ->
                Log.i("ExtensionManager", "SUCCESS: uBlock Origin is installed. ID: ${extension?.id}")
                // Ensure it's enabled (it is by default after install).
                if (extension != null) extensionController.enable(extension, WebExtensionController.EnableSource.APP)
            },
            { error ->
                Log.e("ExtensionManager", "ERROR: Failed to install uBlock Origin.", error)
            }
        )
    }


    override fun onDestroy() {
        super.onDestroy()
        if (::session.isInitialized && session.isOpen) {
            session.close()
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
                    items = listOf(SerializableHistoryItem(url = defaultUrl, title = "Default")),
                    currentIndex = 0
                )
            )
        )
    }
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

    currentIndexValue = tabs[activeTabIndex.intValue].historyState?.currentIndex ?: 0

    var initialLoadDone by rememberSaveable { mutableStateOf(false) }
    var isNavigateInProgress by rememberSaveable { mutableStateOf(false) }


    var saveTrigger by remember { mutableIntStateOf(0) }


    var textFieldValue by rememberSaveable(stateSaver = TextFieldValue.Saver) {
        mutableStateOf(TextFieldValue(tabs[activeTabIndex.value].currentUrl ?: "", TextRange(0)))
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
        derivedStateOf { ((tabs[activeTabIndex.value].historyState?.currentIndex ?: 0) > 0) && !isNavigateInProgress }
    }
    val canGoForward by remember {
        derivedStateOf {
            val history = tabs[activeTabIndex.value].historyState
            if (history == null) false else (history.currentIndex < history.items.lastIndex) && !isNavigateInProgress
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


    // FUNCTIONS


    // This function will be our single, safe way to update settings.
    val updateBrowserSettings = { newSettings: BrowserSettings ->
        browserSettings = newSettings
        Log.e("updateBrowserSettings", browserSettings.toString())
    }

    // LAUNCH EFFECTS
    //

    SideEffect {
        // CONTENT DELEGATE (for loading progress)
        session.progressDelegate = object : GeckoSession.ProgressDelegate {
            override fun onPageStart(session: GeckoSession, url: String) {
                isLoading = true
            }

            override fun onPageStop(session: GeckoSession, success: Boolean) {
                isLoading = false
            }
        }

        // NAVIGATION DELEGATE (for history and URL updates)
        session.navigationDelegate = object : GeckoSession.NavigationDelegate {
            // This is the primary method for history management
            override fun onLocationChange(
                session: GeckoSession,
                url: String?,
                permissions: List<GeckoSession.PermissionDelegate.ContentPermission>,
                hasUserGesture: Boolean
            ) {
                super.onLocationChange(session, url, permissions, hasUserGesture)

                if (url != null) {
                    // Update the text field if not focused
                    if (!isFocusOnTextField) {
                        textFieldValue = TextFieldValue(url, TextRange(url.length))
                    }
                }
            }
        }

        // CORRECT DELEGATE FOR HISTORY MANAGEMENT
        session.historyDelegate = object : GeckoSession.HistoryDelegate {
            override fun onHistoryStateChange(
                session: GeckoSession,
                realtimeHistory: GeckoSession.HistoryDelegate.HistoryList
            ) {


//                // 1. LOG THE HISTORY LIST AS REQUESTED
                Log.d("GeckoHistoryLog", "--- History State Changed ---")
                Log.e("GeckoHistoryLog", "REALTIMe")
                Log.d("GeckoHistoryLog", "Total items: ${realtimeHistory.size}")
                Log.d("GeckoHistoryLog", "Current index: ${realtimeHistory.currentIndex}")
                realtimeHistory.forEachIndexed { index, item ->
                    val marker = if (index == realtimeHistory.currentIndex) "<- CURRENT" else ""
                    Log.d("GeckoHistoryLog", "  [$index]: ${item.uri} ${marker}")
                }
                Log.d("GeckoHistoryLog", "-------------------------------")

                // 2. SYNCHRONIZE OUR SAVED TAB STATE
                tabs[activeTabIndex.value].let { tab ->
                    var databaseHistory = tab.historyState

                    var updatedIndex = -99

                    if (databaseHistory == null) {
                        databaseHistory =
                            SerializableBackForwardList(items = emptyList(), currentIndex = 0)
                    } else {
                        updatedIndex = databaseHistory.currentIndex

                        if (realtimeHistory.isEmpty()) {
                            Log.w("GeckoHistoryLog", "onHistoryStateChange called with an empty history list. Ignoring.")
                            marcinlowercase.oo.browser.session.loadUri(databaseHistory.items[databaseHistory.currentIndex].url)
                            return
                        }
                    }


                    Log.e("GeckoHistoryLog", "DB")
                    Log.d("GeckoHistoryLog", "Total items: ${databaseHistory.items.size}")
                    Log.d("GeckoHistoryLog", "Current index: ${databaseHistory.currentIndex}")
                    databaseHistory.items.forEachIndexed { index, item ->
                        val marker = if (index == databaseHistory.currentIndex) "<- CURRENT" else ""
                        Log.d("GeckoHistoryLog", "  [$index]: ${item.url} ${marker}")
                    }
                    Log.d("GeckoHistoryLog", "-------------------------------")


                    var updatedHistory = databaseHistory?.items?.toMutableList()


                    val currentUrl = databaseHistory.items[currentIndexValue].url
                    val realtimeCurrentItem = realtimeHistory[realtimeHistory.currentIndex]


                    Log.i("GeckoHistoryLog", "BEFORE BIGGEST IF")
                    Log.i("GeckoHistoryLog", "currentUrl: $currentUrl")
                    Log.i("GeckoHistoryLog", "realtimeCurrentItem.uri: ${realtimeCurrentItem.uri}")
                    Log.i("GeckoHistoryLog", "")
                    if (currentUrl == realtimeCurrentItem.uri) {
                        Log.i("GeckoHistoryLog", "DO NOTHING")
                        Log.i("GeckoHistoryLog", "")
                        // Navigation done, ready for the next action
                        isNavigateInProgress = false
                        // Do nothing
                        return
                    } else {
                        val realtimePreviousItem = realtimeHistory[realtimeHistory.currentIndex - 1]
                        if (currentUrl == realtimePreviousItem.uri) {
                            Log.i("GeckoHistoryLog", "realtimePreviousItem.uri: ${realtimePreviousItem.uri}")
                            Log.i("GeckoHistoryLog", "")
                            Log.w("GeckoHistoryLog", "Add new")

                            if (databaseHistory.currentIndex < databaseHistory.items.size - 1) {
                                updatedHistory = databaseHistory.items.subList(
                                    fromIndex = 0,
                                    toIndex = databaseHistory.currentIndex + 1
                                ).toMutableList()

                            }
                            updatedHistory?.add(
                                SerializableHistoryItem(
                                    url = realtimeCurrentItem.uri,
                                    title = realtimeCurrentItem.title
                                )
                            )
                            updatedIndex++
                            currentIndexValue = updatedIndex

                        } else {
                            Log.w("GeckoHistoryLog", "Replaced")
                            updatedHistory?.set(
                                updatedIndex,
                                SerializableHistoryItem(
                                    url = realtimeCurrentItem.uri,
                                    title = realtimeCurrentItem.title
                                )
                            )
                        }
                        var updatedHistoryState: SerializableBackForwardList? = null
                        if (updatedHistory != null) {
                            updatedHistoryState = SerializableBackForwardList(
                                items = updatedHistory,
                                currentIndex = updatedIndex
                            )
                        }

                        if (databaseHistory != updatedHistory) {
                            tabs[activeTabIndex.value] =
                                tab.copy(historyState = updatedHistoryState)
                            saveTrigger++

                            val brandNewHistory = tabs[activeTabIndex.value].historyState
                            if (brandNewHistory == null) {
                                return
                            }
                            // 1. LOG THE HISTORY LIST AS REQUESTED
                            Log.e("GeckoHistoryLog", "NEWWWWW")
                            Log.d("GeckoHistoryLog", "Total items: ${brandNewHistory.items.size}")
                            Log.d(
                                "GeckoHistoryLog",
                                "Current index: ${brandNewHistory.currentIndex}"
                            )
                            brandNewHistory.items.forEachIndexed { index, item ->
                                val marker =
                                    if (index == brandNewHistory.currentIndex) "<- CURRENT" else ""
                                Log.d("GeckoHistoryLog", "  [$index]: ${item.url} ${marker}")
                            }
                            Log.d("GeckoHistoryLog", "-------------------------------")
                        }
                    }

                }
            }
        }


        // You can add ChromeDelegate and PermissionDelegate here as well if needed
    }

    LaunchedEffect(currentIndexValue) {
        Log.e("GeckoHistoryLog", "EEEEEEEEEE $currentIndexValue")
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
        session.settings.userAgentMode = if (browserSettings.isDesktopMode) {
            GeckoSessionSettings.USER_AGENT_MODE_DESKTOP
        } else {
            GeckoSessionSettings.USER_AGENT_MODE_MOBILE
        }
        session.reload()
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

    LaunchedEffect(Unit) {
        val urlToLoad = tabs[activeTabIndex.value].currentUrl ?: browserSettings.defaultUrl
        if (!initialLoadDone) {
            session.loadUri(urlToLoad)
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


//    // This effect will re-launch whenever the animatedPadding value changes (i.e., every frame).
//    LaunchedEffect(animatedPadding) {
//        // We now have a hook that runs on every animation frame.
//        // We can command our WebView to update its layout.
//        webView.requestLayout()
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
                tabs[activeTabIndex.value].historyState?.let { history ->
                    val newIndex = history.currentIndex - 1
                    currentIndexValue = newIndex
                    history.items.getOrNull(newIndex)
                        ?.let { itemToLoad ->
                            session.loadUri(itemToLoad.url)

                            val updatedTab =
                                tabs[activeTabIndex.value].copy(
                                    historyState = history.copy(
                                        currentIndex = newIndex
                                    )
                                )
                            tabs[activeTabIndex.value] =
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
                                    // The factory should simply return the pre-configured GeckoView instance.
                                    // The parent removal is still critical to prevent crashes on recomposition.
                                    (geckoView.parent as? ViewGroup)?.removeView(geckoView)
                                    geckoView
                                },
//                                factory = {
//                                    FrameLayout(it).apply {
//                                        // If the WebView still has a parent from a previous composition, remove it.
//                                        (geckoView.parent as? ViewGroup)?.removeView(geckoView)
//
//                                        // Add our singleton WebView to it.
//                                        addView(
//                                            geckoView,
//                                            FrameLayout.LayoutParams(
//                                                FrameLayout.LayoutParams.MATCH_PARENT,
//                                                FrameLayout.LayoutParams.MATCH_PARENT
//                                            ).apply {
//                                                gravity = Gravity.CENTER
//                                            }
//                                        )
//                                    }
//                                },
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
                                                        tabs[activeTabIndex.value].historyState?.let { history ->
                                                            val newIndex = history.currentIndex - 1
                                                            Log.i("GeckoHistoryLog", "NACK")
                                                            currentIndexValue = newIndex


                                                            history.items.getOrNull(newIndex)
                                                                ?.let { itemToLoad ->
                                                                    isNavigateInProgress = true
                                                                    session.loadUri(itemToLoad.url)


                                                                    val updatedTab =
                                                                        tabs[activeTabIndex.value].copy(
                                                                            historyState = history.copy(
                                                                                currentIndex = newIndex
                                                                            )
                                                                        )
                                                                    tabs[activeTabIndex.value] = updatedTab
                                                                    saveTrigger++

                                                                }
                                                        }
                                                    }

                                                    GestureNavAction.REFRESH -> {
                                                        isNavigateInProgress = true

                                                        session.reload()
                                                    }

                                                    GestureNavAction.FORWARD -> if (canGoForward) {
                                                        tabs[activeTabIndex.intValue].historyState?.let { history ->
                                                            val newIndex = history.currentIndex + 1
                                                            Log.i("GeckoHistoryLog", "FORWARD")
                                                            currentIndexValue = newIndex
                                                            history.items.getOrNull(newIndex)
                                                                ?.let { itemToLoad ->
                                                                    isNavigateInProgress = true
                                                                    session.loadUri(itemToLoad.url)
                                                                    val updatedTab =
                                                                        tabs[activeTabIndex.intValue].copy(
                                                                            historyState = history.copy(
                                                                                currentIndex = newIndex
                                                                            )
                                                                        )
                                                                    tabs[activeTabIndex.value] =
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
                        tabs = tabs,
                        activeTabIndex = activeTabIndex,

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
                            session.loadUri(newUrl)
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

