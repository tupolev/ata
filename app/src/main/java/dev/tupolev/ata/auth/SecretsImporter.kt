package dev.tupolev.ata.auth

import org.json.JSONObject

data class ImportResult(
    val username: Boolean,
    val aasToken: Boolean,
    val sharedKey: Boolean,
    val ownerKey: Boolean,
    val fcmCredentials: Boolean,
) {
    val usable: Boolean get() = aasToken && ownerKey
}

object SecretsImporter {
    fun import(jsonText: String, storage: TokenStorage): ImportResult {
        val json = JSONObject(jsonText)
        val username = json.optString("username").takeIf { it.isNotBlank() }
        val aas = json.optString("aas_token").takeIf { it.isNotBlank() }
        val shared = json.optString("shared_key").takeIf { it.isNotBlank() }
        val owner = json.optString("owner_key").takeIf { it.isNotBlank() }

        username?.let(storage::saveEmail)
        aas?.let(storage::saveAasToken)
        shared?.let(storage::saveSharedKey)
        owner?.let(storage::saveOwnerKey)

        val fcmImported = importFcmCredentials(json.optJSONObject("fcm_credentials"), storage)

        return ImportResult(
            username = username != null,
            aasToken = aas != null,
            sharedKey = shared != null,
            ownerKey = owner != null,
            fcmCredentials = fcmImported,
        )
    }

    private fun importFcmCredentials(source: JSONObject?, storage: TokenStorage): Boolean {
        if (source == null) return false

        return try {
            val keys = source.getJSONObject("keys")
            val gcm = source.getJSONObject("gcm")
            val fcm = source.getJSONObject("fcm")
            val registration = fcm.getJSONObject("registration")

            val converted = JSONObject().apply {
                put("gcmAndroidId", gcm.get("android_id").toString())
                put("gcmSecurityToken", gcm.get("security_token").toString())
                put("gcmToken", gcm.getString("token"))
                put("gcmAppId", gcm.getString("app_id"))
                put("fcmToken", registration.getString("token"))
                put("publicKey", keys.getString("public"))
                put("privateKey", keys.getString("private"))
                put("authSecret", keys.getString("secret"))
            }

            storage.saveFcmCredentials(converted.toString())
            true
        } catch (_: Exception) {
            false
        }
    }
}
