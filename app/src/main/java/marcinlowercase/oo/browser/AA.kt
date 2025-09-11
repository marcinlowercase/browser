//package marcinlowercase.oo.browser
//
//
//import kotlinx.serialization.Serializable
//import androidx.compose.animation.core.Animatable
//import androidx.compose.ui.unit.IntOffset
//import kotlin.math.roundToInt
//import androidx.core.content.ContextCompat
//import android.content.pm.PackageManager
//import android.Manifest
//import android.app.Activity
//import android.content.Context
//import android.content.pm.ActivityInfo
//import android.content.pm.ApplicationInfo
//import android.graphics.Bitmap
//import android.os.Bundle
//import android.util.Log
//import android.util.Patterns
//import android.view.Gravity
//import android.view.View
//import android.view.ViewGroup
//import android.webkit.ConsoleMessage
//import android.webkit.GeolocationPermissions
//import android.webkit.JavascriptInterface
//import android.webkit.PermissionRequest
//import androidx.compose.ui.geometry.Rect
//import androidx.compose.ui.layout.boundsInRoot
//import androidx.compose.ui.layout.onGloballyPositioned
//import android.webkit.WebChromeClient
//import android.webkit.WebResourceRequest
//import android.webkit.WebResourceResponse
//import android.webkit.WebSettings
//import android.webkit.WebView
//import android.webkit.WebViewClient
//import android.widget.FrameLayout
//import androidx.activity.ComponentActivity
//import androidx.activity.compose.BackHandler
//import androidx.activity.compose.rememberLauncherForActivityResult
//import androidx.activity.compose.setContent
//import androidx.activity.enableEdgeToEdge
//import androidx.activity.result.contract.ActivityResultContracts
//import androidx.compose.animation.AnimatedVisibility
//import androidx.compose.animation.animateColorAsState
//import androidx.compose.animation.core.EaseIn
//import androidx.compose.animation.core.animateDpAsState
//import androidx.compose.animation.core.animateFloatAsState
//import androidx.compose.animation.core.snap
//import androidx.compose.animation.core.tween
//import androidx.compose.animation.expandVertically
//import androidx.compose.animation.fadeIn
//import androidx.compose.animation.fadeOut
//import androidx.compose.animation.shrinkVertically
//import androidx.compose.foundation.background
//import androidx.compose.foundation.gestures.awaitEachGesture
//import androidx.compose.foundation.gestures.awaitFirstDown
//import androidx.compose.foundation.gestures.awaitTouchSlopOrCancellation
//import androidx.compose.foundation.gestures.detectHorizontalDragGestures
//import androidx.compose.foundation.gestures.detectVerticalDragGestures
//import androidx.compose.foundation.gestures.drag
//import androidx.compose.foundation.isSystemInDarkTheme
//import androidx.compose.foundation.layout.*
//import androidx.compose.foundation.layout.Arrangement
//import androidx.compose.foundation.pager.HorizontalPager
//import androidx.compose.foundation.pager.rememberPagerState
//import androidx.compose.foundation.shape.RoundedCornerShape
//import androidx.compose.foundation.text.KeyboardActions
//import androidx.compose.foundation.text.KeyboardOptions
//import androidx.compose.material3.*
////import androidx.compose.material3.value
//import androidx.compose.runtime.*
//import androidx.compose.runtime.saveable.rememberSaveable
//import androidx.compose.ui.Alignment
//import androidx.compose.ui.Modifier
//import androidx.compose.ui.draw.blur
//import androidx.compose.ui.draw.clip
//import androidx.compose.ui.focus.FocusManager
//import androidx.compose.ui.focus.onFocusChanged
//import androidx.compose.ui.graphics.Color
//import androidx.compose.ui.hapticfeedback.HapticFeedbackType
//import androidx.compose.ui.input.pointer.pointerInput
//import androidx.compose.ui.layout.onSizeChanged
//import androidx.compose.ui.platform.LocalConfiguration
//import androidx.compose.ui.platform.LocalContext
//import androidx.compose.ui.platform.LocalDensity
//import androidx.compose.ui.platform.LocalFocusManager
//import androidx.compose.ui.platform.LocalHapticFeedback
//import androidx.compose.ui.platform.LocalSoftwareKeyboardController
//import androidx.compose.ui.platform.SoftwareKeyboardController
//import androidx.compose.ui.platform.testTag
//import androidx.compose.ui.res.painterResource
//import androidx.compose.ui.text.TextRange
//import androidx.compose.ui.text.input.ImeAction
//import androidx.compose.ui.text.input.TextFieldValue
//import androidx.compose.ui.tooling.preview.Preview
//import androidx.compose.ui.unit.Dp
//import androidx.compose.ui.unit.LayoutDirection
//import androidx.compose.ui.unit.Velocity
//import androidx.compose.ui.unit.dp
//import androidx.compose.ui.viewinterop.AndroidView
//import androidx.core.content.edit
//import androidx.core.view.WindowCompat
//import androidx.core.view.WindowInsetsCompat
//import androidx.core.view.WindowInsetsControllerCompat
//import kotlinx.coroutines.CoroutineScope
//import kotlinx.coroutines.launch
//import kotlinx.serialization.encodeToString
//import kotlinx.serialization.json.Json
//import marcinlowercase.oo.browser.ui.theme.BrowserTheme
//import org.mozilla.geckoview.*
//import java.net.URLEncoder
//import java.nio.charset.StandardCharsets
//import kotlin.collections.get
//import kotlin.coroutines.coroutineContext
//import kotlin.text.get
//
//
//// --- REMOVED WebView variables ---
//private lateinit var geckoView: GeckoView
//private lateinit var session: GeckoSession
//private lateinit var runtime: GeckoRuntime
//
//// Callback interface for state changes from GeckoView delegates
//interface OnGeckoStateChangedListener {
//    fun onUrlChanged(newUrl: String?)
//    fun onHistoryUpdated()
//}
//
//class MainActivity : ComponentActivity() {
//
//    override fun onCreate(savedInstanceState: Bundle?) {
//        enableEdgeToEdge()
//        super.onCreate(savedInstanceState)
//
//        // 1. Initialize GeckoRuntime
//        if (!::runtime.isInitialized) {
//            runtime = GeckoRuntime.create(this)
//        }
//
//        // 2. Initialize GeckoView and GeckoSession
//        geckoView = GeckoView(this)
//        session = GeckoSession()
//
//        // 3. Open the session with the runtime
//        session.open(runtime)
//
//        // 4. Set initial GeckoSession settings
//        session.settings.apply {
//            javaScriptEnabled = true
//            domStorageEnabled = true
//            userAgentMode = GeckoSessionSettings.UserAgentMode.MOBILE
//        }
//
//        // 5. REMOVED the old WebView initialization block.
//        // The initial URL will be loaded by the BrowserScreen composable.
//
//        setContent {
//            BrowserTheme {
//                Surface(modifier = Modifier.fillMaxSize()) {
//                    BrowserScreen()
//                }
//            }
//        }
//    }
//
//    override fun onDestroy() {
//        super.onDestroy()
//        // Clean up GeckoView resources
//        if (::session.isInitialized && session.isOpen) {
//            session.close()
//        }
//    }
//}
//
//data class BrowserSettings(
//    val paddingDp: Float,
//    val cornerRadiusDp: Float,
//    val isInteractable: Boolean,
//    val defaultUrl: String,
//    val animationSpeed: Int,
//    val singleLineHeight: Int,
//    val isDesktopMode: Boolean,
//    val desktopModeWidth: Int,
//)
//
//enum class GestureNavAction {
//    NONE,
//    BACK,
//    REFRESH,
//    FORWARD
//}
//
//data class CustomPermissionRequest(
//    val title: String,
//    val rationale: String,
//    val iconResAllow: Int,
//    val iconResDeny: Int,
//    val permissionsToRequest: List<String>,
//    val onResult: (Map<String, Boolean>) -> Unit
//)
//
//val LocalBrowserSettings = compositionLocalOf {
//    BrowserSettings(
//        paddingDp = 8f,
//        cornerRadiusDp = 24f,
//        isInteractable = true,
//        defaultUrl = "https://www.google.com/",
//        animationSpeed = 300,
//        singleLineHeight = 64,
//        isDesktopMode = false,
//        desktopModeWidth = 820,
//    )
//}
//
//@Serializable
//enum class TabState {
//    ACTIVE,
//    BACKGROUND,
//    FROZEN
//}
//
//@Serializable
//data class SerializableHistoryItem(val url: String, val title: String)
//
//@Serializable
//data class SerializableBackForwardList(
//    val items: List<SerializableHistoryItem>,
//    val currentIndex: Int
//)
//
//@Serializable
//data class Tab(
//    val id: Long = System.currentTimeMillis(),
//    var state: TabState = TabState.BACKGROUND,
//    var historyState: SerializableBackForwardList? = null
//) {
//    val currentUrl: String?
//        get() = historyState?.items?.getOrNull(historyState!!.currentIndex)?.url
//}
//
//class TabManager(context: Context) {
//    private val prefs = context.getSharedPreferences("BrowserTabs", Context.MODE_PRIVATE)
//    private val json = Json { ignoreUnknownKeys = true }
//
//    private val TABS_KEY = "tabs_list_json"
//
//    fun saveTabs(tabs: List<Tab>) {
//        val jsonString = json.encodeToString(tabs)
//        prefs.edit {
//            putString(TABS_KEY, jsonString)
//        }
//        Log.d("TabManager", "Tabs saved.")
//    }
//
//    fun loadTabs(defaultUrl: String): MutableList<Tab> {
//        val jsonString = prefs.getString(TABS_KEY, null)
//
//        return if (jsonString != null) {
//            try {
//                json.decodeFromString<MutableList<Tab>>(jsonString)
//            } catch (e: Exception) {
//                Log.e("TabManager", "Failed to decode tabs, creating default.", e)
//                createDefaultTabs(defaultUrl)
//            }
//        } else {
//            createDefaultTabs(defaultUrl)
//        }
//    }
//
//    private fun createDefaultTabs(defaultUrl: String): MutableList<Tab> {
//        return mutableListOf(
//            Tab(
//                state = TabState.ACTIVE,
//                historyState = SerializableBackForwardList(
//                    items = listOf(SerializableHistoryItem(url = defaultUrl, title = "")),
//                    currentIndex = 0
//                )
//            )
//        )
//    }
//}
//
//
//@Composable
//fun rememberHasDisplayCutout(): State<Boolean> {
//    val configuration = LocalConfiguration.current
//    val density = LocalDensity.current
//
//    val displayCutoutPaddingValues =
//        WindowInsets.displayCutout.asPaddingValues()
//
//    val hasCutout = remember(configuration, density, displayCutoutPaddingValues) {
//        derivedStateOf {
//            (displayCutoutPaddingValues.calculateTopPadding() > 0.dp ||
//                    displayCutoutPaddingValues.calculateLeftPadding(LayoutDirection.Ltr) > 0.dp ||
//                    displayCutoutPaddingValues.calculateRightPadding(LayoutDirection.Ltr) > 0.dp)
//        }
//    }
//    return hasCutout
//}
//
//@Composable
//fun BrowserScreen(modifier: Modifier = Modifier) {
//
//    /// VARIABLES
//    val context = LocalContext.current
//    val activity = context as? Activity
//
//    val sharedPrefs =
//        remember { context.getSharedPreferences("BrowserPrefs", Context.MODE_PRIVATE) }
//    var browserSettings by remember {
//        mutableStateOf(
//            BrowserSettings(
//                paddingDp = sharedPrefs.getFloat("padding_dp", 8f),
//                cornerRadiusDp = sharedPrefs.getFloat("corner_radius_dp", 24f),
//                isInteractable = sharedPrefs.getBoolean("is_interactable", true),
//                defaultUrl = sharedPrefs.getString("default_url", "https://www.google.com/")
//                    ?: "https://www.google.com/",
//                animationSpeed = sharedPrefs.getInt("animation_speed", 300),
//                singleLineHeight = sharedPrefs.getInt("single_line_height", 64),
//                isDesktopMode = sharedPrefs.getBoolean("is_desktop_mode", false),
//                desktopModeWidth = sharedPrefs.getInt("desktop_mode_width", 820),
//
//                )
//        )
//    }
//
//    val tabManager = remember { TabManager(context) }
//    val tabs = remember {
//        mutableStateListOf<Tab>().apply {
//            addAll(tabManager.loadTabs(browserSettings.defaultUrl))
//        }
//    }
//    val activeTabIndex = remember {
//        mutableIntStateOf(tabs.indexOfFirst { it.state == TabState.ACTIVE }.coerceAtLeast(0))
//    }
//    val currentTab by remember {
//        derivedStateOf { tabs.getOrNull(activeTabIndex.value) }
//    }
//
//    var initialLoadDone by rememberSaveable { mutableStateOf(false) }
//    var saveTrigger by remember { mutableIntStateOf(0) }
//
//    var textFieldValue by rememberSaveable(stateSaver = TextFieldValue.Saver) {
//        mutableStateOf(TextFieldValue(currentTab?.currentUrl ?: "", TextRange(0)))
//    }
//
//    var isImmersiveMode by remember { mutableStateOf(false) }
//    var isLoading by remember { mutableStateOf(false) }
//    var isFocusOnTextField by remember { mutableStateOf(false) }
//
//    val keyboardController = LocalSoftwareKeyboardController.current
//    val focusManager = LocalFocusManager.current
//
//    var textFieldHeightPx by remember { mutableIntStateOf(0) }
//    val density = LocalDensity.current
//    val hapticFeedback = LocalHapticFeedback.current
//
//    val textFieldHeightDp = with(density) { textFieldHeightPx.toDp() }
//
//    var isUrlBarVisible by rememberSaveable { mutableStateOf(true) }
//    var isOptionsPanelVisible by rememberSaveable { mutableStateOf(false) }
//
//    val offsetY = remember { Animatable(0f) }
//    var activeGestureAction by remember { mutableStateOf(GestureNavAction.NONE) }
//    var overlayHeightPx by remember { mutableFloatStateOf(0f) }
//
//    var backButtonRect by remember { mutableStateOf(Rect.Zero) }
//    var refreshButtonRect by remember { mutableStateOf(Rect.Zero) }
//    var forwardButtonRect by remember { mutableStateOf(Rect.Zero) }
//
//    val hasDisplayCutout by rememberHasDisplayCutout()
//
//    // --- All animation states remain the same ---
//    val animatedPadding by animateDpAsState(
//        targetValue = if (!isImmersiveMode) browserSettings.paddingDp.dp else 0.dp,
//        label = "Padding Animation",
//    )
//    val animatedCornerRadius by animateDpAsState(
//        targetValue = if (!isImmersiveMode || hasDisplayCutout) browserSettings.cornerRadiusDp.dp else 0.dp,
//        label = "Corner Radius Animation",
//    )
//    val isKeyboardVisibleForPadding =
//        WindowInsets.ime.asPaddingValues().calculateBottomPadding() > 0.dp
//    val cutoutPaddingValues = WindowInsets.displayCutout.asPaddingValues()
//    val cutoutTop = cutoutPaddingValues.calculateTopPadding()
//    val cutoutStart = cutoutPaddingValues.calculateLeftPadding(LayoutDirection.Ltr)
//    val cutoutEnd = cutoutPaddingValues.calculateRightPadding(LayoutDirection.Ltr)
//    val cutoutBottom = cutoutPaddingValues.calculateBottomPadding()
//    val animatedCutoutTop by animateDpAsState(
//        targetValue = if (!isUrlBarVisible) cutoutTop else 0.dp,
//        animationSpec = tween(browserSettings.animationSpeed),
//        label = "Cutout Top Animation"
//    )
//    val animatedCutoutStart by animateDpAsState(
//        targetValue = if (!isUrlBarVisible) cutoutStart else 0.dp,
//        animationSpec = tween(browserSettings.animationSpeed),
//        label = "Cutout Start Animation"
//    )
//    val animatedCutoutEnd by animateDpAsState(
//        targetValue = if (!isUrlBarVisible) cutoutEnd else 0.dp,
//        animationSpec = tween(browserSettings.animationSpeed),
//        label = "Cutout End Animation"
//    )
//    val animatedCutoutBottom by animateDpAsState(
//        targetValue = if (!isUrlBarVisible) cutoutBottom else 0.dp,
//        animationSpec = tween(browserSettings.animationSpeed),
//        label = "Cutout Bottom Animation"
//    )
//    var staticSystemBarBottom by remember { mutableStateOf(0.dp) }
//    var staticSystemBarTop by remember { mutableStateOf(0.dp) }
//    val currentSystemBarInsets = WindowInsets.systemBars.asPaddingValues()
//    val currentSystemBarTop = currentSystemBarInsets.calculateTopPadding()
//    val currentSystemBarBottom = currentSystemBarInsets.calculateBottomPadding()
//    if (staticSystemBarBottom == 0.dp && currentSystemBarBottom > 0.dp) {
//        staticSystemBarBottom = currentSystemBarBottom
//    }
//    if (staticSystemBarTop == 0.dp && currentSystemBarTop > 0.dp) {
//        staticSystemBarTop = currentSystemBarTop
//    }
//    val animatedSystemBarTop by animateDpAsState(
//        targetValue = if (isUrlBarVisible) staticSystemBarTop else 0.dp,
//        animationSpec = if (hasDisplayCutout) tween(browserSettings.animationSpeed) else snap(0),
//        label = "SystemBar Top Animation"
//    )
//    val animatedSystemBarBottom by animateDpAsState(
//        targetValue = if (!isImmersiveMode && !isKeyboardVisibleForPadding) staticSystemBarBottom else if (isKeyboardVisibleForPadding) browserSettings.paddingDp.dp else 0.dp,
//        animationSpec = if (isImmersiveMode || !isKeyboardVisibleForPadding) tween(browserSettings.animationSpeed) else snap(0),
//        label = "SystemBar Bottom Animation"
//    )
//
//    // Fullscreen video state management
//    var customView by remember { mutableStateOf<View?>(null) }
//    var customViewCallback by remember { mutableStateof<GeckoSession.CustomViewCallback?>(null) }
//    var originalOrientation by remember { mutableIntStateOf(ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED) }
//
//    var pendingPermissionRequest by remember { mutableStateOf<CustomPermissionRequest?>(null) }
//
//    var colorScheme = ColorScheme(
//        backgroundColor = if (isSystemInDarkTheme()) Color.Black else Color.White,
//        foregroundColor = if (isSystemInDarkTheme()) Color.White else Color.Black
//    )
//
//    // Derived state for navigation buttons, now using GeckoSession
//    val canGoBack by remember { derivedStateOf { session.canGoBack() } }
//    val canGoForward by remember { derivedStateOf { session.canGoForward() } }
//
//    // GeckoView History Synchronization Function
//    fun synchronizeState() {
//        val geckoHistory = session.getHistory()
//
//        currentTab?.let { tab ->
//            val serializableItems = geckoHistory.entries.map { entry ->
//                SerializableHistoryItem(url = entry.uri, title = entry.title ?: "")
//            }
//            val geckoSerializableList = SerializableBackForwardList(
//                items = serializableItems,
//                currentIndex = geckoHistory.currentIndex
//            )
//
//            if (tab.historyState != geckoSerializableList) {
//                tabs[activeTabIndex.value] = tab.copy(historyState = geckoSerializableList)
//                saveTrigger++
//                Log.d("GeckoSync", "History synchronized.")
//            }
//        }
//    }
//
//    val geckoStateListener = remember {
//        object : OnGeckoStateChangedListener {
//            override fun onUrlChanged(newUrl: String?) {
//                if (newUrl != null && !isFocusOnTextField) {
//                    textFieldValue = TextFieldValue(newUrl, TextRange(newUrl.length))
//                }
//            }
//
//            override fun onHistoryUpdated() {
//                synchronizeState()
//            }
//        }
//    }
//
//    SideEffect {
//        // Setup GeckoSession Delegates
//        session.setContentDelegate(object : GeckoSession.ContentDelegate {
//            override fun onPageStart(session: GeckoSession, url: String) {
//                isLoading = true
//            }
//
//            override fun onPageStop(session: GeckoSession, success: Boolean) {
//                isLoading = false
//            }
//
//            override fun onLocationChange(session: GeckoSession, url: String?, progress: Int) {
//                geckoStateListener.onUrlChanged(url)
//                geckoStateListener.onHistoryUpdated()
//            }
//        })
//
//        // Add other delegates (Chrome, Permission) here as needed, similar to the previous example.
//    }
//
//    val permissionLauncher = rememberLauncherForActivityResult(
//        contract = ActivityResultContracts.RequestMultiplePermissions(),
//        onResult = { permissions ->
//            pendingPermissionRequest?.onResult?.invoke(permissions)
//            pendingPermissionRequest = null
//        }
//    )
//
//    val updateBrowserSettings = { newSettings: BrowserSettings ->
//        browserSettings = newSettings
//    }
//
//    // LAUNCH EFFECTS
//    LaunchedEffect(browserSettings.isDesktopMode) {
//        session.settings.userAgentMode = if (browserSettings.isDesktopMode) {
//            GeckoSessionSettings.UserAgentMode.DESKTOP
//        } else {
//            GeckoSessionSettings.UserAgentMode.MOBILE
//        }
//        session.reload()
//    }
//
//    LaunchedEffect(isUrlBarVisible, pendingPermissionRequest) {
//        isImmersiveMode = if (pendingPermissionRequest != null) false else !isUrlBarVisible
//    }
//
//    LaunchedEffect(isUrlBarVisible) {
//        val window = (context as? Activity)?.window ?: return@LaunchedEffect
//        val insetsController = WindowCompat.getInsetsController(window, window.decorView)
//        if (isUrlBarVisible) {
//            insetsController.show(WindowInsetsCompat.Type.systemBars())
//        } else {
//            insetsController.hide(WindowInsetsCompat.Type.systemBars())
//            insetsController.systemBarsBehavior =
//                WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
//        }
//    }
//
//    LaunchedEffect(saveTrigger) {
//        if (saveTrigger > 0) {
//            tabManager.saveTabs(tabs)
//        }
//    }
//
//    // Initial Load Effect for GeckoView
//    LaunchedEffect(Unit) {
//        val urlToLoad = currentTab?.currentUrl ?: browserSettings.defaultUrl
//        if (!initialLoadDone) {
//            session.loadUri(urlToLoad)
//            initialLoadDone = true
//        }
//    }
//
//    BackHandler(enabled = !isUrlBarVisible || canGoBack) {
//        when {
//            customView != null -> {
//                customViewCallback?.onCustomViewHidden()
//            }
//            !isUrlBarVisible -> {
//                isUrlBarVisible = true
//                updateBrowserSettings(browserSettings.copy(isInteractable = false))
//            }
//            canGoBack -> {
//                session.goBack() // Use GeckoSession to go back
//            }
//        }
//    }
//
//    // --- LAYOUT ---
//    Box(modifier = Modifier.fillMaxSize()) {
//        CompositionLocalProvider(LocalBrowserSettings provides browserSettings) {
//            Box(
//                modifier = modifier
//                    .fillMaxSize()
//                    .padding(top = animatedSystemBarTop, bottom = animatedSystemBarBottom)
//            ) {
//                Column(
//                    modifier = modifier
//                        .fillMaxSize()
//                        .windowInsetsPadding(WindowInsets.ime)
//                ) {
//                    Box(
//                        modifier = Modifier
//                            .fillMaxWidth()
//                            .weight(1f)
//                            .padding(animatedPadding)
//                            .padding(
//                                top = animatedCutoutTop,
//                                start = animatedCutoutStart,
//                                end = animatedCutoutEnd,
//                                bottom = animatedCutoutBottom
//                            )
//                            .clip(RoundedCornerShape(animatedCornerRadius))
//                            .testTag("WebViewContainer")
//                    ) {
//                        Box(modifier = Modifier.fillMaxSize()) {
//                            // AndroidView now hosts the GeckoView
//                            AndroidView(
//                                factory = {
//                                    (geckoView.parent as? ViewGroup)?.removeView(geckoView)
//                                    geckoView
//                                },
//                                modifier = Modifier.fillMaxSize()
//                            )
//                        }
//
//                        if (!browserSettings.isInteractable) {
//                            Box(
//                                modifier = Modifier
//                                    .fillMaxSize()
//                                    .pointerInput(Unit) {
//                                        val coroutineScope = CoroutineScope(coroutineContext)
//                                        val verticalDragThreshold = with(density) { overlayHeightPx * 2 }
//                                        val horizontalDragThreshold = with(density) { 40.dp.toPx() }
//
//                                        awaitEachGesture {
//                                            val down = awaitFirstDown(requireUnconsumed = false)
//                                            var isTap = true
//                                            val drag = awaitTouchSlopOrCancellation(down.id) { change, _ ->
//                                                isTap = false
//                                                change.consume()
//                                            }
//
//                                            if (isTap) {
//                                                isUrlBarVisible = false
//                                                updateBrowserSettings(browserSettings.copy(isInteractable = true))
//                                            } else if (drag != null) {
//                                                // ... gesture logic ...
//                                                when (activeGestureAction) {
//                                                    GestureNavAction.BACK -> if (canGoBack) {
//                                                        session.goBack()
//                                                    }
//                                                    GestureNavAction.REFRESH -> {
//                                                        session.reload()
//                                                    }
//                                                    GestureNavAction.FORWARD -> if (canGoForward) {
//                                                        session.goForward()
//                                                    }
//                                                    GestureNavAction.NONE -> {}
//                                                }
//                                                // ... animation logic ...
//                                            }
//                                        }
//                                    }
//                            )
//                        }
//                        LoadingOverlay(isLoading = isLoading, colorScheme = colorScheme)
//                    }
//
//                    PermissionPanel(
//                        colorScheme = colorScheme,
//                        browserSettings = browserSettings,
//                        request = pendingPermissionRequest,
//                        onAllow = {
//                            pendingPermissionRequest?.let {
//                                permissionLauncher.launch(it.permissionsToRequest.toTypedArray())
//                            }
//                        },
//                        onDeny = {
//                            pendingPermissionRequest?.onResult?.invoke(emptyMap())
//                            pendingPermissionRequest = null
//                        }
//                    )
//                    BottomPanel(
//                        currentTab = currentTab,
//                        colorScheme = colorScheme,
//                        isImmersiveMode = isImmersiveMode,
//                        isUrlBarVisible = isUrlBarVisible,
//                        isOptionsPanelVisible = isOptionsPanelVisible,
//                        browserSettings = browserSettings,
//                        updateBrowserSettings = { updateBrowserSettings(it) },
//                        textFieldValue = textFieldValue,
//                        focusManager = focusManager,
//                        keyboardController = keyboardController,
//                        textFieldHeightDp = textFieldHeightDp,
//                        toggleOptionsPanel = { isOptionsPanelVisible = it },
//                        changeTextFieldValue = { textFieldValue = it },
//                        onNewUrl = { newUrl ->
//                            session.loadUri(newUrl) // Use GeckoSession to load URL
//                        },
//                        toggleUrlBar = { isUrlBarVisible = it },
//                        setTextFieldHeightPx = { textFieldHeightPx = it },
//                        setIsFocusOnTextField = { isFocusOnTextField = it },
//                    )
//                }
//            }
//        }
//        if (customView != null) {
//            AndroidView(
//                factory = { customView!! as ViewGroup },
//                onRelease = { view ->
//                    (view.parent as? ViewGroup)?.removeView(view)
//                },
//                modifier = Modifier.fillMaxSize()
//            )
//        }
//
//        GestureNavigationOverlay(
//            colorScheme = colorScheme,
//            staticSystemBarTop = staticSystemBarTop,
//            offsetY = offsetY,
//            activeAction = activeGestureAction,
//            canGoBack = canGoBack,
//            canGoForward = canGoForward,
//            onHeightMeasured = { measuredHeight ->
//                if (overlayHeightPx == 0f && measuredHeight > 0) {
//                    overlayHeightPx = measuredHeight
//                }
//            },
//            onBackButtonBoundsChanged = { backButtonRect = it },
//            onRefreshButtonBoundsChanged = { refreshButtonRect = it },
//            onForwardButtonBoundsChanged = { forwardButtonRect = it }
//        )
//    }
//}
//
//
//// --- All other Composable functions (BottomPanel, PermissionPanel, OptionsPanel, etc.) remain unchanged ---
//// They do not need to be aware of the underlying browser engine.
//
//@Composable
//fun BottomPanel(
//    currentTab: Tab?,
//    colorScheme: ColorScheme,
//    isImmersiveMode: Boolean,
//    isUrlBarVisible: Boolean,
//    isOptionsPanelVisible: Boolean,
//    browserSettings: BrowserSettings,
//    updateBrowserSettings: (BrowserSettings) -> Unit,
//    textFieldValue: TextFieldValue,
//    focusManager: FocusManager,
//    keyboardController: SoftwareKeyboardController?,
//    textFieldHeightDp: Dp,
//    toggleOptionsPanel: (Boolean) -> Unit = {},
//    changeTextFieldValue: (TextFieldValue) -> Unit = {},
//    onNewUrl: (String) -> Unit = {},
//    toggleUrlBar: (Boolean) -> Unit = {},
//    setTextFieldHeightPx: (Int) -> Unit = {},
//    setIsFocusOnTextField: (Boolean) -> Unit = {},
//
//    ) {
//    AnimatedVisibility(
//        visible = isUrlBarVisible,
//        enter = expandVertically(tween(browserSettings.animationSpeed)),
//        exit = shrinkVertically(tween(browserSettings.animationSpeed))
//    ) {
//        Column {
//
//
//            // URL BAR
//            Row(
//                modifier = Modifier
//                    .pointerInput(Unit) {
//                        detectVerticalDragGestures(
//                            onVerticalDrag = { change, dragAmount ->
//                                if (dragAmount < 0) {
//                                    toggleOptionsPanel(true)
//                                } else if (dragAmount > 0) {
//                                    toggleOptionsPanel(false)
//                                }
//                            })
//                    }
//                    .padding(
//                        horizontal = browserSettings.paddingDp.dp,
//                        vertical = browserSettings.paddingDp.dp / 2
//                    ),
//                verticalAlignment = Alignment.CenterVertically
//            ) {
//                OutlinedTextField(
//                    value = textFieldValue.text,
//                    onValueChange = { newValue ->
//                        changeTextFieldValue(
//                            TextFieldValue(
//                                newValue,
//                                selection = TextRange(newValue.length)
//                            )
//                        )
//                    },
//                    singleLine = true,
//                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Go),
//                    keyboardActions = KeyboardActions(
//                        onGo = {
//                            val input = textFieldValue.text.trim()
//                            val resetUrl = currentTab?.currentUrl ?: ""
//
//                            if (input.isBlank()) {
//                                changeTextFieldValue(
//                                    TextFieldValue(
//                                        resetUrl,
//                                        TextRange(resetUrl.length)
//                                    )
//                                )
//                                focusManager.clearFocus()
//                                keyboardController?.hide()
//                                return@KeyboardActions
//                            }
//                            val isUrl = try {
//                                Patterns.WEB_URL.matcher(input).matches() ||
//                                        (input.contains(".") && !input.contains(" "))
//                            } catch (_: Exception) {
//                                false
//                            }
//
//                            val finalUrl = if (isUrl) {
//                                if (input.startsWith("http://") || input.startsWith("https://")) {
//                                    input
//                                } else {
//                                    "https://$input"
//                                }
//                            } else {
//                                val encodedQuery =
//                                    URLEncoder.encode(input, StandardCharsets.UTF_8.toString())
//                                "https://www.google.com/search?q=$encodedQuery"
//                            }
//
//                            onNewUrl(finalUrl)
//
//                            focusManager.clearFocus()
//                            keyboardController?.hide()
//                            if (!browserSettings.isInteractable) {
//                                toggleUrlBar(false)
//                                updateBrowserSettings(browserSettings.copy(isInteractable = true))
//                            }
//                        }
//                    ),
//                    modifier = Modifier
//                        .weight(1f)
//                        .height(browserSettings.singleLineHeight.dp)
//                        .onSizeChanged { size ->
//                            setTextFieldHeightPx(size.height)
//                        }
//                        .fillMaxWidth()
//                        .onFocusChanged {
//                            val resetUrl = currentTab?.currentUrl ?: ""
//                            setIsFocusOnTextField(it.isFocused)
//                            if (it.isFocused) {
//
//                                if (textFieldValue.text == resetUrl) {
//
//                                    changeTextFieldValue(TextFieldValue("", TextRange(0)))
//                                }
//                            } else {
//
//                                if (textFieldValue.text.isBlank()) {
//                                    changeTextFieldValue(
//                                        TextFieldValue(
//                                            resetUrl,
//                                            TextRange(resetUrl.length)
//                                        )
//                                    )
//                                }
//                            }
//                        }
//                        .pointerInput(Unit) {
//                            detectHorizontalDragGestures { _, dragAmount ->
//                                if (dragAmount > 0) {
//                                    val resetUrl = currentTab?.currentUrl ?: ""
//                                    changeTextFieldValue(
//                                        TextFieldValue(
//                                            resetUrl,
//                                            selection = TextRange(resetUrl.length)
//                                        )
//                                    )
//                                }
//                            }
//                        },
//                    shape = RoundedCornerShape(browserSettings.cornerRadiusDp.dp),
//                    colors = TextFieldDefaults.colors(
//                        focusedContainerColor = colorScheme.backgroundColor,
//                        unfocusedContainerColor = colorScheme.backgroundColor,
//                        disabledContainerColor = colorScheme.foregroundColor,
//                        errorContainerColor = Color.Red
//                    )
//                )
//                IconButton(
//                    onClick = { updateBrowserSettings(browserSettings.copy(isInteractable = !browserSettings.isInteractable)) },
//                    modifier = Modifier
//                        .padding(start = browserSettings.paddingDp.dp)
//                        .then(if (textFieldHeightDp > 0.dp) Modifier.size(textFieldHeightDp) else Modifier),
//                    colors = IconButtonDefaults.iconButtonColors(
//                        containerColor = colorScheme.foregroundColor
//                    )
//
//                ) {
//                    Icon(
//                        painter = if (browserSettings.isInteractable) painterResource(id = R.drawable.ic_transparent) else painterResource(
//                            id = R.drawable.ic_immersive
//                        ),
//                        contentDescription = "Toggle Interactable",
//                        tint = colorScheme.backgroundColor
//                    )
//                }
//            }
//
//
//            OptionsPanel(
//                colorScheme = colorScheme,
//                isImmersiveMode = isImmersiveMode,
//                isOptionsPanelVisible = isOptionsPanelVisible,
//                toggleOptionsPanel = toggleOptionsPanel,
//                updateBrowserSettings = updateBrowserSettings,
//                browserSettings = browserSettings,
//            )
//        }
//    }
//}
//
//@Composable
//fun PermissionPanel(
//    colorScheme: ColorScheme,
//    browserSettings: BrowserSettings,
//    request: CustomPermissionRequest?,
//    onAllow: () -> Unit,
//    onDeny: () -> Unit
//) {
//    val isVisible = request != null
//
//    AnimatedVisibility(
//        visible = isVisible,
//        enter = expandVertically(animationSpec = tween(browserSettings.animationSpeed)),
//        exit = shrinkVertically(animationSpec = tween(browserSettings.animationSpeed))
//    ) {
//        if (request == null) return@AnimatedVisibility
//
//        Card(
//            modifier = Modifier.fillMaxWidth(),
//            colors = CardDefaults.cardColors(
//                containerColor = Color.Transparent
//            ),
//        ) {
//            Column(
//                modifier = Modifier
//                    .fillMaxWidth()
//                    .padding(horizontal = browserSettings.paddingDp.dp)
//                    .padding(bottom = browserSettings.paddingDp.dp / 2),
//                horizontalAlignment = Alignment.CenterHorizontally
//            ) {
//
//
//                Row(
//                    modifier = Modifier.fillMaxWidth(),
//                    horizontalArrangement = Arrangement.spacedBy(browserSettings.paddingDp.dp)
//                ) {
//                    IconButton(
//                        onClick = onDeny,
//                        modifier = Modifier
//                            .weight(1f)
//                            .height(browserSettings.singleLineHeight.dp),
//                        colors = IconButtonDefaults.iconButtonColors(
//                            containerColor = colorScheme.foregroundColor.copy(alpha = 0.1f)
//                        )
//                    ) {
//                        Icon(
//                            painter = painterResource(id = request.iconResDeny),
//                            contentDescription = "Deny Permission",
//                            tint = colorScheme.foregroundColor
//                        )
//                    }
//
//                    IconButton(
//                        onClick = onAllow,
//                        modifier = Modifier
//                            .weight(1f)
//                            .height(browserSettings.singleLineHeight.dp),
//                        colors = IconButtonDefaults.iconButtonColors(
//                            containerColor = colorScheme.foregroundColor
//                        )
//                    ) {
//                        Icon(
//                            painter = painterResource(id = request.iconResAllow),
//                            contentDescription = "Allow Permission",
//                            tint = colorScheme.backgroundColor
//                        )
//                    }
//                }
//            }
//        }
//    }
//}
//
//
//data class OptionItem(
//    val iconRes: Int,
//    val contentDescription: String,
//    val onClick: () -> Unit,
//)
//
//data class ColorScheme(
//    val backgroundColor: Color,
//    val foregroundColor: Color
//)
//
//@Composable
//fun OptionsPanel(
//    colorScheme: ColorScheme,
//    isImmersiveMode: Boolean,
//    isOptionsPanelVisible: Boolean = false,
//    toggleOptionsPanel: (Boolean) -> Unit = {},
//    updateBrowserSettings: (BrowserSettings) -> Unit,
//    browserSettings: BrowserSettings = LocalBrowserSettings.current,
//) {
//
//
//    val allOptions = remember(browserSettings) {
//        listOf(
//            OptionItem(
//                if (browserSettings.isDesktopMode) R.drawable.ic_mobile else R.drawable.ic_desktop,
//                "Desktop layout"
//            ) {
//                updateBrowserSettings(browserSettings.copy(isDesktopMode = !browserSettings.isDesktopMode))
//            },
//
//            OptionItem(R.drawable.ic_bug, "logBrowserSettings") {
//                Log.e("BROWSER SETTINGS", browserSettings.toString())
//                Log.e("isImmersiveMode", isImmersiveMode.toString())
//            },
//            OptionItem(R.drawable.ic_fullscreen, "Button 4") { /* ... */ },
//            OptionItem(R.drawable.ic_fullscreen, "Button 5") { /* ... */ },
//            OptionItem(R.drawable.ic_fullscreen, "Button 6") { /* ... */ },
//            OptionItem(R.drawable.ic_fullscreen, "Button 7") { /* ... */ },
//            OptionItem(R.drawable.ic_fullscreen, "Button 8") { /* ... */ }
//        )
//    }
//
//    val optionPages = remember(allOptions) {
//        allOptions.chunked(4)
//    }
//
//    val pagerState = rememberPagerState(pageCount = { optionPages.size })
//
//    AnimatedVisibility(
//        visible = isOptionsPanelVisible,
//        enter = expandVertically(tween(browserSettings.animationSpeed)),
//        exit = shrinkVertically(tween(browserSettings.animationSpeed)),
//    ) {
//        Box(
//            modifier = Modifier
//                .fillMaxWidth()
//                .padding(
//                    horizontal = browserSettings.paddingDp.dp,
//                    vertical = browserSettings.paddingDp.dp / 2
//                )
//                .pointerInput(Unit) {
//                    detectVerticalDragGestures(
//                        onVerticalDrag = { change, dragAmount ->
//                            if (dragAmount < 0) {
//                                toggleOptionsPanel(true)
//                            } else if (dragAmount > 0) {
//                                toggleOptionsPanel(false)
//                            }
//                        })
//                }
//
//        ) {
//
//            HorizontalPager(
//                state = pagerState,
//                modifier = Modifier.fillMaxWidth()
//            ) { pageIndex ->
//                Row(
//                    modifier = Modifier
//                        .fillMaxWidth()
//                        .padding(horizontal = browserSettings.paddingDp.dp),
//                    horizontalArrangement = Arrangement.spacedBy(browserSettings.paddingDp.dp)
//                ) {
//                    val pageOptions = optionPages[pageIndex]
//
//                    pageOptions.forEach { option ->
//                        IconButton(
//                            onClick = option.onClick,
//                            modifier = Modifier
//                                .weight(1f)
//                                .height(browserSettings.singleLineHeight.dp),
//                            colors = IconButtonDefaults.iconButtonColors(
//                                containerColor = colorScheme.foregroundColor.copy(alpha = 0.05f)
//                            ),
//
//                            ) {
//                            Icon(
//                                painter = painterResource(id = option.iconRes),
//                                contentDescription = option.contentDescription,
//                                tint = colorScheme.foregroundColor
//                            )
//                        }
//                    }
//
//                    repeat(4 - pageOptions.size) {
//                        Spacer(modifier = Modifier.weight(1f))
//                    }
//                }
//            }
//
//        }
//    }
//}
//
//@Composable
//fun LoadingOverlay(isLoading: Boolean, modifier: Modifier = Modifier, colorScheme: ColorScheme) {
//    AnimatedVisibility(
//        visible = isLoading,
//        modifier = modifier,
//        enter = fadeIn(animationSpec = tween(300)),
//        exit = fadeOut(animationSpec = tween(300))
//    ) {
//        Box(
//            modifier = Modifier
//                .fillMaxSize()
//                .background(colorScheme.backgroundColor.copy(alpha = 0.7f))
//                .pointerInput(Unit) {},
//            contentAlignment = Alignment.Center
//        ) {
//            CircularProgressIndicator(
//                modifier = Modifier.size(64.dp),
//                color = colorScheme.foregroundColor,
//                strokeWidth = 6.dp
//            )
//        }
//    }
//}
//
//
//@Composable
//fun GestureNavigationOverlay(
//    colorScheme: ColorScheme,
//    staticSystemBarTop: Dp,
//    offsetY: Animatable<Float, *>,
//    activeAction: GestureNavAction,
//    canGoBack: Boolean,
//    canGoForward: Boolean,
//    onHeightMeasured: (Float) -> Unit,
//    onBackButtonBoundsChanged: (Rect) -> Unit,
//    onRefreshButtonBoundsChanged: (Rect) -> Unit,
//    onForwardButtonBoundsChanged: (Rect) -> Unit
//) {
//    val browserSettings = LocalBrowserSettings.current
//
//    Box(
//        modifier = Modifier
//            .fillMaxWidth()
//            .onGloballyPositioned {
//                onHeightMeasured(it.size.height.toFloat())
//            }
//            .offset { IntOffset(0, offsetY.value.roundToInt()) }
//    ) {
//        Box(
//            modifier = Modifier
//                .fillMaxWidth()
//                .padding(top = staticSystemBarTop + browserSettings.paddingDp.dp)
//                .padding(horizontal = browserSettings.paddingDp.dp)
//                .height(browserSettings.singleLineHeight.dp * 1.5f)
//        ) {
//            val backgroundColor by animateColorAsState(
//                targetValue = if (activeAction != GestureNavAction.NONE) colorScheme.foregroundColor.copy(
//                    alpha = 0.9f
//                ) else colorScheme.foregroundColor.copy(alpha = 0.5f),
//                label = "BackgroundColor"
//            )
//            val containerBorderRadius by animateDpAsState(
//                targetValue = if (activeAction != GestureNavAction.NONE) browserSettings.singleLineHeight.dp * 1.5f else browserSettings.cornerRadiusDp.dp,
//                animationSpec = tween(
//                    durationMillis = 100,
//                    easing = EaseIn
//                ),
//                label = "ContainerBorderRadius"
//            )
//
//            Box(
//                modifier = Modifier
//                    .fillMaxSize()
//                    .clip(RoundedCornerShape(containerBorderRadius))
//                    .background(backgroundColor)
//                    .blur(10.dp)
//            )
//
//            Row(
//                modifier = Modifier
//                    .fillMaxSize()
//                    .padding(
//                        vertical = browserSettings.paddingDp.dp,
//                        horizontal = browserSettings.singleLineHeight.dp * 1f
//                    ),
//                verticalAlignment = Alignment.CenterVertically
//            ) {
//                val backWeight by animateFloatAsState(
//                    targetValue = if (activeAction == GestureNavAction.BACK) 2f else 1f,
//                    label = "BackWeight"
//                )
//                val refreshWeight by animateFloatAsState(
//                    targetValue = if (activeAction == GestureNavAction.REFRESH) 2f else 1f,
//                    label = "RefreshWeight"
//                )
//                val forwardWeight by animateFloatAsState(
//                    targetValue = if (activeAction == GestureNavAction.FORWARD) 2f else 1f,
//                    label = "ForwardWeight"
//                )
//
//                val backColor by animateColorAsState(
//                    targetValue = if (activeAction == GestureNavAction.BACK && canGoBack) colorScheme.backgroundColor else Color.Transparent,
//                    label = "BackColor"
//                )
//                Box(
//                    modifier = Modifier
//                        .weight(backWeight)
//                        .fillMaxHeight()
//                        .onGloballyPositioned { onBackButtonBoundsChanged(it.boundsInRoot()) }
//                        .clip(RoundedCornerShape(browserSettings.cornerRadiusDp.dp))
//                        .background(backColor)
//                ) {
//                    if (canGoBack) {
//                        Icon(
//                            painter = painterResource(id = R.drawable.ic_arrow_back),
//                            contentDescription = "Go Back",
//                            tint = if (activeAction == GestureNavAction.BACK) colorScheme.foregroundColor else colorScheme.backgroundColor,
//                            modifier = Modifier.align(Alignment.Center)
//                        )
//                    }
//                }
//
//                Spacer(modifier = Modifier.width(browserSettings.paddingDp.dp))
//
//                val refreshColor by animateColorAsState(
//                    targetValue = if (activeAction == GestureNavAction.REFRESH) colorScheme.backgroundColor else Color.Transparent,
//                    label = "RefreshColor"
//                )
//                Box(
//                    modifier = Modifier
//                        .weight(refreshWeight)
//                        .fillMaxHeight()
//                        .onGloballyPositioned { onRefreshButtonBoundsChanged(it.boundsInRoot()) }
//                        .clip(RoundedCornerShape(browserSettings.cornerRadiusDp.dp))
//                        .background(refreshColor)
//                ) {
//                    Icon(
//                        painter = painterResource(id = R.drawable.ic_refresh),
//                        contentDescription = "Refresh",
//                        tint = if (activeAction == GestureNavAction.REFRESH) colorScheme.foregroundColor else colorScheme.backgroundColor,
//                        modifier = Modifier.align(Alignment.Center)
//                    )
//                }
//
//                Spacer(modifier = Modifier.width(browserSettings.paddingDp.dp))
//
//                val forwardColor by animateColorAsState(
//                    targetValue = if (activeAction == GestureNavAction.FORWARD && canGoForward) colorScheme.backgroundColor else Color.Transparent,
//                    label = "ForwardColor"
//                )
//                Box(
//                    modifier = Modifier
//                        .weight(forwardWeight)
//                        .fillMaxHeight()
//                        .onGloballyPositioned { onForwardButtonBoundsChanged(it.boundsInRoot()) }
//                        .clip(RoundedCornerShape(browserSettings.cornerRadiusDp.dp))
//                        .background(forwardColor)
//                ) {
//                    if (canGoForward) {
//                        Icon(
//                            painter = painterResource(id = R.drawable.ic_arrow_forward),
//                            contentDescription = "Go Forward",
//                            tint = if (activeAction == GestureNavAction.FORWARD) colorScheme.foregroundColor else colorScheme.backgroundColor,
//                            modifier = Modifier.align(Alignment.Center)
//                        )
//                    }
//                }
//            }
//        }
//    }
//}
//
//@Composable
//@Preview(showBackground = true)
//fun BrowserScreenPreview() {
//    BrowserTheme {
//        BrowserScreen()
//    }
//}
//
//
//class WebAppInterface() {
//    @JavascriptInterface
//    fun logBackgroundColor(colorString: String) {
//        try {
//            Log.e("WebViewBackground", "Detected web page background color: $colorString")
//        } catch (e: Exception) {
//            Log.e("WebAppInterface", "Failed to parse color string: $colorString", e)
//        }
//    }
//}