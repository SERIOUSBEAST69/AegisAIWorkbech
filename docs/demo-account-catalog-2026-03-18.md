# Demo Account Catalog (2026-03-18)

## Scope
- Demo tenant: `company_id = 1`
- Admin password: `admin`
- All non-admin demo accounts password: `demo1234`

## Core Accounts
- `admin` (治理管理员)
- `exec.demo` (管理层)
- `secops.demo` (安全运维)
- `data.demo` (数据管理员)
- `builder.demo` (AI开发者)
- `biz.demo` (业务负责人)
- `employee.demo` (普通员工)

## Expanded Role Accounts
- 管理层: `exec2.demo`, `exec3.demo`
- 安全运维: `secops2.demo`, `secops3.demo`
- 数据管理员: `data2.demo`, `data3.demo`
- AI开发者: `builder2.demo`, `builder3.demo`
- 业务负责人: `biz2.demo`, `biz3.demo`

## Employee Accounts (10)
- `employee.demo`
- `employee2.demo`
- `employee3.demo`
- `employee4.demo`
- `employee5.demo`
- `employee6.demo`
- `employee7.demo`
- `employee8.demo`
- `employee9.demo`
- `employee10.demo`

## Seeded Dataset Coverage
The demo initializer now tops up data to at least 100 records for company 1 across:
- `privacy_event` (30)
- `risk_event` as anomaly events (30)
- `adversarial_record` as battle reports (10)
- `client_report` as shadow AI discoveries (10)
- `approval_request` (10)
- `audit_log` (10)
- plus supplemental `security_event` and `data_asset` demo records

## Re-run / Multi-tenant Notes
- Data seeders are idempotent and use demo markers to avoid duplicate inserts.
- For real company onboarding, create a new `company_id` first, then reuse the service-layer data assembly pattern from:
  - `backend/src/main/java/com/trustai/config/DemoDatasetInitializer.java`
- Keep tenant isolation by always writing `company_id` explicitly in import flows.
