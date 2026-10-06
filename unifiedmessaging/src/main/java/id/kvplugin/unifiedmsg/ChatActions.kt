package id.kvplugin.unifiedmsg

import android.app.ActivityOptions
import android.app.PendingIntent
import android.app.RemoteInput
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle

enum class ReplyStatus {
    Available,       // notifikasi ada, bisa balas
    Stale,           // notifikasi sudah hilang, tapi aksi balas terakhir masih bisa dicoba
    ListenerOff,
    NoNotification,
    NoReplyAction,
}

/** Aksi pada chat: balas langsung, tandai dibaca, buka di aplikasi. */
object ChatActions {

    /** Ambil aksi dari notifikasi yang tampil; kalau sudah hilang, pakai aksi terakhir yang disimpan. */
    private fun liveActive(app: MessagingApp, key: String): ActiveChat? {
        val cached = ActiveChats.get(app, key)
        if (cached != null && cached.live && cached.reply != null) return cached
        val sbn = MessageListenerService.findActive(app, key)
        if (sbn != null) {
            ActiveChats.put(
                app, key,
                ActiveChat(
                    sbnKey = sbn.key,
                    reply = NotificationParser.findReplyAction(sbn.notification),
                    markRead = NotificationParser.findMarkReadAction(sbn.notification),
                    live = true,
                )
            )
            return ActiveChats.get(app, key)
        }
        return cached
    }

    /** Panggil dari background thread. */
    fun replyStatus(app: MessagingApp, key: String): ReplyStatus {
        val a = liveActive(app, key)
        return when {
            a?.reply != null && a.live -> ReplyStatus.Available
            a?.reply != null -> ReplyStatus.Stale
            !MessageListenerService.isConnected -> ReplyStatus.ListenerOff
            a != null && a.live -> ReplyStatus.NoReplyAction
            else -> ReplyStatus.NoNotification
        }
    }

    /** Balas lewat RemoteInput notifikasi – tanpa membuka aplikasinya. */
    fun reply(ctx: Context, app: MessagingApp, key: String, text: String): Boolean {
        val action = liveActive(app, key)?.reply ?: return false
        val inputs = action.remoteInputs ?: return false
        val intent = Intent().addFlags(Intent.FLAG_RECEIVER_FOREGROUND)
        val results = Bundle()
        inputs.filter { it.allowFreeFormInput }.forEach { results.putCharSequence(it.resultKey, text) }
        RemoteInput.addResultsToIntent(inputs, intent, results)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            RemoteInput.setResultsSource(intent, RemoteInput.SOURCE_FREE_FORM_INPUT)
        }
        return try {
            action.actionIntent.send(ctx, 0, intent)
            ChatStore.get(ctx).insertLocalOutgoing(app, key, text)
            ChatEvents.bump()
            true
        } catch (e: PendingIntent.CanceledException) {
            ActiveChats.forgetReply(app, key)
            ChatEvents.bump()
            false
        }
    }

    /** Jenis konten non-teks yang diterima aksi Balas (mis. tipe gambar seperti image/png). Kosong = teks saja. */
    fun replyDataTypes(app: MessagingApp, key: String): List<String> =
        liveActive(app, key)?.reply?.remoteInputs
            ?.flatMap { it.allowedDataTypes ?: emptySet() }
            ?.distinct()
            ?: emptyList()

    /** Hapus riwayat satu percakapan dari panel (tidak menyentuh chat di WhatsApp/Telegram). */
    fun deleteChat(ctx: Context, app: MessagingApp, key: String) {
        val images = ChatStore.get(ctx).deleteChat(app, key)
        MediaCache.delete(images)
        ActiveChats.remove(app, key)
        PendingIntentCache.remove(app, key)
        ChatEvents.bump()
    }

    fun markRead(ctx: Context, app: MessagingApp, key: String) {
        val active = liveActive(app, key)
        active?.markRead?.actionIntent?.let { pi ->
            runCatching { pi.send(ctx, 0, Intent()) }
        }
        active?.sbnKey?.let { MessageListenerService.cancel(it) }
        ChatStore.get(ctx).markRead(app, key)
        ChatEvents.bump()
    }

    /** Buka chat tertentu di aplikasinya. */
    fun openChat(ctx: Context, app: MessagingApp, key: String): Boolean {
        PendingIntentCache.get(app, key)?.let { pi ->
            if (sendActivityIntent(ctx, pi)) return true
            PendingIntentCache.remove(app, key)
        }
        val chat = ChatStore.get(ctx).get(app, key) ?: return false
        val digits = (chat.phone
            ?: if (!chat.isGroup) ContactLookup.phonesForName(ctx, chat.title).firstOrNull() else null)
            ?.let { Phones.toInternationalDigits(it, Prefs.countryCode(ctx)) }
        if (digits != null && openWithPhone(ctx, app, digits, chat.sourcePackage)) return true
        val launch = ctx.packageManager.getLaunchIntentForPackage(chat.sourcePackage) ?: return false
        return tryStart(ctx, launch)
    }

    /** Untuk hasil pencarian gabungan: coba aplikasi yang paling baru dulu. */
    fun openChatAnyApp(ctx: Context, key: String): Boolean =
        ChatStore.get(ctx).byKey(key).any { openChat(ctx, it.app, key) }

    fun openNumber(ctx: Context, digits: String): Boolean = listOf(
        MessagingApp.WhatsApp to "com.whatsapp",
        MessagingApp.WhatsApp to "com.whatsapp.w4b",
        MessagingApp.Telegram to "org.telegram.messenger",
        MessagingApp.Telegram to "org.telegram.messenger.web",
    ).any { (app, pkg) -> openWithPhone(ctx, app, digits, pkg) }

    private fun openWithPhone(ctx: Context, app: MessagingApp, digits: String, pkg: String): Boolean {
        val uri = when (app) {
            MessagingApp.WhatsApp -> Uri.parse("whatsapp://send?phone=$digits")
            MessagingApp.Telegram -> Uri.parse("tg://resolve?phone=$digits")
        }
        return tryStart(ctx, Intent(Intent.ACTION_VIEW, uri).setPackage(pkg))
    }

    private fun tryStart(ctx: Context, intent: Intent): Boolean = try {
        ctx.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        true
    } catch (e: ActivityNotFoundException) {
        false
    } catch (e: SecurityException) {
        false
    }

    @Suppress("DEPRECATION")
    private fun sendActivityIntent(ctx: Context, pi: PendingIntent): Boolean = try {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            val opts = ActivityOptions.makeBasic().setPendingIntentBackgroundActivityStartMode(
                ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOWED
            )
            pi.send(ctx, 0, null, null, null, null, opts.toBundle())
        } else {
            pi.send()
        }
        true
    } catch (e: PendingIntent.CanceledException) {
        false
    }
}
