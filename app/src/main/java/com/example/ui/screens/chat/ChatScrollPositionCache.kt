package com.example.ui.screens.chat

import java.util.concurrent.ConcurrentHashMap

/**
 * Global/Session in-memory scroll state cache for Chapter Chat screens.
 * Preserves exact scroll index and pixel offset across ViewModel re-creations,
 * navigation transitions, and back-stack re-entries.
 */
object ChatScrollPositionCache {
    private val scrollPositions = ConcurrentHashMap<Long, Pair<Int, Int>>()

    fun getPosition(chapterId: Long): Pair<Int, Int>? {
        return scrollPositions[chapterId]
    }

    fun savePosition(chapterId: Long, index: Int, offset: Int) {
        scrollPositions[chapterId] = Pair(index, offset)
    }

    fun clear(chapterId: Long) {
        scrollPositions.remove(chapterId)
    }
}
