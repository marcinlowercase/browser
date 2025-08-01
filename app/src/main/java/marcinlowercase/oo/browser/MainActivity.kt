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
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.awaitTouchSlopOrCancellation
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.LayoutDirection


class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
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

@Composable
fun rememberHasDisplayCutout(): State<Boolean> {
    // These are fine, as LocalConfiguration and LocalDensity are ambient Composable properties
    val configuration = LocalConfiguration.current
    val density = LocalDensity.current

    // Directly get the PaddingValues at the Composable level
    // WindowInsets.displayCutout here provides the current insets for the composition
    val displayCutoutPaddingValues = WindowInsets.displayCutout.asPaddingValues() // Pass density if needed, or rely on ambient if appropriate for the API version

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
    val sharedPrefs = remember { context.getSharedPreferences("BrowserPrefs", Context.MODE_PRIVATE) }

    var url by rememberSaveable {
        mutableStateOf(sharedPrefs.getString("last_url", "https://www.google.com") ?: "https://www.google.com")
    }

    var textFieldValue by rememberSaveable(stateSaver = TextFieldValue.Saver) {
        mutableStateOf(TextFieldValue(url, TextRange(url.length)))
    }
//    var isLockFullscreenMode by remember { mutableStateOf(false) }
    var isLockFullscreenMode by rememberSaveable { mutableStateOf(sharedPrefs.getBoolean("is_lock_fullscreen_mode", false)) }


    var paddingDp by remember {
        mutableFloatStateOf(sharedPrefs.getFloat("padding_dp", 8f))
    }

    var cornerRadiusDp by remember {
        mutableFloatStateOf(sharedPrefs.getFloat("corner_radius_dp", 32f))
    }

    var webView by remember { mutableStateOf<WebView?>(null) }
    var canGoBack by remember { mutableStateOf(false) }
    var isLoading by remember { mutableStateOf(false) }
    var isFocusOnTextField by remember { mutableStateOf(false) }

    val keyboardController = LocalSoftwareKeyboardController.current
    val focusManager = LocalFocusManager.current

    val cutoutInsets = remember { mutableIntStateOf(WindowInsetsCompat.Type.displayCutout()) }

    var textFieldHeightPx by remember { mutableStateOf(0) }
    // Density is needed to convert Px to Dp
    val density = LocalDensity.current

    // Convert the pixel height to Dp
    val textFieldHeightDp = with(density) { textFieldHeightPx.toDp() }


    var isUrlBarVisible by rememberSaveable { mutableStateOf(true) }


    val hasDisplayCutout by rememberHasDisplayCutout()




    val animatedPadding by animateDpAsState(
        targetValue = if (isUrlBarVisible) paddingDp.dp else 0.dp,
        label = "Padding Animation"
    )

    val animatedCornerRadius by animateDpAsState(
        targetValue = if (isUrlBarVisible || hasDisplayCutout) cornerRadiusDp.dp else 0.dp,
        label = "Corner Radius Animation"
    )

    LaunchedEffect(isUrlBarVisible) {
        val window = (context as? Activity)?.window ?: return@LaunchedEffect
        val insetsController = WindowCompat.getInsetsController(window, window.decorView)
        if (isUrlBarVisible) {
            insetsController.show(WindowInsetsCompat.Type.systemBars())
        } else {
            insetsController.hide(WindowInsetsCompat.Type.systemBars())
            insetsController.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
    }

    LaunchedEffect(url, paddingDp, cornerRadiusDp, isLockFullscreenMode) {
        with(sharedPrefs.edit()) {
            putString("last_url", url)
            putFloat("padding_dp", paddingDp)
            putFloat("corner_radius_dp", cornerRadiusDp)
            putBoolean("is_lock_fullscreen_mode", isLockFullscreenMode)
            apply()
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
//                .clip(RoundedCornerShape(animatedCornerRadius))
//                .padding(if (isUrlBarVisible) paddingDp.dp else 0.dp) // No padding in fullscreen
//
//                .clip(if (isUrlBarVisible) RoundedCornerShape(cornerRadiusDp.dp) else RoundedCornerShape(0.dp)) // No corners in fullscreen]
                .windowInsetsPadding(if (isUrlBarVisible) WindowInsets(0) else WindowInsets.displayCutout)
                .clip(RoundedCornerShape(animatedCornerRadius))
                .testTag("WebViewContainer")


        ) {
            AndroidView(
                factory = { ctx ->
                    WebView(ctx).apply {

                        addJavascriptInterface(WebAppInterface(), "Android")


                        webViewClient = object : WebViewClient() {
                            override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                                super.onPageStarted(view, url, favicon)
                                isLoading = true
                                if (!isFocusOnTextField) url?.let { textFieldValue = TextFieldValue(it, TextRange(it.length)) }
                            }

                            override fun onPageFinished(view: WebView?, currentUrl: String?) {
                                super.onPageFinished(view, currentUrl)
                                isLoading = false
                                canGoBack = view?.canGoBack() ?: false
                                currentUrl?.let {
                                    url = it
                                    if (!isFocusOnTextField) textFieldValue = TextFieldValue(it, TextRange(it.length))
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

//                        setOnTouchListener { _, _ ->
//                            if (!isLockFullscreenMode) isUrlBarVisible = false
//                            false
//                        }
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

            if (isUrlBarVisible ) {
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
                                val dragOrTap = awaitTouchSlopOrCancellation(down.id) { _,_ ->
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
//                Box(
//                    modifier = Modifier
//                        .fillMaxSize()
//                        // *** MODIFICATION HERE: Using the much simpler detectTapGestures API ***
//                        .pointerInput(Unit) {
//                            detectTapGestures(
//                                // The onPress lambda is called as soon as a finger touches down.
//                                // It gives us a scope where we can await the release.
//                                onPress = {
//                                    // tryAwaitRelease() will wait for the finger to be lifted up.
//                                    // - If it's lifted (a tap), it returns true.
//                                    // - If the gesture is consumed by a drag/scroll (a swipe),
//                                    //   it returns false.
//                                    val isTap = tryAwaitRelease()
//
//                                    if (isTap) {
//                                        // The finger was lifted without being stolen by a swipe.
//                                        // This is a confirmed tap.
//                                        isUrlBarVisible = false
//                                    }
//                                    // If it was a swipe (isTap is false), we do nothing.
//                                }
//                            )
//                        }
//                )
            }
        }

        AnimatedVisibility(
            visible = isUrlBarVisible,
            enter = expandVertically(tween(300)),
            exit = shrinkVertically(tween(300))
        )  {
            Row() {
                OutlinedTextField(
                    value = textFieldValue.text,
                    onValueChange = { newValue -> textFieldValue = TextFieldValue(newValue, selection = TextRange(newValue.length)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Go),
                    keyboardActions = KeyboardActions(
                        onGo = {
                            val input = textFieldValue.text.trim()
                            if (input.isBlank()) {
                                textFieldValue = TextFieldValue(url, TextRange(url.length))
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
                                url = if (input.startsWith("http://") || input.startsWith("https://")) {
                                    input
                                } else {
                                    "https://$input"
                                }
                            } else {
                                val encodedQuery = URLEncoder.encode(input, StandardCharsets.UTF_8.toString())
                                url = "https://www.google.com/search?q=$encodedQuery"
                            }
                            focusManager.clearFocus()
                            keyboardController?.hide()
                            if (!isLockFullscreenMode) isUrlBarVisible = false
                        }
                    ),
                    modifier = Modifier
                        .weight(1f)
                        .onSizeChanged { size ->
                            textFieldHeightPx = size.height
                        }
                        .fillMaxWidth()
                        .padding(horizontal = paddingDp.dp, vertical = paddingDp.dp / 2)
                        .onFocusChanged {
                            isFocusOnTextField = it.isFocused
                            if (it.isFocused) {
                                // Ensure the bar is visible when it gets focus
//                            isUrlBarVisible = true
                                if (textFieldValue.text == url) {
                                    textFieldValue = TextFieldValue("", TextRange(0))
                                }
                            } else {
                                if (textFieldValue.text.isBlank()) {
                                    textFieldValue = TextFieldValue(url, TextRange(url.length))
                                }
                            }
                        }
                        .pointerInput(Unit) {
                            detectHorizontalDragGestures { _, dragAmount ->
                                if (dragAmount > 0) {
                                    textFieldValue =
                                        TextFieldValue(url, selection = TextRange(url.length))
                                }
                            }
                        },
                    shape = RoundedCornerShape(cornerRadiusDp.dp),
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = if (isSystemInDarkTheme()) Color.Black else Color.White, // Background when focused
                        unfocusedContainerColor = if (isSystemInDarkTheme()) Color.Black else Color.White, // Background when unfocused
                        disabledContainerColor = if (isSystemInDarkTheme()) Color.White else Color.Black, // Background when disabled
                        errorContainerColor = Color.Red // Background when in error state
                    )
                )
                IconButton(
                    onClick = { isLockFullscreenMode = !isLockFullscreenMode },
                    modifier = Modifier
//                        .padding(start = paddingDp.dp)
                        .then(if (textFieldHeightDp > 0.dp) Modifier.size(textFieldHeightDp) else Modifier)

                ) {
                    Icon(
                        painter = painterResource(id = R.drawable.circle),
                        contentDescription = "Lock Fullscreen",
                        modifier = Modifier.background(if (isSystemInDarkTheme()) Color.Black else Color.White)
//                        modifier = Modifier.size(100.dp)
                    )
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

class WebAppInterface {
    /**
     * This method can be called from JavaScript.
     * It receives the background color of the web page and logs it.
     */
    @android.webkit.JavascriptInterface
    fun logBackgroundColor(color: String) {
        if (color.isNotBlank() && color != "null") {
            Log.d("WebViewBackground", "Detected web page background color: $color")
        } else {
            Log.d("WebViewBackground", "Web page background color could not be determined or is transparent.")
        }
    }
}