package com.example.lakki_phone.bluetooth

import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.charset.StandardCharsets

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
        CAP_DIRECTION(5),
        CAP_DIRECTION_REQUEST_START(6),
        CAP_DIRECTION_REQUEST_STOP(7),
        CAP_STATE(8),
        DEBUG_LOG(9),
    }

    enum class AttributeType(val value: Int) {
        TEXT_UTF8(1),
    }

    enum class CapState(val value: Int) {
        UNKNOWN(0),
        CALIBRATING(1),
        NAVIGATING(2),
        ERROR(3),
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

    data class CapDirectionHeader(
        val direction: Int,
        val reserved: Int = 0,
    )

    data class CapDirectionRequestHeader(
        val reserved0: Int = 0,
        val reserved1: Int = 0,
    )

    data class CapStateHeader(
        val state: CapState,
        val reserved: Int = 0,
    )

    data class DebugLogHeader(
        val severity: Int = 0,
        val reserved: Int = 0,
    )

    data class DecodedAttribute(
        val type: Int,
        val data: ByteArray,
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

    fun buildCapDirectionMessage(
        header: CapDirectionHeader,
        attributes: List<Attribute> = emptyList(),
    ): ByteArray {
        val headerBytes = ByteBuffer.allocate(Int.SIZE_BYTES * 2)
            .order(byteOrder)
            .putInt(header.direction)
            .putInt(header.reserved)
            .array()

        return buildMessage(
            messageType = MessageType.CAP_DIRECTION,
            headerBytes = headerBytes,
            attributes = attributes,
        )
    }

    fun buildCapDirectionRequestStartMessage(
        header: CapDirectionRequestHeader = CapDirectionRequestHeader(),
        attributes: List<Attribute> = emptyList(),
    ): ByteArray {
        val headerBytes = ByteBuffer.allocate(Int.SIZE_BYTES * 2)
            .order(byteOrder)
            .putInt(header.reserved0)
            .putInt(header.reserved1)
            .array()

        return buildMessage(
            messageType = MessageType.CAP_DIRECTION_REQUEST_START,
            headerBytes = headerBytes,
            attributes = attributes,
        )
    }

    fun buildCapDirectionRequestStopMessage(
        header: CapDirectionRequestHeader = CapDirectionRequestHeader(),
        attributes: List<Attribute> = emptyList(),
    ): ByteArray {
        val headerBytes = ByteBuffer.allocate(Int.SIZE_BYTES * 2)
            .order(byteOrder)
            .putInt(header.reserved0)
            .putInt(header.reserved1)
            .array()

        return buildMessage(
            messageType = MessageType.CAP_DIRECTION_REQUEST_STOP,
            headerBytes = headerBytes,
            attributes = attributes,
        )
    }

    fun buildCapStateMessage(
        header: CapStateHeader,
        attributes: List<Attribute> = emptyList(),
    ): ByteArray {
        val headerBytes = ByteBuffer.allocate(Int.SIZE_BYTES * 2)
            .order(byteOrder)
            .putInt(header.state.value)
            .putInt(header.reserved)
            .array()

        return buildMessage(
            messageType = MessageType.CAP_STATE,
            headerBytes = headerBytes,
            attributes = attributes,
        )
    }

    fun buildDebugLogMessage(
        header: DebugLogHeader = DebugLogHeader(),
        line: String,
    ): ByteArray {
        val textAttribute = Attribute(
            type = AttributeType.TEXT_UTF8.value,
            data = line.toByteArray(StandardCharsets.UTF_8),
        )
        val headerBytes = ByteBuffer.allocate(Int.SIZE_BYTES * 2)
            .order(byteOrder)
            .putInt(header.severity)
            .putInt(header.reserved)
            .array()
        return buildMessage(
            messageType = MessageType.DEBUG_LOG,
            headerBytes = headerBytes,
            attributes = listOf(textAttribute),
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

    fun readCapDirectionHeader(payload: ByteArray): CapDirectionHeader? {
        val headerSize = MESSAGE_TYPE_SIZE_BYTES + MESSAGE_LENGTH_SIZE_BYTES + Int.SIZE_BYTES * 2
        if (payload.size < headerSize) {
            return null
        }
        val buffer = ByteBuffer.wrap(payload)
            .order(byteOrder)
        buffer.position(MESSAGE_TYPE_SIZE_BYTES + MESSAGE_LENGTH_SIZE_BYTES)
        return CapDirectionHeader(
            direction = buffer.int,
            reserved = buffer.int,
        )
    }

    fun readCapStateHeader(payload: ByteArray): CapStateHeader? {
        val headerSize = MESSAGE_TYPE_SIZE_BYTES + MESSAGE_LENGTH_SIZE_BYTES + Int.SIZE_BYTES * 2
        if (payload.size < headerSize) {
            return null
        }
        val buffer = ByteBuffer.wrap(payload)
            .order(byteOrder)
        buffer.position(MESSAGE_TYPE_SIZE_BYTES + MESSAGE_LENGTH_SIZE_BYTES)
        val state = CapState.entries.firstOrNull { it.value == buffer.int } ?: CapState.UNKNOWN
        return CapStateHeader(
            state = state,
            reserved = buffer.int,
        )
    }

    fun readAttributes(payload: ByteArray): List<DecodedAttribute> {
        val headerOffset = MESSAGE_TYPE_SIZE_BYTES + MESSAGE_LENGTH_SIZE_BYTES + Int.SIZE_BYTES * 2
        if (payload.size < headerOffset) {
            return emptyList()
        }
        val buffer = ByteBuffer.wrap(payload).order(byteOrder)
        val decoded = mutableListOf<DecodedAttribute>()
        buffer.position(headerOffset)
        while (buffer.remaining() >= ATTRIBUTE_TYPE_SIZE_BYTES + ATTRIBUTE_LENGTH_SIZE_BYTES) {
            val type = buffer.short.toInt() and 0xFFFF
            val attributeLength = buffer.short.toInt() and 0xFFFF
            if (attributeLength < ATTRIBUTE_TYPE_SIZE_BYTES + ATTRIBUTE_LENGTH_SIZE_BYTES) {
                break
            }
            val payloadLength = attributeLength - ATTRIBUTE_TYPE_SIZE_BYTES - ATTRIBUTE_LENGTH_SIZE_BYTES
            if (buffer.remaining() < payloadLength) {
                break
            }
            val attributePayload = ByteArray(payloadLength)
            buffer.get(attributePayload)
            decoded += DecodedAttribute(type = type, data = attributePayload)
        }
        return decoded
    }

    fun readUtf8TextAttribute(payload: ByteArray): String? {
        val textData = readAttributes(payload)
            .firstOrNull { it.type == AttributeType.TEXT_UTF8.value }
            ?.data
            ?: return null
        return textData.toString(StandardCharsets.UTF_8)
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
