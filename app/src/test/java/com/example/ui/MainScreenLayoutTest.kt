package com.example.ui

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MainScreenLayoutTest {
    @Test
    fun bottomNavigationIsCompactOnlyOnPhoneWidths() {
        assertTrue(usesCompactBottomNavigation(360))
        assertTrue(usesCompactBottomNavigation(599))
        assertFalse(usesCompactBottomNavigation(600))
        assertFalse(usesCompactBottomNavigation(800))
    }
}
