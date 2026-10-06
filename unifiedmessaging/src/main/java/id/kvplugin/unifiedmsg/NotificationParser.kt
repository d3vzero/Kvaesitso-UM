package id.kvplugin.unifiedmsg

import android.app.Notification
import android.graphics.Bitmap
import android.os.Build
import androidx.core.app.NotificationCompat

/** Mengambil nama chat + pesan dari notifikasi WhatsApp/Telegram. */
object NotificationParser {

    data class Parsed(
        val title: String,
        val isGroup: Boolean,
        val phone: String?,
        val messages: List<ParsedMessage>,
        /** false kalau judul hanya tebakan (mis. notifikasi berisi balasan kita saja → "You"). */
        val titleReliable: Boolean = true,
    )

    private val SELF_NAMES = setOf("you", "anda", "kamu", "saya", "me", "aku")

    // "Grup Kantor (5 pesan)" / "Family (3 messages)"
    private val countSuffix = Regex("""\s*\(\d+\s+[^()]+\)\s*$""")

    fun parse(n: Notification, postTime: Long): Parsed? {
        if (n.category == Notification.CATEGORY_CALL) return null
        val extras = n.extras ?: return null
        val style = NotificationCompat.MessagingStyle.extractMessagingStyleFromNotification(n)

        if (style != null) {
            val isGroup = style.isGroupConversation
            val selfName = style.user.name?.toString()

            val messages = style.messages.mapNotNull { m ->
                val image = m.dataUri?.takeIf { m.dataMimeType?.startsWith("image/") == true }
                val rawText = m.text?.toString().orEmpty()
                val text = when {
                    rawText.isNotBlank() -> rawText
                    image != null -> PHOTO_PLACEHOLDER
                    else -> return@mapNotNull null
                }
                val name = m.person?.name?.toString()
                val outgoing = m.person == null || (name != null && name == selfName)
                ParsedMessage(
                    sender = if (outgoing) null else name,
                    text = text,
                    time = if (m.timestamp > 0) m.timestamp else postTime,
                    outgoing = outgoing,
                    imageUri = image?.toString(),
                )
            }
            val lastIncoming = style.messages.lastOrNull {
                val name = it.person?.name?.toString()
                name != null && name != selfName
            }
            val reliableTitle = style.conversationTitle?.toString()
                ?: lastIncoming?.person?.name?.toString()
            val title = reliableTitle
                ?: extras.getCharSequence(Notification.EXTRA_TITLE)?.toString()
                ?: return null
            val phone = if (!isGroup) {
                lastIncoming?.person?.uri?.takeIf { it.startsWith("tel:") }?.removePrefix("tel:")
            } else null
            val cleaned = clean(title)
            val reliable = reliableTitle != null &&
                cleaned.lowercase() !in SELF_NAMES &&
                cleaned != selfName
            return Parsed(cleaned, isGroup, phone, messages, reliable).takeIf { it.title.isNotBlank() }
        }

        if (n.category != Notification.CATEGORY_MESSAGE) return null
        val title = extras.getCharSequence(Notification.EXTRA_CONVERSATION_TITLE)
            ?: extras.getCharSequence(Notification.EXTRA_TITLE)
            ?: return null
        val isGroup = extras.getBoolean("android.isGroupConversation", false)
        val text = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString()
        @Suppress("DEPRECATION")
        val picture = extras.get(Notification.EXTRA_PICTURE) as? Bitmap
        val msgs = when {
            !text.isNullOrBlank() || picture != null -> listOf(
                ParsedMessage(null, text?.takeIf { it.isNotBlank() } ?: PHOTO_PLACEHOLDER, postTime, outgoing = false, picture = picture)
            )
            else -> emptyList()
        }
        return Parsed(clean(title.toString()), isGroup, null, msgs).takeIf { it.title.isNotBlank() }
    }

    /** Aksi "Balas" (RemoteInput) – bisa di actions biasa atau WearableExtender. */
    fun findReplyAction(n: Notification): Notification.Action? =
        allActions(n).firstOrNull { a -> a.remoteInputs?.any { it.allowFreeFormInput } == true }

    /** Aksi "Tandai dibaca". */
    fun findMarkReadAction(n: Notification): Notification.Action? =
        allActions(n).firstOrNull { a ->
            if (a.remoteInputs?.isNotEmpty() == true) return@firstOrNull false
            val semantic = Build.VERSION.SDK_INT >= Build.VERSION_CODES.P &&
                a.semanticAction == Notification.Action.SEMANTIC_ACTION_MARK_AS_READ
            val t = a.title?.toString()?.lowercase().orEmpty()
            semantic || "read" in t || "dibaca" in t || "baca" in t
        }

    private fun allActions(n: Notification): List<Notification.Action> =
        (n.actions?.toList() ?: emptyList()) +
            runCatching { Notification.WearableExtender(n).actions }.getOrDefault(emptyList())

    const val PHOTO_PLACEHOLDER = "📷"

    private fun clean(t: String) = t.replace(countSuffix, "").trim()
}
