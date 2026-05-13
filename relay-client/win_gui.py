
#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
USB Relay Client - Python GUI
简单的USB中继客户端图形界面
"""

import tkinter as tk
from tkinter import ttk, scrolledtext
import socket
import threading
import struct
import json
from datetime import datetime

# 协议常量
CMD_DEVLIST_REQ = 0x0001
CMD_DEVLIST_RES = 0x0002
CMD_IMPORT_REQ = 0x0003
CMD_IMPORT_RES = 0x0004
CMD_KEEPALIVE = 0x000A
CMD_PONG = 0x000B
CMD_LOG = 0x1001
CMD_DEVICE_UPDATE = 0x1002
CMD_IMPORT_DEVICE = 0x1003

DEFAULT_HOST = "192.168.31.97"
DEFAULT_PORT = 3240

class USBRelayClient:
    def __init__(self, root):
        self.root = root
        self.root.title("USB Relay Client")
        self.root.geometry("900x650")
        
        # 状态
        self.connected = False
        self.sock = None
        self.receive_thread = None
        self.running = False
        
        self.setup_ui()
        
    def setup_ui(self):
        # 主框架
        main_frame = ttk.Frame(self.root, padding="10")
        main_frame.pack(fill=tk.BOTH, expand=True)
        
        # 顶部：连接控制
        conn_frame = ttk.LabelFrame(main_frame, text="连接设置", padding="10")
        conn_frame.pack(fill=tk.X, pady=5)
        
        # 地址和端口
        ttk.Label(conn_frame, text="服务器地址:").grid(row=0, column=0, sticky=tk.W)
        self.host_var = tk.StringVar(value=DEFAULT_HOST)
        ttk.Entry(conn_frame, textvariable=self.host_var, width=20).grid(row=0, column=1, padx=5)
        
        ttk.Label(conn_frame, text="端口:").grid(row=0, column=2, sticky=tk.W)
        self.port_var = tk.StringVar(value=str(DEFAULT_PORT))
        ttk.Entry(conn_frame, textvariable=self.port_var, width=8).grid(row=0, column=3, padx=5)
        
        # 连接按钮
        self.conn_btn = ttk.Button(conn_frame, text="连接", command=self.toggle_connect)
        self.conn_btn.grid(row=0, column=4, padx=10)
        
        # 状态标签
        self.status_var = tk.StringVar(value="未连接")
        self.status_label = ttk.Label(conn_frame, textvariable=self.status_var, foreground="gray")
        self.status_label.grid(row=0, column=5, padx=5)
        
        # 中间：设备列表
        device_frame = ttk.LabelFrame(main_frame, text="设备列表", padding="10")
        device_frame.pack(fill=tk.BOTH, expand=True, pady=5)
        
        # 设备表格
        columns = ("ID", "VID", "PID", "Class", "Name")
        self.tree = ttk.Treeview(device_frame, columns=columns, show="headings", height=8)
        
        self.tree.heading("ID", text="ID")
        self.tree.heading("VID", text="VID")
        self.tree.heading("PID", text="PID")
        self.tree.heading("Class", text="Class")
        self.tree.heading("Name", text="Name")
        
        self.tree.column("ID", width=60, anchor=tk.CENTER)
        self.tree.column("VID", width=80, anchor=tk.CENTER)
        self.tree.column("PID", width=80, anchor=tk.CENTER)
        self.tree.column("Class", width=80, anchor=tk.CENTER)
        self.tree.column("Name", width=200, anchor=tk.W)
        
        self.tree.pack(side=tk.LEFT, fill=tk.BOTH, expand=True)
        
        # 滚动条
        scrollbar = ttk.Scrollbar(device_frame, orient=tk.VERTICAL, command=self.tree.yview)
        self.tree.configure(yscrollcommand=scrollbar.set)
        scrollbar.pack(side=tk.RIGHT, fill=tk.Y)
        
        # 操作按钮
        btn_frame = ttk.Frame(main_frame)
        btn_frame.pack(fill=tk.X, pady=5)
        
        ttk.Button(btn_frame, text="刷新设备", command=self.refresh_devices).pack(side=tk.LEFT, padx=5)
        ttk.Button(btn_frame, text="导入选中", command=self.import_device).pack(side=tk.LEFT, padx=5)
        ttk.Button(btn_frame, text="Ping", command=self.ping).pack(side=tk.LEFT, padx=5)
        
        # 底部：日志
        log_frame = ttk.LabelFrame(main_frame, text="日志", padding="10")
        log_frame.pack(fill=tk.BOTH, expand=True, pady=5)
        
        self.log_text = scrolledtext.ScrolledText(log_frame, height=12, wrap=tk.WORD)
        self.log_text.pack(fill=tk.BOTH, expand=True)
        self.log_text.config(state=tk.DISABLED)
        
    def log(self, message, level="INFO"):
        """添加日志（线程安全）"""
        timestamp = datetime.now().strftime("%H:%M:%S")
        full_msg = f"[{timestamp}] [{level}] {message}\n"
        
        def update_log():
            self.log_text.config(state=tk.NORMAL)
            self.log_text.insert(tk.END, full_msg)
            self.log_text.see(tk.END)
            self.log_text.config(state=tk.DISABLED)
        
        self.root.after(0, update_log)
        
    def update_devices_from_json(self, json_str):
        """从JSON更新设备列表"""
        try:
            devices = json.loads(json_str)
            # 清空现有设备
            for item in self.tree.get_children():
                self.tree.delete(item)
                
            for dev in devices:
                dev_id = dev.get("id", 0)
                vid = dev.get("vid", "0x0000")
                pid = dev.get("pid", "0x0000")
                cls = dev.get("class", "0x00")
                name = dev.get("name", "")
                self.tree.insert("", tk.END, values=(dev_id, vid, pid, cls, name))
                
            self.log(f"设备列表已更新: {len(devices)} 个设备")
        except Exception as e:
            self.log(f"解析设备列表失败: {e}", "ERROR")
        
    def toggle_connect(self):
        """切换连接状态"""
        if not self.connected:
            self.connect()
        else:
            self.disconnect()
            
    def connect(self):
        """连接到服务器"""
        host = self.host_var.get()
        try:
            port = int(self.port_var.get())
        except ValueError:
            self.log("端口号无效！", "ERROR")
            return
            
        try:
            self.log(f"正在连接 {host}:{port}...")
            self.sock = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
            self.sock.settimeout(5.0)
            self.sock.connect((host, port))
            
            self.connected = True
            self.running = True
            self.status_var.set(f"已连接: {host}:{port}")
            self.status_label.configure(foreground="green")
            self.conn_btn.config(text="断开")
            self.log("连接成功！")
            
            # 启动接收线程
            self.receive_thread = threading.Thread(target=self.receive_loop, daemon=True)
            self.receive_thread.start()
            
        except Exception as e:
            self.log(f"连接失败: {e}", "ERROR")
            self.cleanup_socket()
            
    def disconnect(self):
        """断开连接"""
        self.log("正在断开连接...")
        self.running = False
        self.cleanup_socket()
        
    def cleanup_socket(self):
        """清理socket"""
        self.connected = False
        if self.sock:
            try:
                self.sock.close()
            except:
                pass
            self.sock = None
        self.conn_btn.config(text="连接")
        self.status_var.set("未连接")
        self.status_label.configure(foreground="gray")
        self.log("已断开连接")
        
    def send_cmd(self, cmd, seq=0, dev_id=0, payload=b""):
        """发送命令"""
        if not self.connected or not self.sock:
            self.log("未连接到服务器", "ERROR")
            return False
            
        try:
            # 构建header: cmd(2) + seq(4) + dev_id(2) + length(4) + reserved(4)
            header = struct.pack('!HIHII', cmd, seq, dev_id, len(payload), 0)
            self.sock.sendall(header)
            
            if payload:
                self.sock.sendall(payload)
                
            return True
        except Exception as e:
            self.log(f"发送失败: {e}", "ERROR")
            self.disconnect()
            return False
        
    def ping(self):
        """Ping服务器"""
        self.log("发送 Ping...")
        self.send_cmd(CMD_KEEPALIVE, seq=1)
        
    def refresh_devices(self):
        """刷新设备列表"""
        self.log("请求设备列表...")
        if not self.connected:
            self.log("请先连接！", "WARN")
            return
            
        self.send_cmd(CMD_DEVLIST_REQ)
        
    def import_device(self):
        """导入选中设备"""
        selected = self.tree.selection()
        if not selected:
            self.log("请先选择设备！", "WARN")
            return
            
        item = self.tree.item(selected[0])
        dev_id = item['values'][0]
        
        self.log(f"请求导入设备 ID: {dev_id}...")
        if not self.connected:
            self.log("请先连接！", "WARN")
            return
            
        # 发送 CMD_IMPORT_DEVICE
        # Payload: dev_id(2) + reserved(2)
        payload = struct.pack('!HH', dev_id, 0)
        self.send_cmd(CMD_IMPORT_DEVICE, dev_id=dev_id, payload=payload)
        self.log("已发送导入请求，等待服务器响应...")
        
    def handle_device_update(self, payload):
        """处理设备更新通知"""
        try:
            json_str = payload.decode('utf-8', errors='ignore')
            self.log(f"收到设备更新")
            self.update_devices_from_json(json_str)
        except Exception as e:
            self.log(f"解析设备更新失败: {e}", "ERROR")
    
    def handle_log_message(self, payload):
        """处理日志消息"""
        try:
            if len(payload) < 13:
                return
            
            # 解析timestamp (8 bytes)
            timestamp = int.from_bytes(payload[:8], 'big')
            
            # 解析level (1 byte)
            level = payload[8]
            
            # 解析message length (4 bytes)
            msg_len = int.from_bytes(payload[9:13], 'big')
            
            # 解析message
            if len(payload) >= 13 + msg_len:
                message = payload[13:13+msg_len].decode('utf-8', errors='ignore')
                
                # 映射日志级别
                level_names = {0: "VERBOSE", 1: "DEBUG", 2: "INFO", 3: "WARN", 4: "ERROR"}
                level_name = level_names.get(level, "UNKNOWN")
                
                self.log(f"[SERVER-{level_name}] {message}")
        except Exception as e:
            self.log(f"解析日志失败: {e}", "ERROR")
    
    def handle_import_response(self, dev_id, payload):
        """处理设备导入响应"""
        try:
            if len(payload) < 8:
                self.log("导入响应数据不完整", "ERROR")
                return
            
            # 解析status (2 bytes)
            status = int.from_bytes(payload[:2], 'big')
            
            # 解析dev_id (2 bytes)
            resp_dev_id = int.from_bytes(payload[2:4], 'big')
            
            # 解析result_code (4 bytes)
            result_code = int.from_bytes(payload[4:8], 'big')
            
            # 解析message (如果有)
            message = ""
            if len(payload) > 8:
                message = payload[8:].decode('utf-8', errors='ignore')
            
            if status == 0:
                self.log(f"✅ 设备导入成功 (ID: {resp_dev_id})")
                if message:
                    self.log(f"   {message}")
                self.log(f"⚠️  注意：完整的USB虚拟化需要驱动集成，当前为占位符实现")
            else:
                self.log(f"❌ 设备导入失败 (ID: {resp_dev_id}, Code: {result_code})")
                if message:
                    self.log(f"   错误: {message}")
        except Exception as e:
            self.log(f"解析导入响应失败: {e}", "ERROR")
        
    def receive_loop(self):
        """接收消息循环 - 使用正确的协议解析"""
        buffer = b""
        
        while self.running and self.connected:
            try:
                # 读取数据
                data = self.sock.recv(4096)
                if not data:
                    self.log("连接已关闭")
                    break
                
                buffer += data
                
                # 处理所有完整的消息
                while len(buffer) >= 16:
                    # 解析头部 (16字节)
                    if len(buffer) < 16:
                        break
                    
                    cmd, seq, dev_id, length, _reserved = struct.unpack('!HIHII', buffer[:16])
                    
                    # 检查是否有完整的payload
                    if len(buffer) < 16 + length:
                        break  # 等待更多数据
                    
                    # 提取payload
                    payload = buffer[16:16+length]
                    buffer = buffer[16+length:]
                    
                    # 根据命令码处理
                    if cmd == CMD_DEVICE_UPDATE:  # 0x1002
                        self.handle_device_update(payload)
                    elif cmd == CMD_LOG:  # 0x1001
                        self.handle_log_message(payload)
                    elif cmd == CMD_PONG:  # 0x000B
                        self.log("Pong received")
                    elif cmd == CMD_IMPORT_RES:  # 0x0004
                        self.handle_import_response(dev_id, payload)
                    elif cmd == CMD_DEVLIST_RES:  # 0x0002
                        self.log(f"收到设备列表响应")
                        self.update_devices_from_json(payload.decode('utf-8', errors='ignore'))
                    else:
                        self.log(f"未知命令: 0x{cmd:04x}", "DEBUG")
                
            except socket.timeout:
                continue
            except Exception as e:
                if self.running:
                    self.log(f"接收错误: {e}", "ERROR")
                break
                
        self.cleanup_socket()
        
def main():
    root = tk.Tk()
    app = USBRelayClient(root)
    root.mainloop()
    
if __name__ == "__main__":
    main()
