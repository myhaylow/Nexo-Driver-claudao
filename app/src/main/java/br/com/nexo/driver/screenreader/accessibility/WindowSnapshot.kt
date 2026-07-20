package br.com.nexo.driver.screenreader.accessibility

import android.graphics.Rect
import android.os.Build
import android.view.Display
import android.view.accessibility.AccessibilityNodeInfo
import android.view.accessibility.AccessibilityWindowInfo

/** Immutable, in-memory copy of one supported accessibility window. */
data class WindowSnapshot(
    val windowId: Int,
    val packageName: String,
    val displayId: Int,
    val layer: Int,
    val bounds: Rect,
    val nodes: List<NodeSnapshot>,
    val capturedAt: Long,
)

data class NodeSnapshot(
    val text: String?,
    val contentDescription: String?,
    val hintText: String?,
    val viewId: String?,
    val className: String?,
    val bounds: Rect,
    val visible: Boolean,
    val clickable: Boolean,
    val enabled: Boolean,
    val depth: Int,
) {
    fun textFragments(): List<String> = listOfNotNull(text, contentDescription, hintText)
        .map(String::trim)
        .filter(String::isNotEmpty)
}

/** Copies the properties needed by parsers and recycles every framework node before returning. */
class WindowSnapshotCollector(
    private val now: () -> Long = System::currentTimeMillis,
) {
    fun collect(window: AccessibilityWindowInfo): WindowSnapshot? {
        val root = window.root ?: return null
        return try {
            val packageName = root.packageName?.toString() ?: return null
            val bounds = Rect().also(window::getBoundsInScreen)
            WindowSnapshot(
                windowId = window.id,
                packageName = packageName,
                displayId = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    window.displayId
                } else {
                    Display.DEFAULT_DISPLAY
                },
                layer = window.layer,
                bounds = bounds,
                nodes = walk(root),
                capturedAt = now(),
            )
        } finally {
            root.recycle()
        }
    }

    private fun walk(root: AccessibilityNodeInfo): List<NodeSnapshot> {
        val output = mutableListOf<NodeSnapshot>()
        copyNode(root, output, depth = 0)
        return output
    }

    private fun copyNode(node: AccessibilityNodeInfo, output: MutableList<NodeSnapshot>, depth: Int) {
        if (depth > MAX_DEPTH || output.size >= MAX_NODES) return
        val bounds = Rect().also(node::getBoundsInScreen)
        output += NodeSnapshot(
            text = node.text?.toString(),
            contentDescription = node.contentDescription?.toString(),
            hintText = node.hintText?.toString(),
            viewId = node.viewIdResourceName,
            className = node.className?.toString(),
            bounds = bounds,
            visible = node.isVisibleToUser,
            clickable = node.isClickable,
            enabled = node.isEnabled,
            depth = depth,
        )
        repeat(node.childCount) { index ->
            node.getChild(index)?.let { child ->
                try {
                    copyNode(child, output, depth + 1)
                } finally {
                    child.recycle()
                }
            }
        }
    }

    private companion object {
        const val MAX_DEPTH = 10
        const val MAX_NODES = 300
    }
}
