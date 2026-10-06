package id.kvplugin.unifiedmsg

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.util.Log
import java.io.File
import java.security.MessageDigest

/** Salinan lokal foto dari notifikasi (storage privat aplikasi, maks. 30 hari). */
object MediaCache {
    private const val MAX_BYTES = 15L * 1024 * 1024
    private const val MAX_AGE_MS = 30L * 24 * 60 * 60 * 1000

    private fun dir(ctx: Context) = File(ctx.filesDir, "um_images").apply { mkdirs() }

    private fun sha1(s: String): String =
        MessageDigest.getInstance("SHA-1").digest(s.toByteArray())
            .joinToString("") { "%02x".format(it) }

    /** Kembalikan path file lokal, atau null kalau gambar tidak bisa dibaca. */
    fun save(ctx: Context, app: MessagingApp, key: String, m: ParsedMessage): String? {
        val file = File(dir(ctx), sha1("${app.id}|$key|${m.time}|${m.text}") + ".img")
        if (file.exists() && file.length() > 0) return file.absolutePath

        m.imageUri?.let { uri ->
            try {
                ctx.contentResolver.openInputStream(Uri.parse(uri))?.use { input ->
                    file.outputStream().use { out ->
                        val buf = ByteArray(64 * 1024)
                        var total = 0L
                        while (true) {
                            val n = input.read(buf)
                            if (n < 0) break
                            total += n
                            if (total > MAX_BYTES) throw IllegalStateException("gambar terlalu besar")
                            out.write(buf, 0, n)
                        }
                    }
                }
                if (file.length() > 0) return file.absolutePath
            } catch (t: Throwable) {
                Log.w("KvUnifiedMsg", "Tidak bisa membaca gambar notifikasi: $uri", t)
            }
            file.delete()
        }

        m.picture?.let { bmp ->
            runCatching {
                file.outputStream().use { bmp.compress(Bitmap.CompressFormat.JPEG, 90, it) }
            }
            if (file.length() > 0) return file.absolutePath
            file.delete()
        }
        return null
    }

    fun prune(ctx: Context) {
        val cutoff = System.currentTimeMillis() - MAX_AGE_MS
        dir(ctx).listFiles()?.forEach { if (it.lastModified() < cutoff) it.delete() }
    }

    fun delete(paths: List<String>) {
        paths.forEach { runCatching { File(it).delete() } }
    }

    fun clear(ctx: Context) {
        dir(ctx).listFiles()?.forEach { it.delete() }
    }
}
