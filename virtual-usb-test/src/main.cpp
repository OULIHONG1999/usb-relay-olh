#include <windows.h>
#include <setupapi.h>
#include <initguid.h>
#include <devguid.h>
#include <tchar.h>
#include <stdio.h>
#include <iostream>
#include <vector>
#include <string>

// 定义 USB 设备接口 GUID
DEFINE_GUID(GUID_DEVINTERFACE_USB_DEVICE, 0xA5DCBF10L, 0x6530, 0x11D2, 0x90, 0x1F, 0x00, 0xC0, 0x4F, 0xB9, 0x51, 0xED);

// 获取设备描述字符串
std::string GetDeviceProperty(HDEVINFO hDevInfo, SP_DEVINFO_DATA& DeviceInfoData, DWORD Property) {
    DWORD DataT;
    char buffer[4096];
    DWORD buffersize = 4096;
    
    if (SetupDiGetDeviceRegistryPropertyA(hDevInfo, &DeviceInfoData, Property, &DataT, (PBYTE)buffer, buffersize, &buffersize)) {
        return std::string(buffer);
    }
    return std::string();
}

// 获取硬件 ID
std::vector<std::string> GetHardwareIds(HDEVINFO hDevInfo, SP_DEVINFO_DATA& DeviceInfoData) {
    std::vector<std::string> result;
    DWORD DataT;
    char buffer[8192];
    DWORD buffersize = 8192;
    
    if (SetupDiGetDeviceRegistryPropertyA(hDevInfo, &DeviceInfoData, SPDRP_HARDWAREID, &DataT, (PBYTE)buffer, buffersize, &buffersize)) {
        char* ptr = buffer;
        while (*ptr) {
            result.push_back(std::string(ptr));
            ptr += strlen(ptr) + 1;
        }
    }
    return result;
}

// 枚举所有 USB 设备
void EnumerateUsbDevices() {
    HDEVINFO hDevInfo;
    SP_DEVINFO_DATA DeviceInfoData;
    DeviceInfoData.cbSize = sizeof(SP_DEVINFO_DATA);
    DWORD i;

    printf("=== 枚举 Windows USB 设备 ===\n\n");
    
    // 获取所有 USB 设备信息集
    hDevInfo = SetupDiGetClassDevsA(&GUID_DEVINTERFACE_USB_DEVICE, NULL, NULL, DIGCF_PRESENT | DIGCF_DEVICEINTERFACE);
    
    if (hDevInfo == INVALID_HANDLE_VALUE) {
        printf("无法获取设备信息！错误: %lu\n", GetLastError());
        return;
    }

    // 枚举每个设备
    for (i = 0; SetupDiEnumDeviceInfo(hDevInfo, i, &DeviceInfoData); i++) {
        std::string name = GetDeviceProperty(hDevInfo, DeviceInfoData, SPDRP_FRIENDLYNAME);
        if (name.empty()) {
            name = GetDeviceProperty(hDevInfo, DeviceInfoData, SPDRP_DEVICEDESC);
        }
        
        std::string hardwareId = GetDeviceProperty(hDevInfo, DeviceInfoData, SPDRP_HARDWAREID);
        std::string manufacturer = GetDeviceProperty(hDevInfo, DeviceInfoData, SPDRP_MFG);
        std::string location = GetDeviceProperty(hDevInfo, DeviceInfoData, SPDRP_LOCATION_INFORMATION);
        
        printf("设备 #%lu:\n", i + 1);
        printf("  名称: %s\n", name.empty() ? "(未知)" : name.c_str());
        printf("  制造商: %s\n", manufacturer.empty() ? "(未知)" : manufacturer.c_str());
        printf("  位置: %s\n", location.empty() ? "(未知)" : location.c_str());
        
        // 显示硬件 ID（包含 VID/PID）
        printf("  硬件ID:\n");
        auto hids = GetHardwareIds(hDevInfo, DeviceInfoData);
        for (const auto& hid : hids) {
            printf("    - %s\n", hid.c_str());
            
            // 提取 VID/PID
            std::string::size_type vid_pos = hid.find("VID_");
            std::string::size_type pid_pos = hid.find("PID_");
            if (vid_pos != std::string::npos && pid_pos != std::string::npos) {
                std::string vid = hid.substr(vid_pos + 4, 4);
                std::string pid = hid.substr(pid_pos + 4, 4);
                printf("      VID = 0x%s, PID = 0x%s\n", vid.c_str(), pid.c_str());
            }
        }
        
        printf("\n");
    }

    if (i == 0) {
        printf("没有找到 USB 设备！\n");
    } else {
        printf("总计找到 %lu 个 USB 设备。\n", i);
    }

    SetupDiDestroyDeviceInfoList(hDevInfo);
}

int main() {
    printf("Virtual USB Test Tool v0.1\n\n");
    EnumerateUsbDevices();
    
    printf("\n按任意键退出...");
    getchar();
    
    return 0;
}
