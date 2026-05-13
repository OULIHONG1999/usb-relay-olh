/**
 * USB/IP-OLH TCP Transport Implementation
 */

#include "tcp_transport.h"
#include <stdio.h>
#include <stdlib.h>
#include <string.h>

#ifdef _WIN32
#include <winsock2.h>
#include <ws2tcpip.h>
#else
#include <unistd.h>
#include <sys/socket.h>
#include <netinet/in.h>
#include <netinet/tcp.h>
#include <arpa/inet.h>
#include <netdb.h>
#include <errno.h>
#endif

int usbip_olh_transport_init(void) {
#ifdef _WIN32
    WSADATA wsa;
    return WSAStartup(MAKEWORD(2, 2), &wsa) == 0 ? 0 : -1;
#else
    return 0; /* No init needed on Linux */
#endif
}

void usbip_olh_transport_cleanup(void) {
#ifdef _WIN32
    WSACleanup();
#endif
}

int usbip_olh_connect(usbip_olh_conn_t *conn, const char *host, uint16_t port) {
    struct sockaddr_in addr;
    struct hostent *he;
    int opt;

    memset(conn, 0, sizeof(*conn));
    strncpy(conn->host, host, sizeof(conn->host) - 1);
    conn->port = port;
    conn->sock = INVALID_SOCKET;

    /* Create socket */
    conn->sock = socket(AF_INET, SOCK_STREAM, IPPROTO_TCP);
    if (conn->sock == INVALID_SOCKET) return -1;

    /* Set TCP_NODELAY */
    opt = 1;
    setsockopt(conn->sock, IPPROTO_TCP, TCP_NODELAY, (const char *)&opt, sizeof(opt));

    /* Resolve host */
    he = gethostbyname(host);
    if (!he) {
#ifdef _WIN32
        closesocket(conn->sock);
#else
        close(conn->sock);
#endif
        return -1;
    }

    memset(&addr, 0, sizeof(addr));
    addr.sin_family = AF_INET;
    addr.sin_port = htons(port);
    memcpy(&addr.sin_addr, he->h_addr, he->h_length);

    /* Connect */
    if (connect(conn->sock, (struct sockaddr *)&addr, sizeof(addr)) != 0) {
#ifdef _WIN32
        closesocket(conn->sock);
#else
        close(conn->sock);
#endif
        return -1;
    }

    conn->connected = 1;
    return 0;
}

void usbip_olh_disconnect(usbip_olh_conn_t *conn) {
    if (conn->sock != INVALID_SOCKET) {
#ifdef _WIN32
        closesocket(conn->sock);
#else
        close(conn->sock);
#endif
        conn->sock = INVALID_SOCKET;
    }
    conn->connected = 0;
}

int usbip_olh_send_raw(usbip_olh_conn_t *conn, const void *data, uint32_t len) {
    const char *p = (const char *)data;
    uint32_t remaining = len;

    while (remaining > 0) {
        int sent = send(conn->sock, p, remaining, 0);
        if (sent <= 0) return -1;
        p += sent;
        remaining -= sent;
    }
    return 0;
}

int usbip_olh_recv_raw(usbip_olh_conn_t *conn, void *buf, uint32_t len) {
    char *p = (char *)buf;
    uint32_t remaining = len;

    while (remaining > 0) {
        int recvd = recv(conn->sock, p, remaining, 0);
        if (recvd <= 0) return -1;
        p += recvd;
        remaining -= recvd;
    }
    return 0;
}

int usbip_olh_send_msg(usbip_olh_conn_t *conn,
                        const struct usbip_olh_header *header,
                        const void *payload) {
    struct usbip_olh_header h_net;

    /* Copy and convert header to network byte order */
    memcpy(&h_net, header, sizeof(h_net));
    usbip_olh_header_hton(&h_net);

    /* Send header */
    if (usbip_olh_send_raw(conn, &h_net, sizeof(h_net)) != 0)
        return -1;

    /* Send payload if present */
    if (header->length > 0 && payload != NULL) {
        if (usbip_olh_send_raw(conn, payload, header->length) != 0)
            return -1;
    }

    return 0;
}

int usbip_olh_recv_msg(usbip_olh_conn_t *conn,
                        struct usbip_olh_header *header,
                        void *payload_buf,
                        uint32_t payload_buf_size) {
    /* Receive header */
    if (usbip_olh_recv_raw(conn, header, sizeof(*header)) != 0)
        return -1;

    /* Convert from network byte order */
    usbip_olh_header_ntoh(header);

    /* Receive payload if any */
    if (header->length > 0) {
        if (header->length > payload_buf_size) return -2; /* Buffer too small */
        if (usbip_olh_recv_raw(conn, payload_buf, header->length) != 0)
            return -1;
    }

    return 0;
}
