package id.kvplugin.unifiedmsg

import android.content.Intent
import de.mm20.launcher2.plugin.config.QueryPluginConfig
import de.mm20.launcher2.plugin.config.StorageStrategy
import de.mm20.launcher2.sdk.PluginState
import de.mm20.launcher2.sdk.base.GetParams
import de.mm20.launcher2.sdk.base.SearchParams
import de.mm20.launcher2.sdk.contacts.Contact
import de.mm20.launcher2.sdk.contacts.ContactProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Plugin pencarian kontak Kvaesitso yang berisi chat WhatsApp + Telegram.
 * StoreReference: data ada di perangkat, jadi hasil yang di-pin bisa dipulihkan lewat get().
 */
class UnifiedMessagingPlugin : ContactProvider(
    QueryPluginConfig(storageStrategy = StorageStrategy.StoreReference)
) {

    override suspend fun getPluginState(): PluginState {
        val ctx = context ?: return PluginState.Ready()
        if (!Prefs.hasNotificationAccess(ctx)) {
            return PluginState.SetupRequired(
                setupActivity = Intent(ctx, SetupActivity::class.java),
                message = ctx.getString(R.string.um_setup_required_message),
            )
        }
        val count = withContext(Dispatchers.IO) { ChatStore.get(ctx).countChats() }
        return PluginState.Ready(ctx.getString(R.string.um_state_ready, count))
    }

    override suspend fun search(query: String, params: SearchParams): List<Contact> =
        withContext(Dispatchers.IO) {
            val ctx = context ?: return@withContext emptyList()
            val q = query.trim()
            if (q.length < 2) return@withContext emptyList()

            val results = mutableListOf<Contact>()

            // 1) "@username" -> Telegram
            if (q.startsWith("@")) {
                val u = q.drop(1)
                if (ContactFactory.isTelegramUsername(u)) {
                    results += ContactFactory.telegramUserContact(ctx, u)
                }
            }

            // 2) Nomor telepon -> chat langsung tanpa simpan kontak
            if (Phones.looksLikePhoneQuery(q)) {
                Phones.toInternationalDigits(q, Prefs.countryCode(ctx))?.let {
                    results += ContactFactory.numberContact(ctx, it)
                }
            }

            // 3) Chat yang terindeks, digabung per nama lintas aplikasi
            val nq = ChatStore.normalizeKey(q)
            ChatStore.get(ctx).search(nq)
                .groupBy { it.key }
                .values
                .sortedWith(
                    compareBy<List<ChatEntry>> { g -> if (g.first().key.startsWith(nq)) 0 else 1 }
                        .thenByDescending { g -> g.maxOf { it.updatedAt } }
                )
                .take(8)
                .forEach { results += ContactFactory.chatContact(ctx, it) }

            results
        }

    override suspend fun get(id: String, params: GetParams): Contact? =
        withContext(Dispatchers.IO) {
            val ctx = context ?: return@withContext null
            when {
                id.startsWith(ContactFactory.PREFIX_CHAT) -> {
                    val entries = ChatStore.get(ctx).byKey(id.removePrefix(ContactFactory.PREFIX_CHAT))
                    if (entries.isEmpty()) null else ContactFactory.chatContact(ctx, entries)
                }
                id.startsWith(ContactFactory.PREFIX_TG_USER) ->
                    ContactFactory.telegramUserContact(ctx, id.removePrefix(ContactFactory.PREFIX_TG_USER))
                id.startsWith(ContactFactory.PREFIX_NUMBER) ->
                    ContactFactory.numberContact(ctx, id.removePrefix(ContactFactory.PREFIX_NUMBER))
                else -> null
            }
        }
}
