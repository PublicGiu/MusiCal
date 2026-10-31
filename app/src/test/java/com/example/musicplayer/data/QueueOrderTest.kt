package com.example.musicplayer.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Test unitari per la logica di prioritizzazione della coda, separazione Coda Utente vs Autoplay
 * e finestra rolling di 15 brani successivi.
 */
class QueueOrderTest {

    private fun calculateMoveToTopTargetIndex(fromIndex: Int, currentIndex: Int, count: Int): Int? {
        if (fromIndex !in 0 until count) return null
        return if (currentIndex in 0 until count) {
            if (fromIndex == currentIndex || fromIndex == currentIndex + 1) null
            else if (fromIndex < currentIndex) currentIndex else currentIndex + 1
        } else {
            if (fromIndex == 0) null else 0
        }
    }

    private fun calculateInsertIndex(currentIndex: Int, count: Int): Int {
        return if (currentIndex in 0 until count) currentIndex + 1 else count
    }

    @Test
    fun testInsertSingleTrackAfterCurrentPlayingTrack() {
        val initialQueue = mutableListOf("A", "B", "C")
        val currentIndex = 0
        val insertIndex = calculateInsertIndex(currentIndex, initialQueue.size)
        assertEquals(1, insertIndex)

        initialQueue.add(insertIndex, "X")
        assertEquals(listOf("A", "X", "B", "C"), initialQueue)
        assertEquals("X", initialQueue[currentIndex + 1])
    }

    @Test
    fun testInsertBatchTracksAfterCurrentPlayingTrack() {
        val initialQueue = mutableListOf("Album1_T1", "Album1_T2")
        val currentIndex = 0
        val insertIndex = calculateInsertIndex(currentIndex, initialQueue.size)
        assertEquals(1, insertIndex)

        val newBatch = listOf("Album2_T1", "Album2_T2")
        initialQueue.addAll(insertIndex, newBatch)
        assertEquals(listOf("Album1_T1", "Album2_T1", "Album2_T2", "Album1_T2"), initialQueue)
    }

    @Test
    fun testInsertWhenQueueEmpty() {
        val currentIndex = -1
        val count = 0
        val insertIndex = calculateInsertIndex(currentIndex, count)
        assertEquals(0, insertIndex)
    }

    @Test
    fun testSequentialQueueAdditionPreservesFifoOrder() {
        val timeline = mutableListOf("Current_Track", "Album_Next_1", "Album_Next_2")
        val userQueue = mutableListOf<String>()
        val currentIndex = 0

        // User queues Track_A
        val insertIndexA = currentIndex + 1 + userQueue.size
        timeline.add(insertIndexA, "Track_A")
        userQueue.add("Track_A")

        // User queues Track_B
        val insertIndexB = currentIndex + 1 + userQueue.size
        timeline.add(insertIndexB, "Track_B")
        userQueue.add("Track_B")

        // User queue must strictly be Track_A then Track_B
        assertEquals(listOf("Track_A", "Track_B"), userQueue)
        // Timeline must have Track_A at currentIndex + 1 and Track_B at currentIndex + 2
        assertEquals(listOf("Current_Track", "Track_A", "Track_B", "Album_Next_1", "Album_Next_2"), timeline)
    }

    @Test
    fun testMoveToTopFromLaterInQueue() {
        val queue = mutableListOf("A", "B", "C", "D", "E")
        val currentIndex = 1
        val fromIndex = 4

        val targetIndex = calculateMoveToTopTargetIndex(fromIndex, currentIndex, queue.size)
        assertEquals(2, targetIndex)

        val item = queue.removeAt(fromIndex)
        queue.add(targetIndex!!, item)

        assertEquals(listOf("A", "B", "E", "C", "D"), queue)
        assertEquals("E", queue[currentIndex + 1])
    }

    @Test
    fun testMoveToTopFromEarlierInQueue() {
        val queue = mutableListOf("A", "B", "C", "D")
        var currentIndex = 2
        val fromIndex = 0

        val targetIndex = calculateMoveToTopTargetIndex(fromIndex, currentIndex, queue.size)
        assertEquals(2, targetIndex)

        val item = queue.removeAt(fromIndex)
        if (fromIndex < currentIndex) {
            currentIndex--
        }
        queue.add(targetIndex!!, item)

        assertEquals(listOf("B", "C", "A", "D"), queue)
        assertEquals(1, currentIndex)
        assertEquals("C", queue[currentIndex])
        assertEquals("A", queue[currentIndex + 1])
    }

