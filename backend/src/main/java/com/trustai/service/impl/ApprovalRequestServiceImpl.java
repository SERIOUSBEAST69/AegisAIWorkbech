package com.trustai.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.trustai.entity.ApprovalRequest;
import com.trustai.exception.BizException;
import com.trustai.mapper.ApprovalRequestMapper;
import com.trustai.service.ApprovalRequestService;
import lombok.RequiredArgsConstructor;
import org.flowable.engine.RuntimeService;
import org.flowable.engine.TaskService;
import org.flowable.task.api.Task;
import org.springframework.stereotype.Service;

import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ApprovalRequestServiceImpl extends ServiceImpl<ApprovalRequestMapper, ApprovalRequest> implements ApprovalRequestService {

	private final RuntimeService runtimeService;
	private final TaskService taskService;

	@Override
	public ApprovalRequest startApproval(ApprovalRequest req) {
		req.setStatus("待审批");
		req.setCreateTime(new Date());
		req.setUpdateTime(new Date());
		this.save(req);

		Map<String, Object> vars = new HashMap<>();
		vars.put("approvalId", req.getId());
		vars.put("applicant", req.getApplicantId());
		vars.put("status", "pending");

		var pi = runtimeService.startProcessInstanceByKey("approvalProcess", String.valueOf(req.getId()), vars);
		req.setProcessInstanceId(pi.getProcessInstanceId());

		Task task = taskService.createTaskQuery().processInstanceId(pi.getProcessInstanceId()).singleResult();
		if (task != null) {
			req.setTaskId(task.getId());
		}
		this.updateById(req);
		return req;
	}

	@Override
	public ApprovalRequest approve(Long requestId, Long approverId, String status) {
		ApprovalRequest ar = this.getById(requestId);
		if (ar == null) throw new BizException(40000, "申请不存在");
		if (ar.getTaskId() == null) throw new BizException(40000, "当前无待办任务");

		String flowStatus = mapStatus(status);
		Map<String, Object> vars = Map.of("status", flowStatus);
		taskService.claim(ar.getTaskId(), String.valueOf(approverId));
		taskService.complete(ar.getTaskId(), vars);

		Task next = taskService.createTaskQuery().processInstanceId(ar.getProcessInstanceId()).singleResult();
		if (next == null) {
			ar.setStatus(flowStatus.equals("reject") ? "驳回" : "合规审批通过");
			ar.setTaskId(null);
		} else {
			ar.setTaskId(next.getId());
			if (isDeptTask(next)) {
				ar.setStatus("部门审批");
			} else {
				ar.setStatus("合规审批");
			}
		}
		ar.setApproverId(approverId);
		ar.setUpdateTime(new Date());
		this.updateById(ar);
		return ar;
	}

	@Override
	public List<ApprovalRequest> todo(Long userId) {
		List<Task> tasks = taskService.createTaskQuery().taskCandidateOrAssigned(String.valueOf(userId)).list();
		Map<String, String> taskMap = tasks.stream().collect(Collectors.toMap(Task::getProcessInstanceId, Task::getId, (a, b) -> a));
		return taskMap.keySet().stream()
			.map(pi -> this.lambdaQuery().eq(ApprovalRequest::getProcessInstanceId, pi).one())
			.filter(ar -> ar != null)
			.peek(ar -> ar.setTaskId(taskMap.get(ar.getProcessInstanceId())))
			.toList();
	}

	private String mapStatus(String status) {
		if (status == null) return "reject";
		if (status.contains("拒") || status.contains("驳")) return "reject";
		if (status.equalsIgnoreCase("reject")) return "reject";
		return "approve";
	}

	private boolean isDeptTask(Task task) {
		return task != null && "deptApproval".equals(task.getTaskDefinitionKey());
	}
}
