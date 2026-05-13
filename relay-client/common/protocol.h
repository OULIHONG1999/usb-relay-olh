/**
 * USB/IP-OLH Protocol Definitions (C)
 *
 * 共享协议定义，PC端 (Windows/Linux) 使用
 */

#ifndef USBIP_OLH_PROTOCOL_H
#define USBIP_OLH_PROTOCOL_H

#include <stdint.h>

#ifdef _WIN32
/* Windows: winsock2.h must be included before windows.h */
#ifndef WIN32_LEAN_AND_MEAN
#define WIN32_LEAN_AND_MEAN
#endif
#include <winsock2.h>
#include <ws2tcpip.h>
#pragma comment(lib, "ws2_32.lib")
#else
#include <arpa/inet.h>
#endif

/* ============================================================
 * Constants
 * ============================================================ */

#define USBIP_OLH_PORT          3240
#define USBIP_OLH_VERSION       0x0100

/* Command codes */
#define CMD_DEVLIST_REQ         0x0001
#define CMD_DEVLIST_RES         0x0002
#define CMD_IMPORT_REQ          0x0003
#define CMD_IMPORT_RES          0x0004
#define CMD_URB_SUBMIT          0x0005
#define CMD_URB_COMPLETE        0x0006
#define CMD_URB_UNLINK          0x0007
#define CMD_URB_UNLINK_RET      0x0008
#define CMD_DISCONNECT          0x0009
#define CMD_KEEPALIVE           0x000A
#define CMD_LOG                 0x1001
#define CMD_DEVICE_UPDATE       0x1002  /* Server pushes device list update */
#define CMD_IMPORT_DEVICE       0x1003  /* Client requests to import specific device */

/* USB transfer types */
#define USBIP_OLH_TRANSFER_ISO  0
#define USBIP_OLH_TRANSFER_INT  1
#define USBIP_OLH_TRANSFER_CTRL 2
#define USBIP_OLH_TRANSFER_BULK 3

/* URB Status codes */
#define USBIP_OLH_URB_STATUS_OK         0
#define USBIP_OLH_URB_STATUS_NO_DEVICE  -1
#define USBIP_OLH_URB_STATUS_STALL      -2
#define USBIP_OLH_URB_STATUS_ERROR      -3
#define USBIP_OLH_URB_STATUS_TIMEOUT    -4

/* USB direction */
#define USBIP_OLH_DIR_OUT       0
#define USBIP_OLH_DIR_IN        1

/* USB speed */
#define USBIP_OLH_SPEED_LOW     1
#define USBIP_OLH_SPEED_FULL    2
#define USBIP_OLH_SPEED_HIGH    3

/* USB status codes */
#define USBIP_OLH_STATUS_OK         0
#define USBIP_OLH_STATUS_ERROR      -1
#define USBIP_OLH_STATUS_DEV_GONE   -2
#define USBIP_OLH_STATUS_TIMEOUT    -3
#define USBIP_OLH_STATUS_STALL      -4
#define USBIP_OLH_STATUS_OVERFLOW    -5

/* Limits */
#define USBIP_OLH_MAX_DEVICES       32
#define USBIP_OLH_MAX_INTERFACES    16
#define USBIP_OLH_MAX_ENDPOINTS     32
#define USBIP_OLH_MAX_STRING_LEN    256
#define USBIP_OLH_SETUP_PACKET_SIZE 8

/* ============================================================
 * Packed structures (wire format)
 * ============================================================ */

#ifdef _MSC_VER
#define PACKED_STRUCT(name) __pragma(pack(push, 1)) struct name __pragma(pack(pop))
#else
#define PACKED_STRUCT(name) struct __attribute__((packed)) name
#endif

/* --- Common Header (16 bytes) --- */
PACKED_STRUCT(usbip_olh_header) {
    uint16_t command;       /* CMD_xxx */
    uint32_t seq_num;       /* Sequence number for request-response pairing */
    uint16_t dev_id;        /* Device ID (0xFFFF = global) */
    uint32_t length;        /* Payload length (excluding header) */
    uint32_t reserved;      /* Alignment padding */
};

/* --- Endpoint Info --- */
PACKED_STRUCT(usbip_olh_endpoint_info) {
    uint8_t  bEndpointAddress;
    uint8_t  bmAttributes;       /* Transfer type: 0=ctrl, 1=iso, 2=bulk, 3=int */
    uint16_t wMaxPacketSize;
    uint8_t  bInterval;
};

