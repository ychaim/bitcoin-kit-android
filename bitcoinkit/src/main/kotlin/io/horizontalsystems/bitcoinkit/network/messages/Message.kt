package io.horizontalsystems.bitcoinkit.network.messages

import io.horizontalsystems.bitcoinkit.exceptions.BitcoinException
import io.horizontalsystems.bitcoinkit.io.BitcoinInput
import io.horizontalsystems.bitcoinkit.io.BitcoinOutput
import io.horizontalsystems.bitcoinkit.network.Network
import io.horizontalsystems.bitcoinkit.utils.HashUtils
import java.io.IOException
import java.nio.charset.StandardCharsets
import java.util.*

abstract class Message(cmd: String) {

    private var command: ByteArray = getCommandBytes(cmd)

    fun toByteArray(network: Network): ByteArray {
        val payload = getPayload()
        return BitcoinOutput()
                .writeInt32(network.magic)      // magic
                .write(command)                 // command: char[12]
                .writeInt(payload.size)         // length: uint32_t
                .write(getCheckSum(payload))    // checksum: uint32_t
                .write(payload)                 // payload:
                .toByteArray()
    }

    protected abstract fun getPayload(): ByteArray

    override fun toString(): String {
        return "Message(command=" + getCommandFrom(command) + ")"
    }

    object Builder {

        private val msgMap = initMessages()

        private fun initMessages(): Map<String, Class<*>> {
            val map = HashMap<String, Class<*>>()
            map["addr"] = AddrMessage::class.java
            map["block"] = BlockMessage::class.java
            map["getaddr"] = GetAddrMessage::class.java
            map["getblocks"] = GetBlocksMessage::class.java
            map["getdata"] = GetDataMessage::class.java
            map["getheaders"] = GetHeadersMessage::class.java
            map["inv"] = InvMessage::class.java
            map["ping"] = PingMessage::class.java
            map["pong"] = PongMessage::class.java
            map["verack"] = VerAckMessage::class.java
            map["version"] = VersionMessage::class.java
            map["headers"] = HeadersMessage::class.java
            map["merkleblock"] = MerkleBlockMessage::class.java
            map["tx"] = TransactionMessage::class.java
            map["filterload"] = FilterLoadMessage::class.java

            return map
        }

        /**
         * Parse stream as message.
         */
        @Throws(IOException::class)
        fun <T : Message> parseMessage(input: BitcoinInput, network: Network): T {
            val magic = input.readUnsignedInt()
            if (magic != network.magic) {
                throw BitcoinException("Bad magic. (local) ${network.magic}!=$magic")
            }

            val command = getCommandFrom(input.readBytes(12))
            val payloadLength = input.readInt()
            val expectedChecksum = ByteArray(4)
            input.readFully(expectedChecksum)
            val payload = ByteArray(payloadLength)
            input.readFully(payload)

            // check:
            val actualChecksum = getCheckSum(payload)
            if (!Arrays.equals(expectedChecksum, actualChecksum)) {
                throw BitcoinException("Checksum failed.")
            }

            // build msg:
            val msgClass = msgMap[command] ?: return UnknownMessage(command, payload) as T
            try {
                val constructor = msgClass.getConstructor(ByteArray::class.java)
                return constructor.newInstance(payload) as T
            } catch (e: Exception) {
                throw RuntimeException(e)
            }

        }
    }

    companion object {

        private fun getCommandFrom(cmd: ByteArray): String {
            var n = cmd.size - 1
            while (n >= 0) {
                if (cmd[n].toInt() == 0) {
                    n--
                } else {
                    break
                }
            }
            if (n <= 0) {
                throw BitcoinException("Bad command bytes.")
            }
            val b = Arrays.copyOfRange(cmd, 0, n + 1)
            return String(b, StandardCharsets.UTF_8)
        }

        private fun getCommandBytes(cmd: String): ByteArray {
            val cmdBytes = cmd.toByteArray()
            if (cmdBytes.isEmpty() || cmdBytes.size > 12) {
                throw IllegalArgumentException("Bad command: $cmd")
            }
            val buffer = ByteArray(12)
            System.arraycopy(cmdBytes, 0, buffer, 0, cmdBytes.size)
            return buffer
        }

        private fun getCheckSum(payload: ByteArray): ByteArray {
            val hash = HashUtils.doubleSha256(payload)
            return Arrays.copyOfRange(hash, 0, 4)
        }
    }
}
