@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
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
                Surface(Modifier.fillMaxSize()) { SubEditApp(this) }
            }
        }
    }
}

@Composable
fun SubEditApp(act: ComponentActivity) {
    var ed by remember { mutableStateOf<ProfileEditor?>(null) }
    var trig by remember { mutableIntStateOf(0) }
    var tab by remember { mutableIntStateOf(0) }
    var msg by remember { mutableStateOf("Профиль не загружен") }
    var path by remember { mutableStateOf(ShizukuHelper.CITY_PROFILE_PATH) }
    var mode by remember { mutableIntStateOf(0) }

    val openDoc = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { u: Uri? ->
        u?.let {
            try {
                val b = act.contentResolver.openInputStream(it)?.readBytes() ?: return@let
                val p = ProfileCrypto.decryptProfile(b)
                ed = ProfileEditor(p.iv, p.key, p.rootData)
                trig++
                msg = "Файл открыт"
            } catch (e: Exception) { msg = "Ошибка: ${e.message}" }
        }
    }

    val saveDoc = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/octet-stream")) { u: Uri? ->
        u?.let {
            try {
                val cur = ed ?: return@let
                val out = ProfileCrypto.encryptProfile(cur.iv, cur.key, cur.buildRootData())
                act.contentResolver.openOutputStream(it)?.use { s -> s.write(out) }
                msg = "Файл сохранён!"
            } catch (e: Exception) { msg = "Ошибка: ${e.message}" }
        }
    }

    Column(Modifier.fillMaxSize().padding(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            FilterChip(
                selected = mode == 0,
                onClick = { mode = 0; path = ShizukuHelper.CITY_PROFILE_PATH },
                label = { Text("Subway City") },
                modifier = Modifier.padding(end = 8.dp)
            )
            FilterChip(
                selected = mode == 1,
                onClick = { mode = 1; path = "/sdcard/Android/data/com.kiloo.subwaysurf/files/profile/wallet.json" },
                label = { Text("Original Subway") }
            )
        }

        OutlinedTextField(
            value = path,
            onValueChange = { path = it },
            label = { Text("Путь Shizuku") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
        )

        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Button(onClick = {
                if (ShizukuHelper.hasPermission()) {
                    try {
                        val b = ShizukuHelper.readFileViaShizuku(act, path)
                        val p = ProfileCrypto.decryptProfile(b)
                        ed = ProfileEditor(p.iv, p.key, p.rootData)
                        trig++
                        msg = "Загружено через Shizuku!"
                    } catch (e: Exception) { msg = e.message ?: "Ошибка" }
                } else msg = "Выдайте права в Shizuku"
            }, Modifier.weight(1f)) { Text("Shizuku: Взять") }

            Button(onClick = {
                val cur = ed ?: return@Button
                if (ShizukuHelper.hasPermission()) {
                    try {
                        val b = ProfileCrypto.encryptProfile(cur.iv, cur.key, cur.buildRootData())
                        ShizukuHelper.writeFileViaShizuku(act, path, b)
                        msg = "Сохранено в игру!"
                        Toast.makeText(act, msg, Toast.LENGTH_SHORT).show()
                    } catch (e: Exception) { msg = e.message ?: "Ошибка" }
                }
            }, enabled = ed != null, modifier = Modifier.weight(1f)) { Text("Shizuku: Залить") }
        }

        Row(Modifier.fillMaxWidth().padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            OutlinedButton(onClick = { openDoc.launch("*/*") }, Modifier.weight(1f)) { Text("Открыть файл") }
            OutlinedButton(onClick = { saveDoc.launch("profile") }, enabled = ed != null, modifier = Modifier.weight(1f)) { Text("Скачать файл") }
        }

        Text(msg, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(vertical = 4.dp))

        val tabs = listOf("Персонажи", "Доски", "Кошелёк", "Статы", "Покупки", "Сезон")
        ScrollableTabRow(selectedTabIndex = tab, edgePadding = 0.dp) {
            tabs.forEachIndexed { i, t -> Tab(selected = tab == i, onClick = { tab = i }, text = { Text(t) }) }
        }

        val cur = ed
        if (cur == null) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text("Загрузите профиль") }
        } else {
            key(trig) {
                when (tab) {
                    0 -> SurfersTab(cur) { trig++ }
                    1 -> BoardsTab(cur) { trig++ }
                    2 -> WalletTab(cur) { trig++ }
                    3 -> StatsTab(cur)
                    4 -> PurchasesTab(cur) { trig++ }
                    5 -> SeasonTab(cur)
                }
            }
        }
    }
}

