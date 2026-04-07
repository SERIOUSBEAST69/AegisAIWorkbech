package com.trustai.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.trustai.entity.ApprovalRequest;
import com.trustai.exception.BizException;
import com.trustai.service.EventHubService;
import java.util.Map;
import org.flowable.engine.RuntimeService;
import org.flowable.engine.TaskService;
import org.flowable.task.api.Task;
import org.flowable.task.api.TaskQuery;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ApprovalRequestServiceImplTest {

    @Mock
    private RuntimeService runtimeService;

    @Mock
    private TaskService taskService;

    @Mock
    private EventHubService eventHubService;

    @Mock
    private TaskQuery taskQuery;

    @Spy
    @InjectMocks
    private ApprovalRequestServiceImpl approvalRequestService;

    @BeforeEach
    void setUp() {
        lenient().when(taskService.createTaskQuery()).thenReturn(taskQuery);
        lenient().when(taskQuery.processInstanceId(any())).thenReturn(taskQuery);
        lenient().doReturn(true).when(approvalRequestService).updateById(any(ApprovalRequest.class));
    }

    @Test
    void approveShouldSyncGovernanceEventWhenReasonContainsEventId() {
        ApprovalRequest req = new ApprovalRequest();
        req.setId(88L);
        req.setProcessInstanceId("pi-88");
        req.setTaskId("task-88");
        req.setReason("[DATA]AUTO_REMEDIATION eventId=12345 type=PRIVACY_ALERT");

        doReturn(req).when(approvalRequestService).getById(88L);
        when(taskQuery.singleResult()).thenReturn(null);

        ApprovalRequest updated = approvalRequestService.approve(88L, 7L, "通过");

        assertEquals("合规审批通过", updated.getStatus());
        assertNull(updated.getTaskId());
        verify(eventHubService).syncGovernanceStatus(
            eq("governance"),
            eq(12345L),
            eq("approved"),
            eq(7L),
            eq("approval_request:88")
        );
    }

    @Test
    void approveShouldNotSyncGovernanceEventWhenReasonHasNoEventId() {
        ApprovalRequest req = new ApprovalRequest();
        req.setId(99L);
        req.setProcessInstanceId("pi-99");
        req.setTaskId("task-99");
        req.setReason("manual approval without event id");

        doReturn(req).when(approvalRequestService).getById(99L);
        when(taskQuery.singleResult()).thenReturn(null);

        approvalRequestService.approve(99L, 9L, "approve");

        verify(eventHubService, never()).syncGovernanceStatus(any(), any(), any(), any(), any());
    }

    @Test
    void approveShouldRejectMissingTaskId() {
        ApprovalRequest req = new ApprovalRequest();
        req.setId(66L);
        req.setProcessInstanceId("pi-66");
        req.setTaskId(null);

        doReturn(req).when(approvalRequestService).getById(66L);

        BizException ex = assertThrows(BizException.class, () -> approvalRequestService.approve(66L, 1L, "approve"));
        assertEquals(40000, ex.getCode());
        verify(eventHubService, never()).syncGovernanceStatus(any(), any(), any(), any(), any());
    }
}
