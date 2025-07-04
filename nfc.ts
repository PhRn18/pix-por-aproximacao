import {NativeModules} from "react-native";

const {TerminalNfcModule} = NativeModules;

const THIRTY_SECONDS = 30000;

/**
 * Converte um array de bytes em uma string hexadecimal (em caixa alta).
 *
 * @param arr - Array de números (bytes).
 * @returns String hexadecimal correspondente, ex: [0x90, 0x00] → "9000".
 */
function arrayToHexString(arr: number[]): string {
  return arr.map(b => b.toString(16).padStart(2, '0')).join('').toUpperCase();
}

/**
 * Converte uma string hexadecimal para um array de bytes.
 *
 * @param hex - String contendo somente caracteres hexadecimais.
 * @returns Array de bytes correspondentes à string.
 */
function hexStringToByteArray(hex: string): number[] {
  const bytes: number[] = [];
  for (let i = 0; i < hex.length; i += 2) {
    bytes.push(parseInt(hex.slice(i, i + 2), 16));
  }
  return bytes;
}

/**
 * Gera uma sequência de comandos APDU do tipo UPDATE BINARY com a mensagem NDEF.
 * A URI do Pix é convertida em blocos de no máximo 240 bytes para envio ao cartão.
 *
 * @param uri - URI do Pix no formato "pix://<hostname>?qr=<codigo_pix>"
 * @returns Lista de arrays de bytes, cada um representando um comando APDU completo.
 */
function buildNdefApdus(uri: string): number[][] {
  const uriBytes = new TextEncoder().encode(uri);
  const totalPayloadLength = uriBytes.length + 1;
  const useShortRecord = totalPayloadLength <= 255;

  const ndefHeader = useShortRecord
    ? [0xD1, 0x01, totalPayloadLength, 0x55, 0x00]
    : [0xC1, 0x01, 0x00, 0x00, 0x01, totalPayloadLength, 0x55, 0x00];

  const ndefMessage = new Uint8Array([...ndefHeader, ...uriBytes]);
  const maxChunkSize = 240;
  const apdus: number[][] = [];

  for (let offset = 0; offset < ndefMessage.length; offset += maxChunkSize) {
    const chunk = ndefMessage.slice(offset, offset + maxChunkSize);
    const p1 = (offset >> 8) & 0xFF; // byte mais significativo do offset
    const p2 = offset & 0xFF;        // byte menos significativo do offset

    apdus.push([0x00, 0xD6, p1, p2, chunk.length, ...chunk]);
  }

  return apdus;
}

/**
 * Verifica se a resposta APDU indica sucesso.
 * Uma resposta válida deve terminar com os bytes 0x90 0x00.
 *
 * @param resp - Array de bytes representando a resposta APDU.
 * @returns Verdadeiro se a resposta for bem-sucedida.
 */
function isSuccessResponse(resp: number[]): boolean {
  const len = resp.length;
  return len >= 2 && resp[len - 2] === 0x90 && resp[len - 1] === 0x00;
}

/**
 * Executa o fluxo completo de envio de um código Pix por aproximação:
 * - Aguarda a aproximação do dispositivo NFC
 * - Envia os comandos UPDATE BINARY com a URI codificada
 * - Finaliza a conexão
 *
 * @param pixCode - Código Pix copia e cola.
 * @param brokerUrl - Hostname extraído do código Pix.
 * @throws Erro com mensagem traduzida caso algum passo falhe.
 */
export async function pixNfc(pixCode: string, brokerUrl: string) {
  try {
    const status: string = await TerminalNfcModule.getNFCAvailability();
    if (status !== "available") {
      console.warn("NFC indisponível ou desativado, writePixNfc ignorado");
      return;
    }

    const uri = `pix://${brokerUrl}?qr=${pixCode}`;

    await TerminalNfcModule.poll(THIRTY_SECONDS);

    const ndefApdus = buildNdefApdus(uri);

    for (const apdu of ndefApdus) {
      const hex = arrayToHexString(apdu);
      const respHex = await TerminalNfcModule.transceive(hex);
      const resp = hexStringToByteArray(respHex);
      if (!isSuccessResponse(resp)) {
        throw new Error(`❌ Falha no UPDATE BINARY: ${arrayToHexString(resp)}`);
      }
    }
  } finally {
    await TerminalNfcModule.finish();
  }
}
