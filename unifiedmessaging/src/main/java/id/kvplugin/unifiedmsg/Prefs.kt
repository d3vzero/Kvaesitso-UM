package id.kvplugin.unifiedmsg

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.provider.ContactsContract
import androidx.core.app.NotificationManagerCompat

object Prefs {
    private const val FILE = "um_settings"
    private const val KEY_CC = "country_code"
    const val DEFAULT_CC = "62"

    fun countryCode(ctx: Context): String =
        ctx.getSharedPreferences(FILE, Context.MODE_PRIVATE).getString(KEY_CC, DEFAULT_CC)
            ?.filter(Char::isDigit)?.ifEmpty { DEFAULT_CC } ?: DEFAULT_CC

    fun setCountryCode(ctx: Context, cc: String) {
        ctx.getSharedPreferences(FILE, Context.MODE_PRIVATE).edit()
            .putString(KEY_CC, cc.filter(Char::isDigit)).apply()
    }

    fun hasNotificationAccess(ctx: Context): Boolean =
        NotificationManagerCompat.getEnabledListenerPackages(ctx).contains(ctx.packageName)

    fun hasContactsAccess(ctx: Context): Boolean =
        ctx.checkSelfPermission(Manifest.permission.READ_CONTACTS) == PackageManager.PERMISSION_GRANTED
}

object Phones {
    /** "+62 812-3456" / "0812..." / "0062..." -> "62812..." (hanya digit, format internasional). */
    fun toInternationalDigits(raw: String, defaultCc: String): String? {
        val trimmed = raw.trim()
        val digits = trimmed.filter(Char::isDigit)
        if (digits.length < 7) return null
        return when {
            trimmed.startsWith("+") -> digits
            digits.startsWith("00") -> digits.drop(2)
            digits.startsWith("0") -> defaultCc + digits.drop(1)
            else -> digits
        }
    }

    fun looksLikePhoneQuery(q: String): Boolean =
        q.count(Char::isDigit) >= 7 && q.all { it.isDigit() || it in "+ -()" }
}

object ContactLookup {
    /** Nomor telepon dari kontak HP yang nama tampilnya sama persis dengan [name]. */
    fun phonesForName(ctx: Context, name: String): List<String> {
        if (!Prefs.hasContactsAccess(ctx)) return emptyList()
        return runCatching {
            ctx.contentResolver.query(
                ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                arrayOf(ContactsContract.CommonDataKinds.Phone.NUMBER),
                "${ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME} = ?",
                arrayOf(name),
                null
            )?.use { c ->
                buildList { while (c.moveToNext()) c.getString(0)?.let(::add) }
            }
        }.getOrNull() ?: emptyList()
    }
}
