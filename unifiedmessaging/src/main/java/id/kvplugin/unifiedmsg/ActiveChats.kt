package id.kvplugin.unifiedmsg

import android.app.Notification
import android.app.PendingIntent

/**
 * Aksi dari notifikasi chat (balas, tandai dibaca). Hanya di memori.
 * Setelah notifikasi hilang, aksinya TETAP disimpan (live = false) supaya masih bisa
 * dicoba untuk membalas lagi – Telegram misalnya menghapus notifikasi setelah dibalas.
 */
data class ActiveChat(
    val sbnKey: String,
    val reply: Notification.Action?,
    val markRead: Notification.Action?,
    val live: Boolean = true,
)

object ActiveChats {
    private const val MAX = 300

    private val byChat = object : LinkedHashMap<String, ActiveChat>(64, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, ActiveChat>?) =
            size > MAX
    }
    private val bySbn = HashMap<String, String>()

    private fun id(app: MessagingApp, key: String) = "${app.id}|$key"

    @Synchronized
    fun put(app: MessagingApp, key: String, a: ActiveChat) {
        val cid = id(app, key)
        val old = byChat[cid]
        if (old != null && old.sbnKey != a.sbnKey) bySbn.remove(old.sbnKey)
        byChat[cid] = a.copy(
            reply = a.reply ?: old?.reply,
            markRead = a.markRead ?: old?.markRead,
        )
        bySbn[a.sbnKey] = cid
    }

    @Synchronized
    fun get(app: MessagingApp, key: String): ActiveChat? = byChat[id(app, key)]

    /** Notifikasi hilang: tandai tidak live, tapi aksinya tetap disimpan. */
    @Synchronized
    fun markGone(sbnKey: String): Pair<MessagingApp, String>? {
        val cid = bySbn.remove(sbnKey) ?: return null
        byChat[cid]?.let { byChat[cid] = it.copy(live = false) }
        val parts = cid.split("|", limit = 2)
        val app = MessagingApp.fromId(parts[0]) ?: return null
        return app to parts[1]
    }

    /** Chat yang terhubung ke notifikasi ini (jika pernah tercatat). */
    @Synchronized
    fun chatForSbn(sbnKey: String): Pair<MessagingApp, String>? {
        val cid = bySbn[sbnKey] ?: return null
        val parts = cid.split("|", limit = 2)
        val app = MessagingApp.fromId(parts[0]) ?: return null
        return app to parts[1]
    }

    @Synchronized
    fun remove(app: MessagingApp, key: String) {
        byChat.remove(id(app, key))?.let { bySbn.remove(it.sbnKey) }
    }

    /** Aksi balas sudah tidak valid (PendingIntent dibatalkan aplikasinya). */
    @Synchronized
    fun forgetReply(app: MessagingApp, key: String) {
        val cid = id(app, key)
        byChat[cid]?.let { byChat[cid] = it.copy(reply = null) }
    }

    @Synchronized
    fun clear() {
        byChat.clear(); bySbn.clear()
    }
}

/** contentIntent terakhir per chat (tetap disimpan walau notifikasi sudah hilang). */
object PendingIntentCache {
    private const val MAX = 300

    private val map = object : LinkedHashMap<String, PendingIntent>(64, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, PendingIntent>?) =
            size > MAX
    }

    private fun k(app: MessagingApp, key: String) = "${app.id}|$key"

    @Synchronized
    fun put(app: MessagingApp, key: String, pi: PendingIntent) {
        map[k(app, key)] = pi
    }

    @Synchronized
    fun get(app: MessagingApp, key: String): PendingIntent? = map[k(app, key)]

    @Synchronized
    fun remove(app: MessagingApp, key: String) {
        map.remove(k(app, key))
    }

    @Synchronized
    fun clear() = map.clear()
}
