package marcinlowercase.oo.browser

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.annotation.SuppressLint
import android.graphics.Path
import android.view.accessibility.AccessibilityEvent
import android.util.Log

@SuppressLint("AccessibilityPolicy")
class CursorAccessibilityService : AccessibilityService() {

    companion object {
        // A static reference to the running service instance for easy access
        var instance: CursorAccessibilityService? = null
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        Log.d("Accessibility", "Cursor Accessibility Service Connected")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // We don't need to react to events, so this can be empty.
    }

    override fun onInterrupt() {
        // This is called when the service is interrupted.
    }

    override fun onDestroy() {
        super.onDestroy()
        instance = null
        Log.d("Accessibility", "Cursor Accessibility Service Destroyed")
    }

    fun performClick(x: Float, y: Float) {
        Log.d("Accessibility", "Dispatching click at ($x, $y)")
        val path = Path().apply {
            moveTo(x, y)
        }

        // A gesture is a series of strokes. A click is one stroke of 1ms.
        val gesture = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(path, 0, 1))
            .build()

        // Dispatch the gesture to the system
        dispatchGesture(gesture, null, null)
    }
}