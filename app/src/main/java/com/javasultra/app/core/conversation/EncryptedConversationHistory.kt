package com.javasultra.app.core.conversation

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import com.javasultra.app.core.memory.AndroidKeystoreMemoryEncryptor
import com.javasultra.app.core.memory.MemoryEncryptor
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.security.MessageDigest
import java.time.Instant

enum class ConversationSpeaker { CREATOR, JAVAS, SYSTEM }

data class ConversationMessage(
    val id: String,
    val ownerPrincipalId: String,
    val conversationId: String,
    val speaker: ConversationSpeaker,
    val text: String,
    val createdAt: Instant
)

enum class ConversationHistoryFailure {
    INVALID_MESSAGE, OWNER_MISMATCH, DUPLICATE_ID, NOT_FOUND, STORAGE_ERROR, DECRYPTION_FAILED
}

sealed interface ConversationHistoryResult<out T> {
    data class Success<T>(val value: T) : ConversationHistoryResult<T>
    data class Failure(val reason: ConversationHistoryFailure) : ConversationHistoryResult<Nothing>
}

interface ConversationHistory {
    fun append(message: ConversationMessage, actorPrincipalId: String): ConversationHistoryResult<Unit>
    fun read(
        conversationId: String,
        actorPrincipalId: String,
        limit: Int = 100
    ): ConversationHistoryResult<List<ConversationMessage>>
    fun deleteConversation(conversationId: String, actorPrincipalId: String): ConversationHistoryResult<Int>
}

/**
 * Owner-scoped conversation history. Message contents and identity fields are encrypted
 * with AES-GCM using a dedicated Android Keystore key. Only opaque hashes, message IDs,
 * and timestamps remain in SQLite indexes.
 */
class EncryptedConversationHistory(
    context: Context,
    private val encryptor: MemoryEncryptor = AndroidKeystoreMemoryEncryptor(KEY_ALIAS)
) : SQLiteOpenHelper(context.applicationContext, DATABASE_NAME, null, DATABASE_VERSION), ConversationHistory {

    override fun onConfigure(db: SQLiteDatabase) {
        super.onConfigure(db)
        db.execSQL("PRAGMA secure_delete = ON")
    }

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE $TABLE (" +
                "$ID TEXT PRIMARY KEY NOT NULL, " +
                "$OWNER_HASH TEXT NOT NULL, " +
                "$THREAD_HASH TEXT NOT NULL, " +
                "$PAYLOAD BLOB NOT NULL, " +
                "$CREATED INTEGER NOT NULL)"
        )
        db.execSQL("CREATE INDEX $THREAD_ORDER ON $TABLE ($OWNER_HASH, $THREAD_HASH, $CREATED ASC)")
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        if (oldVersion != DATABASE_VERSION) {
            throw IllegalStateException("Unsupported conversation history database version")
        }
    }

    override fun append(
        message: ConversationMessage,
        actorPrincipalId: String
    ): ConversationHistoryResult<Unit> {
        if (!ConversationHistoryPolicy.valid(message)) {
            return failure(ConversationHistoryFailure.INVALID_MESSAGE)
        }
        if (!ConversationHistoryAccessPolicy.canAccess(message.ownerPrincipalId, actorPrincipalId)) {
            return failure(ConversationHistoryFailure.OWNER_MISMATCH)
        }
        return try {
            val owner = hash(actorPrincipalId)
            val thread = hash(message.conversationId)
            val encrypted = encryptor.encrypt(
                ConversationMessageCodec.encode(message),
                aad(message.id, actorPrincipalId, message.conversationId)
            )
            val values = ContentValues().apply {
                put(ID, message.id)
                put(OWNER_HASH, owner)
                put(THREAD_HASH, thread)
                put(PAYLOAD, encrypted)
                put(CREATED, message.createdAt.toEpochMilli())
            }
            val db = writableDatabase
            db.beginTransaction()
            try {
                val existing = db.query(
                    TABLE, arrayOf(OWNER_HASH, THREAD_HASH, PAYLOAD, CREATED),
                    "$ID = ?", arrayOf(message.id), null, null, null
                ).use { cursor ->
                    if (!cursor.moveToFirst()) null else Existing(
                        cursor.getString(0), cursor.getString(1), cursor.getBlob(2), cursor.getLong(3)
                    )
                }
                if (existing != null) {
                    if (existing.ownerHash != owner || existing.threadHash != thread) {
                        return failure(ConversationHistoryFailure.OWNER_MISMATCH)
                    }
                    val clear = encryptor.decrypt(
                        existing.payload,
                        aad(message.id, actorPrincipalId, message.conversationId)
                    )
                    if (!clear.contentEquals(ConversationMessageCodec.encode(message)) ||
                        existing.createdAt != message.createdAt.toEpochMilli()
                    ) return failure(ConversationHistoryFailure.DUPLICATE_ID)
                } else {
                    db.insertOrThrow(TABLE, null, values)
                }
                db.setTransactionSuccessful()
                ConversationHistoryResult.Success(Unit)
            } finally {
                db.endTransaction()
            }
        } catch (_: Exception) {
            failure(ConversationHistoryFailure.STORAGE_ERROR)
        }
    }

    override fun read(
        conversationId: String,
        actorPrincipalId: String,
        limit: Int
    ): ConversationHistoryResult<List<ConversationMessage>> {
        if (!ConversationHistoryAccessPolicy.validReadRequest(conversationId, actorPrincipalId, limit)) {
            return failure(ConversationHistoryFailure.INVALID_MESSAGE)
        }
        return try {
            val messages = mutableListOf<ConversationMessage>()
            readableDatabase.query(
                TABLE, arrayOf(ID, PAYLOAD), "$OWNER_HASH = ? AND $THREAD_HASH = ?",
                arrayOf(hash(actorPrincipalId), hash(conversationId)),
                null, null, "$CREATED DESC", limit.toString()
            ).use { cursor ->
                while (cursor.moveToNext()) {
                    val id = cursor.getString(0)
                    val decoded = try {
                        ConversationMessageCodec.decode(
                            encryptor.decrypt(
                                cursor.getBlob(1),
                                aad(id, actorPrincipalId, conversationId)
                            )
                        )
                    } catch (_: Exception) {
                        return failure(ConversationHistoryFailure.DECRYPTION_FAILED)
                    }
                    if (decoded.ownerPrincipalId != actorPrincipalId ||
                        decoded.conversationId != conversationId || decoded.id != id
                    ) return failure(ConversationHistoryFailure.DECRYPTION_FAILED)
                    messages += decoded
                }
            }
            ConversationHistoryResult.Success(messages.sortedBy { it.createdAt })
        } catch (_: Exception) {
            failure(ConversationHistoryFailure.STORAGE_ERROR)
        }
    }

    override fun deleteConversation(
        conversationId: String,
        actorPrincipalId: String
    ): ConversationHistoryResult<Int> {
        if (!ConversationHistoryAccessPolicy.validDeleteRequest(conversationId, actorPrincipalId)) {
            return failure(ConversationHistoryFailure.INVALID_MESSAGE)
        }
        return try {
            val db = writableDatabase
            db.beginTransaction()
            try {
                val deleted = db.delete(
                    TABLE, "$OWNER_HASH = ? AND $THREAD_HASH = ?",
                    arrayOf(hash(actorPrincipalId), hash(conversationId))
                )
                db.setTransactionSuccessful()
                ConversationHistoryResult.Success(deleted)
            } finally {
                db.endTransaction()
            }
        } catch (_: Exception) {
            failure(ConversationHistoryFailure.STORAGE_ERROR)
        }
    }

    private fun hash(value: String): String =
        MessageDigest.getInstance("SHA-256").digest(value.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }

    private fun aad(id: String, owner: String, thread: String) =
        ("javas-conversation-v1:$id:$owner:$thread").toByteArray(Charsets.UTF_8)

    private fun failure(reason: ConversationHistoryFailure) =
        ConversationHistoryResult.Failure(reason)

    private data class Existing(
        val ownerHash: String,
        val threadHash: String,
        val payload: ByteArray,
        val createdAt: Long
    )

    private companion object {
        const val DATABASE_NAME = "javas_encrypted_conversations.db"
        const val DATABASE_VERSION = 1
        const val KEY_ALIAS = "javas.conversation.aesgcm.v1"
        const val TABLE = "conversation_messages"
        const val ID = "message_id"
        const val OWNER_HASH = "owner_hash"
        const val THREAD_HASH = "thread_hash"
        const val PAYLOAD = "encrypted_payload"
        const val CREATED = "created_at"
        const val THREAD_ORDER = "idx_conversation_owner_thread_created"
        const val MAX_READ_LIMIT = 200
    }
}

