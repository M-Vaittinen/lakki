#ifndef EXTERNAL_NAVIGATION_PROTOCOL_H
#define EXTERNAL_NAVIGATION_PROTOCOL_H

#include <stddef.h>
#include <stdint.h>

#ifdef __cplusplus
extern "C" {
#endif

/**
 * External navigation protocol definitions shared with the Android app.
 *
 * Wire format for every message (BIG_ENDIAN):
 *   - message type: 4 bytes
 *   - total length: 4 bytes (type + length + message-specific header + attributes)
 *   - message-specific header: 8 bytes for currently defined messages
 *   - attributes: 0..N TLV attributes
 *
 * Attribute TLV format (BIG_ENDIAN):
 *   - attribute type: 2 bytes
 *   - attribute length: 2 bytes (type + length + payload)
 *   - payload: variable length
 */

/** Multi-byte integer fields in the wire protocol are big-endian. */
#define ENP_PROTOCOL_BIG_ENDIAN 1u

/** Common fixed field widths in bytes. */
#define ENP_MESSAGE_TYPE_SIZE_BYTES 4u
#define ENP_MESSAGE_LENGTH_SIZE_BYTES 4u
#define ENP_ATTRIBUTE_TYPE_SIZE_BYTES 2u
#define ENP_ATTRIBUTE_LENGTH_SIZE_BYTES 2u

/** Current fixed header size used by all defined message types. */
#define ENP_FIXED_HEADER_SIZE_BYTES 8u

/** Message type IDs. */
typedef enum enp_message_type {
    ENP_MESSAGE_TYPE_INVALID = 0,
    ENP_MESSAGE_TYPE_HANDSHAKE = 1,
    ENP_MESSAGE_TYPE_DESTINATION = 2,
    ENP_MESSAGE_TYPE_MOVEMENT = 3,
    ENP_MESSAGE_TYPE_LOCATION_REQUEST = 4,
    ENP_MESSAGE_TYPE_LOCATION_UPDATE = 5,
} enp_message_type_t;

/** Optional TLV attribute descriptor (host representation). */
typedef struct enp_attribute {
    uint16_t type;
    const uint8_t* payload;
    uint16_t payload_size;
} enp_attribute_t;

/** HANDSHAKE message-specific header (host representation). */
typedef struct enp_handshake_header {
    uint32_t protocol_version;
    uint32_t capabilities_flags;
} enp_handshake_header_t;

/** DESTINATION message-specific header (host representation). */
typedef struct enp_destination_header {
    uint32_t direction;
    uint32_t distance_meters;
} enp_destination_header_t;

/** MOVEMENT message-specific header (host representation). */
typedef struct enp_movement_header {
    uint32_t direction;
    uint32_t speed_centimeters_per_second;
} enp_movement_header_t;

/** LOCATION_REQUEST message-specific header (host representation). */
typedef struct enp_location_request_header {
    uint32_t reserved0;
    uint32_t reserved1;
} enp_location_request_header_t;

/** LOCATION_UPDATE message-specific header (host representation). */
typedef struct enp_location_header {
    uint32_t latitude_e7;
    uint32_t longitude_e7;
} enp_location_header_t;

/** Returns encoded TLV size (type + length + payload) for one attribute. */
static inline size_t enp_attribute_encoded_size(uint16_t payload_size) {
    return (size_t)ENP_ATTRIBUTE_TYPE_SIZE_BYTES +
           (size_t)ENP_ATTRIBUTE_LENGTH_SIZE_BYTES +
           (size_t)payload_size;
}

/** Returns total encoded message size for 8-byte message-specific headers. */
static inline size_t enp_message_encoded_size(size_t attributes_total_size) {
    return (size_t)ENP_MESSAGE_TYPE_SIZE_BYTES +
           (size_t)ENP_MESSAGE_LENGTH_SIZE_BYTES +
           (size_t)ENP_FIXED_HEADER_SIZE_BYTES +
           attributes_total_size;
}

#ifdef __cplusplus
}
#endif

#endif /* EXTERNAL_NAVIGATION_PROTOCOL_H */
