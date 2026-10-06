package id.kvplugin.unifiedmsg

import android.Manifest
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
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
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Layar pengaturan, bergaya sama dengan panel Conversations (monokrom). */
class SetupActivity : ComponentActivity() {

    private var resumeTick by mutableIntStateOf(0)

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent { SetupScreen(tick = resumeTick, onBack = { finish() }) }
    }

    override fun onResume() {
        super.onResume()
        resumeTick++ // cek ulang status izin setiap kembali dari layar Settings
    }
}

private val Bg = Color.Black
private val CardBg = Color(0x14FFFFFF)
private val Outline = Color(0x26FFFFFF)
private val Dim = Color(0x99FFFFFF)
private val White = Color.White
private val Danger = Color(0xFFFF6B6B)

@Composable
private fun SetupScreen(tick: Int, onBack: () -> Unit) {
    val ctx = LocalContext.current
    val version by ChatEvents.version.collectAsState()
    var permTick by remember { mutableIntStateOf(0) }

    val notifOk = remember(tick, version) { Prefs.hasNotificationAccess(ctx) }
    val contactsOk = remember(tick, permTick) { Prefs.hasContactsAccess(ctx) }
    val batteryOk = remember(tick) {
        ctx.getSystemService(PowerManager::class.java)?.isIgnoringBatteryOptimizations(ctx.packageName) == true
    }
    var chatCount by remember { mutableIntStateOf(0) }
    LaunchedEffect(tick, version) {
        chatCount = withContext(Dispatchers.IO) { ChatStore.get(ctx).countChats() }
    }
    var cc by rememberSaveable { mutableStateOf(Prefs.countryCode(ctx)) }
    var confirmClear by remember { mutableStateOf(false) }
    val appVersion = remember {
        runCatching { ctx.packageManager.getPackageInfo(ctx.packageName, 0).versionName }.getOrNull() ?: "?"
    }

    val contactsLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { permTick++ }

    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = White, onPrimary = Color.Black,
            background = Bg, surface = Color(0xFF151515), onSurface = White,
        )
    ) {
        Column(
            Modifier
                .fillMaxSize()
                .background(Bg)
                .systemBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
                .padding(bottom = 24.dp)
        ) {
            IconButton(onClick = onBack, modifier = Modifier.padding(top = 8.dp)) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.um_back), tint = White)
            }
            Text(
                stringResource(R.string.um_settings),
                color = White, fontSize = 34.sp, fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(top = 4.dp, bottom = 6.dp),
            )
            Text(stringResource(R.string.um_setup_intro), color = Dim, fontSize = 14.sp, lineHeight = 20.sp)

            // ---------- Izin ----------
            SectionTitle(stringResource(R.string.um_section_permissions))
            Card {
                StatusRow(
                    title = stringResource(R.string.um_perm_notif_title),
                    subtitle = stringResource(if (notifOk) R.string.um_perm_notif_ok else R.string.um_perm_notif_missing),
                    ok = notifOk,
                    action = stringResource(if (notifOk) R.string.um_action_manage else R.string.um_action_enable),
                    primary = !notifOk,
                    onAction = { openNotificationAccessSettings(ctx) },
                )
                if (!notifOk) {
                    Text(
                        stringResource(R.string.um_restricted_hint),
                        color = Dim, fontSize = 12.sp, lineHeight = 17.sp,
                        modifier = Modifier.padding(start = 40.dp, top = 6.dp),
                    )
                }
                RowDivider()
                StatusRow(
                    title = stringResource(R.string.um_perm_contacts_title),
                    subtitle = stringResource(R.string.um_perm_contacts_sub),
                    ok = contactsOk,
                    action = if (contactsOk) null else stringResource(R.string.um_action_allow),
                    primary = false,
                    onAction = { contactsLauncher.launch(Manifest.permission.READ_CONTACTS) },
                )
                RowDivider()
                StatusRow(
                    title = stringResource(R.string.um_perm_battery_title),
                    subtitle = stringResource(if (batteryOk) R.string.um_perm_battery_ok else R.string.um_perm_battery_sub),
                    ok = batteryOk,
                    action = if (batteryOk) null else stringResource(R.string.um_action_manage),
                    primary = false,
                    onAction = { requestUnrestrictedBattery(ctx) },
                )
            }

            // ---------- Kvaesitso ----------
            SectionTitle(stringResource(R.string.um_section_kvaesitso))
            Card {
                Step("1", stringResource(R.string.um_step_feed))
                Spacer(Modifier.height(12.dp))
                Step("2", stringResource(R.string.um_step_plugin))
            }

            // ---------- Nomor ----------
            SectionTitle(stringResource(R.string.um_section_phone))
            Card {
                Text(stringResource(R.string.um_country_code_label), color = Dim, fontSize = 13.sp)
                Spacer(Modifier.height(10.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Row(
                        Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(16.dp))
                            .border(1.dp, Outline, RoundedCornerShape(16.dp))
                            .padding(horizontal = 16.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text("+", color = Dim, fontSize = 18.sp)
                        Spacer(Modifier.width(4.dp))
                        BasicTextField(
                            value = cc,
                            onValueChange = { v -> cc = v.filter(Char::isDigit).take(4) },
                            singleLine = true,
                            textStyle = TextStyle(color = White, fontSize = 18.sp),
                            cursorBrush = SolidColor(White),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                    Spacer(Modifier.width(10.dp))
                    Pill(stringResource(R.string.um_btn_save), primary = true) {
                        Prefs.setCountryCode(ctx, cc)
                        Toast.makeText(ctx, R.string.um_saved, Toast.LENGTH_SHORT).show()
                    }
                }
            }

            // ---------- Data ----------
            SectionTitle(stringResource(R.string.um_section_data))
            Card {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(stringResource(R.string.um_data_chats_title), color = White, fontSize = 16.sp)
                        Text(stringResource(R.string.um_data_chats_sub), color = Dim, fontSize = 12.sp)
                    }
                    Text(chatCount.toString(), color = White, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                }
                Spacer(Modifier.height(14.dp))
                Box(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .border(1.dp, Danger.copy(alpha = 0.5f), RoundedCornerShape(16.dp))
                        .clickable { confirmClear = true }
                        .padding(vertical = 14.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(stringResource(R.string.um_btn_clear), color = Danger, fontSize = 15.sp)
                }
            }

            Text(
                stringResource(R.string.um_version_label, appVersion),
                color = Dim, fontSize = 12.sp,
                modifier = Modifier.fillMaxWidth().padding(top = 24.dp),
            )
        }

        if (confirmClear) {
            AlertDialog(
                onDismissRequest = { confirmClear = false },
                title = { Text(stringResource(R.string.um_clear_confirm_title)) },
                text = { Text(stringResource(R.string.um_clear_confirm_text)) },
                confirmButton = {
                    TextButton(onClick = {
                        ChatStore.get(ctx).clear()
                        MediaCache.clear(ctx)
                        PendingIntentCache.clear()
                        ActiveChats.clear()
                        ChatEvents.bump()
                        confirmClear = false
                        Toast.makeText(ctx, R.string.um_cleared, Toast.LENGTH_SHORT).show()
                    }) { Text(stringResource(R.string.um_clear), color = Danger) }
                },
                dismissButton = {
                    TextButton(onClick = { confirmClear = false }) {
                        Text(stringResource(R.string.um_cancel), color = White)
                    }
                },
            )
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text.uppercase(),
        color = Dim, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 1.2.sp,
        modifier = Modifier.padding(start = 4.dp, top = 28.dp, bottom = 10.dp),
    )
}

@Composable
private fun Card(content: @Composable ColumnScope.() -> Unit) {
    val shape = RoundedCornerShape(22.dp)
    Column(
        Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(CardBg)
            .border(1.dp, Outline, shape)
            .padding(16.dp),
        content = content,
    )
}

@Composable
private fun RowDivider() {
    Box(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 14.dp)
            .height(1.dp)
            .background(Outline)
    )
}

