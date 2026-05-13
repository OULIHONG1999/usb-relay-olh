/**
 * USB/IP-OLH TCP Transport Layer
 *
 * 跨平台 TCP 收发封装 (Windows + Linux)
 */

#ifndef USBIP_OLH_TCP_TRANSPORT_H
#define USBIP_OLH_TCP_TRANSPORT_H

#include "protocol.h"

#ifdef _WIN32
typedef SOCKET socket_t;
#else
typedef int socket_t;
#define INVALID_SOCKET (-1)
#endif

/* Connection handle */
typedef struct {
    socket_t sock;
    char     host[256];
    uint16_t port;
    int      connected;
} usbip_olh_conn_t;

/* Initialize the transport layer (call once, Windows: init Winsock) */
int  usbip_olh_transport_init(void);

/* Cleanup transport layer */
void usbip_olh_transport_cleanup(void);

/* Connect to server */
int  usbip_olh_connect(usbip_olh_conn_t *conn, const char *host, uint16_t port);

/* Disconnect */
void usbip_olh_disconnect(usbip_olh_conn_t *conn);

/* Send a complete message (header + payload) */
int  usbip_olh_send_msg(usbip_olh_conn_t *conn,
                         const struct usbip_olh_header *header,
                         const void *payload);

/* Receive a complete message (header + payload) */
/* caller must provide payload_buf of sufficient size */
int  usbip_olh_recv_msg(usbip_olh_conn_t *conn,
                         struct usbip_olh_header *header,
                         void *payload_buf,
                         uint32_t payload_buf_size);

/* Send raw bytes (for URB data) */
int  usbip_olh_send_raw(usbip_olh_conn_t *conn, const void *data, uint32_t len);

/* Receive raw bytes */
int  usbip_olh_recv_raw(usbip_olh_conn_t *conn, void *buf, uint32_t len);

#endif /* USBIP_OLH_TCP_TRANSPORT_H */
