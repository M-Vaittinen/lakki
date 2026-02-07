package com.example.lakki_phone.bluetooth

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
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

}
