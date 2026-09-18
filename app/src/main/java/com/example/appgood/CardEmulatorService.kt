package com.example.appgood

import android.nfc.cardemulation.HostApduService
import android.os.Bundle
import android.util.Log

class CardEmulatorService : HostApduService() {

    override fun processCommandApdu(commandApdu: ByteArray?, extras: Bundle?): ByteArray {
        if (commandApdu == null) return hexStringToByteArray("6F00") // Error

        val hexCommand = commandApdu.joinToString("") { String.format("%02X", it) }
        Log.d("HCEService", "Comando Recibido: $hexCommand")

        // Respuesta genérica "OK" (90 00)
        return hexStringToByteArray("9000")
    }

    override fun onDeactivated(reason: Int) {
        Log.d("HCEService", "Emulación desactivada. Razón: $reason")
    }

    private fun hexStringToByteArray(s: String): ByteArray {
        val len = s.length
        val data = ByteArray(len / 2)
        var i = 0
        while (i < len) {
            data[i / 2] = ((Character.digit(s[i], 16) shl 4) + Character.digit(s[i + 1], 16)).toByte()
            i += 2
        }
        return data
    }
}