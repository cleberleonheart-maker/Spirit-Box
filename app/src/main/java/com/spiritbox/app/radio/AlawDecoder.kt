package com.spiritbox.app.radio

object AlawDecoder {
    fun decode(input: ByteArray): ShortArray {
        val out = ShortArray(input.size)
        for (i in input.indices) {
            val t = (input[i].toInt() xor 0xD5) and 0xFF
            val sign = t and 0x80
            var exponent = (t and 0x70) shr 4
            var data = t and 0x0F
            data = data shl 4
            data += 8
            if (exponent != 0) data += 256
            if (exponent > 1) data = data shl (exponent - 1)
            out[i] = if (sign == 0) data.toShort() else (-data).toShort()
        }
        return out
    }
}