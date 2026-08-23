package com.caipan.music.ui.components

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.caipan.music.R
import com.caipan.music.player.EqBand
import com.caipan.music.plugin.BlurLocation
import com.kyant.backdrop.Backdrop

private class Strings(private val isZh: Boolean) {
    val equalizer get() = if (isZh) "均衡器" else "Equalizer"
    val on get() = if (isZh) "开" else "ON"
    val off get() = if (isZh) "关" else "OFF"
    val import get() = if (isZh) "导入" else "Import"
    val export get() = if (isZh) "导出" else "Export"
    val save get() = if (isZh) "保存" else "Save"
    val presets get() = if (isZh) "预设" else "Presets"
    val reset get() = if (isZh) "重置" else "Reset"
    val presetName get() = if (isZh) "预设名称" else "Preset name"
    val savePreset get() = if (isZh) "保存预设" else "Save Preset"
    val exportPreset get() = if (isZh) "导出预设" else "Export Preset"
    val cancel get() = if (isZh) "取消" else "Cancel"
    val confirm get() = if (isZh) "保存" else "Save"
    val delete get() = if (isZh) "删除" else "Delete"
    val playFirst get() = if (isZh) "请先播放一首歌曲" else "Play a song first"
    val importSuccess get() = if (isZh) "导入成功" else "Imported"
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EqualizerScreen(
    bands: List<EqBand>,
    isEnabled: Boolean,
    presetName: String,
    presets: List<String>,
    onBandChange: (Int, Float) -> Unit,
    onToggle: (Boolean) -> Unit,
    onReset: () -> Unit,
    onSavePreset: (String) -> Unit,
    onLoadPreset: (String) -> Unit,
    onDeletePreset: (String) -> Unit,
    onImport: (Uri) -> Unit,
    onExport: (Uri, String) -> Unit,
    onDismiss: () -> Unit,
    accentColor: Color = Color(0xFF1DB954),
    isLightTheme: Boolean = false,
    isChinese: Boolean = false,
    backdrop: Backdrop? = null
) {
    val context = LocalContext.current
    val s = remember(isChinese) { Strings(isChinese) }
    var showExportDialog by remember { mutableStateOf(false) }
    var showSaveDialog by remember { mutableStateOf(false) }
    var showPresets by remember { mutableStateOf(false) }
    var dialogName by remember(presetName) { mutableStateOf(presetName.ifBlank { "My Preset" }) }
    val scrollState = rememberScrollState()

    val textPrimary = MaterialTheme.colorScheme.onBackground
    val textSecondary = MaterialTheme.colorScheme.onSurfaceVariant

    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) onImport(uri)
    }
    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/plain")) { uri ->
        if (uri != null) onExport(uri, dialogName)
    }

    FullScreenGlassRoute(backdrop = backdrop, isLightTheme = isLightTheme) {
        Column(Modifier.fillMaxSize().statusBarsPadding()) {
            // ── Header ──
            Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically) {
                MuseIconButton(onClick = onDismiss) {
                    Icon(painterResource(R.drawable.ic_apple_arrow_left), if (isChinese) "返回" else "Back", tint = textPrimary, modifier = Modifier.size(24.dp))
                }
                Text(s.equalizer, color = textPrimary, style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.weight(1f).padding(start = 8.dp))
                Text(if (isEnabled) s.on else s.off, color = if (isEnabled) accentColor else textSecondary,
                    fontSize = 13.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(end = 8.dp))
                MuseGlassSwitch(
                    checked = isEnabled,
                    onCheckedChange = onToggle,
                    accentColor = accentColor,
                    backdrop = backdrop
                )
            }

            // ── Action bar ──
            Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp).horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                MuseFilterChip(selected = false, onClick = { importLauncher.launch(arrayOf("text/plain", "*/*")) },
                    label = { Text(s.import, fontSize = 11.sp) },
                    leadingIcon = { Icon(painterResource(R.drawable.ic_apple_file_text), null, modifier = Modifier.size(14.dp)) })
                MuseFilterChip(selected = false, onClick = { showExportDialog = true },
                    label = { Text(s.export, fontSize = 11.sp) },
                    leadingIcon = { Icon(painterResource(R.drawable.ic_apple_download), null, modifier = Modifier.size(14.dp)) })
                MuseFilterChip(selected = false, onClick = { showSaveDialog = true },
                    label = { Text(s.save, fontSize = 11.sp) },
                    leadingIcon = { Icon(painterResource(R.drawable.ic_apple_bookmark), null, modifier = Modifier.size(14.dp)) })
                MuseFilterChip(selected = false, onClick = { showPresets = !showPresets },
                    label = { Text(s.presets, fontSize = 11.sp) },
                    leadingIcon = { Icon(painterResource(R.drawable.ic_apple_library), null, modifier = Modifier.size(14.dp)) })
                Spacer(Modifier.width(10.dp))
                MuseTextButton(onClick = onReset, modifier = Modifier.height(48.dp)) {
                    Text(s.reset, color = textSecondary, fontSize = 11.sp)
                }
            }

            if (presetName.isNotBlank()) {
                Text(presetName, color = accentColor, fontSize = 13.sp, fontWeight = FontWeight.Medium,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 2.dp))
            }

            // ── Presets list ──
            if (showPresets && presets.isNotEmpty()) {
                Card(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp)
                    .museGlass(
                        backdrop, RoundedCornerShape(12.dp),
                        MaterialTheme.colorScheme.surface.copy(alpha = .34f),
                        location = BlurLocation.CARDS
                    ),
                    colors = CardDefaults.cardColors(containerColor = Color.Transparent),
                    shape = RoundedCornerShape(12.dp)) {
                    Column(Modifier.padding(4.dp)) {
                        presets.forEach { name ->
                            Row(Modifier.fillMaxWidth().clickable { onLoadPreset(name); showPresets = false }
                                .padding(horizontal = 8.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically) {
                                Icon(painterResource(R.drawable.ic_apple_activity), null, tint = accentColor, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(8.dp))
                                Text(name, color = textPrimary, fontSize = 13.sp, modifier = Modifier.weight(1f))
                                MuseIconButton(onClick = { onDeletePreset(name) }, modifier = Modifier.size(48.dp)) {
                                    Icon(painterResource(R.drawable.ic_apple_x), s.delete, tint = textSecondary, modifier = Modifier.size(14.dp))
                                }
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.height(8.dp))

            // ── EQ BANDS: horizontal sliders ──
            if (bands.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(s.playFirst, color = textSecondary, fontSize = 15.sp)
                }
            } else {
                Column(Modifier.fillMaxWidth().weight(1f).verticalScroll(scrollState).padding(horizontal = 16.dp)) {
                    bands.forEachIndexed { i, band ->
                        val freqLabel = if (band.freqHz >= 1000) "${band.freqHz / 1000}.${(band.freqHz % 1000) / 100}k" else band.freqHz.toString()
                        Row(Modifier.fillMaxWidth().height(44.dp).padding(vertical = 2.dp),
                            verticalAlignment = Alignment.CenterVertically) {
                            // Frequency label
                            Text(freqLabel, color = textSecondary, fontSize = 12.sp,
                                modifier = Modifier.width(40.dp), textAlign = TextAlign.Center)

                            Spacer(Modifier.width(8.dp))

                            // Slider
                            MuseGlassSlider(
                                value = band.levelDb.coerceIn(band.rangeMinDb, band.rangeMaxDb),
                                onValueChange = { onBandChange(i, it) },
                                valueRange = band.rangeMinDb..band.rangeMaxDb,
                                accentColor = accentColor,
                                backdrop = backdrop,
                                modifier = Modifier.weight(1f).height(40.dp)
                            )

                            // dB value
                            Text(
                                if (band.levelDb >= 0) "+${"%.1f".format(band.levelDb)}" else "%.1f".format(band.levelDb),
                                color = if (band.levelDb != 0f) accentColor else textSecondary,
                                fontSize = 11.sp, fontWeight = FontWeight.Medium,
                                modifier = Modifier.width(42.dp), textAlign = TextAlign.End
                            )
                        }
                    }
                    Spacer(Modifier.height(16.dp))
                }
            }
        }
    }

    // ── Export Dialog ──
    if (showExportDialog) {
        MuseAlertDialog(onDismissRequest = { showExportDialog = false },
            title = { DialogBlurEffect(); Text(s.exportPreset, color = textPrimary) },
            text = { OutlinedTextField(value = dialogName, onValueChange = { dialogName = it },
                label = { Text(s.presetName) }, singleLine = true) },
            confirmButton = { MuseTextButton(onClick = { showExportDialog = false; exportLauncher.launch("${dialogName}.txt") })
                { Text(s.confirm, color = accentColor) } },
            dismissButton = { MuseTextButton(onClick = { showExportDialog = false }) { Text(s.cancel, color = textSecondary) } },
            containerColor = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = .94f),
            tonalElevation = 8.dp, shape = RoundedCornerShape(24.dp))
    }

    if (showSaveDialog) {
        MuseAlertDialog(onDismissRequest = { showSaveDialog = false },
            title = { DialogBlurEffect(); Text(s.savePreset, color = textPrimary) },
            text = { OutlinedTextField(value = dialogName, onValueChange = { dialogName = it },
                label = { Text(s.presetName) }, singleLine = true) },
            confirmButton = { MuseTextButton(onClick = { showSaveDialog = false; onSavePreset(dialogName) })
                { Text(s.confirm, color = accentColor) } },
            dismissButton = { MuseTextButton(onClick = { showSaveDialog = false }) { Text(s.cancel, color = textSecondary) } },
            containerColor = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = .94f),
            tonalElevation = 8.dp, shape = RoundedCornerShape(24.dp))
    }
}
