//fun rememberHasDisplayCutout(): State<Boolean> {
//    // These are fine, as LocalConfiguration and LocalDensity are ambient Composable properties
//    val configuration = LocalConfiguration.current
//    val density = LocalDensity.current
//
//    // Directly get the PaddingValues at the Composable level
//    // WindowInsets.displayCutout here provides the current insets for the composition
//    val displayCutoutPaddingValues =
//        WindowInsets.displayCutout.asPaddingValues() // Pass density if needed, or rely on ambient if appropriate for the API version
//
//    // Now, derivedStateOf can read from displayCutoutPaddingValues
//    // We also key remember on configuration and density to re-evaluate if they change,
//    // and on displayCutoutPaddingValues itself to re-calculate if the insets change.
//    val hasCutout = remember(configuration, density, displayCutoutPaddingValues) {
//        derivedStateOf {
//            // Check if any of the cutout inset dimensions are greater than zero.
//            (displayCutoutPaddingValues.calculateTopPadding() > 0.dp ||
//                    displayCutoutPaddingValues.calculateLeftPadding(LayoutDirection.Ltr) > 0.dp ||
//                    displayCutoutPaddingValues.calculateRightPadding(LayoutDirection.Ltr) > 0.dp)
//            // Bottom cutouts are rare, so often omitted from this specific check
//        }
//    }
//    return hasCutout
//}