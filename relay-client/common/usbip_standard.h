/*
 * Standard USB/IP Protocol v1.1.1 Definitions
 * Compatible with usbip-win2 and Linux usbip
 */

#ifndef USBIP_STANDARD_PROTOCOL_H
#define USBIP_STANDARD_PROTOCOL_H

#include <stdint.h>

#ifdef _MSC_VER
#pragma pack(push, 1)
#define PACKED
#else
#define PACKED __attribute__((packed))
#endif

/* ============================================================
 * Operation Codes (from proto_op.h)
 * ============================================================ */

#define OP_REQ_DEVLIST  0x8005
#define OP_REP_DEVLIST  0x0005
#define OP_REQ_IMPORT   0x8003
#define OP_REP_IMPORT   0x0003

/* ============================================================
 * URB Command Codes (from proto.h)
 * ============================================================ */

#define USBIP_CMD_SUBMIT    1
#define USBIP_CMD_UNLINK    2
#define USBIP_RET_SUBMIT    3
#define USBIP_RET_UNLINK    4

/* ============================================================
 * Common Constants
 * ============================================================ */

#define USBIP_VERSION       0x0111  // v1.1.1
#define DEV_PATH_MAX        256
#define BUS_ID_SIZE         32
#define MAX_ISO_PACKETS     1024

/* USB Speed constants */
#define USB_SPEED_UNKNOWN   0
#define USB_SPEED_LOW       1
#define USB_SPEED_FULL      2
#define USB_SPEED_HIGH      3
#define USB_SPEED_WIRELESS  4
#define USB_SPEED_SUPER     5

/* Direction */
#define USBIP_DIR_OUT       0
#define USBIP_DIR_IN        1

/* Status codes */
#define USBIP_STATUS_OK                 0
#define USBIP_STATUS_NODEV              -1
#define USBIP_STATUS_ERROR              -19
#define USBIP_STATUS_STALL              -32
#define USBIP_STATUS_TIMEOUT            -110

/* ============================================================
 * Device Description Structures
 * ============================================================ */

typedef struct PACKED {
    uint8_t  bInterfaceClass;
    uint8_t  bInterfaceSubClass;
    uint8_t  bInterfaceProtocol;
    uint8_t  padding;
} usbip_usb_interface_t;

typedef struct PACKED {
    char     path[DEV_PATH_MAX];
    char     busid[BUS_ID_SIZE];
    uint32_t busnum;
    uint32_t devnum;
    uint32_t speed;
    uint16_t idVendor;
    uint16_t idProduct;
    uint16_t bcdDevice;
    uint8_t  bDeviceClass;
    uint8_t  bDeviceSubClass;
    uint8_t  bDeviceProtocol;
    uint8_t  bConfigurationValue;
    uint8_t  bNumConfigurations;
    uint8_t  bNumInterfaces;
} usbip_usb_device_t;

/* ============================================================
 * Operation Headers
 * ============================================================ */

typedef struct PACKED {
    uint16_t version;     // USBIP_VERSION
    uint16_t code;        // OP_XXX
    uint32_t status;      // 0 = OK
} op_common_t;

typedef struct PACKED {
    char busid[BUS_ID_SIZE];
} op_import_request_t;

typedef struct PACKED {
    usbip_usb_device_t udev;
} op_import_reply_t;

typedef struct PACKED {
    uint32_t ndev;        // Number of devices
} op_devlist_reply_t;

typedef struct PACKED {
    usbip_usb_device_t udev;
    // Followed by: usbip_usb_interface_t interfaces[]
} op_devlist_reply_extra_t;

/* ============================================================
 * URB Header Structures
 * ============================================================ */

typedef struct PACKED {
    uint32_t command;     // USBIP_CMD_SUBMIT or USBIP_CMD_UNLINK
    uint32_t seqnum;      // Sequence number
    uint32_t devid;       // busnum << 16 | devnum
    uint32_t direction;   // USBIP_DIR_IN or USBIP_DIR_OUT
    uint32_t ep;          // Endpoint number
} usbip_header_basic_t;

typedef struct PACKED {
    uint32_t transfer_flags;
    int32_t  transfer_buffer_length;
    int32_t  start_frame;
    int32_t  number_of_packets;  // -1 for non-ISO
    int32_t  interval;
    uint8_t  setup_packet[8];    // For control transfers
} usbip_header_cmd_submit_t;

typedef struct PACKED {
    int32_t status;
    int32_t actual_length;
    int32_t start_frame;
    int32_t number_of_packets;
    int32_t error_count;
} usbip_header_ret_submit_t;

typedef struct PACKED {
    uint32_t seqnum;      // Seqnum of URB to unlink
} usbip_header_cmd_unlink_t;

typedef struct PACKED {
    int32_t status;
} usbip_header_ret_unlink_t;

/* Complete URB header (48 bytes) */
typedef struct PACKED {
    usbip_header_basic_t base;
    union {
        usbip_header_cmd_submit_t cmd_submit;
        usbip_header_ret_submit_t ret_submit;
        usbip_header_cmd_unlink_t cmd_unlink;
        usbip_header_ret_unlink_t ret_unlink;
    };
} usbip_header_t;

/* ISO packet descriptor */
typedef struct PACKED {
    uint32_t offset;
    uint32_t length;
    uint32_t actual_length;
    uint32_t status;
} usbip_iso_packet_descriptor_t;

#ifdef _MSC_VER
#pragma pack(pop)
#endif

#undef PACKED

#endif /* USBIP_STANDARD_PROTOCOL_H */
