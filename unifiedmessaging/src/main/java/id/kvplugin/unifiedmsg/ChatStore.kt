package id.kvplugin.unifiedmsg

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import android.graphics.Bitmap
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update

data class ChatEntry(
    val app: MessagingApp,
    val key: String,          // judul dinormalisasi (lowercase)
    val title: String,
    val isGroup: Boolean,
    val phone: String?,
    val sourcePackage: String,
    val updatedAt: Long,
    val lastMessage: String? = null,
    val lastSender: String? = null,
    val lastOutgoing: Boolean = false,
    val unread: Int = 0,
)

data class MessageEntry(
    val id: Long,
    val sender: String?,
    val text: String,
    val time: Long,
    val outgoing: Boolean,
    val imagePath: String? = null,
)

data class ParsedMessage(
    val sender: String?,
    val text: String,
    val time: Long,
    val outgoing: Boolean,
    val imageUri: String? = null,     // URI gambar di notifikasi (sementara)
    val picture: Bitmap? = null,      // gambar BigPictureStyle (sementara)
    val imagePath: String? = null,    // salinan lokal
)

/** Sinyal perubahan data untuk UI (satu proses, cukup StateFlow). */
object ChatEvents {
    private val _version = MutableStateFlow(0L)
    val version: StateFlow<Long> = _version
    fun bump() = _version.update { it + 1 }
}

/**
 * Penyimpanan lokal (hanya di storage privat aplikasi, allowBackup=false).
 * Pesan disimpan maks. 300 per chat dan 30 hari.
 */
