package com.javasultra.app.core.learning

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import java.time.Instant

class LearningJournalCodecTest {
    private val now = Instant.parse("2026-11-09T12:00:00Z")
    private fun entry() = LearningJournalEntry(
        "entry-1", "task-1", "Study astronomy", LearningDomain.SCIENCE,
        LearningJournalState.RUNNING, now.minusSeconds(60), now,
        setOf("source-1", "source-2"), setOf("claim-1"), "NETWORK_TIMEOUT"
    )
    private fun event() = LearningJournalEvent(
        "event-1", "entry-1", LearningJournalEventType.SOURCE_RETRIEVED,
        now.plusSeconds(1), "learning-agent", "source-3", null, "SOURCE_RETRIEVED"
    )

    @Test fun roundTripsJournalEntry() {
        assertEquals(entry(), LearningJournalCodec.decodeEntry(LearningJournalCodec.encodeEntry(entry())))
    }

    @Test fun roundTripsJournalEvent() {
        assertEquals(event(), LearningJournalCodec.decodeEvent(LearningJournalCodec.encodeEvent(event())))
    }

    @Test fun rejectsUnsupportedEntryVersion() {
        val bytes = LearningJournalCodec.encodeEntry(entry())
        bytes[3] = 2
        assertThrows(IllegalArgumentException::class.java) { LearningJournalCodec.decodeEntry(bytes) }
    }

    @Test fun rejectsFreeTextThatCouldContainSecrets() {
        val original = event().copy(safeDetail = "token=secret; timeout!")
        val decoded = LearningJournalCodec.decodeEvent(LearningJournalCodec.encodeEvent(original))
        assertEquals(null, decoded.safeDetail)
    }

    @Test fun preservesOnlyStableAllowListedDetailCodes() {
        val original = event().copy(safeDetail = "NETWORK_TIMEOUT")
        val decoded = LearningJournalCodec.decodeEvent(LearningJournalCodec.encodeEvent(original))
        assertEquals("NETWORK_TIMEOUT", decoded.safeDetail)
    }

    @Test fun rejectsTrailingBytes() {
        val bytes = LearningJournalCodec.encodeEntry(entry()) + byteArrayOf(1)
        assertThrows(IllegalArgumentException::class.java) { LearningJournalCodec.decodeEntry(bytes) }
    }
}
