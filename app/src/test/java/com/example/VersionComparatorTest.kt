package com.example

import com.example.data.updater.VersionComparator
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VersionComparatorTest {

    @Test
    fun testIsNewerWithStandardSemver() {
        assertTrue(VersionComparator.isNewer("v1.0.4", "1.0.0"))
        assertTrue(VersionComparator.isNewer("1.1.0", "1.0.9"))
        assertTrue(VersionComparator.isNewer("v2.0.0", "1.9.9"))
        assertTrue(VersionComparator.isNewer("v1.0.1", "1.0"))
        assertTrue(VersionComparator.isNewer("v1.0.0.1", "1.0.0"))
    }

    @Test
    fun testSameOrOlderVersionIsNotNewer() {
        assertFalse(VersionComparator.isNewer("v1.0.0", "1.0.0"))
        assertFalse(VersionComparator.isNewer("1.0", "1.0.0"))
        assertFalse(VersionComparator.isNewer("v1.0.0", "1.0"))
        assertFalse(VersionComparator.isNewer("v0.9.9", "1.0.0"))
        assertFalse(VersionComparator.isNewer("v1.0.3", "1.0.4"))
    }

    @Test
    fun testPreReleaseTagsAndPrefixes() {
        assertTrue(VersionComparator.isNewer("release-1.2.0", "1.1.5"))
        assertTrue(VersionComparator.isNewer("v1.0.5-beta", "1.0.4"))
        assertFalse(VersionComparator.isNewer("v1.0.4-rc1", "1.0.4"))
    }
}
