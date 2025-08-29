package com.android.broadcastassistant.audio

object BassControlPointBuilder {
    /**
     * Build a Modify Source command for BASS Control Point.
     * broadcastId is a 24-bit int.
     */
    fun buildSwitchCommand(
        sourceId: Int = 1,
        bisIndexes: List<Int>,
        broadcastId: Int,
        broadcastCode: ByteArray? = null
    ): ByteArray {
        val opcode: Byte = 0x03 // Modify Source

        val bisBitmap = bisIndexes.fold(0) { acc, idx -> acc or (1 shl (idx - 1)) }

        val bc0 = (broadcastId and 0xFF).toByte()
        val bc1 = ((broadcastId shr 8) and 0xFF).toByte()
        val bc2 = ((broadcastId shr 16) and 0xFF).toByte()

        val code = broadcastCode ?: byteArrayOf()

        val out = ArrayList<Byte>()
        out.add(opcode)
        out.add(sourceId.toByte())
        out.add((bisBitmap and 0xFF).toByte())
        out.add(((bisBitmap shr 8) and 0xFF).toByte())
        out.add(bc0)
        out.add(bc1)
        out.add(bc2)
        out.add(code.size.toByte())
        code.forEach { out.add(it) }
        return out.toByteArray()
    }
}