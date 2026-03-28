package com.trustai.config;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 演示账号目录（company_id=1）。
 *
 * <p>说明：
 * - 管理员账号固定为 admin/admin。
 * - 其他演示账号统一密码为 demo1234。
 * - 普通员工 10 个账号用于演示用户侧权限隔离与批量场景。
 */
public final class DemoAccountCatalog {

    public static final Long DEMO_COMPANY_ID = 1L;
    public static final String ADMIN_PASSWORD = "admin";
    public static final String DEFAULT_DEMO_PASSWORD = "demo1234";

    private static final Map<String, String> ROLE_LABELS = new LinkedHashMap<>();
    private static final List<DemoAccountSeed> DEMO_ACCOUNT_SEEDS;
    private static final List<String> EMPLOYEE_USERNAMES;

    static {
        ROLE_LABELS.put("ADMIN", "治理管理员");
        ROLE_LABELS.put("EXECUTIVE", "管理层");
        ROLE_LABELS.put("SECOPS", "安全运维");
        ROLE_LABELS.put("DATA_ADMIN", "数据管理员");
        ROLE_LABELS.put("AI_BUILDER", "AI应用开发者");
        ROLE_LABELS.put("BUSINESS_OWNER", "业务负责人");
        ROLE_LABELS.put("EMPLOYEE", "普通员工");

        DEMO_ACCOUNT_SEEDS = List.of(
            new DemoAccountSeed("admin", ADMIN_PASSWORD, "治理管理员", "ADMIN", "enterprise", "治理中心", "13800138000", "admin@aegisai.com", "wx_admin"),

            new DemoAccountSeed("exec.demo", DEFAULT_DEMO_PASSWORD, "经营负责人", "EXECUTIVE", "enterprise", "经营管理部", "13800138001", "exec@aegisai.com", "wx_exec_demo"),
            new DemoAccountSeed("exec2.demo", DEFAULT_DEMO_PASSWORD, "经营副总A", "EXECUTIVE", "enterprise", "经营管理部", "13800138011", "exec2@aegisai.com", "wx_exec2_demo"),
            new DemoAccountSeed("exec3.demo", DEFAULT_DEMO_PASSWORD, "经营副总B", "EXECUTIVE", "enterprise", "经营管理部", "13800138012", "exec3@aegisai.com", "wx_exec3_demo"),

            new DemoAccountSeed("secops.demo", DEFAULT_DEMO_PASSWORD, "安全运维负责人", "SECOPS", "enterprise", "安全运营中心", "13800138002", "secops@aegisai.com", "wx_secops_demo"),
            new DemoAccountSeed("secops2.demo", DEFAULT_DEMO_PASSWORD, "安全运维工程师A", "SECOPS", "enterprise", "安全运营中心", "13800138013", "secops2@aegisai.com", "wx_secops2_demo"),
            new DemoAccountSeed("secops3.demo", DEFAULT_DEMO_PASSWORD, "安全运维工程师B", "SECOPS", "enterprise", "安全运营中心", "13800138014", "secops3@aegisai.com", "wx_secops3_demo"),

            new DemoAccountSeed("data.demo", DEFAULT_DEMO_PASSWORD, "数据管理员", "DATA_ADMIN", "enterprise", "数据治理部", "13800138003", "data@aegisai.com", "wx_data_demo"),
            new DemoAccountSeed("data2.demo", DEFAULT_DEMO_PASSWORD, "数据治理工程师A", "DATA_ADMIN", "enterprise", "数据治理部", "13800138015", "data2@aegisai.com", "wx_data2_demo"),
            new DemoAccountSeed("data3.demo", DEFAULT_DEMO_PASSWORD, "数据治理工程师B", "DATA_ADMIN", "enterprise", "数据治理部", "13800138016", "data3@aegisai.com", "wx_data3_demo"),

            new DemoAccountSeed("builder.demo", DEFAULT_DEMO_PASSWORD, "AI应用开发者", "AI_BUILDER", "ai-team", "模型平台组", "13800138004", "builder@aegisai.com", "wx_builder_demo"),
            new DemoAccountSeed("builder2.demo", DEFAULT_DEMO_PASSWORD, "AI开发工程师A", "AI_BUILDER", "ai-team", "模型平台组", "13800138017", "builder2@aegisai.com", "wx_builder2_demo"),
            new DemoAccountSeed("builder3.demo", DEFAULT_DEMO_PASSWORD, "AI开发工程师B", "AI_BUILDER", "ai-team", "模型平台组", "13800138018", "builder3@aegisai.com", "wx_builder3_demo"),

            new DemoAccountSeed("biz.demo", DEFAULT_DEMO_PASSWORD, "业务负责人", "BUSINESS_OWNER", "enterprise", "业务创新部", "13800138006", "biz@aegisai.com", "wx_biz_demo"),
            new DemoAccountSeed("biz2.demo", DEFAULT_DEMO_PASSWORD, "业务线负责人A", "BUSINESS_OWNER", "enterprise", "业务创新部", "13800138019", "biz2@aegisai.com", "wx_biz2_demo"),
            new DemoAccountSeed("biz3.demo", DEFAULT_DEMO_PASSWORD, "业务线负责人B", "BUSINESS_OWNER", "enterprise", "业务创新部", "13800138020", "biz3@aegisai.com", "wx_biz3_demo"),

            new DemoAccountSeed("employee.demo", DEFAULT_DEMO_PASSWORD, "普通员工1", "EMPLOYEE", "enterprise", "业务一线一组", "13800138007", "employee@aegisai.com", "wx_employee_demo"),
            new DemoAccountSeed("employee2.demo", DEFAULT_DEMO_PASSWORD, "普通员工2", "EMPLOYEE", "enterprise", "业务一线一组", "13800138021", "employee2@aegisai.com", "wx_employee2_demo"),
            new DemoAccountSeed("employee3.demo", DEFAULT_DEMO_PASSWORD, "普通员工3", "EMPLOYEE", "enterprise", "业务一线一组", "13800138022", "employee3@aegisai.com", "wx_employee3_demo"),
            new DemoAccountSeed("employee4.demo", DEFAULT_DEMO_PASSWORD, "普通员工4", "EMPLOYEE", "enterprise", "业务一线二组", "13800138023", "employee4@aegisai.com", "wx_employee4_demo"),
            new DemoAccountSeed("employee5.demo", DEFAULT_DEMO_PASSWORD, "普通员工5", "EMPLOYEE", "enterprise", "业务一线二组", "13800138024", "employee5@aegisai.com", "wx_employee5_demo"),
            new DemoAccountSeed("employee6.demo", DEFAULT_DEMO_PASSWORD, "普通员工6", "EMPLOYEE", "enterprise", "业务一线二组", "13800138025", "employee6@aegisai.com", "wx_employee6_demo"),
            new DemoAccountSeed("employee7.demo", DEFAULT_DEMO_PASSWORD, "普通员工7", "EMPLOYEE", "enterprise", "业务一线三组", "13800138026", "employee7@aegisai.com", "wx_employee7_demo"),
            new DemoAccountSeed("employee8.demo", DEFAULT_DEMO_PASSWORD, "普通员工8", "EMPLOYEE", "enterprise", "业务一线三组", "13800138027", "employee8@aegisai.com", "wx_employee8_demo"),
            new DemoAccountSeed("employee9.demo", DEFAULT_DEMO_PASSWORD, "普通员工9", "EMPLOYEE", "enterprise", "业务一线三组", "13800138028", "employee9@aegisai.com", "wx_employee9_demo"),
            new DemoAccountSeed("employee10.demo", DEFAULT_DEMO_PASSWORD, "普通员工10", "EMPLOYEE", "enterprise", "业务一线四组", "13800138029", "employee10@aegisai.com", "wx_employee10_demo")
        );

        EMPLOYEE_USERNAMES = DEMO_ACCOUNT_SEEDS.stream()
            .filter(seed -> "EMPLOYEE".equals(seed.roleCode()))
            .map(DemoAccountSeed::username)
            .collect(Collectors.toList());
    }

    private DemoAccountCatalog() {
    }

    public static Map<String, String> roleLabels() {
        return ROLE_LABELS;
    }

    public static List<DemoAccountSeed> demoAccountSeeds() {
        return DEMO_ACCOUNT_SEEDS;
    }

    public static List<String> employeeUsernames() {
        return EMPLOYEE_USERNAMES;
    }

    public record DemoAccountSeed(
        String username,
        String password,
        String realName,
        String roleCode,
        String organizationType,
        String department,
        String phone,
        String email,
        String wechatOpenId
    ) {
    }
}
