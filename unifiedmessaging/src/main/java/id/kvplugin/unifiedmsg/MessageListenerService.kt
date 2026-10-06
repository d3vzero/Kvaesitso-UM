package id.kvplugin.unifiedmsg

import android.app.Notification
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import java.util.concurrent.Executors

class MessageListenerService : NotificationListenerService() {

    private val io = Executors.newSingleThreadExecutor()

    override fun onListenerConnected() {
        instance = this
        val active = runCatching { activeNotifications }.getOrNull()
        io.execute {
            MediaCache.prune(this)
            active?.forEach { handlePosted(it) }
            ChatEvents.bump()
        }
    }

    override fun onListenerDisconnected() {
        instance = null
    }

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        if (MessagingApp.fromPackage(sbn.packageName) == null) return
        io.execute {
            handlePosted(sbn)
            ChatEvents.bump()
        }
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification) {
        if (MessagingApp.fromPackage(sbn.packageName) == null) return
        io.execute {
            // Notifikasi hilang = sudah dibaca di aplikasi / di-dismiss
            val (app, key) = ActiveChats.markGone(sbn.key) ?: return@execute
            ChatStore.get(this).markRead(app, key)
            ChatEvents.bump()
        }
    }

    override fun onDestroy() {
        instance = null
        io.shutdown()
        super.onDestroy()
    }

    private fun handlePosted(sbn: StatusBarNotification) {
        try {
            val app = MessagingApp.fromPackage(sbn.packageName) ?: return
            val n = sbn.notification ?: return
            if (n.flags and Notification.FLAG_GROUP_SUMMARY != 0) return

            var parsed = NotificationParser.parse(n, sbn.postTime) ?: return
            if (parsed.title.equals(app.label, ignoreCase = true)) return

            val store = ChatStore.get(this)
            if (!parsed.titleReliable) {
                // Mis. WhatsApp memperbarui notifikasi yang isinya hanya balasan kita ("You").
                // Pakai chat yang sudah tercatat untuk notifikasi ini; kalau tidak ada, abaikan.
                val (knownApp, knownKey) = ActiveChats.chatForSbn(sbn.key) ?: return
                val known = store.get(knownApp, knownKey) ?: return
                parsed = parsed.copy(title = known.title, isGroup = known.isGroup)
            }
            val key = ChatStore.normalizeKey(parsed.title)
            if (key.isEmpty()) return

            // Simpan foto dari notifikasi SEKARANG – izin akses URI-nya hilang bersama notifikasi
            val messages = parsed.messages.map { m ->
                if (m.imageUri != null || m.picture != null) {
                    m.copy(imagePath = MediaCache.save(this, app, key, m), picture = null)
                } else m
            }

            store.insertMessages(app, key, messages)

            val last = messages.lastOrNull()
            store.upsertChat(
                ChatEntry(
                    app = app,
                    key = key,
                    title = parsed.title,
                    isGroup = parsed.isGroup,
                    phone = parsed.phone,
                    sourcePackage = sbn.packageName,
                    updatedAt = maxOf(sbn.postTime, last?.time ?: 0L),
                    lastMessage = last?.text,
                    lastSender = last?.sender,
                    lastOutgoing = last?.outgoing ?: false,
                    unread = messages.count { !it.outgoing },
                )
            )

            n.contentIntent?.let { PendingIntentCache.put(app, key, it) }
            ActiveChats.put(
                app, key,
                ActiveChat(
                    sbnKey = sbn.key,
                    reply = NotificationParser.findReplyAction(n),
                    markRead = NotificationParser.findMarkReadAction(n),
                )
            )
        } catch (t: Throwable) {
            Log.w("KvUnifiedMsg", "Gagal memproses notifikasi", t)
        }
    }

    companion object {
        @Volatile
        private var instance: MessageListenerService? = null

        val isConnected: Boolean get() = instance != null

        fun cancel(sbnKey: String) {
            runCatching { instance?.cancelNotification(sbnKey) }
        }

        /** Cari langsung di notifikasi yang sedang tampil (tidak bergantung cache memori). */
        fun findActive(app: MessagingApp, key: String): StatusBarNotification? {
            val svc = instance ?: return null
            val list = runCatching { svc.activeNotifications }.getOrNull() ?: return null
            return list
                .filter { sbn ->
                    MessagingApp.fromPackage(sbn.packageName) == app &&
                        sbn.notification.flags and Notification.FLAG_GROUP_SUMMARY == 0 &&
                        NotificationParser.parse(sbn.notification, sbn.postTime)
                            ?.let { ChatStore.normalizeKey(it.title) == key } == true
                }
                .maxByOrNull { it.postTime }
        }
    }
}
