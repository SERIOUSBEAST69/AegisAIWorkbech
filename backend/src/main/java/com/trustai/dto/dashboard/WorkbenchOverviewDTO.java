package com.trustai.dto.dashboard;

import java.util.ArrayList;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class WorkbenchOverviewDTO {
    private Operator operator;
    private String headline;
    private String subheadline;
    private List<String> sceneTags = new ArrayList<>();
    private List<Metric> metrics = new ArrayList<>();
    private Trend trend = new Trend();
    private List<RiskBucket> riskDistribution = new ArrayList<>();
    private List<TodoItem> todos = new ArrayList<>();
    private List<ActivityFeed> feeds = new ArrayList<>();
    /** 数据来源标识：real_db = 真实数据库查询；mock = 降级/演示数据。前端据此显示数据来源标签。*/
    private String _dataSource = "real_db";

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Operator {
        private String displayName;
        private String roleName;
        private String department;
        private String avatar;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Metric {
        private String key;
        private String label;
        private Long value;
        private String suffix;
        private Integer delta;
        private String hint;
    }

    @Data
    @NoArgsConstructor
    public static class Trend {
        private List<String> labels = new ArrayList<>();
        private List<Long> riskSeries = new ArrayList<>();
        private List<Long> auditSeries = new ArrayList<>();
        private List<Long> aiCallSeries = new ArrayList<>();
        private List<Long> costSeries = new ArrayList<>();
        private Long forecastNextDay = 0L;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class RiskBucket {
        private String level;
        private Long value;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TodoItem {
        private String priority;
        private String title;
        private String description;
        private String route;
        private String metric;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ActivityFeed {
        private String level;
        private String title;
        private String description;
        private String route;
        private String timeLabel;
    }
}