@Composable
fun SurfersTab(ed: ProfileEditor, up: () -> Unit) {
    val list = remember { ed.getSurfers() }
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = { ed.unlockAllSurfers(true); up() }, Modifier.weight(1f)) { Text("Открыть всех") }
            OutlinedButton(onClick = { ed.unlockAllSurfers(false); up() }, Modifier.weight(1f)) { Text("Закрыть всех") }
        }
        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(list) { s ->
                val id = s.optInt("id")
                val name = GameData.surferNames[id] ?: id.toString()
                var unl by remember { mutableStateOf(s.optBoolean("isUnlocked", false)) }
                var lvl by remember { mutableStateOf(s.optInt("level", 1).toString()) }
                var hs by remember { mutableStateOf(s.optInt("highScore", 0).toString()) }
                val skins = remember(id) { GameData.skins.filter { it.ownerId == id } }

                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(10.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(name, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                            Checkbox(checked = unl, onCheckedChange = { unl = it; s.put("isUnlocked", it); s.put("wasSeen", it) })
                            Text("Открыт")
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedTextField(
                                value = lvl,
                                onValueChange = { lvl = it; it.toIntOrNull()?.coerceIn(1, 20)?.let { v -> s.put("level", v) } },
                                label = { Text("Уровень") },
                                modifier = Modifier.weight(1f),
                                singleLine = true
                            )
                            OutlinedTextField(
                                value = hs,
                                onValueChange = { hs = it; it.toIntOrNull()?.let { v -> s.put("highScore", v) } },
                                label = { Text("Рекорд") },
                                modifier = Modifier.weight(1f),
                                singleLine = true
                            )
                        }
                        skins.forEach { sk ->
                            var skUnl by remember { mutableStateOf(ed.isSkinUnlocked(sk.id)) }
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Checkbox(checked = skUnl, onCheckedChange = { skUnl = it; ed.updateSkin(sk.id, it) })
                                Text(sk.name, style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun BoardsTab(ed: ProfileEditor, up: () -> Unit) {
    val list = remember { ed.getBoards() }
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = { ed.unlockAllBoards(true); up() }, Modifier.weight(1f)) { Text("Открыть доски") }
            OutlinedButton(onClick = { ed.unlockAllBoards(false); up() }, Modifier.weight(1f)) { Text("Закрыть доски") }
        }
        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(list) { b ->
                val id = b.optInt("id")
                val bName = GameData.boardNames[id] ?: id.toString()
                val owner = GameData.boardOwner[id]?.let { GameData.surferNames[it] }
                val title = if (owner != null) "$bName ($owner)" else bName
                var unl by remember { mutableStateOf(b.optBoolean("isUnlocked", false)) }
                var lvl by remember { mutableStateOf(b.optInt("level", 1).toString()) }

                Card(Modifier.fillMaxWidth()) {
                    Row(Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(title, style = MaterialTheme.typography.titleSmall)
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Checkbox(checked = unl, onCheckedChange = { unl = it; b.put("isUnlocked", it); b.put("wasSeen", it) })
                                Text("Открыта")
                            }
                        }
                        OutlinedTextField(
                            value = lvl,
                            onValueChange = { lvl = it; it.toIntOrNull()?.coerceIn(1, 20)?.let { v -> b.put("level", v) } },
                            label = { Text("Ур.") },
                            modifier = Modifier.width(100.dp),
                            singleLine = true
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun WalletTab(ed: ProfileEditor, up: () -> Unit) {
    LazyColumn(Modifier.fillMaxSize().padding(top = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        item {
            Button(onClick = { GameData.walletTags.values.forEach { ed.setWalletItem(it, 9999999) }; up() }, Modifier.fillMaxWidth()) {
                Text("Выдать по 9 999 999 всего")
            }
        }
        items(GameData.walletTags.entries.toList()) { (k, tag) ->
            var vStr by remember { mutableStateOf(ed.getWalletItem(tag).toString()) }
            OutlinedTextField(
                value = vStr,
                onValueChange = { vStr = it; it.toIntOrNull()?.let { v -> ed.setWalletItem(tag, v) } },
                label = { Text(k) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )
        }
    }
}

@Composable
fun StatsTab(ed: ProfileEditor) {
    LazyColumn(Modifier.fillMaxSize().padding(top = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        items(GameData.statKeys) { k ->
            var vStr by remember { mutableStateOf(ed.getStat(k).toString()) }
            OutlinedTextField(
                value = vStr,
                onValueChange = { vStr = it; it.toIntOrNull()?.let { v -> ed.setStat(k, v) } },
                label = { Text(k) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )
        }
    }
}

@Composable
fun PurchasesTab(ed: ProfileEditor, up: () -> Unit) {
    var cid by remember { mutableStateOf(GameData.ALL_PURCHASE_IDS.first()) }
    var trNum by remember { mutableStateOf("1") }
    val list = remember { ed.getPurchaseEntries() }

    Column(Modifier.fillMaxSize().padding(top = 6.dp)) {
        OutlinedTextField(value = cid, onValueChange = { cid = it }, label = { Text("ID покупки") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
        Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Button(onClick = { ed.addPurchase(cid); up() }, Modifier.weight(1f)) { Text("Добавить ID") }
            Button(onClick = { ed.addPurchase("shop.currency.keypack6"); up() }, Modifier.weight(1f)) { Text("Без рекламы") }
        }
        Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(value = trNum, onValueChange = { trNum = it }, label = { Text("Трек 1-999") }, modifier = Modifier.width(120.dp), singleLine = true)
            Button(onClick = {
                val n = trNum.toIntOrNull()
                if (n != null && n in 1..999) {
                    val numStr = n.toString().padStart(3, '0')
                    ed.addPurchase("districttrial.premiumladder." + numStr)
                    up()
                }
            }, Modifier.weight(1f)) { Text("Бонус-трек") }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Button(onClick = { GameData.ALL_PURCHASE_IDS.forEach { ed.addPurchase(it) }; up() }, Modifier.weight(1f)) { Text("Добавить все") }
            OutlinedButton(onClick = { ed.clearPurchases(); up() }, Modifier.weight(1f)) { Text("Очистить") }
        }
        LazyColumn(Modifier.padding(top = 6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            items(list) { (pid, dt) ->
                Card(Modifier.fillMaxWidth()) {
                    Row(Modifier.padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(pid, style = MaterialTheme.typography.bodyMedium)
                            Text(dt, style = MaterialTheme.typography.bodySmall)
                        }
                        TextButton(onClick = { ed.removePurchaseProduct(pid); up() }) { Text("X") }
                    }
                }
            }
        }
    }
}

@Composable
fun SeasonTab(ed: ProfileEditor) {
    var bought by remember { mutableStateOf(ed.isSeasonPassPurchased()) }
    var pts by remember { mutableStateOf(ed.getSeasonPassPoints().toString()) }
    Column(Modifier.fillMaxSize().padding(top = 12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Switch(checked = bought, onCheckedChange = { bought = it; ed.setSeasonPass(it, pts.toIntOrNull() ?: 0) })
            Spacer(Modifier.width(12.dp))
            Text("Сезонный пропуск куплен")
        }
        OutlinedTextField(
            value = pts,
            onValueChange = { pts = it; ed.setSeasonPass(bought, it.toIntOrNull() ?: 0) },
            label = { Text("Очки сезона") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true
        )
    }
}
