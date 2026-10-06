package id.kvplugin.unifiedmsg

import android.content.Context
import android.net.Uri
import de.mm20.launcher2.sdk.contacts.Contact
import de.mm20.launcher2.search.contact.ContactInfoType
import de.mm20.launcher2.search.contact.CustomContactAction
import de.mm20.launcher2.search.contact.PhoneNumber

/** Mengubah data chat menjadi objek Contact untuk Kvaesitso. */
object ContactFactory {
    const val SCHEME = "kvmsg"

    const val PREFIX_CHAT = "chat:"
    const val PREFIX_TG_USER = "tguser:"
    const val PREFIX_NUMBER = "num:"

    private val tgUsernameRegex = Regex("^[A-Za-z][A-Za-z0-9_]{4,31}$")

    fun isTelegramUsername(u: String) = tgUsernameRegex.matches(u)

    /**
     * Satu hasil "unified": chat dengan nama yang sama di WhatsApp & Telegram digabung.
     * Ketuk = buka chat terbaru. Aksi tambahan = WhatsApp/Telegram via nomor (jika diketahui).
     */
    fun chatContact(ctx: Context, entries: List<ChatEntry>): Contact {
        val latest = entries.maxBy { it.updatedAt }
        val cc = Prefs.countryCode(ctx)
        val isGroup = entries.any { it.isGroup }

        val rawPhones = buildList {
            entries.mapNotNullTo(this) { it.phone }
            if (!isGroup) addAll(ContactLookup.phonesForName(ctx, latest.title))
        }
        val phones = rawPhones.mapNotNull { Phones.toInternationalDigits(it, cc) }.distinct()

        return Contact(
            id = PREFIX_CHAT + latest.key,
            uri = Uri.Builder().scheme(SCHEME).authority("open")
                .appendQueryParameter("key", latest.key).build(),
            name = latest.title,
            phoneNumbers = phones.map { PhoneNumber("+$it", ContactInfoType.Mobile) },
            customActions = phones.flatMap { messagingActions(ctx, it) },
        )
    }

    /** Hasil cepat untuk query "@username" -> buka profil Telegram. */
    fun telegramUserContact(ctx: Context, username: String): Contact {
        val uri = Uri.parse("tg://resolve?domain=$username")
        return Contact(
            id = PREFIX_TG_USER + username.lowercase(),
            uri = uri,
            name = ctx.getString(R.string.um_tg_username_name, "@$username"),
            customActions = listOf(
                CustomContactAction("Telegram @$username", uri, null, "org.telegram.messenger"),
                CustomContactAction("Telegram @$username", uri, null, "org.telegram.messenger.web"),
            ),
        )
    }

    /** Hasil cepat untuk query nomor telepon -> chat tanpa menyimpan kontak. */
    fun numberContact(ctx: Context, digits: String): Contact = Contact(
        id = PREFIX_NUMBER + digits,
        uri = Uri.Builder().scheme(SCHEME).authority("number")
            .appendQueryParameter("d", digits).build(),
        name = "+$digits",
        phoneNumbers = listOf(PhoneNumber("+$digits", ContactInfoType.Mobile)),
        customActions = messagingActions(ctx, digits),
    )

    /**
     * Kvaesitso menjalankan aksi dengan Intent(ACTION_VIEW).setPackage(pkg).setDataAndType(uri, mime),
     * dan otomatis menyembunyikan aksi yang aplikasinya tidak terpasang.
     */
    fun messagingActions(ctx: Context, digits: String): List<CustomContactAction> {
        val shown = "+$digits"
        val wa = Uri.parse("whatsapp://send?phone=$digits")
        val tg = Uri.parse("tg://resolve?phone=$digits")
        return listOf(
            CustomContactAction(ctx.getString(R.string.um_action_whatsapp, shown), wa, null, "com.whatsapp"),
            CustomContactAction(ctx.getString(R.string.um_action_whatsapp_business, shown), wa, null, "com.whatsapp.w4b"),
            CustomContactAction(ctx.getString(R.string.um_action_telegram, shown), tg, null, "org.telegram.messenger"),
            CustomContactAction(ctx.getString(R.string.um_action_telegram, shown), tg, null, "org.telegram.messenger.web"),
        )
    }
}
