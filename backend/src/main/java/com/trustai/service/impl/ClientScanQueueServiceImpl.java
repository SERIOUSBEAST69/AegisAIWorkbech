package com.trustai.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.trustai.entity.ClientScanQueue;
import com.trustai.mapper.ClientScanQueueMapper;
import com.trustai.service.ClientScanQueueService;
import org.springframework.stereotype.Service;

@Service
public class ClientScanQueueServiceImpl extends ServiceImpl<ClientScanQueueMapper, ClientScanQueue>
        implements ClientScanQueueService {
}
