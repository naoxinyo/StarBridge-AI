package com.dshbox.app.models

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.AtomicFile
import android.util.Base64
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/** Device keys stay encrypted in app-private preferences; overlays contain only references. */
class ModelProviderStore(context: Context) {
    private val appContext = context.applicationContext
    private val prefs = appContext.getSharedPreferences("starbridge_model_providers", Context.MODE_PRIVATE)

    fun profiles(): List<ModelProviderProfile> = synchronized(lock) {
        val rows = JSONArray(prefs.getString("profiles", "[]"))
        (0 until rows.length()).map { index ->
            val row = rows.getJSONObject(index)
            val models = row.getJSONArray("models")
            ModelProviderProfile(row.getString("id"), row.getBoolean("enabled"),
                row.getString("baseUrl"), row.getString("protocol"),
                (0 until models.length()).map { models.getString(it) })
        }
    }

    fun hasKey(id: String): Boolean = prefs.contains("key.$id")

    fun save(profile: ModelProviderProfile, newKey: String, clearKey: Boolean) = synchronized(lock) {
        ModelProviders.validate(profile)
        require(!profile.enabled || newKey.isNotBlank() || (!clearKey && hasKey(profile.id))) { "请先填写 API Key，或关闭启用开关后保存" }
        require(newKey.none(Char::isISOControl)) { "API Key 不能包含换行或控制字符" }
        val updated = profiles().filterNot { it.id == profile.id } + profile
        val edit = prefs.edit().putString("profiles", encode(updated))
        if (clearKey) edit.remove("key.${profile.id}")
        if (newKey.isNotBlank()) edit.putString("key.${profile.id}", encrypt(profile.id, newKey))
        check(edit.commit()) { "配置保存失败" }
        refreshOverlay()
    }

    fun remove(id: String) = synchronized(lock) {
        check(prefs.edit().putString("profiles", encode(profiles().filterNot { it.id == id }))
            .remove("key.$id").commit()) { "配置删除失败" }
        refreshOverlay()
    }

    fun refreshOverlay() = synchronized(lock) {
        val file = File(File(appContext.filesDir, "user-data"), ModelProviders.OVERLAY_RELATIVE_PATH)
        check(file.parentFile!!.isDirectory || file.parentFile!!.mkdirs()) { "无法创建模型配置目录" }
        val atomic = AtomicFile(file)
        val output = atomic.startWrite()
        try {
            file.setReadable(false, false); file.setWritable(false, false)
            file.setReadable(true, true); file.setWritable(true, true)
            output.write(ModelProviders.renderOverlay(profiles()).toByteArray(Charsets.UTF_8))
            atomic.finishWrite(output)
        } catch (failure: Throwable) {
            atomic.failWrite(output)
            throw failure
        }
    }

    /** Called only for the DSH process, after normal environment assembly. */
    fun environment(): Map<String, String> = synchronized(lock) {
        refreshOverlay()
        buildMap {
            profiles().filter { it.enabled }.forEach { profile ->
                prefs.getString("key.${profile.id}", null)?.let { encrypted ->
                    put(ModelProviders.keyRef(profile.id), decrypt(profile.id, encrypted))
                }
            }
        }
    }

    private fun encode(profiles: List<ModelProviderProfile>): String = JSONArray().apply {
        profiles.forEach { profile -> put(JSONObject().put("id", profile.id).put("enabled", profile.enabled)
            .put("baseUrl", profile.baseUrl).put("protocol", profile.protocol).put("models", JSONArray(profile.models))) }
    }.toString()

    private fun key(): SecretKey {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (store.getKey(KEY_ALIAS, null) as? SecretKey)?.let { return it }
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").apply {
            init(KeyGenParameterSpec.Builder(KEY_ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).build())
        }.generateKey()
    }

    private fun encrypt(id: String, secret: String): String {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key())
        cipher.updateAAD(id.toByteArray(Charsets.UTF_8))
        val bytes = cipher.doFinal(secret.toByteArray(Charsets.UTF_8))
        return Base64.encodeToString(cipher.iv, Base64.NO_WRAP) + ":" + Base64.encodeToString(bytes, Base64.NO_WRAP)
    }

    private fun decrypt(id: String, encrypted: String): String {
        val parts = encrypted.split(':', limit = 2)
        require(parts.size == 2) { "密钥格式损坏，请重新配置" }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, Base64.decode(parts[0], Base64.NO_WRAP)))
        cipher.updateAAD(id.toByteArray(Charsets.UTF_8))
        return String(cipher.doFinal(Base64.decode(parts[1], Base64.NO_WRAP)), Charsets.UTF_8)
    }

    companion object {
        private const val KEY_ALIAS = "starbridge.model-providers.v1"
        private val lock = Any()
    }
}
