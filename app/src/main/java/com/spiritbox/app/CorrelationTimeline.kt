package com.spiritbox.app

/** Correlação entre capturas de rádio e picos do magnetômetro. Sem dependência de
 * Android, para poder ser testada: guarda as amostras do EMF e as capturas de rádio
 * e calcula quais coincidem dentro de uma janela de tempo. */
class CorrelationTimeline(private val capacity: Int = DEFAULT_CAPACITY) {

    data class Sample(val t: Long, val mg: Float)

    data class RadioEvent(val t: Long, val freqKHz: Double, val level: Int)

    data class Peak(val t: Long, val mg: Float)

    /** Um par rádio+EMF cujos tempos caem dentro da janela. */
    data class Combined(val radio: RadioEvent, val peak: Peak)

    private val emf = ArrayList<Sample>()
    private val radio = ArrayList<RadioEvent>()

    @Synchronized
    fun addEmf(t: Long, mg: Float) {
        emf.add(Sample(t, mg))
        trim(emf)
    }

    @Synchronized
    fun addRadio(t: Long, freqKHz: Double, level: Int) {
        radio.add(RadioEvent(t, freqKHz, level))
        trim(radio)
    }

    @Synchronized
    fun clear() {
        emf.clear()
        radio.clear()
    }

    @Synchronized
    fun emfSamples(): List<Sample> = ArrayList(emf)

    @Synchronized
    fun radioEvents(): List<RadioEvent> = ArrayList(radio)

    /** Picos do campo: cada excursão contínua acima de [thresholdMg] vira um pico no
     * ponto de maior valor. Uma passagem que sobe, oscila e desce conta como um só. */
    @Synchronized
    fun peaks(thresholdMg: Float = PEAK_THRESHOLD_MG): List<Peak> {
        val out = ArrayList<Peak>()
        var above = false
        var best: Sample? = null
        for (s in emf) {
            if (s.mg >= thresholdMg) {
                if (!above) {
                    above = true
                    best = s
                } else if (best == null || s.mg > best.mg) {
                    best = s
                }
            } else if (above) {
                best?.let { out.add(Peak(it.t, it.mg)) }
                above = false
                best = null
            }
        }
        best?.let { out.add(Peak(it.t, it.mg)) }
        return out
    }

    /** Cada captura de rádio que tem um pico do campo dentro de [windowMs] vira um
     * evento combinado, pareado com o pico mais próximo no tempo. */
    @Synchronized
    fun combined(
        windowMs: Long = WINDOW_MS,
        thresholdMg: Float = PEAK_THRESHOLD_MG
    ): List<Combined> {
        val ps = peaks(thresholdMg)
        if (ps.isEmpty() || radio.isEmpty()) return emptyList()
        val out = ArrayList<Combined>()
        for (r in radio) {
            var nearest: Peak? = null
            var bestDelta = Long.MAX_VALUE
            for (p in ps) {
                val d = kotlin.math.abs(p.t - r.t)
                if (d <= windowMs && d < bestDelta) {
                    bestDelta = d
                    nearest = p
                }
            }
            nearest?.let { out.add(Combined(r, it)) }
        }
        return out
    }

    private fun <T> trim(list: ArrayList<T>) {
        while (list.size > capacity) list.removeAt(0)
    }

    companion object {
        const val DEFAULT_CAPACITY = 6000

        /** Variação em mG a partir da qual uma leitura já conta como pico. */
        const val PEAK_THRESHOLD_MG = 15f

        /** Janela em que a captura de rádio e o pico do campo são "simultâneos". */
        const val WINDOW_MS = 5000L
    }
}
