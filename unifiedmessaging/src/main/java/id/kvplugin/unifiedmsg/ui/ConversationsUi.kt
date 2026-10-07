package id.kvplugin.unifiedmsg.ui

import android.content.Intent
import android.graphics.BitmapFactory
import android.text.format.DateFormat
import android.text.format.DateUtils
import android.widget.Toast
import androidx.annotation.StringRes
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.drawable.toBitmap
import id.kvplugin.unifiedmsg.ChatActions
import id.kvplugin.unifiedmsg.ChatEntry
import id.kvplugin.unifiedmsg.ChatEvents
import id.kvplugin.unifiedmsg.ChatStore
import id.kvplugin.unifiedmsg.MessageEntry
import id.kvplugin.unifiedmsg.MessagingApp
import id.kvplugin.unifiedmsg.NotificationParser
import id.kvplugin.unifiedmsg.Prefs
import id.kvplugin.unifiedmsg.R
import id.kvplugin.unifiedmsg.ReplyStatus
import id.kvplugin.unifiedmsg.SetupActivity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Date

// ---------- state & host ----------

data class ChatRef(val app: MessagingApp, val key: String)

enum class ChatFilter(@StringRes val label: Int) {
    Unread(R.string.um_filter_unread),
    Individual(R.string.um_filter_individual),
    Group(R.string.um_filter_group),
    WhatsApp(R.string.um_filter_whatsapp),
    Telegram(R.string.um_filter_telegram),
}

/** Lembar aksi di bagian bawah panel (bukan Dialog, karena panel bisa berjalan di jendela overlay). */
sealed interface PanelSheet {
    data class ChatMenu(val ref: ChatRef, val title: String, val unread: Boolean) : PanelSheet
    data class ConfirmDelete(val ref: ChatRef, val title: String) : PanelSheet
    data class ReplyInfo(val ref: ChatRef, val title: String) : PanelSheet
}

class ConversationsState {
    var selected by mutableStateOf<ChatRef?>(null)
    var sheet by mutableStateOf<PanelSheet?>(null)
    var viewingImage by mutableStateOf<String?>(null)
    var query by mutableStateOf("")
    var filter by mutableStateOf<ChatFilter?>(null)
}

/** Diimplementasikan oleh overlay (feed Kvaesitso) dan activity biasa. */
interface PanelHost {
    val state: ConversationsState
    fun close()
    fun launch(intent: Intent)
}

// ---------- gaya monokrom (cocok dengan tema Kvaesitso hitam-putih) ----------

private val WidgetBg = Color(0xFF141414)   // sama dengan kartu widget Kvaesitso, tidak transparan
private val AvatarBg = Color(0xFF2A2A2A)
private val Bg = Color.Transparent // wallpaper tetap terlihat; kartu chat sudah punya latar sendiri
private val CardBg = Color(0x14FFFFFF)
private val Outline = Color(0x26FFFFFF)
private val Dim = Color(0x99FFFFFF)
private val White = Color.White
private val Black = Color.Black

@Composable
fun ConversationsApp(host: PanelHost) {
    val ctx = LocalContext.current
    val version by ChatEvents.version.collectAsState()
    var chats by remember { mutableStateOf<List<ChatEntry>>(emptyList()) }
    LaunchedEffect(version) {
        chats = withContext(Dispatchers.IO) { ChatStore.get(ctx).allChats() }
    }
    val hasAccess = remember(version) { Prefs.hasNotificationAccess(ctx) }

    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = White, onPrimary = Black,
            background = Black, surface = Black, onSurface = White,
        )
    ) {
        Box(Modifier.fillMaxSize().background(Bg)) {
            val sel = host.state.selected
            if (sel == null) {
                ChatListScreen(host, chats, hasAccess)
            } else {
                ChatDetailScreen(
                    host = host,
                    ref = sel,
                    chat = chats.firstOrNull { it.app == sel.app && it.key == sel.key },
                    version = version,
                )
            }
            host.state.viewingImage?.let { path ->
                ImageViewer(path) { host.state.viewingImage = null }
            }
            host.state.sheet?.let { sheet -> SheetHost(host, sheet) }
        }
    }
}

