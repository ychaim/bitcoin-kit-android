package io.horizontalsystems.bitcoinkit.network.messages

import io.horizontalsystems.bitcoinkit.io.BitcoinInput
import io.horizontalsystems.bitcoinkit.io.BitcoinOutput
import io.horizontalsystems.bitcoinkit.network.Network
import io.horizontalsystems.bitcoinkit.utils.HashUtils
import java.io.ByteArrayInputStream
import java.io.IOException

/**
 * GetHeaders Message
 *
 *   Size       Field       Description
 *   ====       =====       ===========
 *   4 bytes    Version     Negotiated protocol version
 *   VarInt     Count       Number of locator hash entries
 *   Variable   Hashes      Locator hash entries
 *  32 bytes    HashStop    Hash of the last desired block or zero to get as many as possible
 */
class GetHeadersMessage : Message {

    var version: Int = 0                    // uint32
    lateinit var hashes: Array<ByteArray>   // byte[32]
    lateinit var hashStop: ByteArray        // hash of the last desired block header; set to zero to get as many blocks as possible (2000)

    constructor(blockHashes: Array<ByteArray>, network: Network) : super("getheaders") {
        hashes = blockHashes
        version = network.protocolVersion
        hashStop = network.zeroHashBytes
    }

    @Throws(IOException::class)
    constructor(payload: ByteArray) : super("getheaders") {
        BitcoinInput(ByteArrayInputStream(payload)).use { input ->
            version = input.readInt()
            val hashCount = input.readVarInt() // do not keep hash count
            hashes = Array(hashCount.toInt()) { input.readBytes(32) }
            hashStop = input.readBytes(32)
        }
    }

    override fun getPayload(): ByteArray {
        val output = BitcoinOutput()
        output.writeInt(version).writeVarInt(hashes.size.toLong())
        for (i in hashes.indices) {
            output.write(hashes[i])
        }
        output.write(hashStop)
        return output.toByteArray()
    }

    override fun toString(): String {
        val list = hashes
                .take(10)
                .map { hash -> HashUtils.toHexStringAsLE(hash) }
                .joinToString()

        return ("GetHeadersMessage(" + hashes.size + ": [" + list + "], hashStop=" + HashUtils.toHexStringAsLE(hashStop) + ")")
    }
}
