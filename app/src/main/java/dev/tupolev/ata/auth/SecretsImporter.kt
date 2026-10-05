package dev.tupolev.ata.auth

import org.json.JSONObject

data class ImportResult(
    val username: Boolean,
    val aasToken: Boolean,
    val sharedKey: Boolean,
    val ownerKey: Boolean,
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

        return ImportResult(username != null, aas != null, shared != null, owner != null)
    }
}
