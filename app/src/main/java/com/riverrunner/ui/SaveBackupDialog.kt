package com.riverrunner.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import com.riverrunner.i18n.LocalStrings
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

@Composable
internal fun SaveBackupDialog(vm: AppViewModel, onClose: () -> Unit) {
    val st = LocalStrings.current
    fun t(zh: String, en: String) = if (st.isZh) zh else en
    val scope = rememberCoroutineScope()
    var busy by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf<String?>(null) }
    fun transfer(failure: String, operation: suspend () -> String) {
        busy = true
        status = null
        scope.launch {
            try {
                status = operation()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                status = failure
            } finally {
                busy = false
            }
        }
    }
    val export = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) { uri ->
        if (uri != null) transfer(t("无法导出存档。请检查可用空间和保存位置。", "Could not export the save. Check free space and destination.")) {
            vm.exportSave(uri)
            t("存档备份已保存", "Save backup written")
        }
    }
    val restore = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) transfer(t("无法恢复存档。请检查文件是否为完整的 River Runner 存档备份。",
            "Could not restore this save. Choose a complete River Runner save backup.")) {
            val profile = vm.importSave(uri)
            t("已恢复并切换到「${profile.name}」", "Restored and switched to “${profile.name}”")
        }
    }
    val freeSlot = vm.profileList.size < vm.maxSlots
    AlertDialog(
        onDismissRequest = { if (!busy) onClose() },
        properties = DialogProperties(dismissOnBackPress = !busy, dismissOnClickOutside = !busy),
        title = { Text(t("存档备份与恢复", "Save backup and restore")) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(t("当前存档：${vm.profile.name}", "Current save: ${vm.profile.name}"))
                Text(t("备份包含牌谱、复盘分析、成就、训练进度和导入的求解器参考。恢复会创建新槽位。",
                    "Backups include hand histories, replay analysis, achievements, training progress and imported solver references. Restoring creates a new slot."))
                OutlinedButton(onClick = { export.launch("river-runner-save.zip") }, enabled = !busy,
                    modifier = Modifier.fillMaxWidth()) { Text(t("导出当前存档", "Export current save")) }
                OutlinedButton(onClick = { restore.launch(arrayOf("application/zip", "application/x-zip-compressed", "application/octet-stream")) },
                    enabled = !busy && freeSlot, modifier = Modifier.fillMaxWidth()) {
                    Text(t("导入到新槽位", "Restore into a new slot"))
                }
                if (!freeSlot) Text(t("恢复备份需要一个空槽位。", "Free a save slot to restore a backup."))
                if (busy) Text(t("正在处理存档…", "Working with save data…"))
                status?.let { Text(it, color = Palette.Gold) }
            }
        },
        confirmButton = { TextButton(onClick = onClose, enabled = !busy) { Text(st.back) } },
    )
}
