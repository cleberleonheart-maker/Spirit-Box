package com.spiritbox.app

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import org.json.JSONObject
import java.io.File
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
    private const val LATEST_REDIRECT = "https://github.com/$REPO/releases/latest"
    const val RELEASES_PAGE = "https://github.com/$REPO/releases"
    private const val APK_NAME = "app-release.apk"

    private const val COOLDOWN_MS = 24L * 60 * 60 * 1000 // 1 consulta automática por dia

    private val executor = Executors.newSingleThreadExecutor { r ->
        Thread(r, "spiritbox-update").apply { isDaemon = true }
    }

    data class UpdateInfo(
        val tag: String,
        val versionName: String,
        val htmlUrl: String,
        val apkUrl: String
    )

    /** Resultado da consulta. Separa "nao ha nada novo" de "a consulta falhou":
     * antes os dois chegavam como null e uma falha de rede aparecia como se
     * o app estivesse atualizado. */
    sealed class Result {
        object UpToDate : Result()
        object Unavailable : Result()
        data class Available(val info: UpdateInfo) : Result()
    }

    /**
     * Descobre a ultima release sem depender da API do GitHub.
     *
     * Head a `github.com/.../releases/latest` e le a tag do header Location.
     * Motivo: em varias redes (celular, wifi domestico) o `api.github.com` e
     * interceptado/redirect e devolve 404, deixando o auto-check sempre em
     * falha — o utilizador nunca via aviso de update. O `github.com` e
     * redirecionado corretamente para `/releases/tag/vX.Y.Z`.
     */
    private fun fetchLatestFromRedirect(): UpdateInfo? {
        var conn: HttpURLConnection? = null
        return try {
            conn = (URL(LATEST_REDIRECT).openConnection() as HttpURLConnection).apply {
                requestMethod = "HEAD"
                instanceFollowRedirects = false
                setRequestProperty("User-Agent", "SpiritBox-Android")
                connectTimeout = 10_000
                readTimeout = 10_000
            }
            val loc = conn.getHeaderField("Location").orEmpty()
            val tag = Regex("/releases/tag/([^/?#]+)").find(loc)?.groupValues?.get(1)
                ?: return null
            val name = tag.trimStart('v', 'V')
            UpdateInfo(
                tag = tag,
                versionName = name,
                htmlUrl = "https://github.com/$REPO/releases/tag/$tag",
                apkUrl = "https://github.com/$REPO/releases/download/$tag/$APK_NAME"
            )
        } catch (e: Exception) {
            Log.w(TAG, "Falha ao verificar atualização (redirect)", e)
            null
        } finally {
            conn?.disconnect()
        }
    }

    /** Deve rodar via [check] — nunca na main thread. */
    private fun fetchLatest(): UpdateInfo? {
        fetchLatestFromRedirect()?.let { return it }
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
            UpdateInfo(
                tag = tag,
                versionName = name,
                htmlUrl = html,
                apkUrl = "https://github.com/$REPO/releases/download/$tag/$APK_NAME"
            )
        } catch (e: Exception) {
            Log.w(TAG, "Falha ao verificar atualização", e)
            null
        } finally {
            conn?.disconnect()
        }
    }

    /**
     * Baixa o APK da release mais recente para o cache do app.
     * @param onProgress entregue na main thread com uma mensagem de progresso.
     * @param onResult entregue na main thread com o arquivo baixado, ou null.
     */
    fun downloadLatest(
        context: Context,
        info: UpdateInfo,
        onProgress: (String) -> Unit,
        onResult: (File?) -> Unit
    ) {
        executor.execute {
            var file: File? = null
            try {
                onProgress(context.getString(R.string.update_downloading))
                val target = File(context.cacheDir, "spiritbox-update.apk")
                val conn = URL(info.apkUrl).openConnection() as HttpURLConnection
                conn.instanceFollowRedirects = true
                conn.connectTimeout = 15_000
                conn.readTimeout = 30_000
                conn.setRequestProperty("User-Agent", "SpiritBox-Android")
                if (conn.responseCode in 200..299) {
                    conn.inputStream.use { input ->
                        target.outputStream().use { out -> input.copyTo(out) }
                    }
                    file = target
                } else {
                    Log.w(TAG, "Download APK: HTTP ${conn.responseCode}")
                }
                conn.disconnect()
            } catch (e: Exception) {
                Log.w(TAG, "Falha no download da atualização", e)
            }
            val result = file
            Handler(Looper.getMainLooper()).post { onResult(result) }
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
        onResult: (Result) -> Unit
    ) {
        val currentName = BuildConfig.VERSION_NAME
        if (!force) {
            val last = Prefs.lastUpdateCheckAt(context)
            if (System.currentTimeMillis() - last < COOLDOWN_MS) {
                Handler(Looper.getMainLooper()).post { onResult(Result.UpToDate) }
                return
            }
        }
        executor.execute {
            var result: Result = Result.Unavailable
            var fetched = false
            try {
                val latest = fetchLatest()
                fetched = latest != null
                result = when {
                    latest == null -> Result.Unavailable
                    VersionComparator.isNewer(latest.versionName, currentName) ->
                        Result.Available(latest)
                    else -> Result.UpToDate
                }
            } catch (e: Exception) {
                Log.w(TAG, "Falha na verificação de atualização", e)
            }
            // So marca o cooldown quando a consulta de fato respondeu: senao uma
            // falha de rede burava as 24 h e o usuario ficava o dia inteiro sem
            // checagem automatica.
            if (fetched) {
                try {
                    Prefs.markUpdateCheck(context)
                } catch (e: Exception) {
                    Log.w(TAG, "Falha ao gravar o cooldown", e)
                }
            }
            val out = result
            Handler(Looper.getMainLooper()).post { onResult(out) }
        }
    }
}
