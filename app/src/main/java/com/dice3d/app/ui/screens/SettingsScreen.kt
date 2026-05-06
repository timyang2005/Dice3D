package com.dice3d.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dice3d.app.data.DarkModePreference
import com.dice3d.app.data.DiceType
import com.dice3d.app.viewmodel.SettingsViewModel

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel,
    onBack: () -> Unit
) {
    val settings by viewModel.settings.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("设置") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                ),
                modifier = Modifier.statusBarsPadding()
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                )
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("骰子配置", style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(12.dp))

                    Text("骰子类型", style = MaterialTheme.typography.bodyMedium)
                    FlowRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        DiceType.entries.forEach { type ->
                            OutlinedButton(
                                onClick = { viewModel.updateDiceType(type) },
                                colors = ButtonDefaults.outlinedButtonColors(
                                    containerColor = if (settings.diceType == type)
                                        MaterialTheme.colorScheme.primaryContainer
                                    else Color.Transparent
                                )
                            ) {
                                Text(type.displayName, fontSize = 13.sp)
                            }
                        }
                    }

                    Spacer(Modifier.height(12.dp))

                    Text("骰子数量: ${settings.diceCount}", style = MaterialTheme.typography.bodyMedium)
                    Slider(
                        value = settings.diceCount.toFloat(),
                        onValueChange = { viewModel.updateDiceCount(it.toInt()) },
                        valueRange = 1f..10f,
                        steps = 8,
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(Modifier.height(12.dp))

                    Text("骰子颜色", style = MaterialTheme.typography.bodyMedium)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        val colors = listOf(
                            0xFFFFFFFF to "白",
                            0xFFE53935 to "红",
                            0xFF1E88E5 to "蓝",
                            0xFF43A047 to "绿",
                            0xFFFFB300 to "黄",
                            0xFF8E24AA to "紫",
                            0xFF212121 to "黑",
                            0xFFFF6D00 to "橙"
                        )
                        colors.forEach { (color, label) ->
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(36.dp)
                                        .clip(CircleShape)
                                        .background(Color(color))
                                        .clickable { viewModel.updateDiceColor(color) }
                                        .then(
                                            if (settings.diceColor == color)
                                                Modifier.background(
                                                    MaterialTheme.colorScheme.primary.copy(alpha = 0.3f),
                                                    CircleShape
                                                )
                                            else Modifier
                                        )
                                )
                                Text(label, fontSize = 10.sp)
                            }
                        }
                    }
                }
            }

            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                )
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("高级设置", style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(12.dp))

                    SettingSwitch("显示总和", settings.showSum) { viewModel.updateShowSum(it) }
                    SettingSwitch("音效", settings.soundEnabled) { viewModel.updateSoundEnabled(it) }
                    SettingSwitch("触觉反馈", settings.hapticEnabled) { viewModel.updateHapticEnabled(it) }
                    SettingSwitch("陀螺仪投掷", settings.gyroEnabled) { viewModel.updateGyroEnabled(it) }

                    Spacer(Modifier.height(8.dp))

                    Text(
                        "模拟速度: ${String.format("%.1f", settings.simSpeed)}×",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Slider(
                            value = settings.simSpeed,
                            onValueChange = { viewModel.updateSimSpeed(it) },
                            valueRange = 0.1f..5.0f,
                            steps = 48,
                            modifier = Modifier.weight(1f)
                        )
                        Spacer(Modifier.width(8.dp))
                        OutlinedButton(
                            onClick = { viewModel.resetSimSpeed() },
                            contentPadding = androidx.compose.foundation.layout.PaddingValues(
                                horizontal = 12.dp, vertical = 4.dp
                            )
                        ) {
                            Text("重置", fontSize = 12.sp)
                        }
                    }
                }
            }

            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                )
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("深色模式", style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(12.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        DarkModePreference.entries.forEach { mode ->
                            OutlinedButton(
                                onClick = { viewModel.updateDarkMode(mode) },
                                modifier = Modifier.weight(1f),
                                colors = ButtonDefaults.outlinedButtonColors(
                                    containerColor = if (settings.darkMode == mode)
                                        MaterialTheme.colorScheme.primaryContainer
                                    else Color.Transparent
                                )
                            ) {
                                Text(
                                    when (mode) {
                                        DarkModePreference.FOLLOW_SYSTEM -> "跟随系统"
                                        DarkModePreference.LIGHT -> "浅色"
                                        DarkModePreference.DARK -> "深色"
                                    },
                                    fontSize = 12.sp
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SettingSwitch(label: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium)
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}
