package com.pauloneves.nfc

import android.nfc.NfcAdapter
import android.nfc.tech.IsoDep
import android.os.Handler
import android.util.Log
import com.facebook.react.bridge.Promise
import com.facebook.react.bridge.ReactApplicationContext
import com.facebook.react.bridge.ReactContextBaseJavaModule
import com.facebook.react.bridge.ReactMethod

/**
 * Módulo nativo NFC para integração Pix por aproximação.
 * Responsável por iniciar o modo leitor, selecionar o AID Pix e enviar comandos APDU via IsoDep.
 */
class PixNfcModule(reactContext: ReactApplicationContext) :
    ReactContextBaseJavaModule(reactContext) {

    override fun getName() = "TerminalNfcModule"

    private var isoDep: IsoDep? = null
    private var nfcAdapter: NfcAdapter? = null

    companion object {
        private const val TAG = "TerminalNfc"

        // Códigos de erro padronizados para integração com JavaScript
        private const val TIMEOUT_ERROR = "TIMEOUT"
        private const val POLL_ERROR = "POLL_ERROR"
        private const val NO_NFC = "NO_NFC"
        private const val NO_ACTIVITY = "NO_ACTIVITY"
        private const val NO_ISODEP = "NO_ISODEP"
        private const val AID_REJECTED = "AID_REJECTED"
        private const val TRANSCEIVE_ERROR = "TRANSCEIVE_ERROR"

        /**
         * Comando SELECT AID padrão para iniciar comunicação com o app Pix HCE
         * (conforme especificação do Pix por Aproximação do Bacen)
         */
        private val SELECT_AID_COMMAND = byteArrayOf(
            0x00, 0xA4.toByte(), 0x04, 0x00, 0x08,
            0xA0.toByte(), 0x00, 0x00, 0x09, 0x40, 0xBC.toByte(), 0xB0.toByte(), 0x00
        )

        /**
         * Flags de leitura NFC definindo o modo de operação do leitor.
         */
        private const val NFC_FLAGS = (
            NfcAdapter.FLAG_READER_NFC_A or
            NfcAdapter.FLAG_READER_SKIP_NDEF_CHECK or
            NfcAdapter.FLAG_READER_NO_PLATFORM_SOUNDS
        )
    }

    /**
     * Verifica a disponibilidade do NFC no dispositivo.
     * Retorna uma string: "notSupported", "available" ou "disabled".
     */
    @ReactMethod
    fun getNFCAvailability(promise: Promise) {
        val adapter = NfcAdapter.getDefaultAdapter(currentActivity)
        val status = when {
            adapter == null -> "notSupported"
            adapter.isEnabled -> "available"
            else -> "disabled"
        }
        promise.resolve(status)
    }

    /**
     * Inicia o modo leitor NFC aguardando a aproximação de um dispositivo HCE.
     * Estabelece a conexão via IsoDep e executa o SELECT AID automaticamente.
     *
     * @param timeout Tempo máximo de espera (em milissegundos).
     */
    @ReactMethod
    fun poll(timeout: Int, promise: Promise) {
        val activity = currentActivity ?: return promise.reject(
            NO_ACTIVITY,
            "Erro interno: tela do app não encontrada"
        )

        val adapter = NfcAdapter.getDefaultAdapter(activity)
        if (adapter == null) {
            promise.reject(NO_NFC, "Este dispositivo não possui suporte a NFC")
            return
        }

        nfcAdapter = adapter

        adapter.enableReaderMode(activity, { tag ->
            try {
                val iso = IsoDep.get(tag) ?: return@enableReaderMode promise.reject(
                    NO_ISODEP,
                    "Cartão ou dispositivo incompatível com o padrão NFC esperado"
                )
                iso.connect()
                isoDep = iso
                handleAID(promise)
            } catch (e: Exception) {
                promise.reject(
                    POLL_ERROR,
                    "Erro durante a leitura por aproximação. Tente novamente",
                    e
                )
            }
        }, NFC_FLAGS, null)

        Handler(activity.mainLooper).postDelayed({
            try {
                adapter.disableReaderMode(activity)
                promise.reject(TIMEOUT_ERROR, "Tempo esgotado para aproximação. Tente novamente")
            } catch (_: Exception) {
            }
        }, timeout.toLong())
    }

    /**
     * Envia um comando APDU (hex string) para o dispositivo NFC conectado via IsoDep.
     */
    @ReactMethod
    fun transceive(hexData: String, promise: Promise) {
        try {
            val data = hexData.toByteArrayFromHex()
            Log.d(TAG, "➡ Enviando APDU: ${data.toHexString()}")

            val response = isoDep?.transceive(data)
            val responseHex = response?.toHexString() ?: "null"
            Log.d(TAG, "⬅ Resposta APDU: $responseHex")
            promise.resolve(responseHex)
        } catch (e: Exception) {
            Log.e(TAG, "❌ Erro no transceive: ${e.message}", e)
            promise.reject(TRANSCEIVE_ERROR, "Erro de comunicação. Tente novamente", e)
        }
    }

    /**
     * Finaliza a sessão NFC, encerrando conexões e limpando estado interno.
     */
    @ReactMethod
    fun finish(promise: Promise) {
        currentActivity?.let {
            try {
                isoDep?.close()
            } catch (e: Exception) {
                Log.w(TAG, "Erro ao fechar IsoDep: ${e.message}")
            } finally {
                isoDep = null
            }

            try {
                nfcAdapter?.disableReaderMode(it)
            } catch (e: Exception) {
                Log.w(TAG, "Erro ao desativar reader mode: ${e.message}")
            }
        }

        promise.resolve("finished")
    }

    /**
     * Executa o SELECT AID logo após a conexão com o dispositivo NFC.
     * Se a resposta for válida (9000), resolve o Promise com sucesso.
     */
    private fun handleAID(promise: Promise) {
        val response = selectAID(isoDep!!)
        if (response.isSuccessfulSelectAid()) {
            promise.resolve("""{"connected": true}""")
        } else {
            promise.reject(
                AID_REJECTED,
                "Falha ao selecionar o aplicativo para o Pix por Aproximação. Tente novamente"
            )
        }
    }

    /**
     * Envia o comando SELECT AID ao dispositivo NFC e retorna a resposta em bytes.
     */
    private fun selectAID(iso: IsoDep): ByteArray {
        val response = iso.transceive(SELECT_AID_COMMAND)
        val responseHex = response.toHexString()
        Log.d(TAG, "📥 SELECT AID resposta (hex): $responseHex")
        return response
    }

    /** Converte um array de bytes para uma string hexadecimal (ex: [0x90, 0x00] → "9000") */
    private fun ByteArray.toHexString(): String =
        joinToString("") { "%02X".format(it) }

    /** Converte uma string hexadecimal para um array de bytes */
    private fun String.toByteArrayFromHex(): ByteArray =
        replace(" ", "").chunked(2).map { it.toInt(16).toByte() }.toByteArray()

    /** Verifica se a resposta do SELECT AID é bem-sucedida (termina com 0x9000) */
    private fun ByteArray.isSuccessfulSelectAid(): Boolean =
        size >= 2 && this[size - 2] == 0x90.toByte() && this[size - 1] == 0x00.toByte()

    /** Verifica se o comando atual é do tipo UPDATE BINARY */
    private fun ByteArray.isUpdateBinaryCommand(): Boolean =
        getOrNull(1) == 0xD6.toByte()
}
