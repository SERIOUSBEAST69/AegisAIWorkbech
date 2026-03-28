package com.trustai.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.trustai.config.RabbitConfig;
import com.trustai.dto.AiCallBriefDto;
import com.trustai.dto.DataAssetDetailDto;
import com.trustai.dto.DataAssetDto;
import com.trustai.document.AssetDocument;
import com.trustai.entity.AiCallLog;
import com.trustai.entity.DataAsset;
import com.trustai.exception.BizException;
import com.trustai.mapper.AiCallLogMapper;
import com.trustai.mapper.DataAssetMapper;
import com.trustai.repository.AssetEsRepository;
import com.trustai.service.DataAssetService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;

import java.util.Date;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
public class DataAssetServiceImpl extends ServiceImpl<DataAssetMapper, DataAsset> implements DataAssetService {

	private static final ObjectMapper MAPPER = new ObjectMapper();

	private final AiCallLogMapper aiCallLogMapper;
	private final ObjectProvider<AssetEsRepository> assetEsRepositoryProvider;
	private final RabbitTemplate rabbitTemplate;

	public DataAssetServiceImpl(AiCallLogMapper aiCallLogMapper,
							 ObjectProvider<AssetEsRepository> assetEsRepositoryProvider,
							 RabbitTemplate rabbitTemplate) {
		this.aiCallLogMapper = aiCallLogMapper;
		this.assetEsRepositoryProvider = assetEsRepositoryProvider;
		this.rabbitTemplate = rabbitTemplate;
	}

	@Override
	public DataAsset register(DataAsset entity) {
		Date now = new Date();
		entity.setCreateTime(now);
		this.save(entity);
		saveEs(entity);

		// 异步触发扫描：通过 RabbitMQ 发布资产注册事件
		// AssetRegisterConsumer 负责创建扫描任务、执行扫描并生成风险事件
		try {
			rabbitTemplate.convertAndSend(RabbitConfig.ASSET_REGISTER_QUEUE, MAPPER.writeValueAsString(entity));
		} catch (Exception e) {
			log.warn("Failed to publish asset register event for asset {}, scan will be skipped", entity.getId(), e);
		}

		return entity;
	}

	@Override
	public Page<DataAssetDto> page(Integer current, Integer size, String keyword) {
		Page<DataAsset> entityPage = new Page<>(current, size);
		LambdaQueryWrapper<DataAsset> qw = new LambdaQueryWrapper<>();
		if (keyword != null && !keyword.isEmpty()) {
			qw.like(DataAsset::getName, keyword).or().like(DataAsset::getDescription, keyword);
		}
		this.page(entityPage, qw.orderByDesc(DataAsset::getCreateTime));
		Page<DataAssetDto> dtoPage = new Page<>(entityPage.getCurrent(), entityPage.getSize(), entityPage.getTotal());
		dtoPage.setRecords(entityPage.getRecords().stream().map(item -> {
			DataAssetDto dto = new DataAssetDto();
			BeanUtils.copyProperties(item, dto);
			return dto;
		}).collect(Collectors.toList()));
		return dtoPage;
	}

	@Override
	public DataAssetDetailDto detailWithCalls(Long id) {
		DataAsset asset = this.getById(id);
		if (asset == null) throw new BizException(40400, "数据资产不存在");
		List<AiCallBriefDto> calls = aiCallLogMapper.selectList(new LambdaQueryWrapper<AiCallLog>()
						.eq(AiCallLog::getDataAssetId, id)
						.orderByDesc(AiCallLog::getCreateTime)
						.last("limit 50"))
				.stream()
				.map(log -> {
					AiCallBriefDto dto = new AiCallBriefDto();
					dto.setId(log.getId());
					dto.setModelCode(log.getModelCode());
					dto.setCreateTime(log.getCreateTime());
					dto.setDurationMs(log.getDurationMs());
					return dto;
				})
				.collect(Collectors.toList());
		DataAssetDetailDto dto = new DataAssetDetailDto();
		BeanUtils.copyProperties(asset, dto);
		dto.setCalls(calls);
		return dto;
	}

	private void saveEs(DataAsset entity) {
		AssetEsRepository assetEsRepository = assetEsRepositoryProvider.getIfAvailable();
		if (assetEsRepository == null) {
			return;
		}
		try {
			AssetDocument doc = new AssetDocument();
			doc.setId(String.valueOf(entity.getId()));
			doc.setAssetId(entity.getId());
			doc.setName(entity.getName());
			doc.setType(entity.getType());
			doc.setSensitivityLevel(entity.getSensitivityLevel());
			doc.setLocation(entity.getLocation());
			doc.setDescription(entity.getDescription());
			doc.setCreateTime(entity.getCreateTime());
			assetEsRepository.save(doc);
		} catch (Exception ignored) { }
	}
}