/* --- Interface Info --- */
PACKED_STRUCT(usbip_olh_interface_info) {
    uint8_t  bInterfaceNumber;
    uint8_t  bAlternateSetting;
    uint8_t  bInterfaceClass;
    uint8_t  bInterfaceSubClass;
    uint8_t  bInterfaceProtocol;
    uint8_t  num_endpoints;
    /* Followed by: usbip_olh_endpoint_info endpoints[num_endpoints] */
};

/* --- Device Info (in DEVLIST_RES) --- */
#ifdef _MSC_VER
#pragma pack(push, 1)
typedef struct usbip_olh_device_info {
    uint16_t dev_id;
    uint16_t busnum;
    uint16_t devnum;
    uint16_t speed;             /* USBIP_OLH_SPEED_xxx */
    uint16_t vendor_id;
    uint16_t product_id;
    uint16_t bcdDevice;
    uint8_t  device_class;
    uint8_t  device_subclass;
    uint8_t  device_protocol;
    uint8_t  config_count;
    uint8_t  interface_count;
    uint16_t path_len;
    /* Followed by: char path[path_len] */
    /* Followed by: uint16_t desc_len */
    /* Followed by: uint8_t device_descriptor[desc_len] */
    /* Followed by: usbip_olh_interface_info interfaces[] */
} usbip_olh_device_info_t;
#pragma pack(pop)
#else
PACKED_STRUCT(usbip_olh_device_info) {
    uint16_t dev_id;
    uint16_t busnum;
    uint16_t devnum;
    uint16_t speed;             /* USBIP_OLH_SPEED_xxx */
    uint16_t vendor_id;
    uint16_t product_id;
    uint16_t bcdDevice;
    uint8_t  device_class;
    uint8_t  device_subclass;
    uint8_t  device_protocol;
    uint8_t  config_count;
    uint8_t  interface_count;
    uint16_t path_len;
    /* Followed by: char path[path_len] */
    /* Followed by: uint16_t desc_len */
    /* Followed by: uint8_t device_descriptor[desc_len] */
    /* Followed by: usbip_olh_interface_info interfaces[] */
};
#endif

/* --- URB Submit (0x0005) --- */
PACKED_STRUCT(usbip_olh_urb_submit) {
    uint16_t dev_id;
    uint32_t urb_seq;           /* URB sequence number for pairing/cancel */
    uint8_t  transfer_type;     /* USBIP_OLH_TRANSFER_xxx */
    uint8_t  endpoint;
    uint8_t  direction;         /* USBIP_OLH_DIR_xxx */
    uint8_t  reserved;
    uint32_t transfer_flags;
    uint32_t buffer_length;
    uint8_t  setup_packet[USBIP_OLH_SETUP_PACKET_SIZE];
    uint32_t interval;
    uint32_t number_of_packets; /* Isochronous only */
    uint32_t iso_frame_desc;    /* Isochronous only */
    /* Followed by: uint8_t buffer[buffer_length] (OUT direction only) */
};

/* --- URB Complete (0x0006) --- */
PACKED_STRUCT(usbip_olh_urb_complete) {
    uint16_t dev_id;
    uint32_t urb_seq;
    int32_t  status;            /* USBIP_OLH_STATUS_xxx */
    uint32_t actual_length;
    uint32_t error_count;       /* Isochronous only */
    /* Followed by: uint8_t buffer[actual_length] (IN direction only) */
};

/* --- URB Unlink (0x0007) --- */
PACKED_STRUCT(usbip_olh_urb_unlink) {
    uint16_t dev_id;
    uint32_t urb_seq;           /* URB sequence to cancel */
};

/* --- URB Unlink Return (0x0008) --- */
PACKED_STRUCT(usbip_olh_urb_unlink_ret) {
    uint16_t dev_id;
    uint32_t urb_seq;
    int32_t  status;
};

/* --- Import Request (0x0003) --- */
PACKED_STRUCT(usbip_olh_import_req) {
    uint16_t dev_id;
};

/* --- Import Response (0x0004) --- */
PACKED_STRUCT(usbip_olh_import_res) {
    int32_t  status;
    uint16_t dev_id;
    uint16_t num_interfaces;
    /* Followed by: usbip_olh_interface_info interfaces[num_interfaces] */
};