class ChatStore private constructor(context: Context) :
    SQLiteOpenHelper(context.applicationContext, "um_chats.db", null, 3) {

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE chats(
                app TEXT NOT NULL,
                chat_key TEXT NOT NULL,
                title TEXT NOT NULL,
                is_group INTEGER NOT NULL DEFAULT 0,
                phone TEXT,
                source_pkg TEXT NOT NULL,
                updated_at INTEGER NOT NULL,
                last_message TEXT,
                last_sender TEXT,
                last_outgoing INTEGER NOT NULL DEFAULT 0,
                unread INTEGER NOT NULL DEFAULT 0,
                PRIMARY KEY(app, chat_key)
            )
            """.trimIndent()
        )
        db.execSQL("CREATE INDEX idx_chats_key ON chats(chat_key)")
        db.execSQL(
            """
            CREATE TABLE messages(
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                app TEXT NOT NULL,
                chat_key TEXT NOT NULL,
                sender TEXT,
                text TEXT NOT NULL,
                time INTEGER NOT NULL,
                outgoing INTEGER NOT NULL DEFAULT 0,
                image_path TEXT,
                UNIQUE(app, chat_key, time, text)
            )
            """.trimIndent()
        )
        db.execSQL("CREATE INDEX idx_msg_chat ON messages(app, chat_key, time)")
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        if (oldVersion < 2) {
            db.execSQL("DROP TABLE IF EXISTS chats")
            db.execSQL("DROP TABLE IF EXISTS messages")
            onCreate(db)
            return
        }
        if (oldVersion < 3) {
            db.execSQL("ALTER TABLE messages ADD COLUMN image_path TEXT")
        }
    }

    // ---------- chats ----------

    fun upsertChat(e: ChatEntry) {
        val db = writableDatabase
        db.beginTransaction()
        try {
            val oldPhone = db.rawQuery(
                "SELECT phone FROM chats WHERE app = ? AND chat_key = ?",
                arrayOf(e.app.id, e.key)
            ).use { c -> if (c.moveToFirst()) c.getString(0) else null }

            val values = ContentValues().apply {
                put("app", e.app.id)
                put("chat_key", e.key)
                put("title", e.title)
                put("is_group", if (e.isGroup) 1 else 0)
                put("phone", e.phone ?: oldPhone)
                put("source_pkg", e.sourcePackage)
                put("updated_at", e.updatedAt)
                put("last_message", e.lastMessage)
                put("last_sender", e.lastSender)
                put("last_outgoing", if (e.lastOutgoing) 1 else 0)
                put("unread", e.unread)
            }
            db.insertWithOnConflict("chats", null, values, SQLiteDatabase.CONFLICT_REPLACE)
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    fun markRead(app: MessagingApp, key: String) {
        writableDatabase.execSQL(
            "UPDATE chats SET unread = 0 WHERE app = ? AND chat_key = ?",
            arrayOf(app.id, key)
        )
    }

    fun allChats(limit: Int = 300): List<ChatEntry> =
        readableDatabase.rawQuery(
            "SELECT * FROM chats ORDER BY updated_at DESC LIMIT $limit", null
        ).use { it.toChats() }

    fun search(normalizedQuery: String, limit: Int = 60): List<ChatEntry> {
        val escaped = normalizedQuery
            .replace("\\", "\\\\")
            .replace("%", "\\%")
            .replace("_", "\\_")
        return readableDatabase.rawQuery(
            "SELECT * FROM chats WHERE chat_key LIKE ? ESCAPE '\\' ORDER BY updated_at DESC LIMIT $limit",
            arrayOf("%$escaped%")
        ).use { it.toChats() }
    }

    fun byKey(key: String): List<ChatEntry> =
        readableDatabase.rawQuery(
            "SELECT * FROM chats WHERE chat_key = ? ORDER BY updated_at DESC",
            arrayOf(key)
        ).use { it.toChats() }

    fun get(app: MessagingApp, key: String): ChatEntry? =
        readableDatabase.rawQuery(
            "SELECT * FROM chats WHERE app = ? AND chat_key = ?",
            arrayOf(app.id, key)
        ).use { it.toChats().firstOrNull() }

    fun countChats(): Int =
        readableDatabase.rawQuery("SELECT COUNT(DISTINCT chat_key) FROM chats", null)
            .use { if (it.moveToFirst()) it.getInt(0) else 0 }

    // ---------- messages ----------

    /** Simpan pesan dari notifikasi; duplikat (notifikasi di-update ulang) diabaikan. */
    fun insertMessages(app: MessagingApp, key: String, msgs: List<ParsedMessage>) {
        if (msgs.isEmpty()) return
        val db = writableDatabase
        db.beginTransaction()
        try {
            for (m in msgs) {
                if (m.outgoing && hasRecentOutgoing(db, app, key, m.text, m.time)) continue
                val v = ContentValues().apply {
                    put("app", app.id)
                    put("chat_key", key)
                    put("sender", m.sender)
                    put("text", m.text)
                    put("time", m.time)
                    put("outgoing", if (m.outgoing) 1 else 0)
                    put("image_path", m.imagePath)
                }
                val id = db.insertWithOnConflict("messages", null, v, SQLiteDatabase.CONFLICT_IGNORE)
                if (id == -1L && m.imagePath != null) {
                    db.execSQL(
                        "UPDATE messages SET image_path = ? WHERE app = ? AND chat_key = ? AND time = ? AND text = ? AND image_path IS NULL",
                        arrayOf<Any>(m.imagePath, app.id, key, m.time, m.text)
                    )
                }
            }
            prune(db, app, key)
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    /** Balasan yang dikirim dari panel kita (ditampilkan langsung). */
    fun insertLocalOutgoing(app: MessagingApp, key: String, text: String) {
        val now = System.currentTimeMillis()
        insertMessages(app, key, listOf(ParsedMessage(null, text, now, outgoing = true)))
        writableDatabase.execSQL(
            "UPDATE chats SET last_message = ?, last_sender = NULL, last_outgoing = 1, updated_at = ?, unread = 0 WHERE app = ? AND chat_key = ?",
            arrayOf<Any>(text, now, app.id, key)
        )
    }

    fun messages(app: MessagingApp, key: String, limit: Int = 300): List<MessageEntry> =
        readableDatabase.rawQuery(
            "SELECT id, sender, text, time, outgoing, image_path FROM messages WHERE app = ? AND chat_key = ? ORDER BY time DESC LIMIT $limit",
            arrayOf(app.id, key)
        ).use { c ->
            val out = ArrayList<MessageEntry>(c.count)
            while (c.moveToNext()) {
                out += MessageEntry(
                    id = c.getLong(0),
                    sender = c.getString(1),
                    text = c.getString(2),
                    time = c.getLong(3),
                    outgoing = c.getInt(4) != 0,
                    imagePath = c.getString(5),
                )
            }
            out.reverse() // lama -> baru
            out
        }

    /** Hapus satu percakapan beserta pesannya. Mengembalikan path foto yang perlu dihapus. */
    fun deleteChat(app: MessagingApp, key: String): List<String> {
        val db = writableDatabase
        val images = db.rawQuery(
            "SELECT image_path FROM messages WHERE app = ? AND chat_key = ? AND image_path IS NOT NULL",
            arrayOf(app.id, key)
        ).use { c -> buildList { while (c.moveToNext()) add(c.getString(0)) } }
        db.beginTransaction()
        try {
            db.delete("messages", "app = ? AND chat_key = ?", arrayOf(app.id, key))
            db.delete("chats", "app = ? AND chat_key = ?", arrayOf(app.id, key))
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
        return images
    }

    fun clear() {
        writableDatabase.delete("chats", null, null)
        writableDatabase.delete("messages", null, null)
    }

    private fun hasRecentOutgoing(db: SQLiteDatabase, app: MessagingApp, key: String, text: String, time: Long): Boolean =
        db.rawQuery(
            "SELECT 1 FROM messages WHERE app = ? AND chat_key = ? AND outgoing = 1 AND text = ? AND ABS(time - ?) < 180000 LIMIT 1",
            arrayOf(app.id, key, text, time.toString())
        ).use { it.moveToFirst() }

    private fun prune(db: SQLiteDatabase, app: MessagingApp, key: String) {
        val cutoff = System.currentTimeMillis() - 30L * 24 * 60 * 60 * 1000
        db.execSQL("DELETE FROM messages WHERE time < ?", arrayOf(cutoff))
        db.execSQL(
            """
            DELETE FROM messages WHERE id IN (
                SELECT id FROM messages WHERE app = ? AND chat_key = ?
                ORDER BY time DESC LIMIT -1 OFFSET 300
            )
            """.trimIndent(),
            arrayOf(app.id, key)
        )
    }

    private fun Cursor.toChats(): List<ChatEntry> {
        val out = ArrayList<ChatEntry>(count)
        while (moveToNext()) {
            val app = MessagingApp.fromId(getString(getColumnIndexOrThrow("app"))) ?: continue
            out += ChatEntry(
                app = app,
                key = getString(getColumnIndexOrThrow("chat_key")),
                title = getString(getColumnIndexOrThrow("title")),
                isGroup = getInt(getColumnIndexOrThrow("is_group")) != 0,
                phone = getString(getColumnIndexOrThrow("phone")),
                sourcePackage = getString(getColumnIndexOrThrow("source_pkg")),
                updatedAt = getLong(getColumnIndexOrThrow("updated_at")),
                lastMessage = getString(getColumnIndexOrThrow("last_message")),
                lastSender = getString(getColumnIndexOrThrow("last_sender")),
                lastOutgoing = getInt(getColumnIndexOrThrow("last_outgoing")) != 0,
                unread = getInt(getColumnIndexOrThrow("unread")),
            )
        }
        return out
    }

    companion object {
        @Volatile
        private var instance: ChatStore? = null

        fun get(context: Context): ChatStore =
            instance ?: synchronized(this) {
                instance ?: ChatStore(context).also { instance = it }
            }

        fun normalizeKey(title: String): String =
            title.trim().lowercase().replace(Regex("\\s+"), " ")
    }
}
