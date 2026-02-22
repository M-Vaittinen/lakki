package com.example.lakki_phone.bluetooth

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.nio.ByteBuffer

class ExternalNavigationProtocolTest {

    @Test
    fun movementMessageIncludesFixedHeaderWithNoAttributes() {
        val encoded = ExternalNavigationProtocol.buildMovementMessage(
            ExternalNavigationProtocol.MovementHeader(
                direction = 270,
                speedCentimetersPerSecond = 123,
            ),
        )

        val buffer = ByteBuffer.wrap(encoded).order(ExternalNavigationProtocol.byteOrder)
        assertEquals(ExternalNavigationProtocol.MessageType.MOVEMENT.value, buffer.int)
        assertEquals(encoded.size, buffer.int)
        assertEquals(270, buffer.int)
        assertEquals(123, buffer.int)
        assertEquals(0, buffer.remaining())
    }

    @Test
    fun destinationMessageAppendsAttributesUsingTlv() {
        val attribute = ExternalNavigationProtocol.Attribute(
            type = 9,
            data = byteArrayOf(0x01, 0x02, 0x03),
        )

        val encoded = ExternalNavigationProtocol.buildDestinationMessage(
            header = ExternalNavigationProtocol.DestinationHeader(
                direction = 90,
                distanceMeters = 42,
            ),
            attributes = listOf(attribute),
        )

        val buffer = ByteBuffer.wrap(encoded).order(ExternalNavigationProtocol.byteOrder)
        assertEquals(ExternalNavigationProtocol.MessageType.DESTINATION.value, buffer.int)
        assertEquals(encoded.size, buffer.int)
        assertEquals(90, buffer.int)
        assertEquals(42, buffer.int)

        assertEquals(9, buffer.short.toInt())
        assertEquals(attribute.encodedSize(), buffer.short.toInt())
        val data = ByteArray(3)
        buffer.get(data)
        assertArrayEquals(byteArrayOf(0x01, 0x02, 0x03), data)
        assertEquals(0, buffer.remaining())
    }

    @Test
    fun capDirectionMessageUsesFixedHeader() {
        val encoded = ExternalNavigationProtocol.buildCapDirectionMessage(
            ExternalNavigationProtocol.CapDirectionHeader(
                direction = 135,
                reserved = 0,
            ),
        )

        val buffer = ByteBuffer.wrap(encoded).order(ExternalNavigationProtocol.byteOrder)
        assertEquals(ExternalNavigationProtocol.MessageType.CAP_DIRECTION.value, buffer.int)
        assertEquals(encoded.size, buffer.int)
        assertEquals(135, buffer.int)
        assertEquals(0, buffer.int)
        assertEquals(0, buffer.remaining())
    }

    @Test
    fun attributeEqualityUsesPayloadContentInsteadOfArrayReference() {
        val left = ExternalNavigationProtocol.Attribute(
            type = 7,
            data = byteArrayOf(0x0A, 0x0B),
        )
        val right = ExternalNavigationProtocol.Attribute(
            type = 7,
            data = byteArrayOf(0x0A, 0x0B),
        )

        assertEquals(left, right)
        assertEquals(left.hashCode(), right.hashCode())
    }


    @Test
    fun capStateMessageParsesErrorStateAndExplanationText() {
        val encoded = ExternalNavigationProtocol.buildCapStateMessage(
            header = ExternalNavigationProtocol.CapStateHeader(
                state = ExternalNavigationProtocol.CapState.ERROR,
            ),
            attributes = listOf(
                ExternalNavigationProtocol.Attribute(
                    type = ExternalNavigationProtocol.AttributeType.TEXT_UTF8.value,
                    data = "Magnetometer calibration timeout".toByteArray(),
                ),
            ),
        )

        val messageType = ExternalNavigationProtocol.readMessageType(encoded)
        val header = ExternalNavigationProtocol.readCapStateHeader(encoded)
        val text = ExternalNavigationProtocol.readUtf8TextAttribute(encoded)

        assertEquals(ExternalNavigationProtocol.MessageType.CAP_STATE, messageType)
        assertEquals(ExternalNavigationProtocol.CapState.ERROR, header?.state)
        assertEquals("Magnetometer calibration timeout", text)
    }

    @Test
    fun debugLogMessageContainsUtf8AttributeLine() {
        val encoded = ExternalNavigationProtocol.buildDebugLogMessage(
            line = "MAG_CAL progress=67%",
        )

        assertEquals(
            ExternalNavigationProtocol.MessageType.DEBUG_LOG,
            ExternalNavigationProtocol.readMessageType(encoded),
        )
        assertEquals("MAG_CAL progress=67%", ExternalNavigationProtocol.readUtf8TextAttribute(encoded))
    }

    @Test
    fun utf8TextAttributeReturnsNullWhenMissing() {
        val encoded = ExternalNavigationProtocol.buildMovementMessage(
            ExternalNavigationProtocol.MovementHeader(
                direction = 10,
                speedCentimetersPerSecond = 1,
            ),
        )

        assertNull(ExternalNavigationProtocol.readUtf8TextAttribute(encoded))
    }

}