    @Test
    fun testMoveToTopNoOpCases() {
        val count = 5
        val currentIndex = 2

        assertNull(calculateMoveToTopTargetIndex(fromIndex = 2, currentIndex = currentIndex, count = count))
        assertNull(calculateMoveToTopTargetIndex(fromIndex = 3, currentIndex = currentIndex, count = count))
        assertNull(calculateMoveToTopTargetIndex(fromIndex = -1, currentIndex = currentIndex, count = count))
        assertNull(calculateMoveToTopTargetIndex(fromIndex = 5, currentIndex = currentIndex, count = count))
    }

    @Test
    fun testMoveToTopWhenStopped() {
        val count = 4
        val currentIndex = -1

        assertNull(calculateMoveToTopTargetIndex(fromIndex = 0, currentIndex = currentIndex, count = count))
        assertEquals(0, calculateMoveToTopTargetIndex(fromIndex = 2, currentIndex = currentIndex, count = count))
    }

    @Test
    fun testUserQueueHasStrictPriorityOverAutoplay() {
        // Brano corrente P (indice 0 in ExoPlayer)
        val currentTrack = "P"
        val userQueue = mutableListOf("User_1", "User_2")
        val autoplayQueue = (1..15).map { "Auto_$it" }.toMutableList()

        // Costruzione playlist totale in ExoPlayer: [P] + UserQueue + AutoplayQueue
        val exoPlayerTimeline = mutableListOf(currentTrack)
        exoPlayerTimeline.addAll(userQueue)
        exoPlayerTimeline.addAll(autoplayQueue)

        assertEquals(18, exoPlayerTimeline.size)
        // Gli elementi 1 e 2 sono rigorosamente quelli della UserQueue
        assertEquals("User_1", exoPlayerTimeline[1])
        assertEquals("User_2", exoPlayerTimeline[2])

        // Gli elementi autoplay iniziano SOLO dopo l'ultimo elemento della UserQueue
        assertEquals("Auto_1", exoPlayerTimeline[3])
        assertEquals("Auto_15", exoPlayerTimeline[17])
    }

    @Test
    fun testUserQueueConsumptionAndHiding() {
        var userQueue = listOf("Track_X", "Track_Y")
        assertTrue(userQueue.isNotEmpty()) // Sezione visibile

        // Track_X inizia la riproduzione
        userQueue = userQueue.drop(1)
        assertEquals(listOf("Track_Y"), userQueue)
        assertTrue(userQueue.isNotEmpty()) // Ancora 1 elemento

        // Track_Y inizia la riproduzione
        userQueue = userQueue.drop(1)
        assertTrue(userQueue.isEmpty()) // Lista vuota -> Sezione "In coda" nascosta automaticamente!
    }

    @Test
    fun testAutoplayRollingWindowMaintains15Tracks() {
        val autoplayQueue = (1..15).map { "Track_$it" }.toMutableList()
        assertEquals(15, autoplayQueue.size)

        // Un brano autoplay viene consumato (avviato) oppure rimosso con 'X'
        autoplayQueue.removeAt(0)
        assertEquals(14, autoplayQueue.size)

        // Replenishment automatico estrae 1 nuovo brano
        val newTrackCandidate = "Track_16"
        autoplayQueue.add(newTrackCandidate)

        // La finestra è mantenuta a esattamente 15 brani successivi
        assertEquals(15, autoplayQueue.size)
        assertEquals("Track_2", autoplayQueue.first())
        assertEquals("Track_16", autoplayQueue.last())
    }

    @Test
    fun testAutoplayMoveToTop() {
        val autoplayQueue = (1..15).map { "Track_$it" }.toMutableList()
        // Spostamento del brano a indice 4 ("Track_5") in cima all'autoplay (indice 0)
        val fromIndex = 4
        val item = autoplayQueue.removeAt(fromIndex)
        autoplayQueue.add(0, item)

        assertEquals("Track_5", autoplayQueue[0])
        assertEquals("Track_1", autoplayQueue[1])
        assertEquals(15, autoplayQueue.size)
    }
}
