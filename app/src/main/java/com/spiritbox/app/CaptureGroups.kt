package com.spiritbox.app

/** Uma captura individual, reduzida ao que o agrupamento precisa. */
data class Sighting(
    val freqKHz: Double,
    val level: Int,
    val time: Long
)

/** Uma frequência agrupada: quantas vezes apareceu, maior nível (dB) e os horários
 * da primeira e da última captura. */
data class FrequencyGroup(
    val freqKHz: Double,
    val count: Int,
    val maxLevel: Int,
    val firstTime: Long,
    val lastTime: Long
)

/**
 * Agrupa capturas por frequência para o histórico. Pura, para ser testada.
 *
 * Capturas dentro de [TOLERANCE_KHZ] viram uma linha só (a cooldown do motor já
 * evita repeteco da mesma frequência em pouco tempo; a tolerância cobre pequenos
 * desvios do arredondamento do passo). A ordem de saída é por [FrequencyGroup.lastTime]
 * crescente, então a atividade mais recente fica no fim — igual à lista normal.
 */
object CaptureGroups {

    const val TOLERANCE_KHZ = 1.0

    private class Bucket(var g: FrequencyGroup)

    fun group(captures: List<Sighting>): List<FrequencyGroup> {
        val buckets = ArrayList<Bucket>()
        for (c in captures) {
            val existing = buckets.firstOrNull {
                kotlin.math.abs(it.g.freqKHz - c.freqKHz) <= TOLERANCE_KHZ
            }
            if (existing == null) {
                buckets.add(Bucket(FrequencyGroup(c.freqKHz, 1, c.level, c.time, c.time)))
            } else {
                val g = existing.g
                existing.g = g.copy(
                    count = g.count + 1,
                    maxLevel = if (c.level > g.maxLevel) c.level else g.maxLevel,
                    firstTime = if (c.time < g.firstTime) c.time else g.firstTime,
                    lastTime = if (c.time > g.lastTime) c.time else g.lastTime
                )
            }
        }
        return buckets.map { it.g }.sortedBy { it.lastTime }
    }
}
