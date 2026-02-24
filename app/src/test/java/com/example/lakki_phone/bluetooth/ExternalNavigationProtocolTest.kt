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
        assertEquals(
            ExternalNavigationProtocol.DebugLogHeader(severity = 0, reserved = 0),
            ExternalNavigationProtocol.readDebugLogHeader(encoded),
        )
        assertEquals("MAG_CAL progress=67%", ExternalNavigationProtocol.readUtf8TextAttribute(encoded))
    }

    @Test
    fun debugLogHeaderIsNotTreatedAsAttribute() {
        val encoded = ExternalNavigationProtocol.buildDebugLogMessage(
            header = ExternalNavigationProtocol.DebugLogHeader(
                severity = 0x0001_0018,
                reserved = 0,
            ),
            line = "cap direction: 0 deg",
        )

        val attributes = ExternalNavigationProtocol.readAttributes(encoded)

        assertEquals(1, attributes.size)
        assertEquals(ExternalNavigationProtocol.AttributeType.TEXT_UTF8.value, attributes.first().type)
        assertEquals("cap direction: 0 deg", ExternalNavigationProtocol.readUtf8TextAttribute(encoded))
    }


    @Test
    fun utf8TextAttributeTrimsCStyleNullTerminatorAndTrailingBytes() {
        val encoded = ExternalNavigationProtocol.buildDebugLogMessage(
            line = "cap direction: 0 deg\u0000garbage",
        )

        assertEquals("cap direction: 0 deg", ExternalNavigationProtocol.readUtf8TextAttribute(encoded))
        assertEquals(
            "cap direction: 0 deg",
            ExternalNavigationProtocol.readSanitizedDebugLogLine(
                payload = encoded,
                maxLength = 512,
            ),
        )
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

    @Test
    fun readAttributesReturnsEmptyForTruncatedPayload() {
        val encoded = ExternalNavigationProtocol.buildDebugLogMessage(line = "abc")
        val truncated = encoded.copyOf(encoded.size - 1)

        assertNull(ExternalNavigationProtocol.readUtf8TextAttribute(truncated))
        assertEquals(
            emptyList<ExternalNavigationProtocol.DecodedAttribute>(),
            ExternalNavigationProtocol.readAttributes(truncated),
        )
    }

    @Test
    fun rawCapStateNavigatingPayloadParsesToNavigatingState() {
        val payload = byteArrayOf(
            0x00, 0x00, 0x00, 0x08,
            0x00, 0x00, 0x00, 0x10,
            0x00, 0x00, 0x00, 0x02,
            0x00, 0x00, 0x00, 0x00,
        )

        assertEquals(
            ExternalNavigationProtocol.MessageType.CAP_STATE,
            ExternalNavigationProtocol.readMessageType(payload),
        )
        assertEquals(
            ExternalNavigationProtocol.CapState.NAVIGATING,
            ExternalNavigationProtocol.readCapStateHeader(payload)?.state,
        )
    }


    @Test
    fun readAttributesIgnoresTrailingTransportBytesPastMessageLength() {
        val encoded = ExternalNavigationProtocol.buildDebugLogMessage(
            line = "cap direction: 0 deg",
        )
        val withTrailingBytes = encoded + byteArrayOf(0x55, 0x66)

        assertEquals("cap direction: 0 deg", ExternalNavigationProtocol.readUtf8TextAttribute(withTrailingBytes))
    }

    @Test
    fun readAttributesReturnsEmptyWhenAttributeSectionHasNonTlvTrailingByte() {
        val payload = byteArrayOf(
            0x00, 0x00, 0x00, 0x09,
            0x00, 0x00, 0x00, 0x29,
            0x00, 0x00, 0x00, 0x00,
            0x00, 0x00, 0x00, 0x00,
            0x00, 0x01,
            0x00, 0x18,
            0x63, 0x61, 0x70, 0x20, 0x64, 0x69, 0x72, 0x65,
            0x63, 0x74, 0x69, 0x6f, 0x6e, 0x3a, 0x20, 0x30,
            0x20, 0x64, 0x65, 0x67,
            0x28,
        )

        assertEquals(
            emptyList<ExternalNavigationProtocol.DecodedAttribute>(),
            ExternalNavigationProtocol.readAttributes(payload),
        )
        assertNull(ExternalNavigationProtocol.readUtf8TextAttribute(payload))
    }
    @Test
    fun rawDebugLogPayloadParsesExpectedTextThroughProductionReceivePath() {
        val payload = byteArrayOf(
            0x00, 0x00, 0x00, 0x09,
            0x00, 0x00, 0x00, 0x28,
            0x00, 0x00, 0x00, 0x00,
            0x00, 0x00, 0x00, 0x00,
            0x00, 0x01,
            0x00, 0x18,
            0x63, 0x61, 0x70, 0x20, 0x64, 0x69, 0x72, 0x65,
            0x63, 0x74, 0x69, 0x6f, 0x6e, 0x3a, 0x20, 0x30,
            0x20, 0x64, 0x65, 0x67,
        )

        assertEquals(
            "cap direction: 0 deg",
            ExternalNavigationProtocol.readSanitizedDebugLogLine(
                payload = payload,
                maxLength = 512,
            ),
        )
    }

}
