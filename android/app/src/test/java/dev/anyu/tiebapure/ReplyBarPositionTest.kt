package dev.anyu.tiebapure

import android.content.SharedPreferences
import org.junit.Assert.assertEquals
import org.junit.Test
import java.lang.reflect.Proxy

class ReplyBarPositionTest {
    @Test fun migrationAndIndependentPersistence() {
        val values = mutableMapOf<String, String>()
        val editor = Proxy.newProxyInstance(javaClass.classLoader, arrayOf(SharedPreferences.Editor::class.java)) { proxy, method, args ->
            when (method.name) {
                "putString" -> { values[args[0] as String] = args[1] as String; proxy }
                "apply" -> null
                else -> error("Unexpected editor method ${method.name}")
            }
        } as SharedPreferences.Editor
        val prefs = Proxy.newProxyInstance(javaClass.classLoader, arrayOf(SharedPreferences::class.java)) { _, method, args ->
            when (method.name) {
                "getString" -> values[args[0] as String] ?: args[1]
                "edit" -> editor
                else -> error("Unexpected preference method ${method.name}")
            }
        } as SharedPreferences

        assertEquals(BottomBarPosition.RIGHT, loadReplyBarPosition(prefs, BottomBarPosition.RIGHT))
        assertEquals("right", values["reply-bar-position"])
        assertEquals(BottomBarPosition.RIGHT, loadReplyBarPosition(prefs, BottomBarPosition.LEFT))
        prefs.edit().putString("reply-bar-position", "center").apply()
        assertEquals(BottomBarPosition.CENTER, loadReplyBarPosition(prefs, BottomBarPosition.RIGHT))
        values["reply-bar-position"] = "unknown"
        assertEquals(BottomBarPosition.LEFT, loadReplyBarPosition(prefs, BottomBarPosition.RIGHT))
    }
}
