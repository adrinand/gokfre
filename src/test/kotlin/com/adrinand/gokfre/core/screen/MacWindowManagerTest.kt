package com.adrinand.gokfre.core.screen

import io.kotest.matchers.shouldBe
import org.junit.Test
import java.awt.Rectangle

class MacWindowManagerTest {
    private val selfPid = ProcessHandle.current().pid()
    private val focusedAppPid = selfPid + 1000
    private val focusedElementPid = selfPid + 2000
    private val frontmostPid = selfPid + 3000
    private val arrangePid = selfPid + 4000

    @Test
    fun `uses the focused application pid when it is available`() {
        val source =
            FakeMacWindowSource(
                focusedAppPid = focusedAppPid,
                focusedElementPid = focusedElementPid,
                frontmostPid = frontmostPid,
                titles = mutableMapOf(focusedAppPid to "Terminal"),
            )

        MacWindowManager(source).getActiveWindowId() shouldBe
            WindowHandle.Mac(pid = focusedAppPid, title = "Terminal")
    }

    @Test
    fun `falls back to the focused element when the focused application is unavailable`() {
        val source =
            FakeMacWindowSource(
                focusedElementPid = focusedElementPid,
                frontmostPid = frontmostPid,
                focusedWindowPids = mutableSetOf(focusedElementPid),
            )

        MacWindowManager(source).getActiveWindowId() shouldBe WindowHandle.Mac(pid = focusedElementPid)
    }

    @Test
    fun `falls back to the frontmost window when the focused element has no focused window`() {
        val source =
            FakeMacWindowSource(
                focusedElementPid = focusedElementPid,
                frontmostPid = frontmostPid,
            )

        MacWindowManager(source).getActiveWindowId() shouldBe WindowHandle.Mac(pid = frontmostPid)
    }

    @Test
    fun `falls back when the focused application is gokfre itself`() {
        val source =
            FakeMacWindowSource(
                focusedAppPid = selfPid,
                focusedElementPid = focusedElementPid,
                frontmostPid = frontmostPid,
                focusedWindowPids = mutableSetOf(focusedElementPid),
            )

        MacWindowManager(source).getActiveWindowId() shouldBe WindowHandle.Mac(pid = focusedElementPid)
    }

    @Test
    fun `falls back when the focused element belongs to gokfre`() {
        val source =
            FakeMacWindowSource(
                focusedElementPid = selfPid,
                frontmostPid = frontmostPid,
                focusedWindowPids = mutableSetOf(selfPid),
            )

        MacWindowManager(source).getActiveWindowId() shouldBe WindowHandle.Mac(pid = frontmostPid)
    }

    @Test
    fun `returns null when every source fails`() {
        MacWindowManager(FakeMacWindowSource()).getActiveWindowId() shouldBe null
    }

    @Test
    fun `returns null when the frontmost window belongs to gokfre`() {
        val source = FakeMacWindowSource(frontmostPid = selfPid)

        MacWindowManager(source).getActiveWindowId() shouldBe null
    }

    @Test
    fun `applies bounds to the captured pid`() {
        val source = FakeMacWindowSource()
        val manager = MacWindowManager(source)
        val bounds = Rectangle(10, 20, 300, 400)

        manager.setWindowBounds(WindowHandle.Mac(pid = arrangePid), bounds)

        source.bounds shouldBe (arrangePid to bounds)
    }

    @Test
    fun `raises the captured pid`() {
        val source = FakeMacWindowSource()
        val manager = MacWindowManager(source)

        manager.focusWindow(WindowHandle.Mac(pid = arrangePid))

        source.raisedPid shouldBe arrangePid
    }
}

private class FakeMacWindowSource(
    var focusedAppPid: Long? = null,
    var focusedElementPid: Long? = null,
    var frontmostPid: Long? = null,
    var focusedWindowPids: MutableSet<Long> = mutableSetOf(),
    var titles: MutableMap<Long, String> = mutableMapOf(),
) : MacWindowSource {
    var bounds: Pair<Long, Rectangle>? = null
    var raisedPid: Long? = null

    override fun focusedApplicationPid(): Long? = focusedAppPid

    override fun focusedElementPid(): Long? = focusedElementPid

    override fun hasFocusedWindow(pid: Long): Boolean = pid in focusedWindowPids

    override fun frontmostWindowPid(selfPid: Long): Long? = frontmostPid?.takeIf { it != selfPid }

    override fun windowTitle(pid: Long): String? = titles[pid]

    override fun setBounds(
        pid: Long,
        bounds: Rectangle,
    ) {
        this.bounds = pid to bounds
    }

    override fun raise(pid: Long) {
        raisedPid = pid
    }
}
