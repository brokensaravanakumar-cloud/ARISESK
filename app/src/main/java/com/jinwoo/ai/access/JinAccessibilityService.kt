package com.jinwoo.ai.access

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import com.jinwoo.ai.core.Guard

/**
 * Voice-driven screen control, deliberately narrow:
 *  - limited by the manifest config to Play Store, Chrome, Gmail and BookMyShow
 *  - never taps payment / purchase / delete / sign-out controls ([Guard])
 *  - never types into password, PIN, OTP or card fields
 *  - does nothing on its own: every action is a direct result of a command you spoke
 */
class JinAccessibilityService : AccessibilityService() {

    enum class TapResult { OK, NOT_FOUND, BLOCKED, WRONG_APP, NO_ACCESS }
    enum class TypeResult { OK, NO_FIELD, SENSITIVE, NO_ACCESS }

    companion object {
        @Volatile
        var instance: JinAccessibilityService? = null
        val isRunning: Boolean get() = instance != null
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
    }

    override fun onUnbind(intent: Intent?): Boolean {
        instance = null
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        instance = null
        super.onDestroy()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // Intentionally empty: we never watch or record what is on screen.
    }

    override fun onInterrupt() {}

    // ---------------------------------------------------------------- helpers

    private fun allowedRoot(): AccessibilityNodeInfo? {
        val root = rootInActiveWindow ?: return null
        val pkg = root.packageName?.toString() ?: return null
        return if (pkg in Guard.allowedPackages) root else null
    }

    private fun labelOf(n: AccessibilityNodeInfo): String {
        val t = n.text?.toString()
        if (!t.isNullOrBlank()) return t
        return n.contentDescription?.toString() ?: ""
    }

    private fun walk(node: AccessibilityNodeInfo?, depth: Int, counter: IntArray, visit: (AccessibilityNodeInfo) -> Unit) {
        if (node == null || depth > 40 || counter[0] > 2500) return
        counter[0]++
        visit(node)
        for (i in 0 until node.childCount) walk(node.getChild(i), depth + 1, counter, visit)
    }

    fun activePackage(): String? = rootInActiveWindow?.packageName?.toString()

    // ---------------------------------------------------------------- actions

    fun tapText(target: String, exact: Boolean = false): TapResult {
        val root = allowedRoot() ?: return if (rootInActiveWindow == null) TapResult.NO_ACCESS else TapResult.WRONG_APP
        if (Guard.isBlockedTarget(target)) return TapResult.BLOCKED
        val want = target.lowercase().trim()
        if (want.isEmpty()) return TapResult.NOT_FOUND

        var best: AccessibilityNodeInfo? = null
        var bestScore = Int.MAX_VALUE
        var blockedHit = false
        walk(root, 0, IntArray(1)) { n ->
            val l = labelOf(n)
            if (l.isNotBlank()) {
                val low = l.lowercase().trim()
                val match = if (exact) low == want else (low == want || low.contains(want))
                if (match) {
                    if (Guard.isBlockedTarget(l)) {
                        blockedHit = true
                    } else if (n.isVisibleToUser) {
                        val score = if (low == want) 0 else low.length
                        if (score < bestScore) {
                            best = n
                            bestScore = score
                        }
                    }
                }
            }
        }
        val hit = best ?: return if (blockedHit) TapResult.BLOCKED else TapResult.NOT_FOUND

        var cur: AccessibilityNodeInfo? = hit
        var hops = 0
        while (cur != null && hops < 6) {
            if (cur.isClickable && cur.isEnabled) {
                return if (cur.performAction(AccessibilityNodeInfo.ACTION_CLICK)) TapResult.OK else TapResult.NOT_FOUND
            }
            cur = cur.parent
            hops++
        }
        return if (hit.performAction(AccessibilityNodeInfo.ACTION_CLICK)) TapResult.OK else TapResult.NOT_FOUND
    }

    /** Taps the first visible clickable item whose label contains [needle] (e.g. YouTube's "Play video" results). */
    fun tapFirstContaining(needle: String): Boolean {
        val root = allowedRoot() ?: return false
        val want = needle.lowercase()
        var hit: AccessibilityNodeInfo? = null
        walk(root, 0, IntArray(1)) { n ->
            if (hit == null && n.isVisibleToUser && labelOf(n).lowercase().contains(want) &&
                !Guard.isBlockedTarget(labelOf(n))
            ) hit = n
        }
        var cur: AccessibilityNodeInfo? = hit
        var hops = 0
        while (cur != null && hops < 6) {
            if (cur.isClickable && cur.isEnabled) return cur.performAction(AccessibilityNodeInfo.ACTION_CLICK)
            cur = cur.parent
            hops++
        }
        return false
    }

    fun hasExactText(text: String): Boolean {
        val root = allowedRoot() ?: return false
        val want = text.lowercase().trim()
        var found = false
        walk(root, 0, IntArray(1)) { n ->
            if (!found && n.isVisibleToUser && labelOf(n).lowercase().trim() == want) found = true
        }
        return found
    }

    fun scroll(down: Boolean): Boolean {
        val root = allowedRoot() ?: return false
        var found: AccessibilityNodeInfo? = null
        walk(root, 0, IntArray(1)) { n ->
            if (found == null && n.isScrollable && n.isVisibleToUser) found = n
        }
        val f = found ?: return false
        val action = if (down) AccessibilityNodeInfo.ACTION_SCROLL_FORWARD else AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD
        return f.performAction(action)
    }

    fun typeText(text: String): TypeResult {
        val root = allowedRoot() ?: return TypeResult.NO_ACCESS
        var input: AccessibilityNodeInfo? = root.findFocus(AccessibilityNodeInfo.FOCUS_INPUT)
        if (input == null || !input.isEditable) {
            var first: AccessibilityNodeInfo? = null
            walk(root, 0, IntArray(1)) { n ->
                if (first == null && n.isEditable && n.isVisibleToUser) first = n
            }
            input = first
        }
        val f = input ?: return TypeResult.NO_FIELD
        if (f.isPassword ||
            Guard.isSensitiveField(f.hintText?.toString()) ||
            Guard.isSensitiveField(f.contentDescription?.toString())
        ) return TypeResult.SENSITIVE
        f.performAction(AccessibilityNodeInfo.ACTION_FOCUS)
        val args = Bundle()
        args.putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, text)
        return if (f.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)) TypeResult.OK else TypeResult.NO_FIELD
    }

    fun pressEnter(): Boolean {
        val root = allowedRoot() ?: return false
        val f = root.findFocus(AccessibilityNodeInfo.FOCUS_INPUT)
        if (f != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            if (f.performAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_IME_ENTER.id)) return true
        }
        return tapText("search", exact = true) == TapResult.OK
    }

    fun back(): Boolean = performGlobalAction(GLOBAL_ACTION_BACK)

    fun home(): Boolean = performGlobalAction(GLOBAL_ACTION_HOME)

    /** Reads up to ~25 visible labels aloud (never password fields). */
    fun readScreen(): String {
        val root = allowedRoot() ?: return ""
        val seen = LinkedHashSet<String>()
        walk(root, 0, IntArray(1)) { n ->
            if (seen.size < 25 && n.isVisibleToUser && !n.isPassword) {
                val l = labelOf(n).trim()
                if (l.length in 2..80) seen.add(l)
            }
        }
        return seen.joinToString(", ")
    }
}