object ConversationHistoryPolicy {
    const val MAX_MESSAGE_CHARS = 16_000

    fun valid(message: ConversationMessage): Boolean =
        message.id.isNotBlank() && message.id.length <= 128 &&
            message.ownerPrincipalId.isNotBlank() && message.ownerPrincipalId.length <= 256 &&
            message.conversationId.isNotBlank() && message.conversationId.length <= 128 &&
            message.text.isNotBlank() && message.text.length <= MAX_MESSAGE_CHARS
}

object ConversationMessageCodec {
    private const val VERSION = 1
    private const val MAX_BYTES = 65_536

    fun encode(message: ConversationMessage): ByteArray {
        require(ConversationHistoryPolicy.valid(message))
        return ByteArrayOutputStream().also { bytes ->
            DataOutputStream(bytes).use { out ->
                out.writeInt(VERSION)
                out.writeUTF(message.id)
                out.writeUTF(message.ownerPrincipalId)
                out.writeUTF(message.conversationId)
                out.writeUTF(message.speaker.name)
                out.writeUTF(message.text)
                out.writeLong(message.createdAt.toEpochMilli())
            }
        }.toByteArray().also { require(it.size <= MAX_BYTES) }
    }

    fun decode(bytes: ByteArray): ConversationMessage {
        require(bytes.size in 1..MAX_BYTES)
        DataInputStream(ByteArrayInputStream(bytes)).use { input ->
            require(input.readInt() == VERSION)
            val result = ConversationMessage(
                input.readUTF(),
                input.readUTF(),
                input.readUTF(),
                ConversationSpeaker.valueOf(input.readUTF()),
                input.readUTF(),
                Instant.ofEpochMilli(input.readLong())
            )
            require(input.available() == 0 && ConversationHistoryPolicy.valid(result))
            return result
        }
    }
}


object ConversationHistoryAccessPolicy {
    fun canAccess(ownerPrincipalId: String, actorPrincipalId: String): Boolean =
        ownerPrincipalId.isNotBlank() && actorPrincipalId.isNotBlank() &&
            ownerPrincipalId == actorPrincipalId

    fun validReadRequest(conversationId: String, actorPrincipalId: String, limit: Int): Boolean =
        conversationId.isNotBlank() && actorPrincipalId.isNotBlank() && limit in 1..200

    fun validDeleteRequest(conversationId: String, actorPrincipalId: String): Boolean =
        conversationId.isNotBlank() && actorPrincipalId.isNotBlank()
}
