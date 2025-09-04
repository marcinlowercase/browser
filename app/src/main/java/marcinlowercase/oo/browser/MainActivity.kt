package marcinlowercase.oo.browser

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
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.awaitTouchSlopOrCancellation
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusManager
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.edit
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import marcinlowercase.oo.browser.ui.theme.BrowserTheme
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
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

data class PermissionRequest(
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
        defaultUrl = "https://www.google.com",
        animationSpeed = 300,
        singleLineHeight = 64,
        isDesktopMode = false,
        desktopModeWidth = 820,
    )
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
                defaultUrl = sharedPrefs.getString("default_url", "https://www.google.com")
                    ?: "https://www.google.com",
                animationSpeed = sharedPrefs.getInt("animation_speed", 300),
                singleLineHeight = sharedPrefs.getInt("single_line_height", 64),
                isDesktopMode = sharedPrefs.getBoolean("is_desktop_mode", false),
                desktopModeWidth = sharedPrefs.getInt("desktop_mode_width", 820),

            )
        )
    }

    var url by rememberSaveable {
        mutableStateOf(
            sharedPrefs.getString("last_url", browserSettings.defaultUrl)
                ?: browserSettings.defaultUrl
        )
    }

    var textFieldValue by rememberSaveable(stateSaver = TextFieldValue.Saver) {
        mutableStateOf(TextFieldValue(url, TextRange(url.length)))
    }

    var isImmersiveMode by remember { mutableStateOf(false) }




