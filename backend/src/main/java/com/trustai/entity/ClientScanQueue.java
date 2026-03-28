package com.trustai.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 云端扫描队列 – 记录通过Web界面发起的客户端下载事件，
 * 并在本地扫描开启时将下载任务纳入云端合规扫描队列。
 */
@Data
@TableName("client_scan_queue")
public class ClientScanQueue {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 公司ID（租户隔离） */
    private Long companyId;

    /** 下载平台：windows / macos / linux */
    private String platform;

    /** 发起下载的主机名（由前端或请求头获取） */
    private String hostname;

    /** 操作系统用户名 */
    private String osUsername;

    /** 浏览器 User-Agent */
    private String userAgent;

    /**
     * 队列状态：
     * queued   – 已加入队列，等待客户端安装并上报
     * scanning – 客户端已安装并正在执行首次扫描
     * done     – 扫描完成，结果已上报
     * failed   – 超时或失败
     */
    private String status;

    /** 扫描结果（JSON，扫描完成后填充） */
    private String scanResult;

    /** 下载时间 */
    private LocalDateTime downloadTime;

    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
