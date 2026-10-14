package com.subedit.app

import android.content.Context
import android.content.pm.PackageManager
import dev.rikka.shizuku.Shizuku
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import javax.crypto.Cipher
import javax.crypto.spec.SecretKeySpec

object ProfileCrypto {
    data class DecryptedPayload(val iv: ByteArray, val key: ByteArray, val rootData: JSONObject)

    fun decryptProfile(raw: ByteArray): DecryptedPayload {
        if (raw.size < 32) throw IllegalArgumentException("Файл слишком мал (< 32 байт)")
        val iv = raw.copyOfRange(0, 16)
        val key = raw.copyOfRange(16, 32)
        val encrypted = raw.copyOfRange(32, raw.size)

        val cipher = Cipher.getInstance("AES/ECB/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(key, "AES"))

        val counter = iv.copyOf()
        val decrypted = ByteArray(encrypted.size)

        for (offset in encrypted.indices step 16) {
            val blockLen = minOf(16, encrypted.size - offset)
            val encryptedCounter = cipher.doFinal(counter)
            for (i in 0 until blockLen) {
                decrypted[offset + i] = (encrypted[offset + i].toInt() xor encryptedCounter[i].toInt()).toByte()
            }
            incrementCounter(counter)
        }

        val jsonStr = String(decrypted, Charsets.UTF_8)
        return DecryptedPayload(iv, key, JSONObject(jsonStr))
    }

    fun encryptProfile(iv: ByteArray, key: ByteArray, rootData: JSONObject): ByteArray {
        val plain = rootData.toString().toByteArray(Charsets.UTF_8)
        val cipher = Cipher.getInstance("AES/ECB/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(key, "AES"))

        val counter = iv.copyOf()
        val encrypted = ByteArray(plain.size)

        for (offset in plain.indices step 16) {
            val blockLen = minOf(16, plain.size - offset)
            val encryptedCounter = cipher.doFinal(counter)
            for (i in 0 until blockLen) {
                encrypted[offset + i] = (plain[offset + i].toInt() xor encryptedCounter[i].toInt()).toByte()
            }
            incrementCounter(counter)
        }

        val out = ByteArray(32 + encrypted.size)
        System.arraycopy(iv, 0, out, 0, 16)
        System.arraycopy(key, 0, out, 16, 16)
        System.arraycopy(encrypted, 0, out, 32, encrypted.size)
        return out
    }

    private fun incrementCounter(counter: ByteArray) {
        var carry = 1
        for (i in 15 downTo 8) {
            val s = (counter[i].toInt() and 0xFF) + carry
            counter[i] = (s and 0xFF).toByte()
            carry = s ushr 8
            if (carry == 0) break
        }
    }
}

class ProfileEditor(val iv: ByteArray, val key: ByteArray, private val rootData: JSONObject) {
    val profile: JSONObject = if (rootData.has("profile") && rootData.opt("profile") is String) {
        JSONObject(rootData.getString("profile"))
    } else {
        rootData
    }

    init {
        ensureAllSurfers()
        ensureAllBoards()
    }

    fun buildRootData(): JSONObject {
        if (rootData.has("profile")) {
            rootData.put("profile", profile.toString())
            return rootData
        }
        return profile
    }

    private fun ensureAllSurfers() {
        val arr = profile.optJSONArray("surferProfiles") ?: JSONArray().also { profile.put("surferProfiles", it) }
        val existing = mutableSetOf<Int>()
        for (i in 0 until arr.length()) existing.add(arr.getJSONObject(i).optInt("id"))
        for ((id, _) in GameData.surferNames) {
            if (id !in existing) {
                arr.put(JSONObject().apply {
                    put("id", id)
                    put("level", 1)
                    put("isUnlocked", false)
                    put("wasSeen", false)
                    put("highScore", 0)
                    put("selectedSkin", 0)
                })
            }
        }
    }

    private fun ensureAllBoards() {
        val arr = profile.optJSONArray("boardProfiles") ?: JSONArray().also { profile.put("boardProfiles", it) }
        val existing = mutableSetOf<Int>()
        for (i in 0 until arr.length()) existing.add(arr.getJSONObject(i).optInt("id"))
        for ((id, _) in GameData.boardNames) {
            if (id !in existing) {
                arr.put(JSONObject().apply {
                    put("id", id)
                    put("level", 1)
                    put("isUnlocked", false)
                    put("wasSeen", false)
                })
            }
        }
    }

    fun getSurfers(): List<JSONObject> {
        val arr = profile.optJSONArray("surferProfiles") ?: return emptyList()
        return (0 until arr.length()).map { arr.getJSONObject(it) }
    }

    fun unlockAllSurfers(unlock: Boolean) {
        getSurfers().forEach {
            it.put("isUnlocked", unlock)
            it.put("wasSeen", unlock)
        }
        GameData.skins.forEach { updateSkin(it.id, unlock) }
    }

    fun isSkinUnlocked(skinId: Int): Boolean {
        val arr = profile.optJSONArray("surferSkinProfiles") ?: return false
        for (i in 0 until arr.length()) {
            val s = arr.getJSONObject(i)
            if (s.optInt("id") == skinId) return s.optBoolean("isUnlocked", false)
        }
        return false
    }

    fun updateSkin(skinId: Int, unlocked: Boolean) {
        val arr = profile.optJSONArray("surferSkinProfiles") ?: JSONArray().also { profile.put("surferSkinProfiles", it) }
        for (i in 0 until arr.length()) {
            val s = arr.getJSONObject(i)
            if (s.optInt("id") == skinId) {
                s.put("isUnlocked", unlocked)
                s.put("wasSeen", unlocked)
                return
            }
        }
        arr.put(JSONObject().apply {
            put("id", skinId)
            put("isUnlocked", unlocked)
            put("wasSeen", unlocked)
        })
    }

    fun getBoards(): List<JSONObject> {
        val arr = profile.optJSONArray("boardProfiles") ?: return emptyList()
        return (0 until arr.length()).map { arr.getJSONObject(it) }
    }

    fun unlockAllBoards(unlock: Boolean) {
        getBoards().forEach {
            it.put("isUnlocked", unlock)
            it.put("wasSeen", unlock)
        }
    }

    fun getWalletItem(tag: Int): Int {
        val arr = profile.optJSONArray("wallet") ?: return 0
        for (i in 0 until arr.length()) {
            val item = arr.getJSONObject(i)
            if (item.optInt("dataTag") == tag) return item.optInt("count", 0)
        }
        return 0
    }

    fun setWalletItem(tag: Int, count: Int) {
        val arr = profile.optJSONArray("wallet") ?: JSONArray().also { profile.put("wallet", it) }
        for (i in 0 until arr.length()) {
            val item = arr.getJSONObject(i)
            if (item.optInt("dataTag") == tag) {
                item.put("count", count)
                return
            }
        }
        arr.put(JSONObject().apply {
            put("dataTag", tag)
            put("count", count)
        })
    }

    fun getStat(key: String): Int = profile.optInt(key, 0)
    fun setStat(key: String, value: Int) { profile.put(key, value) }

    fun getPurchaseEntries(): List<Pair<String, String>> {
        val hist = profile.optJSONObject("purchaseHistory") ?: return emptyList()
        val list = mutableListOf<Pair<String, String>>()
        val keys = hist.keys()
        while (keys.hasNext()) {
            val pid = keys.next()
            val dates = hist.optJSONObject(pid)?.optJSONArray("PurchaseDates") ?: continue
            for (i in 0 until dates.length()) {
                list.add(pid to dates.getString(i))
            }
        }
        return list
    }

    fun addPurchase(productId: String) {
        val now = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSSSSSS'Z'")
            .withZone(ZoneOffset.UTC)
            .format(Instant.now())
        val hist = profile.optJSONObject("purchaseHistory") ?: JSONObject().also { profile.put("purchaseHistory", it) }
        if (hist.has(productId)) {
            val entry = hist.getJSONObject(productId)
            val dates = entry.optJSONArray("PurchaseDates") ?: JSONArray().also { entry.put("PurchaseDates", it) }
            dates.put(now)
            entry.put("PurchaseCount", dates.length())
        } else {
            hist.put(productId, JSONObject().apply {
                put("PurchaseCount", 1)
                put("PurchaseDates", JSONArray().put(now))
            })
        }
    }

    fun removePurchaseProduct(productId: String) {
        profile.optJSONObject("purchaseHistory")?.remove(productId)
    }

    fun clearPurchases() {
        profile.put("purchaseHistory", JSONObject())
    }

    fun isSeasonPassPurchased(): Boolean = profile.optBoolean("seasonPassPurchased", false)
    fun getSeasonPassPoints(): Int = profile.optInt("seasonPassPoints", 0)
    fun setSeasonPass(purchased: Boolean, points: Int) {
        profile.put("seasonPassPurchased", purchased)
        profile.put("seasonPassPoints", points)
    }
}

object ShizukuHelper {
    const val CITY_PROFILE_PATH = "/sdcard/Android/data/com.sybogames.subway.surfers.city/files/profile"

    fun isAvailable(): Boolean = try { Shizuku.pingBinder() } catch (e: Exception) { false }

    fun hasPermission(): Boolean {
        if (!isAvailable()) return false
        return if (Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED) {
            true
        } else {
            Shizuku.requestPermission(1001)
            false
        }
    }

    private fun execShizuku(cmd: String): Int {
        val method = Shizuku::class.java.getDeclaredMethod(
            "newProcess",
            Array<String>::class.java,
            Array<String>::class.java,
            String::class.java
        )
        method.isAccessible = true
        val process = method.invoke(null, arrayOf("sh", "-c", cmd), null, null) as Process
        return process.waitFor()
    }

    fun readFileViaShizuku(context: Context, remotePath: String): ByteArray {
        val tempFile = File(context.cacheDir, "shizuku_temp_profile")
        if (tempFile.exists()) tempFile.delete()
        val code = execShizuku("cp \"$remotePath\" \"${tempFile.absolutePath}\" && chmod 666 \"${tempFile.absolutePath}\"")
        if (code != 0 || !tempFile.exists()) {
            throw IllegalStateException("Не удалось прочитать $remotePath (код $code). Проверь путь и доступ Shizuku.")
        }
        return tempFile.readBytes()
    }

    fun writeFileViaShizuku(context: Context, remotePath: String, bytes: ByteArray) {
        val tempFile = File(context.cacheDir, "shizuku_temp_out")
        tempFile.writeBytes(bytes)
        val code = execShizuku("cp \"${tempFile.absolutePath}\" \"$remotePath\"")
        if (code != 0) {
            throw IllegalStateException("Ошибка записи в $remotePath (код $code)")
        }
    }
}
