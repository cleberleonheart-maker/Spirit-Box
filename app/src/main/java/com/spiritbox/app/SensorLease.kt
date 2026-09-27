package com.spiritbox.app

/** Contagem de donos de um recurso que precisa sobreviver a liberacoes
 * atrasadas. O Dialog entrega o OnDismissListener pela fila do Looper, o que
 * chega depois do acquire seguinte: sem contagem, quem abriu o mapa registrou o
 * sensor e o dismiss atrasado do dialogo o desregistrou em seguida, deixando
 * available = false num aparelho com magnetometro.
 *
 * acquire devolve true para quem de fato precisa registrar, e release devolve
 * true para quem de fato precisa desregistrar. Uma liberacao atrasada apenas
 * devolve uma das varias referencias e nao derruba o registro. */
class SensorLease {

    private var refs = 0

    val count: Int get() = refs
    val isHeld: Boolean get() = refs > 0

    fun acquire(): Boolean {
        refs++
        return refs == 1
    }

    fun release(): Boolean {
        if (refs == 0) return false
        refs--
        return refs == 0
    }

    /** Solta tudo de uma vez, para quando ninguem pode mais devolver a referencia. */
    fun releaseAll(): Int {
        val had = refs
        refs = 0
        return had
    }
}