//    var webView by remember { mutableStateOf<WebView?>(null) }
    var canGoBack by remember { mutableStateOf(false) }
    var isLoading by remember { mutableStateOf(false) }
    var isFocusOnTextField by remember { mutableStateOf(false) }

    val keyboardController = LocalSoftwareKeyboardController.current
    val focusManager = LocalFocusManager.current

    var textFieldHeightPx by remember { mutableIntStateOf(0) }
    // Density is needed to convert Px to Dp
    val density = LocalDensity.current

    // Convert the pixel height to Dp
    val textFieldHeightDp = with(density) { textFieldHeightPx.toDp() }


    var isUrlBarVisible by rememberSaveable { mutableStateOf(true) }


    var isOptionsPanelVisible by rememberSaveable { mutableStateOf(false) }


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
        mutableStateOf<PermissionRequest?>(null)
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



    val webView = remember {
        WebView(context).apply {
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
                    pendingPermissionRequest = PermissionRequest(
                        title = "Location Access Required",
                        rationale = "This website wants to use your device's location.",
                        iconResAllow = R.drawable.ic_location_on,
                        iconResDeny = R.drawable.ic_location_off,
                        permissionsToRequest = listOf(
                            android.Manifest.permission.ACCESS_FINE_LOCATION,
                            android.Manifest.permission.ACCESS_COARSE_LOCATION
                        ),
                        // This is the key: the onResult callback for this specific request
                        // knows how to talk back to the WebView's Geolocation callback.
                        onResult = { permissions ->
                            val isGranted = permissions[android.Manifest.permission.ACCESS_FINE_LOCATION] == true ||
                                    permissions[android.Manifest.permission.ACCESS_COARSE_LOCATION] == true
                            callback.invoke(origin, isGranted, false)
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
            }

            // The WebViewClient handles content loading events.
            webViewClient = object : WebViewClient() {
                override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                    super.onPageStarted(view, url, favicon)
                    isLoading = true
                    if (!isFocusOnTextField) url?.let {
                        textFieldValue = TextFieldValue(it, TextRange(it.length))
                    }
                }

                override fun onPageFinished(view: WebView?, currentUrl: String?) {
                    super.onPageFinished(view, currentUrl)
                    isLoading = false
                    canGoBack = view?.canGoBack() ?: false
                    currentUrl?.let {
                        url = it
                        if (!isFocusOnTextField) textFieldValue =
                            TextFieldValue(it, TextRange(it.length))
                    }
                    // Force a scroll to the top to fix coordinate system bugs
                    view?.scrollTo(0, 0)

                    // Your JS script for getting the background color
                    val jsScript =
                        """(function() { ... })();""".trimIndent() // Keep your full script here
                    view?.evaluateJavascript(jsScript, null)

                    if (browserSettings.isDesktopMode) {
                        // --- THIS IS THE FINAL, AGGRESSIVE SCRIPT ---
                        view?.evaluateJavascript(
                            """
            (function() {
                // The function we want to run to enforce our viewport.
                function enforceDesktopViewport() {
                    console.log('Enforcing desktop viewport...');
                    var meta = document.querySelector('meta[name=viewport]');
                    if (!meta) {
                        meta = document.createElement('meta');
                        meta.setAttribute('name', 'viewport');
                        document.getElementsByTagName('head')[0].appendChild(meta);
                    }
                    // Crucially, check if the content is already correct.
                    // This prevents an infinite loop of observer callbacks.
                    if (meta.getAttribute('content') !== 'width=${browserSettings.desktopModeWidth}') {
                        console.log('Viewport was wrong, correcting to width=${browserSettings.desktopModeWidth}.');
                        meta.setAttribute('content', 'width=${browserSettings.desktopModeWidth}');
                    }
                }

                // 1. Enforce it immediately.
                enforceDesktopViewport();

                // 2. Create an observer to watch for any changes to the <head> element.
                //    This will detect if the site's own JS tries to change the viewport.
                var observer = new MutationObserver(function(mutations) {
                    // When a change is detected, run our enforcement function again.
                    enforceDesktopViewport();
                });

                // 3. Start observing. We watch for changes to child elements in the head.
                var head = document.getElementsByTagName('head')[0];
                if (head) {
                    observer.observe(head, {
                        childList: true, 
                        subtree: true 
                    });
                }
            })();
            """.trimIndent(), null
                        )
                    }



                }

                override fun shouldInterceptRequest(
                    view: WebView?,
                    request: WebResourceRequest?
                ): WebResourceResponse? {
                    request?.requestHeaders?.put("Origin", url)
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


    // FUNCTIONS


    // This function will be our single, safe way to update settings.
    val updateBrowserSettings = { newSettings: BrowserSettings ->
        browserSettings = newSettings
        Log.e("updateBrowserSettings", browserSettings.toString())
    }

    // LAUNCH EFFECTS
    //


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


    // The LaunchedEffect now saves the entire settings object (or individual fields)
    LaunchedEffect(url, browserSettings) {
        sharedPrefs.edit {
            putString("last_url", url)
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

    LaunchedEffect(url) {
        if (webView.url != url) {
            webView.loadUrl(url)
        }
    }

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
            else -> {
                webView.goBack()
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
                    if (isLoading) {
                        LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                    }
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

                        if (!browserSettings.isInteractable) {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .pointerInput(Unit) {
                                        awaitEachGesture {
                                            // 1. At the start of each new gesture, reset our flag.
                                            var isDrag = false

                                            // 2. Wait for the initial press.
                                            val down = awaitFirstDown(requireUnconsumed = false)

                                            // 3. Use awaitTouchSlopOrCancellation. We are most interested
                                            //    in its onSlopCrossed lambda.
                                            val dragOrTap =
                                                awaitTouchSlopOrCancellation(down.id) { _, _ ->
                                                    // THIS IS THE KEY: This lambda is called the *moment* a
                                                    // drag is detected. We set our flag here. This happens
                                                    // before the WebView can fully "steal" the gesture,
                                                    // making our flag a reliable source of truth.
                                                    isDrag = true
                                                    // We don't need to do anything with the change object itself.
                                                }

                                            // 4. AFTER the gesture is over, we check OUR flag, not the
                                            //    unreliable return value of dragOrTap.
                                            if (!isDrag) {
                                                // If our flag is still false, it means onSlopCrossed was
                                                // never called. Therefore, it must be a tap.
                                                isUrlBarVisible = false
                                                Log.e("isImmersiveMode",  isImmersiveMode.toString())
                                                Log.e("HHH", animatedSystemBarBottom.toString())
                                                updateBrowserSettings(
                                                    browserSettings.copy(
                                                        isInteractable = true
                                                    )
                                                )
                                            }
                                        }
                                    }
                            )

                        }
                    }

//                    PermissionPanel(
//                        setIsImmersiveMode= setIsImmersiveMode,
//                        textFieldHeightDp = textFieldHeightDp,
//                        browserSettings = browserSettings,
//                        request = pendingPermissionRequest,
//                        onPermissionResult = { allow ->
//                            if (allow) {
//                                // User clicked "Allow" on our panel. Now trigger the system dialog.
//                                permissionLauncher.launch(
//                                    arrayOf(
//                                        android.Manifest.permission.ACCESS_FINE_LOCATION,
//                                        android.Manifest.permission.ACCESS_COARSE_LOCATION
//                                    )
//                                )
//                            } else {
//                                // User clicked "Deny" on our panel. Tell the WebView and hide.
//                                pendingPermissionRequest?.second?.invoke(
//                                    pendingPermissionRequest!!.first,
//                                    false,
//                                    false
//                                )
//                                pendingPermissionRequest = null
//                            }
//                        }
//                    )

                    PermissionPanel(
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
                        isImmersiveMode = isImmersiveMode,
                        isUrlBarVisible = isUrlBarVisible,
                        isOptionsPanelVisible = isOptionsPanelVisible,
                        browserSettings = browserSettings,
                        updateBrowserSettings = updateBrowserSettings,
                        textFieldValue = textFieldValue,
                        url = url,
                        focusManager = focusManager,
                        keyboardController = keyboardController,
                        textFieldHeightDp = textFieldHeightDp,
                        toggleOptionsPanel = { isOptionsPanelVisible = it },
                        changeTextFieldValue = { textFieldValue = it },
                        changeUrl = { url = it },
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
    }


}

@Composable
fun BottomPanel(
    isImmersiveMode: Boolean,
    isUrlBarVisible: Boolean,
    isOptionsPanelVisible: Boolean,
    browserSettings: BrowserSettings,
    updateBrowserSettings: (BrowserSettings) -> Int,
    textFieldValue: TextFieldValue,
    url: String,
    focusManager: FocusManager,
    keyboardController: SoftwareKeyboardController?,
    textFieldHeightDp: Dp,
    toggleOptionsPanel: (Boolean) -> Unit = {},
    changeTextFieldValue: (TextFieldValue) -> Unit = {},
    changeUrl: (String) -> Unit = {},
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
                            if (input.isBlank()) {
                                changeTextFieldValue(TextFieldValue(url, TextRange(url.length)))
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

                            if (isUrl) {
                                changeUrl(
                                    if (input.startsWith("http://") || input.startsWith("https://")) {
                                        input
                                    } else {
                                        "https://$input"
                                    }
                                )

                            } else {
                                val encodedQuery =
                                    URLEncoder.encode(
                                        input,
                                        StandardCharsets.UTF_8.toString()
                                    )
                                changeUrl("https://www.google.com/search?q=$encodedQuery")
                            }
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
                            setIsFocusOnTextField(it.isFocused)
                            if (it.isFocused) {

                                if (textFieldValue.text == url) {
                                    changeTextFieldValue(TextFieldValue("", TextRange(0)))
                                }
                            } else {

                                if (textFieldValue.text.isBlank()) {
                                    changeTextFieldValue(TextFieldValue(url, TextRange(url.length)))
                                }
                            }
                        }
                        .pointerInput(Unit) {
                            detectHorizontalDragGestures { _, dragAmount ->
                                if (dragAmount > 0) {
                                    changeTextFieldValue(
                                        TextFieldValue(
                                            url,
                                            selection = TextRange(url.length)
                                        )
                                    )
                                }
                            }
                        },
                    shape = RoundedCornerShape(browserSettings.cornerRadiusDp.dp),
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = if (isSystemInDarkTheme()) Color.Black else Color.White, // Background when focused
                        unfocusedContainerColor = if (isSystemInDarkTheme()) Color.Black else Color.White, // Background when unfocused
                        disabledContainerColor = if (isSystemInDarkTheme()) Color.White else Color.Black, // Background when disabled
                        errorContainerColor = Color.Red // Background when in error state
                    )
                )
                IconButton(
                    onClick = { updateBrowserSettings(browserSettings.copy(isInteractable = !browserSettings.isInteractable)) },
                    modifier = Modifier
                        .padding(start = browserSettings.paddingDp.dp)
                        .then(if (textFieldHeightDp > 0.dp) Modifier.size(textFieldHeightDp) else Modifier),
                    colors = IconButtonDefaults.iconButtonColors(
                        containerColor = MaterialTheme.colorScheme.primary
                    )

                ) {
                    Icon(
                        painter = if (browserSettings.isInteractable) painterResource(id = R.drawable.ic_transparent) else painterResource(
                            id = R.drawable.ic_immersive
                        ),
                        contentDescription = "Toggle Interactable",
//                        tint = MaterialTheme.colorScheme.onPrimary
                        tint = MaterialTheme.colorScheme.onPrimary
                    )
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
    request: PermissionRequest?,
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
                            containerColor = MaterialTheme.colorScheme.secondaryContainer
                        )
                    ) {
                        Icon(
                            painter = painterResource(id = request.iconResDeny), // You can make this icon generic too
                            contentDescription = "Deny Permission",
                            tint = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                    }

                    // --- Allow Button ---
                    IconButton(
                        onClick = onAllow,
                        modifier = Modifier
                            .weight(1f)
                            .height(browserSettings.singleLineHeight.dp),
                        colors = IconButtonDefaults.iconButtonColors(
                            containerColor = MaterialTheme.colorScheme.primary
                        )
                    ) {
                        Icon(
                            painter = painterResource(id = request.iconResDeny), // You can make this icon generic too
                            contentDescription = "Allow Permission",
                            tint = MaterialTheme.colorScheme.onPrimary
                        )
                    }
                }
            }
        }
    }
}
//@Composable
//fun PermissionPanel(
//    textFieldHeightDp: Dp,
//    setIsImmersiveMode: (Boolean) -> Unit,
//    browserSettings: BrowserSettings,
//    // The pending request, which also controls visibility. Null means hidden.
//    request: Pair<String, GeolocationPermissions.Callback>?,
//    // Event for when the user makes a choice on OUR panel.
//    onPermissionResult: (allow: Boolean) -> Unit
//) {
//    val isVisible = request != null
//    val origin = request?.first ?: "" // The website URL
//
//
//    AnimatedVisibility(
//        visible = isVisible,
//        enter = expandVertically(),
//        exit = shrinkVertically()
//    ) {
//        Card(
//            modifier = Modifier
//                .fillMaxWidth(),
////                .padding(
////                    horizontal = browserSettings.paddingDp.dp,
////                    vertical = browserSettings.paddingDp.dp / 2
////                )
//            colors = CardDefaults.cardColors(
//                containerColor = Color.Transparent
//            ),
//        ) {
//            // Apply padding to the Column to give the content some breathing room
//            Column(modifier = Modifier.padding(browserSettings.paddingDp.dp)) {
//                // --- THIS IS THE MODIFIED ROW ---
//                Row(
//                    modifier = Modifier.fillMaxWidth(),
//                    // This automatically adds space BETWEEN the buttons
//                    horizontalArrangement = Arrangement.spacedBy(browserSettings.paddingDp.dp)
//                ) {
//                    // --- Deny Button ---
//                    IconButton(
//                        onClick = { onPermissionResult(false) },
//                        // This makes the button take up one share of the available space
//                        modifier = Modifier
//                            .weight(1f)
//                            .height(browserSettings.singleLineHeight.dp), // Use a fixed height
//                        colors = IconButtonDefaults.iconButtonColors(
//                            containerColor = MaterialTheme.colorScheme.secondaryContainer // A less prominent color
//                        )
//                    ) {
//                        Icon(
//                            painter = painterResource(id = R.drawable.ic_location_off),
//                            contentDescription = "Deny Location Permission",
//                            tint = MaterialTheme.colorScheme.onSecondaryContainer
//                        )
//                    }
//
//                    // --- Allow Button ---
//                    IconButton(
//                        onClick = { onPermissionResult(true) },
//                        // This also takes up one share, creating a 50/50 split
//                        modifier = Modifier
//                            .weight(1f)
//                            .height(browserSettings.singleLineHeight.dp), // Use a fixed height
//                        colors = IconButtonDefaults.iconButtonColors(
//                            containerColor = MaterialTheme.colorScheme.primary // The main action color
//                        )
//                    ) {
//                        Icon(
//                            painter = painterResource(id = R.drawable.ic_location_on),
//                            contentDescription = "Allow Location Permission",
//                            tint = MaterialTheme.colorScheme.onPrimary
//                        )
//                    }
//                }
//            }
//        }
//    }
//}


