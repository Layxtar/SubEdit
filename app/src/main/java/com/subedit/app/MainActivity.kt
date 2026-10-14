package com.subedit.app

import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme(colorScheme = darkColorScheme()) {
                Surface(modifier = Modifier.fillMaxSize()) {
                    SubEditApp(this)
                }
            }
        }
    }
}

@Composable
fun SubEditApp(activity: ComponentActivity) {
    var editor by remember { mutableStateOf<ProfileEditor?>(null) }
    var refreshTrigger by remember { mutableIntStateOf(0) }
    var selectedTab by remember { mutableIntStateOf(0) }
    var statusMsg by remember { mutableStateOf("Профиль не загружен") }
    var remotePath by remember { mutableStateOf(ShizukuHelper.CITY_PROFILE_PATH) }
    var gameMode by remember { mutableIntStateOf(0) } // 0 = Subway City, 1 = Original Subway

    val openLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        uri?.let {
            try {
                val bytes = activity.contentResolver.openInputStream(it)?.readBytes() ?: return@let
                val payload = ProfileCrypto.decryptProfile(bytes)
                editor = ProfileEditor(payload.iv, payload.key, payload.rootData)
                refreshTrigger++
                statusMsg = "Файл открыт через проводник"
            } catch (e: Exception) {
                statusMsg = "Ошибка: ${e.message}"
            }
        }
    }

    val saveLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/octet-stream")) { uri: Uri? ->
        uri?.let {
            try {
                val ed = editor ?: return@let
                val outBytes = ProfileCrypto.encryptProfile(ed.iv, ed.key, ed.buildRootData())
                activity.contentResolver.openOutputStream(it)?.use { os -> os.write(outBytes) }
                statusMsg = "Файл успешно сохранён!"
            } catch (e: Exception) {
                statusMsg = "Ошибка сохранения: ${e.message}"
            }
        }
    }

    Column(modifier = Modifier.fillMaxSize().padding(8.dp)) {
        // Переключатель игры (City / Original Subway)
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            FilterChip(
                selected = gameMode == 0,
                onClick = {
                    gameMode = 0
                    remotePath = "/sdcard/Android/data/com.sybogames.subway.surfers.city/files/profile"
                },
                label = { Text("Subway City") },
                modifier = Modifier.padding(end = 8.dp)
            )
            FilterChip(
                selected = gameMode == 1,
                onClick = {
                    gameMode = 1
                    remotePath = "/sdcard/Android/data/com.kiloo.subwaysurf/files/profile/wallet.json"
                },
                label = { Text("Original Subway (Скоро)") }
            )
        }

        OutlinedTextField(
            value = remotePath,
            onValueChange = { remotePath = it },
            label = { Text("Путь Shizuku к файлу профиля") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
        )

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Button(
                onClick = {
                    if (ShizukuHelper.hasPermission()) {
                        try {
                            val bytes = ShizukuHelper.readFileViaShizuku(activity, remotePath)
                            val payload = ProfileCrypto.decryptProfile(bytes)
                            editor = ProfileEditor(payload.iv, payload.key, payload.rootData)
                            refreshTrigger++
                            statusMsg = "Загружено через Shizuku!"
                        } catch (e: Exception) {
                            statusMsg = e.message ?: "Ошибка Shizuku"
                        }
                    } else {
                        statusMsg = "Запустите Shizuku и выдайте права"
                    }
                },
                modifier = Modifier.weight(1f)
            ) { Text("Shizuku: Загрузить") }

            Button(
                onClick = {
                    val ed = editor ?: return@Button
                    if (ShizukuHelper.hasPermission()) {
                        try {
                            val bytes = ProfileCrypto.encryptProfile(ed.iv, ed.key, ed.buildRootData())
                            ShizukuHelper.writeFileViaShizuku(activity, remotePath, bytes)
                            statusMsg = "Сохранено в игру через Shizuku!"
                            Toast.makeText(activity, statusMsg, Toast.LENGTH_SHORT).show()
                        } catch (e: Exception) {
                            statusMsg = e.message ?: "Ошибка записи"
                        }
                    }
                },
                enabled = editor != null,
                modifier = Modifier.weight(1f)
            ) { Text("Shizuku: Сохранить") }
        }

        Row(modifier = Modifier.fillMaxWidth().padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            OutlinedButton(onClick = { openLauncher.launch("*/*") }, modifier = Modifier.weight(1f)) {
                Text("Открыть файл...")
            }
            OutlinedButton(
                onClick = { saveLauncher.launch("profile") },
                enabled = editor != null,
                modifier = Modifier.weight(1f)
            ) {
                Text("Скачать файл...")
            }
        }

        Text(
            text = statusMsg,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(vertical = 4.dp)
        )

        val tabs = listOf("Персонажи", "Доски", "Кошелёк", "Статистика", "Покупки", "Сезон")
        ScrollableTabRow(selectedTabIndex = selectedTab, edgePadding = 0.dp) {
            tabs.forEachIndexed { idx, title ->
                Tab(selected = selectedTab == idx, onClick = { selectedTab = idx }, text = { Text(title) })
            }
        }

        val currentEditor = editor
        if (currentEditor == null) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("Загрузите профиль через Shizuku или выберите файл с телефона")
            }
        } else {
            key(refreshTrigger) {
                when (selectedTab) {
                    0 -> SurfersTab(currentEditor) { refreshTrigger++ }
                    1 -> BoardsTab(currentEditor) { refreshTrigger++ }
                    2 -> WalletTab(currentEditor) { refreshTrigger++ }
                    3 -> StatsTab(currentEditor)
                    4 -> PurchasesTab(currentEditor) { refreshTrigger++ }
                    5 -> SeasonTab(currentEditor)
                }
            }
        }
    }
}

