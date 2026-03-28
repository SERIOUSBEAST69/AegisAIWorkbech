package com.trustai.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.trustai.entity.ApprovalRequest;
import org.flowable.task.api.Task;

import java.util.List;

public interface ApprovalRequestService extends IService<ApprovalRequest> {

	ApprovalRequest startApproval(ApprovalRequest req);

	ApprovalRequest approve(Long requestId, Long approverId, String status);

	List<ApprovalRequest> todo(Long userId);
}
