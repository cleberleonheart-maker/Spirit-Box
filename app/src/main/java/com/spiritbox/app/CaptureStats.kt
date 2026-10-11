package com.spiritbox.app

/**
 * Estatísticas agregadas do histórico de capturas. Pura, para ser testada.
 *
 * Reusa o agrupamento por frequência ([CaptureGroups]) e expõe um resumo com o
 * total de capturas, quantas frequências distintas apareceram e as "top"
 * frequências (mais avistamentos e, no empate, maior nível).
 */
object CaptureStats {

    data class Summary(
        val total: Int,
        val distinct: Int,
        val top: List<FrequencyGroup>
    )

    fun summarize(captures: List<Sighting>, limit: Int = 5): Summary {
        val groups = CaptureGroups.group(captures)
        val top = groups
            .sortedWith(
                compareByDescending<FrequencyGroup> { it.count }
                    .thenByDescending { it.maxLevel }
            )
            .take(limit.coerceAtLeast(0))
        return Summary(captures.size, groups.size, top)
    }

    /** Frequência mais forte do histórico (maior nível); empate vai para a mais recente. */
    fun peak(captures: List<Sighting>): Sighting? =
        captures.maxWithOrNull(compareBy<Sighting> { it.level }.thenBy { it.time })
}