@Composable
private fun StatusRow(
    title: String,
    subtitle: String,
    ok: Boolean,
    action: String?,
    primary: Boolean,
    onAction: () -> Unit,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(
            if (ok) Icons.Filled.CheckCircle else Icons.Filled.Warning,
            contentDescription = null,
            tint = if (ok) White else Dim,
            modifier = Modifier.size(24.dp),
        )
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f)) {
            Text(title, color = White, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
            Text(subtitle, color = Dim, fontSize = 12.sp, lineHeight = 16.sp)
        }
        if (action != null) {
            Spacer(Modifier.width(10.dp))
            Pill(action, primary, onAction)
        }
    }
}

@Composable
private fun Pill(label: String, primary: Boolean, onClick: () -> Unit) {
    val shape = RoundedCornerShape(50)
    Box(
        Modifier
            .clip(shape)
            .background(if (primary) White else Color.Transparent)
            .border(1.dp, if (primary) White else Outline, shape)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp)
    ) {
        Text(label, color = if (primary) Color.Black else White, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun Step(number: String, text: String) {
    Row(verticalAlignment = Alignment.Top) {
        Box(
            Modifier.size(26.dp).clip(CircleShape).border(1.dp, Outline, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Text(number, color = White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.width(14.dp))
        Text(text, color = White, fontSize = 14.sp, lineHeight = 20.sp, modifier = Modifier.weight(1f))
    }
}

private fun openNotificationAccessSettings(ctx: Context) {
    val intent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        Intent(Settings.ACTION_NOTIFICATION_LISTENER_DETAIL_SETTINGS).putExtra(
            Settings.EXTRA_NOTIFICATION_LISTENER_COMPONENT_NAME,
            ComponentName(ctx, MessageListenerService::class.java).flattenToString()
        )
    } else {
        Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
    }
    runCatching { ctx.startActivity(intent) }
        .onFailure { runCatching { ctx.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)) } }
}

private fun requestUnrestrictedBattery(ctx: Context) {
    val pkg = Uri.parse("package:${ctx.packageName}")
    runCatching {
        ctx.startActivity(Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, pkg))
    }.onFailure {
        runCatching { ctx.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, pkg)) }
    }
}
