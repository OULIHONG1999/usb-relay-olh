
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
CMD_PING = 0x0001
CMD_PONG = 0x0002
CMD_DEVLIST_REQ = 0x0010
CMD_DEVLIST_RES = 0x0011
CMD_LOG = 0x0020
CMD_DEV_UPDATE = 0x0030

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
        
        # 添加默认测试设备
        self.add_test_device()
        
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
        
    def add_test_device(self):
        """添加测试设备"""
        self.tree.insert("", tk.END, values=(1, "0x1234", "0x5678", "0x00", "Test USB Device"))
        
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
            header = struct.pack('!HIHHI', cmd, seq, dev_id, len(payload), 0)
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
        self.send_cmd(CMD_PING, seq=1)
        
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
        
        self.log(f"导入设备 ID: {dev_id}...")
        if not self.connected:
            self.log("请先连接！", "WARN")
            return
            
        self.log("导入功能开发中...")
        
    def print_hex_debug(self, data, label="数据"):
        """打印十六进制数据用于调试"""
        hex_str = ' '.join(f'{b:02x}' for b in data[:64])
        self.log(f"{label} ({len(data)} bytes): {hex_str}...", "DEBUG")
        
    def try_parse_header(self, data):
        """尝试解析协议头部，更健壮"""
        if len(data) < 16:
            return None
            
        try:
            self.print_hex_debug(data[:16], "尝试解析头部")
            cmd, seq, dev_id, length, _reserved = struct.unpack('!HIHHI', data[:16])
            self.log(f"解析到: cmd=0x{cmd:04x}, seq={seq}, dev_id={dev_id}, length={length}", "DEBUG")
            return {
                'cmd': cmd,
                'seq': seq,
                'dev_id': dev_id,
                'length': length
            }
        except Exception as e:
            # 不记录错误，只在真正需要时记录
            return None
        
    def handle_log(self, header, payload):
        """处理日志消息"""
        try:
            self.print_hex_debug(payload, "日志payload")
            # 简单点，直接尝试解码整个payload
            try:
                msg = payload.decode('utf-8', errors='ignore')
                if msg:
                    self.log(f"[SERVER] {msg}")
            except:
                pass
        except Exception as e:
            pass
        
    def handle_devlist_res(self, header, payload):
        """处理设备列表响应"""
        self.log(f"收到设备列表响应 (size: {header['length']})")
        try:
            self.print_hex_debug(payload, "设备列表payload")
            json_str = payload.decode('utf-8', errors='ignore')
            self.log(f"JSON字符串: {json_str}", "DEBUG")
            self.update_devices_from_json(json_str)
        except Exception as e:
            self.log(f"解析设备列表失败: {e}", "ERROR")
        
    def receive_loop(self):
        """接收消息循环"""
        buffer = b""
        
        while self.running and self.connected:
            try:
                # 读取数据
                data = self.sock.recv(8192)
                if not data:
                    self.log("连接已关闭")
                    break
                
                self.print_hex_debug(data, "收到原始数据")
                buffer += data
                
                # 简单处理：先清空旧的，尝试以文本方式处理看看是否有可读内容
                try:
                    text = buffer.decode('utf-8', errors='ignore')
                    if text and len(text.strip()) > 0:
                        self.log(f"收到文本数据: {text[:100]}...", "DEBUG")
                except:
                    pass
                
                # 尝试找JSON数据
                try:
                    idx = buffer.find(b'[{')
                    if idx >= 0:
                        json_end = buffer.find(b'}]', idx)
                        if json_end >= 0:
                            json_data = buffer[idx:json_end+2]
                            self.log(f"尝试解析JSON: {json_data}", "DEBUG")
                            json_str = json_data.decode('utf-8', errors='ignore')
                            self.update_devices_from_json(json_str)
                            buffer = buffer[json_end+2:]
                            continue
                except:
                    pass
                
                # 简单清空buffer避免累积
                buffer = b""
                
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