// ---------- daftar percakapan ----------

@Composable
private fun ChatListScreen(host: PanelHost, chats: List<ChatEntry>, hasAccess: Boolean) {
    val ctx = LocalContext.current
    val st = host.state
    val filtered = remember(chats, st.query, st.filter) {
        val q = ChatStore.normalizeKey(st.query)
        chats.filter { c ->
            val matchQuery = q.isEmpty() || c.key.contains(q) ||
                (c.lastMessage?.lowercase()?.contains(q) == true)
            val matchFilter = when (st.filter) {
                null -> true
                ChatFilter.Unread -> c.unread > 0
                ChatFilter.Individual -> !c.isGroup
                ChatFilter.Group -> c.isGroup
                ChatFilter.WhatsApp -> c.app == MessagingApp.WhatsApp
                ChatFilter.Telegram -> c.app == MessagingApp.Telegram
            }
            matchQuery && matchFilter
        }
    }

    Column(
        Modifier
            .fillMaxSize()
            .systemBarsPadding()
            .imePadding()
            .padding(horizontal = 8.dp)
    ) {
        Row(
            Modifier.fillMaxWidth().padding(start = 8.dp, end = 8.dp, top = 24.dp, bottom = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                stringResource(R.string.um_conversations_title),
                fontSize = 34.sp, fontWeight = FontWeight.Bold, color = White,
                modifier = Modifier.weight(1f),
            )
        }

        if (!hasAccess) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(WidgetBg)
                    .clickable { host.launch(Intent(ctx, SetupActivity::class.java)) }
                    .padding(18.dp)
            ) {
                Text(stringResource(R.string.um_complete_setup), color = White, fontSize = 17.sp)
            }
            Spacer(Modifier.height(12.dp))
        }

        LazyColumn(
            Modifier.weight(1f).fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues(bottom = 8.dp),
        ) {
            if (filtered.isEmpty()) {
                item {
                    Text(
                        stringResource(if (chats.isEmpty()) R.string.um_empty_no_chats else R.string.um_empty_no_match),
                        color = Dim, fontSize = 14.sp, modifier = Modifier.padding(24.dp),
                    )
                }
            }
            items(filtered, key = { "${it.app.id}|${it.key}" }) { chat ->
                ChatRow(
                    chat,
                    onClick = { st.selected = ChatRef(chat.app, chat.key) },
                    onLongClick = {
                        st.sheet = PanelSheet.ChatMenu(ChatRef(chat.app, chat.key), chat.title, chat.unread > 0)
                    },
                )
            }
        }

        Row(
            Modifier
                .fillMaxWidth()
                .padding(start = 8.dp, end = 8.dp, top = 10.dp, bottom = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            ChatFilter.entries.forEach { f ->
                val toggle = { st.filter = if (st.filter == f) null else f }
                val app = when (f) {
                    ChatFilter.WhatsApp -> MessagingApp.WhatsApp
                    ChatFilter.Telegram -> MessagingApp.Telegram
                    else -> null
                }
                if (app != null) {
                    FilterIconPill(app, st.filter == f, Modifier.width(44.dp), toggle)
                } else {
                    FilterPill(stringResource(f.label), st.filter == f, Modifier.weight(1f), toggle)
                }
            }
        }

        SearchBar(
            modifier = Modifier.padding(horizontal = 8.dp),
            value = st.query,
            onChange = { st.query = it },
            onMenu = { host.launch(Intent(ctx, SetupActivity::class.java)) },
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ChatRow(chat: ChatEntry, onClick: () -> Unit, onLongClick: () -> Unit) {
    val you = stringResource(R.string.um_you)
    val shape = RoundedCornerShape(16.dp)
    Row(
        Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(WidgetBg)
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Avatar(chat.title)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            // baris 1: nama · waktu · ikon aplikasi
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    chat.title,
                    color = White, fontSize = 15.sp,
                    fontWeight = if (chat.unread > 0) FontWeight.Bold else FontWeight.SemiBold,
                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                Spacer(Modifier.width(8.dp))
                Text(relativeTime(chat.updatedAt), color = Dim, fontSize = 13.sp, maxLines = 1)
                Spacer(Modifier.width(8.dp))
                AppBadge(chat.sourcePackage, size = 18.dp)
            }
            // baris 2: cuplikan pesan (1 baris) · jumlah belum dibaca
            val preview = buildString {
                when {
                    chat.lastOutgoing -> append("$you: ")
                    chat.isGroup && chat.lastSender != null -> append("${chat.lastSender}: ")
                }
                append(chat.lastMessage.orEmpty().replace('\n', ' '))
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    preview,
                    color = if (chat.unread > 0) White else Dim,
                    fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                if (chat.unread > 0) {
                    Spacer(Modifier.width(8.dp))
                    Box(
                        Modifier.size(20.dp).clip(CircleShape).background(White),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            if (chat.unread > 99) "99+" else chat.unread.toString(),
                            color = Black, fontSize = 11.sp, fontWeight = FontWeight.Bold,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun Avatar(title: String, size: Dp = 36.dp) {
    Box(
        Modifier.size(size).clip(CircleShape).background(AvatarBg),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            title.trim().firstOrNull()?.uppercase() ?: "?",
            color = White, fontSize = 15.sp, fontWeight = FontWeight.SemiBold,
        )
    }
}

/** Ikon aplikasi asal (WA/Telegram), dibuat grayscale supaya cocok dengan tema monokrom. */
@Composable
private fun AppBadge(pkg: String, size: Dp = 20.dp) {
    val ctx = LocalContext.current
    val bmp = remember(pkg) {
        runCatching {
            ctx.packageManager.getApplicationIcon(pkg).toBitmap(96, 96).asImageBitmap()
        }.getOrNull()
    }
    if (bmp != null) {
        Image(
            bmp, null,
            Modifier.size(size).clip(CircleShape),
            colorFilter = ColorFilter.colorMatrix(ColorMatrix().apply { setToSaturation(0f) }),
        )
    }
}

@Composable
private fun FilterPill(label: String, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val shape = RoundedCornerShape(50)
    Box(
        modifier
            .height(36.dp)
            .clip(shape)
            .background(if (selected) White else CardBg)
            .border(1.dp, if (selected) White else Outline, shape)
            .clickable(onClick = onClick)
            .padding(horizontal = 6.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            label,
            color = if (selected) Black else White,
            fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
        )
    }
}

/** Filter per aplikasi memakai ikon (grayscale) supaya semua filter muat satu baris. */
@Composable
private fun FilterIconPill(app: MessagingApp, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val ctx = LocalContext.current
    val pkg = remember(app) {
        app.packages.firstOrNull { p ->
            runCatching { ctx.packageManager.getApplicationInfo(p, 0) }.isSuccess
        } ?: app.packages.first()
    }
    val shape = RoundedCornerShape(50)
    Box(
        modifier
            .height(36.dp)
            .clip(shape)
            .background(if (selected) White else CardBg)
            .border(1.dp, if (selected) White else Outline, shape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        AppBadge(pkg, size = 20.dp)
    }
}

/** Bar pencarian bergaya sama dengan bar "Search" di home Kvaesitso (ukuran disamakan). */
@Composable
private fun SearchBar(modifier: Modifier, value: String, onChange: (String) -> Unit, onMenu: () -> Unit) {
    Row(
        modifier
            .fillMaxWidth()
            .height(64.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            Icons.Filled.Search, null, tint = White,
            modifier = Modifier.padding(start = 4.dp).size(24.dp),
        )
        Spacer(Modifier.width(14.dp))
        Box(Modifier.weight(1f)) {
            if (value.isEmpty()) {
                Text(
                    stringResource(R.string.um_search_hint),
                    color = White, fontSize = 16.sp, fontWeight = FontWeight.Bold,
                )
            }
            BasicTextField(
                value = value,
                onValueChange = onChange,
                singleLine = true,
                textStyle = TextStyle(color = White, fontSize = 16.sp, fontWeight = FontWeight.Bold),
                cursorBrush = SolidColor(White),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                modifier = Modifier.fillMaxWidth(),
            )
        }
        val endShift = Modifier.offset(x = 8.dp)
        if (value.isNotEmpty()) {
            IconButton(onClick = { onChange("") }, modifier = endShift) {
                Icon(Icons.Filled.Clear, stringResource(R.string.um_clear), tint = White)
            }
        } else {
            IconButton(onClick = onMenu, modifier = endShift) {
                Icon(Icons.Filled.MoreVert, stringResource(R.string.um_settings), tint = White)
            }
        }
    }
}

// ---------- detail + balas ----------

@Composable
private fun ChatDetailScreen(host: PanelHost, ref: ChatRef, chat: ChatEntry?, version: Long) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val keyboard = LocalSoftwareKeyboardController.current
    val focus = remember { FocusRequester() }

    var messages by remember(ref) { mutableStateOf<List<MessageEntry>>(emptyList()) }
    LaunchedEffect(ref, version) {
        messages = withContext(Dispatchers.IO) { ChatStore.get(ctx).messages(ref.app, ref.key) }
    }
    var replyStatus by remember(ref) { mutableStateOf<ReplyStatus?>(null) }
    LaunchedEffect(ref, version) {
        replyStatus = withContext(Dispatchers.IO) { ChatActions.replyStatus(ref.app, ref.key) }
    }
    val canReply = replyStatus == ReplyStatus.Available || replyStatus == ReplyStatus.Stale
    var draft by remember(ref) { mutableStateOf("") }

    // Langsung buka papan ketik saat detail dibuka
    LaunchedEffect(ref, canReply) {
        if (canReply) {
            delay(300)
            runCatching { focus.requestFocus() }
            keyboard?.show()
        }
    }

    fun openInApp() {
        if (ChatActions.openChat(ctx, ref.app, ref.key)) host.close()
    }

    fun send() {
        val text = draft.trim()
        if (text.isEmpty()) return
        scope.launch {
            val ok = withContext(Dispatchers.IO) { ChatActions.reply(ctx, ref.app, ref.key, text) }
            if (ok) draft = "" else Toast.makeText(ctx, R.string.um_reply_failed, Toast.LENGTH_SHORT).show()
        }
    }

    Column(Modifier.fillMaxSize().systemBarsPadding().imePadding()) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 8.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(WidgetBg)
                .padding(vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = { host.state.selected = null }) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.um_back), tint = White)
            }
            Column(Modifier.weight(1f)) {
                Text(
                    chat?.title ?: ref.key,
                    color = White, fontSize = 17.sp, fontWeight = FontWeight.Bold,
                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
                Text(
                    ref.app.label + if (chat?.isGroup == true) " · " + stringResource(R.string.um_filter_group) else "",
                    color = Dim, fontSize = 12.sp,
                )
            }
            TextButton(onClick = ::openInApp) {
                Text(stringResource(R.string.um_open_in_app), color = White)
            }
            IconButton(onClick = {
                host.state.sheet = PanelSheet.ChatMenu(ref, chat?.title ?: ref.key, (chat?.unread ?: 0) > 0)
            }) {
                Icon(Icons.Filled.MoreVert, stringResource(R.string.um_more), tint = White)
            }
        }

        LazyColumn(
            Modifier.weight(1f).fillMaxWidth(),
            reverseLayout = true,
            contentPadding = PaddingValues(12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            items(messages.asReversed(), key = { it.id }) { m ->
                Bubble(
                    m,
                    showSender = chat?.isGroup == true,
                    onOpenImage = { host.state.viewingImage = it },
                    onOpenInApp = ::openInApp,
                )
            }
        }

        if (canReply) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .padding(8.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(WidgetBg)
            ) {
                if (replyStatus == ReplyStatus.Stale) {
                    Text(
                        stringResource(R.string.um_reply_stale_hint),
                        color = Dim, fontSize = 12.sp,
                        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 10.dp),
                    )
                }
                ReplyBar(draft, { draft = it }, focus, ::send)
            }
        } else {
            Column(
                Modifier
                    .fillMaxWidth()
                    .padding(8.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(WidgetBg)
                    .padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                val reason = when (replyStatus) {
                    null -> R.string.um_reply_checking
                    ReplyStatus.ListenerOff -> R.string.um_reply_unavailable_listener
                    ReplyStatus.NoReplyAction -> R.string.um_reply_unavailable_no_action
                    else -> R.string.um_reply_unavailable
                }
                Text(stringResource(reason), color = Dim, fontSize = 13.sp)
                Spacer(Modifier.height(8.dp))
                TextButton(onClick = ::openInApp) {
                    Text(stringResource(R.string.um_open_in, ref.app.label), color = White)
                }
            }
        }
    }
}

private val MEDIA_MARKERS = listOf("📷", "🖼", "🎥", "📹", "🎬", "🎞")

@Composable
private fun Bubble(
    m: MessageEntry,
    showSender: Boolean,
    onOpenImage: (String) -> Unit,
    onOpenInApp: () -> Unit,
) {
    val ctx = LocalContext.current
    val shape = RoundedCornerShape(18.dp)
    // Media yang tidak bisa diambil dari notifikasi (video, dll.) -> ketuk untuk buka di aplikasi
    val mediaOnlyInApp = m.imagePath == null && MEDIA_MARKERS.any { m.text.startsWith(it) }
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = if (m.outgoing) Arrangement.End else Arrangement.Start,
    ) {
        Column(
            Modifier
                .widthIn(max = 300.dp)
                .clip(shape)
                .then(
                    if (m.outgoing) Modifier.background(White)
                    else Modifier.background(WidgetBg)
                )
                .then(if (mediaOnlyInApp) Modifier.clickable(onClick = onOpenInApp) else Modifier)
                .padding(horizontal = 12.dp, vertical = 8.dp)
        ) {
            if (showSender && !m.outgoing && m.sender != null) {
                Text(m.sender, color = Dim, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }
            m.imagePath?.let { path ->
                ImageThumb(path) { onOpenImage(path) }
                Spacer(Modifier.height(4.dp))
            }
            val caption = if (m.imagePath != null && m.text == NotificationParser.PHOTO_PLACEHOLDER) "" else m.text
            if (caption.isNotEmpty()) {
                Text(caption, color = if (m.outgoing) Black else White, fontSize = 15.sp)
            }
            if (mediaOnlyInApp) {
                Text(stringResource(R.string.um_media_open_in_app), color = Dim, fontSize = 11.sp)
            }
            Text(
                DateFormat.getTimeFormat(ctx).format(Date(m.time)),
                color = if (m.outgoing) Black.copy(alpha = 0.5f) else Dim,
                fontSize = 10.sp,
                modifier = Modifier.align(Alignment.End),
            )
        }
    }
}

@Composable
private fun ImageThumb(path: String, onClick: () -> Unit) {
    val bmp by produceState<ImageBitmap?>(null, path) {
        value = withContext(Dispatchers.IO) { decodeSampled(path, 720) }
    }
    val b = bmp
    if (b == null) {
        Box(Modifier.size(200.dp, 150.dp).clip(RoundedCornerShape(12.dp)).background(CardBg))
    } else {
        val ratio = (b.width.toFloat() / b.height).coerceIn(0.6f, 1.8f)
        Image(
            b, null,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .width(240.dp)
                .aspectRatio(ratio)
                .clip(RoundedCornerShape(12.dp))
                .clickable(onClick = onClick),
        )
    }
}

@Composable
private fun ImageViewer(path: String, onClose: () -> Unit) {
    val bmp by produceState<ImageBitmap?>(null, path) {
        value = withContext(Dispatchers.IO) { decodeSampled(path, 2048) }
    }
    Box(
        Modifier
            .fillMaxSize()
            .background(Color(0xF5000000))
            .clickable(onClick = onClose),
        contentAlignment = Alignment.Center,
    ) {
        bmp?.let {
            Image(it, null, contentScale = ContentScale.Fit, modifier = Modifier.fillMaxSize().systemBarsPadding())
        }
    }
}

private fun decodeSampled(path: String, maxSize: Int): ImageBitmap? = runCatching {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeFile(path, bounds)
    var sample = 1
    while (bounds.outWidth / sample > maxSize || bounds.outHeight / sample > maxSize) sample *= 2
    BitmapFactory.decodeFile(path, BitmapFactory.Options().apply { inSampleSize = sample })
        ?.asImageBitmap()
}.getOrNull()

@Composable
private fun ReplyBar(
    value: String,
    onChange: (String) -> Unit,
    focus: FocusRequester,
    onSend: () -> Unit,
) {
    val shape = RoundedCornerShape(24.dp)
    Row(
        Modifier.fillMaxWidth().padding(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .weight(1f)
                .clip(shape)
                .background(AvatarBg)
                .padding(horizontal = 18.dp, vertical = 14.dp)
        ) {
            if (value.isEmpty()) Text(stringResource(R.string.um_reply_hint), color = Dim, fontSize = 16.sp)
            BasicTextField(
                value = value,
                onValueChange = onChange,
                maxLines = 5,
                textStyle = TextStyle(color = White, fontSize = 16.sp),
                cursorBrush = SolidColor(White),
                keyboardOptions = KeyboardOptions(
                    capitalization = KeyboardCapitalization.Sentences,
                    imeAction = ImeAction.Send,
                ),
                keyboardActions = KeyboardActions(onSend = { onSend() }),
                modifier = Modifier.fillMaxWidth().focusRequester(focus),
            )
        }
        Spacer(Modifier.width(8.dp))
        IconButton(
            onClick = onSend,
            modifier = Modifier.size(48.dp).clip(CircleShape).background(White),
        ) {
            Icon(Icons.AutoMirrored.Filled.Send, stringResource(R.string.um_send), tint = Black)
        }
    }
}

private fun relativeTime(t: Long): String =
    DateUtils.getRelativeTimeSpanString(
        t, System.currentTimeMillis(), DateUtils.MINUTE_IN_MILLIS, DateUtils.FORMAT_ABBREV_RELATIVE
    ).toString()


// ---------- lembar aksi (menu chat, konfirmasi hapus, info balasan) ----------

private val Danger = Color(0xFFFF6B6B)

@Composable
private fun SheetHost(host: PanelHost, sheet: PanelSheet) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val st = host.state
    Box(
        Modifier
            .fillMaxSize()
            .background(Color(0xB3000000))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
            ) { st.sheet = null },
        contentAlignment = Alignment.BottomCenter,
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp))
                .background(Color(0xFF141414))
                .border(1.dp, Outline, RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                ) { /* tahan klik agar tidak menutup */ }
                .navigationBarsPadding()
                .padding(horizontal = 20.dp, vertical = 20.dp)
        ) {
            when (sheet) {
                is PanelSheet.ChatMenu -> {
                    SheetTitle(sheet.title, sheet.ref.app.label)
                    if (sheet.unread) {
                        SheetItem(stringResource(R.string.um_mark_read)) {
                            st.sheet = null
                            scope.launch(Dispatchers.IO) { ChatActions.markRead(ctx, sheet.ref.app, sheet.ref.key) }
                        }
                    }
                    SheetItem(stringResource(R.string.um_open_in, sheet.ref.app.label)) {
                        st.sheet = null
                        if (ChatActions.openChat(ctx, sheet.ref.app, sheet.ref.key)) host.close()
                    }
                    SheetItem(stringResource(R.string.um_reply_info)) {
                        st.sheet = PanelSheet.ReplyInfo(sheet.ref, sheet.title)
                    }
                    SheetItem(stringResource(R.string.um_delete_chat), color = Danger) {
                        st.sheet = PanelSheet.ConfirmDelete(sheet.ref, sheet.title)
                    }
                }

                is PanelSheet.ConfirmDelete -> {
                    SheetTitle(stringResource(R.string.um_delete_chat_title, sheet.title), null)
                    Text(stringResource(R.string.um_delete_chat_text), color = Dim, fontSize = 14.sp, lineHeight = 20.sp)
                    Spacer(Modifier.height(20.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        SheetButton(stringResource(R.string.um_cancel), Modifier.weight(1f), White, Outline) {
                            st.sheet = null
                        }
                        SheetButton(stringResource(R.string.um_delete), Modifier.weight(1f), Danger, Danger.copy(alpha = 0.5f)) {
                            st.sheet = null
                            if (st.selected == sheet.ref) st.selected = null
                            scope.launch(Dispatchers.IO) { ChatActions.deleteChat(ctx, sheet.ref.app, sheet.ref.key) }
                        }
                    }
                }

                is PanelSheet.ReplyInfo -> {
                    SheetTitle(stringResource(R.string.um_reply_info), sheet.title)
                    var info by remember(sheet) { mutableStateOf<Pair<ReplyStatus, List<String>>?>(null) }
                    LaunchedEffect(sheet) {
                        info = withContext(Dispatchers.IO) {
                            ChatActions.replyStatus(sheet.ref.app, sheet.ref.key) to
                                ChatActions.replyDataTypes(sheet.ref.app, sheet.ref.key)
                        }
                    }
                    val i = info
                    if (i == null) {
                        Text(stringResource(R.string.um_reply_checking), color = Dim, fontSize = 14.sp)
                    } else {
                        val statusText = when (i.first) {
                            ReplyStatus.Available -> R.string.um_reply_info_live
                            ReplyStatus.Stale -> R.string.um_reply_info_stale
                            else -> R.string.um_reply_info_none
                        }
                        InfoLine(stringResource(R.string.um_reply_info_status), stringResource(statusText))
                        val types = if (i.first == ReplyStatus.Available || i.first == ReplyStatus.Stale) {
                            if (i.second.isEmpty()) stringResource(R.string.um_reply_info_text_only)
                            else stringResource(R.string.um_reply_info_text_plus, i.second.joinToString(", "))
                        } else "–"
                        InfoLine(stringResource(R.string.um_reply_info_types), types)
                    }
                    Spacer(Modifier.height(16.dp))
                    SheetButton(stringResource(R.string.um_close), Modifier.fillMaxWidth(), White, Outline) {
                        st.sheet = null
                    }
                }
            }
        }
    }
}

@Composable
private fun SheetTitle(title: String, subtitle: String?) {
    Text(title, color = White, fontSize = 20.sp, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
    if (subtitle != null) Text(subtitle, color = Dim, fontSize = 13.sp)
    Spacer(Modifier.height(14.dp))
}

@Composable
private fun SheetItem(label: String, color: Color = White, onClick: () -> Unit) {
    Text(
        label,
        color = color, fontSize = 17.sp,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 14.dp),
    )
}

@Composable
private fun SheetButton(label: String, modifier: Modifier, color: Color, border: Color, onClick: () -> Unit) {
    Box(
        modifier
            .clip(RoundedCornerShape(50))
            .border(1.dp, border, RoundedCornerShape(50))
            .clickable(onClick = onClick)
            .padding(vertical = 14.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, color = color, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun InfoLine(label: String, value: String) {
    Column(Modifier.padding(vertical = 6.dp)) {
        Text(label, color = Dim, fontSize = 12.sp)
        Text(value, color = White, fontSize = 15.sp)
    }
}
