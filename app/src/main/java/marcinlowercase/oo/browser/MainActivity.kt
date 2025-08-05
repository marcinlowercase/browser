package marcinlowercase.oo.browser

import android.app.Activity
import android.content.Context
import android.graphics.Bitmap
import android.os.Bundle
import android.util.Log
import android.util.Patterns
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateDpAsState
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import marcinlowercase.oo.browser.ui.theme.BrowserTheme
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import androidx.compose.material3.Icon
import androidx.compose.ui.Alignment
import androidx.compose.ui.focus.FocusManager
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.SoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.core.content.edit


class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

//        WindowCompat.getInsetsController(window, window.decorView)
        setContent {
            BrowserTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    BrowserScreen(
                        modifier = Modifier.padding(innerPadding)
                    )
                }
            }
        }
    }
}

data class BrowserSettings(
    val paddingDp: Float,
    val cornerRadiusDp: Float,
    val isLockFullscreenMode: Boolean,
    val defaultUrl: String,
)


// This creates the "tunnel" that will provide our settings object.
// We provide a default value as a fallback.
val LocalBrowserSettings = compositionLocalOf {
    BrowserSettings(
        paddingDp = 8f,
        cornerRadiusDp = 24f,
        isLockFullscreenMode = false,
        defaultUrl = "https://www.google.com",
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
    val context = LocalContext.current
    val sharedPrefs =
        remember { context.getSharedPreferences("BrowserPrefs", Context.MODE_PRIVATE) }

    val browserSettings = remember {
        BrowserSettings(
            paddingDp = sharedPrefs.getFloat("padding_dp", 8f),
            cornerRadiusDp = sharedPrefs.getFloat("corner_radius_dp", 24f),
            isLockFullscreenMode = sharedPrefs.getBoolean("is_lock_fullscreen_mode", false),
            defaultUrl = sharedPrefs.getString("default_url", "https://www.google.com")
                ?: "https://www.google.com"
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


    var webView by remember { mutableStateOf<WebView?>(null) }
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
        targetValue = if (isUrlBarVisible) browserSettings.paddingDp.dp else 0.dp,
        label = "Padding Animation",
    )

    val animatedCornerRadius by animateDpAsState(
        targetValue = if (isUrlBarVisible || hasDisplayCutout) browserSettings.cornerRadiusDp.dp else 0.dp,
        label = "Corner Radius Animation",
    )


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
            putBoolean("is_lock_fullscreen_mode", browserSettings.isLockFullscreenMode)
            putString("default_url", browserSettings.defaultUrl)
        }
    }

    LaunchedEffect(Unit) {
        focusManager.clearFocus()
    }


    BackHandler(enabled = !isUrlBarVisible || canGoBack) {
        if (!isUrlBarVisible) {
            isUrlBarVisible = true
        } else {
            webView?.goBack()
        }
    }

    CompositionLocalProvider(LocalBrowserSettings provides browserSettings) {
        Column(
            modifier = modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.ime.exclude(WindowInsets.navigationBars))
        ) {

            if (isLoading) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .padding(animatedPadding)
                    .windowInsetsPadding(if (isUrlBarVisible) WindowInsets(0) else WindowInsets.displayCutout)
                    .clip(RoundedCornerShape(animatedCornerRadius))
                    .testTag("WebViewContainer")
            ) {
                AndroidView(
                    factory = { ctx ->
                        WebView(ctx).apply {

                            addJavascriptInterface(
                                WebAppInterface(),
                                "Android"
                            )


                            webViewClient = object : WebViewClient() {
                                override fun onPageStarted(
                                    view: WebView?,
                                    url: String?,
                                    favicon: Bitmap?
                                ) {
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

                                    val jsScript = """
                                     (function() {
                                         var bodyColor = window.getComputedStyle(document.body).backgroundColor;
                                         var htmlColor = window.getComputedStyle(document.documentElement).backgroundColor;
                                         // A color of 'rgba(0, 0, 0, 0)' means transparent.
                                         // If the body is transparent, use the html tag's color as the fallback.
                                         var finalColor = (bodyColor === 'rgba(0, 0, 0, 0)') ? htmlColor : bodyColor;
                                         // Call the Kotlin method through the interface we named "Android"
                                         Android.logBackgroundColor(finalColor);
                                     })();
                                 """.trimIndent()

                                    view?.evaluateJavascript(jsScript, null)

                                }
                            }


                            settings.apply {
                                javaScriptEnabled = true
                                domStorageEnabled = true
                                cacheMode = WebSettings.LOAD_DEFAULT
                            }
                            loadUrl(url)
                            webView = this
                        }
                    },
                    update = {
                        it.loadUrl(url)
                    },
                    modifier = Modifier.fillMaxSize()
                )

                if (isUrlBarVisible) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            // *** THE DEFINITIVE FIX: Manually track the drag state ***
                            .pointerInput(Unit) {
                                awaitEachGesture {
                                    // 1. At the start of each new gesture, reset our flag.
                                    var isDrag = false

                                    // 2. Wait for the initial press.
                                    val down = awaitFirstDown(requireUnconsumed = false)

                                    // 3. Use awaitTouchSlopOrCancellation. We are most interested
                                    //    in its onSlopCrossed lambda.
                                    val dragOrTap = awaitTouchSlopOrCancellation(down.id) { _, _ ->
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
                                    }
                                }
                            }
                    )

                }
            }

            BottomPanel(
                isUrlBarVisible = isUrlBarVisible,
                isOptionsPanelVisible = isOptionsPanelVisible,
                browserSettings = browserSettings,
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
                setIsFocusOnTextField = { isFocusOnTextField = it }

            )


        }
    }
}