data class OptionItem(
    val iconRes: Int, // The drawable resource ID for the icon
    val contentDescription: String,
    val onClick: () -> Unit,
)

@Composable
fun OptionsPanel(
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
                                containerColor = MaterialTheme.colorScheme.onPrimary
                            ),

                            ) {
                            Icon(
                                painter = painterResource(id = option.iconRes),
                                contentDescription = option.contentDescription,
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    }

                    // If a page has fewer than 4 items, we add spacers to keep the layout consistent.
                    repeat(4 - pageOptions.size) {
                        Spacer(modifier = Modifier.weight(1f))
                    }
                }
            }

//            LazyRow(
//                modifier = Modifier.fillMaxWidth(),
//                // Add consistent spacing between each button
//                horizontalArrangement = Arrangement.spacedBy(browserSettings.paddingDp.dp)
//            ) {
//                // The `items` block is like a forEach loop for LazyRow/LazyColumn
//                items(options) { option ->
//                    // --- 3. Create an IconButton for each option ---
//                    IconButton(
//                        onClick = option.onClick,
//                        // Make each button take up roughly 1/4th of the screen width
//                        // minus the padding, so four are visible at a time.
//                        modifier = Modifier.width(IntrinsicSize.Min), // Example sizing
//                        colors = IconButtonDefaults.iconButtonColors(
//                            containerColor = MaterialTheme.colorScheme.primaryContainer
//                        )
//                    ) {
//                        Icon(
//                            painter = painterResource(id = option.iconRes),
//                            contentDescription = option.contentDescription,
//                            tint = MaterialTheme.colorScheme.onPrimaryContainer
//                        )
//                    }
//                }
//
//            }
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