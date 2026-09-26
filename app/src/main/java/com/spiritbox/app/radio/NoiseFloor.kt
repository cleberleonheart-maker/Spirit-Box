package com.spiritbox.app.radio

import kotlin.math.min

/**
 * Piso de ruido de uma faixa, estimado por percentil das ultimas [capacity]
 * passagens de dwell.
 *
 * O limiar de captura e absoluto (padrao 12%), mas o ruido de fundo muda muito de
 * uma faixa para outra: 225-400 MHz tem piso alto e AM tem piso baixo. Com o mesmo
 * numero nas duas, a faixa barulhenta enche o historico de falsos positivos e a
 * silenciosa nunca captura. Aqui a referencia e o proprio ruido medido.
 *
 * Usa percentil baixo (nao media) de proposito: numa varredura normal o sinal
 * occupy uma minoria das frequencias, entao a media sobe junto com cada captura e
 * o piso passa a enxergar justamente o que quer filtrar.
 */
class NoiseFloor(private val capacity: Int = DEFAULT_CAPACITY) {

    private val samples = FloatArray(capacity)
    private var count = 0
    private var next = 0

    /** Quantas amostras ja entraram (ate [capacity]). */
    val size: Int get() = count

    fun add(value: Float) {
        if (capacity <= 0) return
        samples[next] = value
        next = (next + 1) % capacity
        if (count < capacity) count++
    }

    fun clear() {
        count = 0
        next = 0
    }

    /**
     * Percentil [p] (0 = menor, 1 = maior) das amostras, por interpolacao entre as
     * duas vizinhas. Com [size] == 0 devolve 0, ou seja "sem informacao" — quem chama
     * trata o piso como zero e usa so o limiar do usuario.
     */
    fun percentile(p: Double): Float {
        if (count == 0) return 0f
        val sorted = samples.copyOf(count)
        sorted.sort()
        if (count == 1) return sorted[0]
        val pos = p.coerceIn(0.0, 1.0) * (count - 1)
        val lo = pos.toInt()
        val hi = min(lo + 1, count - 1)
        val frac = (pos - lo).toFloat()
        return sorted[lo] + (sorted[hi] - sorted[lo]) * frac
    }

    companion object {
        const val DEFAULT_CAPACITY = 64

        /** Fracao das amostras considerada ruido: as 20% mais baixas. */
        const val PERCENTILE = 0.2

        /**
         * Quantas amostras sao necessarias antes de o piso valer alguma coisa. Antes
         * disso o app acabou de abrir e nao tem medida do ruido da faixa.
         */
        const val MIN_SAMPLES = 24

        /**
         * Quanto o pico tem de superar o piso. 3x porque entre duas frequencias da
         * mesma faixa o ruido varia, porem nenhum piso se multiplica por 3 sem que a
         * captura seja o proprio ruido.
         */
        const val MULTIPLIER = 3.0
    }
}
