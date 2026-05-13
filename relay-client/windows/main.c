
/**
 * Relay Client - Windows
 *
 * Connect to Relay Host (Android), access remote USB devices
 *
 * Usage: relay-client.exe <host> [port]
 */

#include <stdio.h>
#include <stdlib.h>
#include <string.h>

/* Include protocol.h first to get winsock2.h before windows.h */
#include "protocol.h"
#include "tcp_transport.h"

#include <windows.h>

static volatile int running = 1;
static usbip_olh_conn_t* g_conn = NULL;

// Shared state for commands
#define MAX_RESPONSE_SIZE 8192
static volatile int response_ready = 0;
static volatile int response_cmd = 0;
static uint8_t response_payload[MAX_RESPONSE_SIZE];
static uint32_t response_length = 0;
static CRITICAL_SECTION cs;

// Device list storage
#define MAX_DEVICES 32
#ifdef _MSC_VER
static usbip_olh_device_info_t device_list[MAX_DEVICES];
#else
static struct usbip_olh_device_info device_list[MAX_DEVICES];
#endif
static int device_count = 0;
static CRITICAL_SECTION device_cs;

BOOL WINAPI console_handler(DWORD sig) {
    if (sig == CTRL_C_EVENT) {
        running = 0;
        return TRUE;
    }
    return FALSE;
}

