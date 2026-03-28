/**
 * Aegis 客户端预加载脚本（contextBridge）
 *
 * 通过 contextBridge 向渲染进程暴露受限的 IPC API，
 * 确保渲染进程不能直接访问 Node.js 原生模块。
 */

'use strict';

const { contextBridge, ipcRenderer } = require('electron');

contextBridge.exposeInMainWorld('aegisClient', {
  /** 获取客户端基本信息（clientId、服务器地址、上次扫描结果等） */
  getClientInfo: () => ipcRenderer.invoke('get-client-info'),

  /** 手动触发一次扫描 */
  runScan: () => ipcRenderer.invoke('run-scan'),

  /** 获取当前配置 */
  getConfig: () => ipcRenderer.invoke('get-config'),

  /** 保存配置（serverUrl、scanIntervalMinutes 等） */
  saveConfig: (cfg) => ipcRenderer.invoke('save-config', cfg),

  /** 设置当前登录态（用于控制扫描闸门） */
  setAuthState: (state) => ipcRenderer.invoke('set-auth-state', state),

  /** 获取当前登录态 */
  getAuthState: () => ipcRenderer.invoke('get-auth-state'),

  /** 监听扫描完成事件 */
  onScanComplete: (callback) => {
    ipcRenderer.on('scan-complete', (event, result) => callback(result));
  },

  /** 移除扫描完成监听 */
  offScanComplete: (callback) => {
    ipcRenderer.removeListener('scan-complete', callback);
  },
});
