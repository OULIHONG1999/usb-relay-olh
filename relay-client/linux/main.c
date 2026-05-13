/**
 * Relay Client - Linux
 *
 * 连接 Relay Host (Android)，访问远程 USB 设备
 *
 * 用法: relay-client <android_ip> [port]
 */

#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <signal.h>
#include <unistd.h>
#include "protocol.h"
#include "tcp_transport.h"

static volatile int running = 1;
void sig_handler(int sig) { (void)sig; running = 0; }

static void print_devices(struct usbip_olh_device_info *devs, int count) {
    printf("\n╔══════════════════════════════════════════════╗\n");
    printf("║        Remote USB Devices (%d)               \n", count);
    printf("╠════╦════════════╦════════╦════════╦══════════╣\n");
    printf("║ ID ║ Path       ║ VID    ║ PID    ║ Class    ║\n");
    printf("╠════╬════════════╬════════╬════════╬══════════╣\n");
    for (int i = 0; i < count; i++) {
        printf("║ %2d ║ %-10s ║ %04x   ║ %04x   ║ %02x       ║\n",
               devs[i].dev_id, devs[i].path, devs[i].vendor_id,
               devs[i].product_id, devs[i].device_class);
    }
    printf("╚════╩════════════╩════════╩════════╩══════════╝\n\n");
}

static void interactive_loop(usbip_olh_conn_t *conn) {
    char input[256];
    printf("Relay Client ready. Commands:\n  list   - List devices\n  import <id> - Import device\n  quit   - Exit\n\n");

    while (running) {
        printf("relay-client> "); fflush(stdout);
        if (!fgets(input, sizeof(input), stdin)) break;
        char *nl = strchr(input, '\n'); if (nl) *nl = '\0';

        if (strncmp(input, "list", 4) == 0) {
            struct usbip_olh_device_info *devs = NULL;
            int n = usbip_olh_request_device_list(conn, &devs);
            if (n < 0) printf("Error: failed to get device list\n");
            else if (n == 0) printf("No remote devices found\n");
            else { print_devices(devs, n); free(devs); }
        } else if (strncmp(input, "import", 6) == 0) {
            int id = atoi(input + 7);
            if (id <= 0) { printf("Usage: import <id>\n"); continue; }
            printf("Importing device %d...\n", id);
            int r = usbip_olh_import_device(conn, id, NULL, NULL);
            printf(r == 0 ? "Device %d imported ✓\n" : "Import failed: %d\n", id, r);
        } else if (strncmp(input, "quit", 4) == 0) break;
        else if (strlen(input) > 0) printf("Unknown: %s\n", input);
    }
}

int main(int argc, char *argv[]) {
    if (argc < 2) {
        fprintf(stderr, "Relay Client v0.1.0\nUsage: %s <host> [port]\n", argv[0]);
        return 1;
    }
    const char *host = argv[1];
    uint16_t port = argc >= 3 ? (uint16_t)atoi(argv[2]) : 3240;

    signal(SIGINT, sig_handler);
    usbip_olh_transport_init();

    printf("Connecting to %s:%d...\n", host, port);
    usbip_olh_conn_t conn;
    if (usbip_olh_connect(&conn, host, port) != 0) {
        fprintf(stderr, "Connection failed\n");
        usbip_olh_transport_cleanup();
        return 1;
    }
    printf("Connected to Relay Host ✓\n");

    interactive_loop(&conn);
    usbip_olh_disconnect(&conn);
    usbip_olh_transport_cleanup();
    printf("Disconnected.\n");
    return 0;
}
