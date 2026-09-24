package com.spiritbox.app

/**
 * Comparação de versões de release, pura e testável.
 *
 * Aceita tags do GitHub como "1.2.3", "v1.2.3", "1.2" ou "1". A versão do app
 * (BuildConfig.VERSION_NAME) também é interpretada ("1.0" ou "1.0.0").
 */
object VersionComparator {

    data class SemVer(val major: Int, val minor: Int = 0, val patch: Int = 0) {
        override fun toString(): String = "$major.$minor.$patch"
    }

    /** Interpreta "v1.2.3" ou "1.2"; retorna null se não for uma versão válida. */
    fun parse(raw: String): SemVer? {
        var s = raw.trim().removePrefix("v").removePrefix("V")
        val i = s.indexOf('-')
        if (i >= 0) s = s.substring(0, i) // descarta sufixos tipo "-rc1"
        if (s.isBlank()) return null
        val parts = s.split('.')
        if (parts.size > 3) return null
        val nums = parts.map { it.toIntOrNull() ?: return null }
        if (nums.any { it < 0 }) return null
        return SemVer(nums[0], if (nums.size > 1) nums[1] else 0, if (nums.size > 2) nums[2] else 0)
    }

    /**
     * Retorna true se [latest] for estritamente mais novo que [current].
     * Sinais: 1.0 < 1.0.1 < 1.1 < 2.0. Tags iguais => false.
     */
    fun isNewer(latest: String?, current: String): Boolean {
        val l = latest?.let { parse(it) } ?: return false
        val c = parse(current) ?: return false
        return compare(l, c) > 0
    }

    fun compare(a: SemVer, b: SemVer): Int =
        when {
            a.major != b.major -> a.major.compareTo(b.major)
            a.minor != b.minor -> a.minor.compareTo(b.minor)
            else -> a.patch.compareTo(b.patch)
        }
}
