package com.spiritbox.app

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.Executors

/**
 * Verifica no GitHub se o [repo] tem uma release mais nova que a instalada
 * ([BuildConfig.VERSION_NAME / VERSION_CODE]) e entrega o resultado na
 * main thread via [onResult].
 *
 * - Rede e parsing rodam num executor daemon de thread única (nunca na main).
 * - Respeita um cooldown persistido em [Prefs] (intervalo mínimo entre
 *   consultas automáticas); use `force = true` para checar na hora.
 * - Se a API falhar (sem rede, rate-limit, repo inexistente) entrega null
 *   silenciosamente — nunca trava nem mostra erro.
 */
object UpdateChecker {

    private const val TAG = "SpiritBox"
    private const val REPO = "cleberleonheart-maker/Spirit-Box"
    private const val LATEST_API = "https://api.github.com/repos/$REPO/releases/latest"
    const val RELEASES_PAGE = "https://github.com/$REPO/releases"

    private const val COOLDOWN_MS = 24L * 60 * 60 * 1000 // 1 consulta automática por dia

    private val executor = Executors.newSingleThreadExecutor { r ->
        Thread(r, "spiritbox-update").apply { isDaemon = true }
    }

    data class UpdateInfo(
        val tag: String,
        val versionName: String,
        val htmlUrl: String
    )

    /** Deve rodar via [check] — nunca na main thread. */
    private fun fetchLatest(): UpdateInfo? {
        var conn: HttpURLConnection? = null
        return try {
            conn = (URL(LATEST_API).openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                setRequestProperty("Accept", "application/vnd.github+json")
                setRequestProperty("User-Agent", "SpiritBox-Android")
                connectTimeout = 10_000
                readTimeout = 10_000
            }
            if (conn.responseCode != 200) {
                Log.w(TAG, "GitHub releases: HTTP ${conn.responseCode}")
                return null
            }
            val body = conn.inputStream.bufferedReader().use { it.readText() }
            val json = JSONObject(body)
            val tag = json.optString("tag_name").ifBlank { return null }
            val html = json.optString("html_url").ifBlank { return null }
            val name = tag.trimStart('v', 'V')
            UpdateInfo(tag, name, html)
        } catch (e: Exception) {
            Log.w(TAG, "Falha ao verificar atualização", e)
            null
        } finally {
            conn?.disconnect()
        }
    }

    /**
     * Compara a release mais recente com a versão instalada e notifica.
     * @param onResult entregue na main thread: `UpdateInfo` se houver versão
     *   nova, `null` caso contrário.
     */
    fun check(
        context: Context,
        force: Boolean = false,
        onResult: (UpdateInfo?) -> Unit
    ) {
        val currentName = BuildConfig.VERSION_NAME
        if (!force) {
            val last = Prefs.lastUpdateCheckAt(context)
            if (System.currentTimeMillis() - last < COOLDOWN_MS) return
        }
        executor.execute {
            var info: UpdateInfo? = null
            try {
                val latest = fetchLatest()
                if (latest != null && VersionComparator.isNewer(latest.versionName, currentName)) {
                    info = latest
                }
                Prefs.markUpdateCheck(context)
            } catch (e: Exception) {
                Log.w(TAG, "Falha na verificação de atualização", e)
            }
            val result = info
            Handler(Looper.getMainLooper()).post { onResult(result) }
        }
    }
}
