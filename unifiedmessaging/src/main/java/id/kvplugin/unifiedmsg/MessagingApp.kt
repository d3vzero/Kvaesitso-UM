package id.kvplugin.unifiedmsg

/** Aplikasi chat yang didukung. */
enum class MessagingApp(val id: String, val label: String, val packages: List<String>) {
    WhatsApp(
        id = "wa",
        label = "WhatsApp",
        packages = listOf("com.whatsapp", "com.whatsapp.w4b"),
    ),
    Telegram(
        id = "tg",
        label = "Telegram",
        packages = listOf(
            "org.telegram.messenger",
            "org.telegram.messenger.web",
            "org.thunderdog.challegram",
        ),
    );

    companion object {
        fun fromPackage(pkg: String): MessagingApp? = entries.firstOrNull { pkg in it.packages }
        fun fromId(id: String): MessagingApp? = entries.firstOrNull { it.id == id }
    }
}
