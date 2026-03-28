package com.trustai.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.trustai.document.ModelDocument;
import com.trustai.entity.AiModel;
import com.trustai.mapper.AiModelMapper;
import com.trustai.repository.ModelEsRepository;
import com.trustai.service.AiModelService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AiModelServiceImpl extends ServiceImpl<AiModelMapper, AiModel> implements AiModelService {

	private final ObjectProvider<ModelEsRepository> modelEsRepositoryProvider;

	@Override
	public boolean save(AiModel entity) {
		boolean db = super.save(entity);
		saveEs(entity);
		return db;
	}

	@Override
	public boolean updateById(AiModel entity) {
		boolean db = super.updateById(entity);
		saveEs(entity);
		return db;
	}

	private void saveEs(AiModel entity) {
		ModelEsRepository modelEsRepository = modelEsRepositoryProvider.getIfAvailable();
		if (modelEsRepository == null) {
			return;
		}
		try {
			ModelDocument doc = new ModelDocument();
			doc.setId(String.valueOf(entity.getId()));
			doc.setModelId(entity.getId());
			doc.setModelName(entity.getModelName());
			doc.setModelCode(entity.getModelCode());
			doc.setProvider(entity.getProvider());
			doc.setModelType(entity.getModelType());
			doc.setRiskLevel(entity.getRiskLevel());
			doc.setStatus(entity.getStatus());
			doc.setDescription(entity.getDescription());
			doc.setCreateTime(entity.getCreateTime());
			modelEsRepository.save(doc);
		} catch (Exception ignored) { }
	}
}