@Composable
fun SurfersTab(editor: ProfileEditor, onUpdate: () -> Unit) {
    val surfers = remember { editor.getSurfers() }
    Column(modifier = Modifier.fillMaxSize()) {
        Row(modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = { editor.unlockAllSurfers(true); onUpdate() }, modifier = Modifier.weight(1f)) {
                Text("Открыть всех + скины")
            }
            OutlinedButton(onClick = { editor.unlockAllSurfers(false); onUpdate() }, modifier = Modifier.weight(1f)) {
                Text("Закрыть всех")
            }
        }
        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(surfers) { surfer ->
                val id = surfer.optInt("id")
                val name = GameData.surferNames[id] ?: id.toString()
                var unlocked by remember { mutableStateOf(surfer.optBoolean("isUnlocked", false)) }
                var levelStr by remember { mutableStateOf(surfer.optInt("level", 1).toString()) }
                var scoreStr by remember { mutableStateOf(surfer.optInt("highScore", 0).toString()) }
                val surferSkins = remember(id) { GameData.skins.filter { it.ownerId == id } }

                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(10.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(name, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                            Checkbox(checked = unlocked, onCheckedChange = {
                                unlocked = it
                                surfer.put("isUnlocked", it)
                                surfer.put("wasSeen", it)
                            })
                            Text("Открыт")
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedTextField(
                                value = levelStr,
                                onValueChange = {
                                    levelStr = it
                                    it.toIntOrNull()?.coerceIn(1, 20)?.let { lvl -> surfer.put("level", lvl) }
                                },
                                label = { Text("Уровень (1-20)") },
                                modifier = Modifier.weight(1f),
                                singleLine = true
                            )
                            OutlinedTextField(
                                value = scoreStr,
                                onValueChange = {
                                    scoreStr = it
                                    it.toIntOrNull()?.let { hs -> surfer.put("highScore", hs) }
                                },
                                label = { Text("Рекорд") },
                                modifier = Modifier.weight(1f),
                                singleLine = true
                            )
                        }
                        if (surferSkins.isNotEmpty()) {
                            Text("Скины:", style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(top = 6.dp))
                            surferSkins.forEach { skin ->
                                var skinUnl by remember { mutableStateOf(editor.isSkinUnlocked(skin.id)) }
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Checkbox(checked = skinUnl, onCheckedChange = {
                                        skinUnl = it
                                        editor.updateSkin(skin.id, it)
                                    })
                                    Text(skin.name, style = MaterialTheme.typography.bodySmall)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun BoardsTab(editor: ProfileEditor, onUpdate: () -> Unit) {
    val boards = remember { editor.getBoards() }
    Column(modifier = Modifier.fillMaxSize()) {
        Row(modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = { editor.unlockAllBoards(true); onUpdate() }, modifier = Modifier.weight(1f)) {
                Text("Открыть все доски")
            }
            OutlinedButton(onClick = { editor.unlockAllBoards(false); onUpdate() }, modifier = Modifier.weight(1f)) {
                Text("Закрыть все доски")
            }
        }
        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(boards) { board ->
                val bid = board.optInt("id")
                val bName = GameData.boardNames[bid] ?: bid.toString()
                val ownerName = GameData.boardOwner[bid]?.let { GameData.surferNames[it] }
                val title = if (ownerName != null) "$bName ($ownerName)" else bName
                var unlocked by remember { mutableStateOf(board.optBoolean("isUnlocked", false)) }
                var levelStr by remember { mutableStateOf(board.optInt("level", 1).toString()) }

                Card(modifier = Modifier.fillMaxWidth()) {
                    Row(modifier = Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(title, style = MaterialTheme.typography.titleSmall)
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Checkbox(checked = unlocked, onCheckedChange = {
                                    unlocked = it
                                    board.put("isUnlocked", it)
                                    board.put("wasSeen", it)
                                })
                                Text("Открыта")
                            }
                        }
                        OutlinedTextField(
                            value = levelStr,
                            onValueChange = {
                                levelStr = it
                                it.toIntOrNull()?.coerceIn(1, 20)?.let { lvl -> board.put("level", lvl) }
                            },
                            label = { Text("Ур. (1-20)") },
                            modifier = Modifier.width(110.dp),
                            singleLine = true
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun WalletTab(editor: ProfileEditor, onUpdate: () -> Unit) {
    val labels = mapOf(
        "coins" to "Монеты (Coins)",
        "keys" to "Ключи (Keys)",
        "revives" to "Воскрешения (Revives)",
        "board_tokens" to "Токены досок",
        "surfer_tokens" to "Токены персонажей",
        "tickets" to "Билеты (Tickets)"
    )
    LazyColumn(modifier = Modifier.fillMaxSize().padding(top = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        item {
            Button(
                onClick = {
                    GameData.walletTags.values.forEach { tag -> editor.setWalletItem(tag, 9999999) }
                    onUpdate()
                },
                modifier = Modifier.fillMaxWidth()
            ) { Text("Выдать по 9 999 999 всех ресурсов") }
        }
        items(GameData.walletTags.entries.toList()) { (key, tag) ->
            var valStr by remember { mutableStateOf(editor.getWalletItem(tag).toString()) }
            OutlinedTextField(
                value = valStr,
                onValueChange = {
                    valStr = it
                    it.toIntOrNull()?.let { v -> editor.setWalletItem(tag, v) }
                },
                label = { Text(labels[key] ?: key) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )
        }
    }
}

@Composable
fun StatsTab(editor: ProfileEditor) {
    LazyColumn(modifier = Modifier.fillMaxSize().padding(top = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        items(GameData.statKeys) { statKey ->
            var valStr by remember { mutableStateOf(editor.getStat(statKey).toString()) }
            OutlinedTextField(
                value = valStr,
                onValueChange = {
                    valStr = it
                    it.toIntOrNull()?.let { v -> editor.setStat(statKey, v) }
                },
                label = { Text(statKey) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )
        }
    }
}

@Composable
fun PurchasesTab(editor: ProfileEditor, onUpdate: () -> Unit) {
    var customId by remember { mutableStateOf(GameData.ALL_PURCHASE_IDS.first()) }
    var trailNum by remember { mutableStateOf("1") }
    val entries = remember { editor.getPurchaseEntries() }

    Column(modifier = Modifier.fillMaxSize().padding(top = 6.dp)) {
        OutlinedTextField(
            value = customId,
            onValueChange = { customId = it },
            label = { Text("ID покупки") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true
        )
        Row(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Button(onClick = { editor.addPurchase(customId); onUpdate() }, modifier = Modifier.weight(1f)) {
                Text("Добавить ID")
            }
            Button(onClick = { editor.addPurchase("shop.currency.keypack6"); onUpdate() }, modifier = Modifier.weight(1f)) {
                Text("Убрать рекламу")
            }
        }
        Row(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = trailNum,
                onValueChange = { trailNum = it },
                label = { Text("Трек (1-999)") },
                modifier = Modifier.width(130.dp),
                singleLine = true
            )
            Button(onClick = {
                val n = trailNum.toIntOrNull()
                if (n != null && n in 1..999) {
                    editor.addPurchase("districttrial.premiumladder.%03d".format(n))
                    onUpdate()
                }
            }, modifier = Modifier.weight(1f)) {
                Text("Добавить бонус-трек")
            }
        }
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Button(onClick = {
                GameData.ALL_PURCHASE_IDS.forEach { editor.addPurchase(it) }
                onUpdate()
            }, modifier = Modifier.weight(1f)) { Text("Добавить все") }
            OutlinedButton(onClick = { editor.clearPurchases(); onUpdate() }, modifier = Modifier.weight(1f)) {
                Text("Очистить все")
            }
        }
        Text("История покупок (${entries.size}):", style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(vertical = 6.dp))
        LazyColumn(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            items(entries) { (pid, date) ->
                Card(modifier = Modifier.fillMaxWidth()) {
                    Row(modifier = Modifier.padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(pid, style = MaterialTheme.typography.bodyMedium)
                            Text(date, style = MaterialTheme.typography.bodySmall)
                        }
                        TextButton(onClick = { editor.removePurchaseProduct(pid); onUpdate() }) {
                            Text("Удалить")
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun SeasonTab(editor: ProfileEditor) {
    var purchased by remember { mutableStateOf(editor.isSeasonPassPurchased()) }
    var pointsStr by remember { mutableStateOf(editor.getSeasonPassPoints().toString()) }

    Column(modifier = Modifier.fillMaxSize().padding(top = 12.d
