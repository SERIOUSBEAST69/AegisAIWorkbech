package com.trustai.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;
import com.trustai.dto.DataAssetDetailDto;
import com.trustai.dto.DataAssetDto;
import com.trustai.entity.DataAsset;

public interface DataAssetService extends IService<DataAsset> {
	Page<DataAssetDto> page(Integer current, Integer size, String keyword);

	DataAssetDetailDto detailWithCalls(Long id);

	DataAsset register(DataAsset entity);
}
