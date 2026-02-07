package com.example.lakki_phone.bluetooth

import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Placeholder implementation of the binary protocol used for communicating with
 * an external navigation device over Bluetooth.
 *
 * Message layout (all values are BIG_ENDIAN):
 * - message type: 4 bytes
 * - total length: 4 bytes (type + length + message-specific header + attributes)
 * - message-specific header: variable per message type
 * - attributes: 0..N TLV attributes
 *
 * Attribute TLV layout:
 * - attribute type: 2 bytes
 * - attribute length: 2 bytes (type + length + data)
 * - attribute data: variable
 */
object ExternalNavigationProtocol {
    private const val MESSAGE_TYPE_SIZE_BYTES = 4
    private const val MESSAGE_LENGTH_SIZE_BYTES = 4
    private const val ATTRIBUTE_TYPE_SIZE_BYTES = 2
    private const val ATTRIBUTE_LENGTH_SIZE_BYTES = 2

    /** Protocol endianness fixed for all multi-byte numeric fields. */
    val byteOrder: ByteOrder = ByteOrder.BIG_ENDIAN

    enum class MessageType(val value: Int) {
        INVALID(0),
        HANDSHAKE(1),
        DESTINATION(2),
        MOVEMENT(3),
        DESTINATION_REQUEST(4),
    }

    /**
     * Optional extensible attributes for future protocol additions.
     * [data] contains the raw binary payload of the attribute.
     */
    class Attribute(
        val type: Int,
        data: ByteArray,
    ) {
        private val payload: ByteArray = data.copyOf()

        /** Immutable copy of the raw binary payload. */
        val data: ByteArray
            get() = payload.copyOf()

        init {
            require(type in 0..0xFFFF) { "Attribute type must fit in 16 bits." }
        }

        fun encodedSize(): Int {
            return ATTRIBUTE_TYPE_SIZE_BYTES + ATTRIBUTE_LENGTH_SIZE_BYTES + payload.size
        }

        fun encode(): ByteArray {
            val length = encodedSize()
            val buffer = ByteBuffer.allocate(length).order(byteOrder)
            buffer.putShort(type.toShort())
            buffer.putShort(length.toShort())
            buffer.put(payload)
            return buffer.array()
        }

        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other !is Attribute) return false
            return type == other.type && payload.contentEquals(other.payload)
        }

        override fun hashCode(): Int {
            return 31 * type + payload.contentHashCode()
        }
    }

    data class HandshakeHeader(
        val protocolVersion: Int,
        val capabilitiesFlags: Int,
    )

    data class DestinationHeader(
        val direction: Int,
        val distanceMeters: Int,
    )

    data class MovementHeader(
        val direction: Int,
        val speedCentimetersPerSecond: Int,
    )

    fun buildHandshakeMessage(
        header: HandshakeHeader,
        attributes: List<Attribute> = emptyList(),
    ): ByteArray {
        val headerBytes = ByteBuffer.allocate(Int.SIZE_BYTES * 2)
            .order(byteOrder)
            .putInt(header.protocolVersion)
            .putInt(header.capabilitiesFlags)
            .array()

        return buildMessage(
            messageType = MessageType.HANDSHAKE,
            headerBytes = headerBytes,
            attributes = attributes,
        )
    }

    fun buildDestinationMessage(
        header: DestinationHeader,
        attributes: List<Attribute> = emptyList(),
    ): ByteArray {
        val headerBytes = ByteBuffer.allocate(Int.SIZE_BYTES * 2)
            .order(byteOrder)
            .putInt(header.direction)
            .putInt(header.distanceMeters)
            .array()

        return buildMessage(
            messageType = MessageType.DESTINATION,
            headerBytes = headerBytes,
            attributes = attributes,
        )
    }

    fun buildMovementMessage(
        header: MovementHeader,
        attributes: List<Attribute> = emptyList(),
    ): ByteArray {
        val headerBytes = ByteBuffer.allocate(Int.SIZE_BYTES * 2)
            .order(byteOrder)
            .putInt(header.direction)
            .putInt(header.speedCentimetersPerSecond)
            .array()

        return buildMessage(
            messageType = MessageType.MOVEMENT,
            headerBytes = headerBytes,
            attributes = attributes,
        )
    }

    fun readMessageType(payload: ByteArray): MessageType? {
        if (payload.size < MESSAGE_TYPE_SIZE_BYTES) {
            return null
        }
        val typeValue = ByteBuffer.wrap(payload)
            .order(byteOrder)
            .int
        return MessageType.entries.firstOrNull { it.value == typeValue }
    }

    private fun buildMessage(
        messageType: MessageType,
        headerBytes: ByteArray,
        attributes: List<Attribute>,
    ): ByteArray {
        val attributesSize = attributes.sumOf(Attribute::encodedSize)
        val totalLength = MESSAGE_TYPE_SIZE_BYTES +
            MESSAGE_LENGTH_SIZE_BYTES +
            headerBytes.size +
            attributesSize

        val buffer = ByteBuffer.allocate(totalLength).order(byteOrder)
        buffer.putInt(messageType.value)
        buffer.putInt(totalLength)
        buffer.put(headerBytes)
        attributes.forEach { attribute ->
            buffer.put(attribute.encode())
        }

        return buffer.array()
    }
}
