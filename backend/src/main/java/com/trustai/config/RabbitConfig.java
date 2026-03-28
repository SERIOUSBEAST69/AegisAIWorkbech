package com.trustai.config;

import org.springframework.amqp.core.Queue;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RabbitConfig {

    public static final String AUDIT_LOG_QUEUE    = "audit.log.queue";
    public static final String SCAN_TASK_QUEUE    = "scan.task.queue";
    /** 资产注册事件队列：注册后异步触发扫描 + 风险事件生成 */
    public static final String ASSET_REGISTER_QUEUE = "asset.register.queue";

    @Bean
    public Queue auditLogQueue() {
        return new Queue(AUDIT_LOG_QUEUE, true);
    }

    @Bean
    public Queue scanTaskQueue() {
        return new Queue(SCAN_TASK_QUEUE, true);
    }

    @Bean
    public Queue assetRegisterQueue() {
        return new Queue(ASSET_REGISTER_QUEUE, true);
    }
}
