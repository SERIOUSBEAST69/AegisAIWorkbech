package com.trustai.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 客户端扫描报告 – 记录轻量级客户端上报的影子AI发现结果。
 */
@Data
@TableName("client_report")
public class ClientReport {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 公司ID（租户隔离） */
    private Long companyId;

    /** 客户端唯一标识（客户端首次启动时生成并持久化的 UUID） */
    private String clientId;

    /** 主机名 */
    private String hostname;

    /** 操作系统用户名 */
    private String osUsername;

    /** 操作系统类型，如 Windows / macOS / Linux */
    private String osType;

    /** 客户端版本号 */
    private String clientVersion;

    /**
     * 发现的AI服务列表（JSON数组字符串）。
     * 每个元素结构：
     * { "name": "ChatGPT", "domain": "chat.openai.com", "category": "chat",
     *   "source": "browser_history|network|process", "riskLevel": "high|medium|low",
     *   "firstSeen": "ISO8601", "lastSeen": "ISO8601" }
     */
    private String discoveredServices;

    /** 本次扫描发现的影子AI服务数量 */
    private Integer shadowAiCount;

    /** 综合风险等级：none / low / medium / high */
    private String riskLevel;

    /** 本次扫描时间 */
    private LocalDateTime scanTime;

    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