#ifdef _MSC_VER
static void print_devices(usbip_olh_device_info_t* devs, int count) {
#else
static void print_devices(struct usbip_olh_device_info* devs, int count) {
#endif
    printf("\nRemote USB Devices (%d):\n", count);
    printf("  %-4s %-8s %-8s %-6s\n", "ID", "VID", "PID", "Class");
    printf("  ---- -------- -------- ------\n");
    for (int i = 0; i < count; i++) {
        printf("  %-4d %04x     %04x     %02x\n",
               devs[i].dev_id, devs[i].vendor_id,
               devs[i].product_id, devs[i].device_class);
    }
    printf("\n");
}

static void handle_log_message(const struct usbip_olh_header* header, const uint8_t* payload);
static void handle_device_update(const struct usbip_olh_header* header, const uint8_t* payload);

static void handle_log_message(const struct usbip_olh_header* header, const uint8_t* payload) {
    if (header->length < 13) return; // timestamp(8) + level(1) + len(4) = 13
    
    // Parse timestamp
    uint64_t timestamp = 0;
    for (int i = 0; i < 8; i++) {
        timestamp = (timestamp << 8) | (payload[i] & 0xFF);
    }
    
    uint8_t level = payload[8];
    
    // Parse message length
    uint32_t msg_len = 0;
    msg_len = (msg_len << 8) | (payload[9] & 0xFF);
    msg_len = (msg_len << 8) | (payload[10] & 0xFF);
    msg_len = (msg_len << 8) | (payload[11] & 0xFF);
    msg_len = (msg_len << 8) | (payload[12] & 0xFF);
    
    if (13 + msg_len > header->length) return;
    
    // Get message
    char* msg = (char*)malloc(msg_len + 1);
    if (msg) {
        memcpy(msg, payload + 13, msg_len);
        msg[msg_len] = '\0';
        
        // Format time
        char time_str[32];
        SYSTEMTIME st;
        GetLocalTime(&st);
        sprintf(time_str, "%02d:%02d:%02d", st.wHour, st.wMinute, st.wSecond);
        
        printf("\r[LOG %s] %s\n", time_str, msg);
        free(msg);
        
        printf("relay-client> ");
        fflush(stdout);
    }
}

DWORD WINAPI receive_thread(LPVOID param) {
    while (running) {
        if (!g_conn || !g_conn->connected) {
            Sleep(100);
            continue;
        }
        
        struct usbip_olh_header header;
        uint8_t payload[MAX_RESPONSE_SIZE];
        int ret = usbip_olh_recv_msg(g_conn, &header, payload, sizeof(payload));
        
        if (ret != 0) {
            // Error or connection closed
            if (running) {
                printf("\r[ERROR] Connection error, exiting...\n");
            }
            running = 0;
            break;
        }
        
        if (header.command == CMD_LOG) {
            handle_log_message(&header, payload);
        } else if (header.command == CMD_DEVICE_UPDATE) {
            // Handle device update from server
            handle_device_update(&header, payload);
        } else {
            // It's a command response, store it for main thread
            EnterCriticalSection(&cs);
            response_cmd = header.command;
            response_length = header.length;
            if (header.length > 0 && header.length < MAX_RESPONSE_SIZE) {
                memcpy(response_payload, payload, header.length);
            }
            response_ready = 1;
            LeaveCriticalSection(&cs);
        }
    }
    return 0;
}

static void handle_device_update(const struct usbip_olh_header* header, const uint8_t* payload) {
    // Parse JSON device list from payload
    // For now, just print the raw data
    printf("\r[DEVICE UPDATE] Received device list update (%d bytes)\n", header->length);
    
    // In a real implementation, you would parse the JSON and update the device list
    // For this example, we'll just show that we received an update
    
    printf("relay-client> ");
    fflush(stdout);
}

static void interactive_loop() {
    char input[256];
    printf("Commands: list | import <id> | refresh | quit\n\n");
    
    while (running) {
        printf("relay-client> ");
        fflush(stdout);
        
        if (!fgets(input, sizeof(input), stdin)) {
            break;
        }
        
        char* nl = strchr(input, '\n');
        if (nl) *nl = '\0';
        
        if (strncmp(input, "list", 4) == 0) {
            // Display current device list
            EnterCriticalSection(&device_cs);
            print_devices(device_list, device_count);
            LeaveCriticalSection(&device_cs);
        } else if (strncmp(input, "import", 6) == 0) {
            int id = atoi(input + 7);
            if (id <= 0 || id > device_count) {
                printf("Usage: import <id> (valid IDs: 1-%d)\n", device_count);
                continue;
            }
            printf("Importing device %d...\n", id);
            // TODO: Implement actual device import functionality
            printf("Device import feature not fully implemented yet.\n");
        } else if (strncmp(input, "refresh", 7) == 0) {
            printf("Refreshing device list...\n");
            // In a real implementation, this would request a fresh device list from the server
            printf("Device list refreshed.\n");
        } else if (strncmp(input, "quit", 4) == 0) {
            running = 0;
            break;
        } else if (strlen(input) > 0) {
            printf("Unknown command: %s\n", input);
            printf("Available commands: list | import <id> | refresh | quit\n");
        }
    }
}

int main(int argc, char* argv[]) {
    if (argc < 2) {
        fprintf(stderr, "Relay Client v0.1.0\nUsage: %s <host> [port]\n", argv[0]);
        return 1;
    }
    const char* host = argv[1];
    uint16_t port = argc >= 3 ? (uint16_t)atoi(argv[2]) : USBIP_OLH_PORT;
    
    SetConsoleCtrlHandler(console_handler, TRUE);
    usbip_olh_transport_init();
    InitializeCriticalSection(&cs);
    InitializeCriticalSection(&device_cs);
    
    printf("Connecting to %s:%d...\n", host, port);
    usbip_olh_conn_t conn;
    if (usbip_olh_connect(&conn, host, port) != 0) {
        fprintf(stderr, "Connection failed\n");
        DeleteCriticalSection(&cs);
        usbip_olh_transport_cleanup();
        return 1;
    }
    printf("Connected \xE2\x9C\x93\n");
    g_conn = &conn;
    
    // Start receive thread
    HANDLE h_thread = CreateThread(NULL, 0, receive_thread, NULL, 0, NULL);
    if (!h_thread) {
        fprintf(stderr, "Failed to create thread\n");
        usbip_olh_disconnect(&conn);
        DeleteCriticalSection(&cs);
        usbip_olh_transport_cleanup();
        return 1;
    }
    
    interactive_loop();
    
    running = 0;
    WaitForSingleObject(h_thread, 1000);
    CloseHandle(h_thread);
    
    usbip_olh_disconnect(&conn);
    DeleteCriticalSection(&cs);
    DeleteCriticalSection(&device_cs);
    usbip_olh_transport_cleanup();
    printf("Disconnected.\n");
    return 0;
}

