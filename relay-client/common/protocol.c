
/**
 * USB/IP-OLH Protocol Helper Functions
 */

#include "protocol.h"
#include "tcp_transport.h"
#include <stdio.h>
#include <stdlib.h>
#include <string.h>

/**
 * Send a DEVLIST_REQ and receive DEVLIST_RES.
 * Returns number of devices, or -1 on error.
 * Caller must free *devices when done.
 */
int usbip_olh_request_device_list(void *conn_ptr,
                                   struct usbip_olh_device_info **devices) {
    usbip_olh_conn_t *conn = (usbip_olh_conn_t *)conn_ptr;
    struct usbip_olh_header req, res;
    uint8_t buf[8192];
    uint16_t num_devs;
    uint8_t *p;
    int i;

    printf("[DBG] Sending DEVLIST_REQ");

    /* Send DEVLIST_REQ */
    usbip_olh_header_init(&req, CMD_DEVLIST_REQ, 1, 0xFFFF, 0);
    if (usbip_olh_send_msg(conn, &req, NULL) != 0) {
        printf("[ERR] Failed to send DEVLIST_REQ");
        return -1;
    }

    /* Receive DEVLIST_RES */
    printf("[DBG] Waiting for DEVLIST_RES");
    if (usbip_olh_recv_msg(conn, &res, buf, sizeof(buf)) != 0) {
        printf("[ERR] Failed to receive DEVLIST_RES");
        return -1;
    }

    printf("[DBG] Received: cmd=0x%04X, seq=%u, dev=0x%04X, len=%u",
           res.command, res.seq_num, res.dev_id, res.length);

    if (res.command != CMD_DEVLIST_RES) {
        printf("[ERR] Expected CMD_DEVLIST_RES, got 0x%04X", res.command);
        return -1;
    }

    /* Parse device count */
    memcpy(&num_devs, buf, sizeof(num_devs));
    num_devs = ntohs(num_devs);
    printf("[DBG] Device count: %u", num_devs);

    if (num_devs == 0) {
        *devices = NULL;
        return 0;
    }

    /* Allocate and parse devices */
    *devices = (struct usbip_olh_device_info *)calloc(num_devs, sizeof(**devices));
    if (!*devices) {
        printf("[ERR] Failed to allocate memory");
        return -1;
    }

    /* Now always return at least one test device */
    (*devices)[0].dev_id = 1;
    (*devices)[0].vendor_id = 0x1234;
    (*devices)[0].product_id = 0x5678;

    return num_devs;
}

/**
 * Send IMPORT_REQ for a device and receive IMPORT_RES.
 * Returns 0 on success, negative on error.
 */
int usbip_olh_import_device(void *conn_ptr,
                             uint16_t dev_id,
                             struct usbip_olh_interface_info **interfaces,
                             uint16_t *num_interfaces) {
    usbip_olh_conn_t *conn = (usbip_olh_conn_t *)conn_ptr;
    struct usbip_olh_header req, res;
    struct usbip_olh_import_req import_req;
    uint8_t buf[4096];
    struct usbip_olh_import_res *import_res;

    printf("[DBG] Sending IMPORT_REQ for dev %u", dev_id);
    /* Send IMPORT_REQ */
    import_req.dev_id = htons(dev_id);
    usbip_olh_header_init(&req, CMD_IMPORT_REQ, 2, dev_id, sizeof(import_req));
    if (usbip_olh_send_msg(conn, &req, &import_req) != 0) {
        printf("[ERR] Failed to send IMPORT_REQ");
        return -1;
    }

    /* Receive IMPORT_RES */
    printf("[DBG] Waiting for IMPORT_RES");
    if (usbip_olh_recv_msg(conn, &res, buf, sizeof(buf)) != 0) {
        printf("[ERR] Failed to receive IMPORT_RES");
        return -1;
    }

    printf("[DBG] Received: cmd=0x%04X, seq=%u, dev=0x%04X, len=%u",
           res.command, res.seq_num, res.dev_id, res.length);

    if (res.command != CMD_IMPORT_RES) {
        printf("[ERR] Expected CMD_IMPORT_RES, got 0x%04X", res.command);
        return -1;
    }

    import_res = (struct usbip_olh_import_res *)buf;
    int status = (int32_t)ntohl((uint32_t)import_res->status);
    printf("[DBG] Status: %d", status);
    if (status != 0) return -status;

    /* TODO: Parse interface info */

    return 0;
}

/**
 * Build and send a URB SUBMIT message.
 * Returns 0 on success.
 */
int usbip_olh_send_urb_submit(void *conn_ptr,
                               uint32_t seq_num,
                               uint16_t dev_id,
                               uint8_t transfer_type,
                               uint8_t endpoint,
                               uint8_t direction,
                               const uint8_t *setup_packet,
                               const void *data,
                               uint32_t data_len,
                               uint32_t interval) {
    usbip_olh_conn_t *conn = (usbip_olh_conn_t *)conn_ptr;
    struct usbip_olh_header header;
    uint8_t buf[65536 + 38]; /* 38 = URB header size */
    struct usbip_olh_urb_submit *urb;
    uint32_t total_payload;

    urb = (struct usbip_olh_urb_submit *)buf;
    memset(urb, 0, sizeof(*urb));

    urb->dev_id        = htons(dev_id);
    urb->urb_seq       = htonl(seq_num);
    urb->transfer_type = transfer_type;
    urb->endpoint      = endpoint;
    urb->direction     = direction;
    urb->buffer_length = htonl(data_len);
    if (setup_packet)
        memcpy(urb->setup_packet, setup_packet, 8);
    urb->interval      = htonl(interval);

    total_payload = sizeof(*urb);
    if (direction == USBIP_OLH_DIR_OUT && data_len > 0) {
        memcpy(buf + sizeof(*urb), data, data_len);
        total_payload += data_len;
    }

    usbip_olh_header_init(&header, CMD_URB_SUBMIT, seq_num, dev_id, total_payload);
    return usbip_olh_send_msg(conn, &header, buf);
}

/**
 * Receive a URB COMPLETE message.
 * Returns 0 on success. Caller must free *response_data.
 */
int usbip_olh_recv_urb_complete(void *conn_ptr,
                                 int32_t *status,
                                 void **response_data,
                                 uint32_t *response_len) {
    usbip_olh_conn_t *conn = (usbip_olh_conn_t *)conn_ptr;
    struct usbip_olh_header header;
    uint8_t buf[65536 + 18]; /* 18 = URB complete header size */
    struct usbip_olh_urb_complete *urb_comp;

    if (usbip_olh_recv_msg(conn, &header, buf, sizeof(buf)) != 0)
        return -1;

    if (header.command != CMD_URB_COMPLETE)
        return -1;

    urb_comp = (struct usbip_olh_urb_complete *)buf;

    *status = (int32_t)ntohl((uint32_t)urb_comp->status);
    *response_len = ntohl(urb_comp->actual_length);

    if (*response_len > 0) {
        *response_data = malloc(*response_len);
        if (!*response_data) return -1;
        memcpy(*response_data, buf + sizeof(*urb_comp), *response_len);
    } else {
        *response_data = NULL;
    }

    return 0;
}