@Composable
fun BottomPanel(
    isUrlBarVisible: Boolean,
    isOptionsPanelVisible: Boolean,
    browserSettings: BrowserSettings,
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
    setIsFocusOnTextField: (Boolean) -> Unit = {}
) {
    AnimatedVisibility(
        visible = isUrlBarVisible,
        enter = expandVertically(tween(300)),
        exit = shrinkVertically(tween(300))
    ) {
        Column {
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
                            if (!browserSettings.isLockFullscreenMode) toggleUrlBar(false)
                        }
                    ),
                    modifier = Modifier
                        .weight(1f)
                        .onSizeChanged { size ->
                            setTextFieldHeightPx(size.height)
                        }
                        .fillMaxWidth()
                        //                            .padding(horizontal = browserSettings.paddingDp.dp, vertical = browserSettings.paddingDp.dp / 2)
                        .onFocusChanged {
                            setIsFocusOnTextField(it.isFocused)
                            if (it.isFocused) {
                                // Ensure the bar is visible when it gets focus
                                //                            isUrlBarVisible = true
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
                    onClick = { toggleUrlBar(!isUrlBarVisible) },
                    modifier = Modifier
                        .padding(start = browserSettings.paddingDp.dp)
                        .then(if (textFieldHeightDp > 0.dp) Modifier.size(textFieldHeightDp) else Modifier),
                    colors = IconButtonDefaults.iconButtonColors(
                        containerColor = MaterialTheme.colorScheme.primary
                    )

                ) {
                    Icon(
                        painter = painterResource(id = R.drawable.ic_fullscreen),
                        contentDescription = "Lock Fullscreen",
                        tint = MaterialTheme.colorScheme.onPrimary
                    )
                }
            }


            // SETTING OPTIONS
            OptionsPanel(
                isOptionsPanelVisible = isOptionsPanelVisible,
                toggleOptionsPanel = toggleOptionsPanel
            )
        }
    }
}

@Composable
fun OptionsPanel(
    isOptionsPanelVisible: Boolean = false,
    toggleOptionsPanel: (Boolean) -> Unit = {}
) {

    var browserSettings = LocalBrowserSettings.current

    AnimatedVisibility(
        visible = isOptionsPanelVisible,
        enter = expandVertically(tween(300)),
        exit = shrinkVertically(tween(300)),
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
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(browserSettings.paddingDp.dp) // Adds space between columns
            ) {
                // --- First Column ---
                Column(
                    modifier = Modifier.weight(1f), // Take up 1 share of the width
                    verticalArrangement = Arrangement.spacedBy(browserSettings.paddingDp.dp) // Adds space between buttons
                ) {
                    // First Button
                    IconButton(
                        onClick = { /* TODO: Action 1 */ },
                        modifier = Modifier.fillMaxWidth(), // Fill the column's width
                        colors = IconButtonDefaults.iconButtonColors(
                            containerColor = MaterialTheme.colorScheme.primary
                        )
                    ) {
                        Icon(
                            painter = painterResource(id = R.drawable.ic_fullscreen),
                            contentDescription = "Button 1",
                            tint = MaterialTheme.colorScheme.onPrimary
                        )
                    }
                    // Second Button
                    IconButton(
                        onClick = { /* TODO: Action 2 */ },
                        modifier = Modifier.fillMaxWidth(), // Fill the column's width
                        colors = IconButtonDefaults.iconButtonColors(
                            containerColor = MaterialTheme.colorScheme.primary
                        )
                    ) {
                        Icon(
                            painter = painterResource(id = R.drawable.ic_fullscreen),
                            contentDescription = "Button 2",
                            tint = MaterialTheme.colorScheme.onPrimary
                        )
                    }
                }

                // --- Second Column ---
                Column(
                    modifier = Modifier.weight(1f), // Take up 1 share of the width
                    verticalArrangement = Arrangement.spacedBy(browserSettings.paddingDp.dp) // Adds space between buttons
                ) {
                    // Third Button
                    IconButton(
                        onClick = { /* TODO: Action 3 */ },
                        modifier = Modifier.fillMaxWidth(), // Fill the column's width
                        colors = IconButtonDefaults.iconButtonColors(
                            containerColor = MaterialTheme.colorScheme.primary
                        )
                    ) {
                        Icon(
                            painter = painterResource(id = R.drawable.ic_fullscreen),
                            contentDescription = "Button 3",
                            tint = MaterialTheme.colorScheme.onPrimary
                        )
                    }
                    // Fourth Button
                    IconButton(
                        onClick = { /* TODO: Action 4 */ },
                        modifier = Modifier.fillMaxWidth(), // Fill the column's width
                        colors = IconButtonDefaults.iconButtonColors(
                            containerColor = MaterialTheme.colorScheme.primary
                        )
                    ) {
                        Icon(
                            painter = painterResource(id = R.drawable.ic_fullscreen),
                            contentDescription = "Button 4",
                            tint = MaterialTheme.colorScheme.onPrimary
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
    @android.webkit.JavascriptInterface
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