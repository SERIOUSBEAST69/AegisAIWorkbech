package com.trustai.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.trustai.config.RabbitConfig;
import com.trustai.entity.SensitiveScanTask;
import com.trustai.mapper.SensitiveScanTaskMapper;
import com.trustai.service.SensitiveScanTaskService;
import lombok.RequiredArgsConstructor;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class SensitiveScanTaskServiceImpl extends ServiceImpl<SensitiveScanTaskMapper, SensitiveScanTask> implements SensitiveScanTaskService {

	private static final ObjectMapper MAPPER = new ObjectMapper();
	private final RabbitTemplate rabbitTemplate;

	@Override
	public boolean save(SensitiveScanTask entity) {
		boolean db = super.save(entity);
		sendAsync(entity);
		return db;
	}

	private void sendAsync(SensitiveScanTask task) {
		try {
			rabbitTemplate.convertAndSend(RabbitConfig.SCAN_TASK_QUEUE, MAPPER.writeValueAsString(task));
		} catch (Exception ignored) { }
	}
}