/* --- Disconnect (0x0009) --- */
PACKED_STRUCT(usbip_olh_disconnect) {
    uint16_t dev_id;
    uint16_t reason;            /* 0=user, 1=error, 2=device_removed */
};

/* ============================================================
 * Utility macros
 * ============================================================ */

#define USBIP_OLH_HEADER_SIZE   sizeof(struct usbip_olh_header)

/* Convert header fields to network byte order */
static inline void usbip_olh_header_hton(struct usbip_olh_header *h) {
    h->command  = htons(h->command);
    h->seq_num  = htonl(h->seq_num);
    h->dev_id   = htons(h->dev_id);
    h->length   = htonl(h->length);
    h->reserved = htonl(h->reserved);
}

static inline void usbip_olh_header_ntoh(struct usbip_olh_header *h) {
    h->command  = ntohs(h->command);
    h->seq_num  = ntohl(h->seq_num);
    h->dev_id   = ntohs(h->dev_id);
    h->length   = ntohl(h->length);
    h->reserved = ntohl(h->reserved);
}

/* Initialize a header */
static inline void usbip_olh_header_init(
    struct usbip_olh_header *h,
    uint16_t cmd,
    uint32_t seq,
    uint16_t dev_id,
    uint32_t payload_len)
{
    h->command  = cmd;
    h->seq_num  = seq;
    h->dev_id   = dev_id;
    h->length   = payload_len;
    h->reserved = 0;
}

/* ============================================================
 * Protocol Helper Functions
 * ============================================================ */

/**
 * Request device list from host.
 * Returns number of devices, or -1 on error.
 * Caller must free *devices when done.
 */
int usbip_olh_request_device_list(void *conn, struct usbip_olh_device_info **devices);

/**
 * Import a device from host.
 * Returns 0 on success, negative on error.
 */
int usbip_olh_import_device(void *conn, uint16_t dev_id, struct usbip_olh_interface_info **interfaces, uint16_t *num_interfaces);

/**
 * Send URB submit.
 */
int usbip_olh_send_urb_submit(void *conn, uint32_t seq_num, uint16_t dev_id,
                              uint8_t transfer_type, uint8_t endpoint, uint8_t direction,
                              const uint8_t *setup_packet, const void *data,
                              uint32_t data_len, uint32_t interval);

/**
 * Receive URB complete.
 */
int usbip_olh_recv_urb_complete(void *conn, int32_t *status, void **response_data, uint32_t *response_len);

/* ============================================================
 * URB Structures (for USB transfer)
 * ============================================================ */

/* --- URB Submit (CMD_URB_SUBMIT payload) --- */
PACKED_STRUCT(usbip_olh_urb_submit) {
    uint32_t seq_num;           /* Sequence number */
    uint16_t dev_id;            /* Device ID */
    uint8_t  transfer_type;     /* USBIP_OLH_TRANSFER_xxx */
    uint8_t  endpoint;          /* Endpoint number (0-15) */
    uint8_t  direction;         /* USBIP_OLH_DIR_xxx */
    uint8_t  reserved1;
    uint32_t data_len;          /* Transfer data length */
    uint32_t interval;          /* For ISO/INT transfers */
    uint8_t  setup_packet[8];   /* For CTRL transfers only */
};

/* --- URB Complete (CMD_URB_COMPLETE payload header) --- */
PACKED_STRUCT(usbip_olh_urb_complete) {
    uint32_t seq_num;           /* Matching submit seq_num */
    int32_t  status;            /* USBIP_OLH_URB_STATUS_xxx */
    uint32_t data_len;          /* Actual data length */
};

/* --- URB Unlink (CMD_URB_UNLINK payload) --- */
PACKED_STRUCT(usbip_olh_urb_unlink) {
    uint32_t seq_num;           /* Seq num of URB to unlink */
    uint16_t dev_id;            /* Device ID */
    uint16_t reserved;
};

/* --- URB Unlink Return (CMD_URB_UNLINK_RET payload) --- */
PACKED_STRUCT(usbip_olh_urb_unlink_ret) {
    uint32_t seq_num;           /* Matching unlink seq_num */
    int32_t  status;            /* Status of unlink operation */
};

#endif /* USBIP_OLH_PROTOCOL_H */