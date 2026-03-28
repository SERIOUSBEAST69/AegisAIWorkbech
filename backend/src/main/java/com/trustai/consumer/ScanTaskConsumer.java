package com.trustai.consumer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.trustai.config.RabbitConfig;
import com.trustai.entity.SensitiveScanTask;
import com.trustai.service.SensitiveScanTaskService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class ScanTaskConsumer {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private final SensitiveScanTaskService sensitiveScanTaskService;

    @RabbitListener(queues = RabbitConfig.SCAN_TASK_QUEUE)
    public void onMessage(String payload) {
        try {
            SensitiveScanTask task = MAPPER.readValue(payload, SensitiveScanTask.class);
            sensitiveScanTaskService.save(task);
        } catch (Exception e) {
            log.error("Consume scan task failed", e);
        }
    }
}
