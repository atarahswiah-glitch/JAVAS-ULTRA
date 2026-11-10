package com.javasultra.app.core.memory

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.time.Instant

/** Versioned binary codec. The entire encoded record is encrypted before persistence. */
object MemoryRecordCodec {
    private const val FORMAT_VERSION = 1
    private const val MAX_TAGS = 256

    fun encode(record: MemoryRecord): ByteArray {
        require(record.validate().isEmpty()) { "Invalid memory record" }
        val bytes = ByteArrayOutputStream()
        DataOutputStream(bytes).use { out ->
            out.writeInt(FORMAT_VERSION)
            out.writeUTF(record.id.value)
            out.writeUTF(record.ownerPrincipalId)
            out.writeUTF(record.kind.name)
            out.writeUTF(record.content)
            out.writeUTF(record.provenance.sourceType.name)
            out.writeNullable(record.provenance.sourceReference)
            out.writeLong(record.provenance.observedAt.toEpochMilli())
            out.writeNullable(record.provenance.publisherOrActor)
            out.writeNullable(record.provenance.notes)
            out.writeLong(record.createdAt.toEpochMilli())
            out.writeLong(record.updatedAt.toEpochMilli())
            out.writeDouble(record.confidence)
            out.writeUTF(record.verification.name)
            out.writeUTF(record.sensitivity.name)
            out.writeUTF(record.lifecycle.name)
            out.writeBoolean(record.expiresAt != null)
            record.expiresAt?.let { out.writeLong(it.toEpochMilli()) }
            out.writeNullable(record.supersedes?.value)
            require(record.tags.size <= MAX_TAGS) { "Too many memory tags" }
            out.writeInt(record.tags.size)
            record.tags.sorted().forEach(out::writeUTF)
        }
        return bytes.toByteArray()
    }

    fun decode(bytes: ByteArray): MemoryRecord {
        require(bytes.size <= 1_048_576) { "Memory record exceeds codec limit" }
        DataInputStream(ByteArrayInputStream(bytes)).use { input ->
            require(input.readInt() == FORMAT_VERSION) { "Unsupported memory record format" }
            val id = MemoryId(input.readUTF())
            val owner = input.readUTF()
            val kind = MemoryKind.valueOf(input.readUTF())
            val content = input.readUTF()
            val sourceType = MemorySourceType.valueOf(input.readUTF())
            val sourceReference = input.readNullable()
            val observedAt = Instant.ofEpochMilli(input.readLong())
            val publisher = input.readNullable()
            val notes = input.readNullable()
            val createdAt = Instant.ofEpochMilli(input.readLong())
            val updatedAt = Instant.ofEpochMilli(input.readLong())
            val confidence = input.readDouble()
            val verification = MemoryVerification.valueOf(input.readUTF())
            val sensitivity = MemorySensitivity.valueOf(input.readUTF())
            val lifecycle = MemoryLifecycle.valueOf(input.readUTF())
            val expiresAt = if (input.readBoolean()) Instant.ofEpochMilli(input.readLong()) else null
            val supersedes = input.readNullable()?.let(::MemoryId)
            val tagCount = input.readInt()
            require(tagCount in 0..MAX_TAGS) { "Invalid tag count" }
            val tags = buildSet { repeat(tagCount) { add(input.readUTF()) } }
            require(input.available() == 0) { "Unexpected trailing memory data" }
            return MemoryRecord(
                id, owner, kind, content,
                MemoryProvenance(sourceType, sourceReference, observedAt, publisher, notes),
                createdAt, updatedAt, confidence, verification, sensitivity, lifecycle,
                expiresAt, supersedes, tags
            ).also { require(it.validate().isEmpty()) { "Decoded memory record failed validation" } }
        }
    }

    private fun DataOutputStream.writeNullable(value: String?) {
        writeBoolean(value != null)
        if (value != null) writeUTF(value)
    }

    private fun DataInputStream.readNullable(): String? = if (readBoolean()) readUTF() else null
}
