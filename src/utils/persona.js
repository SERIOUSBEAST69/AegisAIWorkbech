const ALL = 'all';

const ROLE_TO_PERSONA = {
  ADMIN: 'governanceAdmin',
  EXECUTIVE: 'executive',
  SECOPS: 'secops',
  DATA_ADMIN: 'dataAdmin',
  AI_BUILDER: 'aiBuilder',
  BUSINESS_OWNER: 'businessOwner',
  EMPLOYEE: 'employee',
};

const PERSONAS = {
  executive: {
    id: 'executive',
    label: '管理层',
    kicker: 'EXECUTIVE COMMAND LENS',
    signature: '30 秒完成经营、风险与合规判断的董事会级驾驶舱。',
    introSubtitle: 'Board-Level Trusted AI Command Center',
    headline: 'Aegis Workbench 董事会级可信AI决策舱',
    subheadline: '把风险、成本、履约与业务势能压缩进一屏，让管理层先看到结论，再决定资源和节奏。',
    sceneTags: ['经营态势', 'AI 投入产出', '高危闭环率', '监管准备度'],
    benefits: [
      { title: '30 秒看清全局', metric: '全局态势', description: '你先看到高危风险、成本走向、未闭环事项和治理成熟度。' },
      { title: '批示有证据链', metric: '审计可追溯', description: '每个判断都能回到日志、告警、资产和审批原因，不是拍脑袋。' },
      { title: '跨部门一屏协同', metric: '风险优先级', description: '安全、数据、业务和研发的关键压力会被统一翻译成管理语言。' },
    ],
    journey: [
      { step: '01', title: '先看高危与成本', description: '确认哪些风险必须今天拍板，以及 AI 调用是否已经逼近预算或舆情边界。' },
      { step: '02', title: '看闭环效率', description: '检查告警、主体权利、共享审批是否卡在关键节点，避免治理只监测不处置。' },
      { step: '03', title: '下达资源动作', description: '根据工作台信号决定是加审计、加算力、加治理，还是冻结高风险模型。' },
    ],
    quickActions: [
      { title: '查看风险事件', description: '优先压降高危事件', route: '/risk-event-manage' },
      { title: '查看审批流', description: '识别卡点与责任链', route: '/approval-manage' },
    ],
    roleHints: ['exec', 'ceo', 'cxo', 'director', 'management', 'leader', 'principal', 'president', '校长', '院长', '管理层', '总经理'],
  },
  secops: {
    id: 'secops',
    label: '安全运维',
    kicker: 'SECURITY OPERATIONS LENS',
    signature: '把异常模型调用、告警闭环和证据链完整性放在你面前。',
    introSubtitle: 'Security Operations War Room',
    headline: 'Aegis Workbench 安全运维战情台',
    subheadline: '你先看到谁在异常访问、哪些模型在越界调用、哪条证据链还不完整，而不是泛泛的运营指标。',
    sceneTags: ['告警闭环', '异常调用', '风险证据链', '应急优先级'],
    benefits: [
      { title: '异常优先级自动前置', metric: '高危优先', description: '高危风险、待签收告警和异常模型调用会优先浮到你面前。' },
      { title: '证据链可直接导出', metric: '审计可复盘', description: '从日志、IP、设备、资产到审批原因可以快速串起来。' },
      { title: '响应路径更短', metric: '少点几层', description: '首页直接给你最需要处理的风险、告警和扫描入口。' },
    ],
    journey: [
      { step: '01', title: '签收高危告警', description: '先把真正可能造成事故或合规暴露的告警签收并定级。' },
      { step: '02', title: '回到证据链', description: '用审计日志、风险事件和敏感扫描结果还原发生了什么。' },
      { step: '03', title: '联动策略处置', description: '必要时冻结模型、调整权限、更新策略或发起审批。' },
    ],
    quickActions: [
      { title: '实时威胁监控', description: '检测代理窃取行为', route: '/threat-monitor' },
      { title: '审计日志', description: '追踪证据链', route: '/audit-log' },
      { title: '敏感扫描', description: '复核高敏数据暴露面', route: '/sensitive-scan' },
    ],
    roleHints: ['security', 'sec', 'soc', 'audit', 'risk', 'ops', '运维', '安全', '审计'],
  },
  dataAdmin: {
    id: 'dataAdmin',
    label: '数据管理员',
    kicker: 'DATA GOVERNANCE LENS',
    signature: '围绕资产纳管、共享审批、主体权利和脱敏策略展开工作流。',
    introSubtitle: 'Data Governance Control Deck',
    headline: 'Aegis Workbench 数据资产治理中枢',
    subheadline: '让你先看到高敏资产、共享流转、主体权利和脱敏策略命中情况，而不是被无关模块打断。',
    sceneTags: ['资产纳管', '共享审批', '主体权利', '脱敏策略'],
    benefits: [
      { title: '资产状态一屏掌握', metric: '高敏资产', description: '你可以快速定位哪些数据资产已纳管、谁在访问、共享到哪里。' },
      { title: '共享与履约同步', metric: '工单联动', description: '主体权利、共享审批与策略处置被拉进同一条业务链。' },
      { title: '脱敏预览更直观', metric: '规则命中', description: '在真正放行前先看到脱敏策略是否足够安全、是否影响可用性。' },
    ],
    journey: [
      { step: '01', title: '看高敏资产热区', description: '先判断高敏资产分布和最近共享动向，确认压力点在哪里。' },
      { step: '02', title: '处理共享与主体请求', description: '优先推进共享审批和主体权利工单，降低合规积压。' },
      { step: '03', title: '校准脱敏策略', description: '基于预览结果微调规则，保证安全与业务可用性平衡。' },
    ],
    quickActions: [
      { title: '数据资产', description: '进入资产清单与画像', route: '/data-asset' },
      { title: '脱敏预览', description: '校验脱敏效果', route: '/desense-preview' },
    ],
    roleHints: ['data', 'asset', 'steward', 'dba', 'privacy', '数据', '资产', '数据管理员'],
  },
  aiBuilder: {
    id: 'aiBuilder',
    label: 'AI 应用开发者',
    kicker: 'AI BUILDER LENS',
    signature: '把模型准入、调用成本、配额、风险门禁和可交付性摆到研发面前。',
    introSubtitle: 'AI Delivery Studio',
    headline: 'Aegis Workbench AI 应用交付工作室',
    subheadline: '你先看到模型是否可用、成本是否失控、风险门禁是否拦截，而不是被纯管理页面包围。',
    sceneTags: ['模型准入', '调用成本', '配额健康度', '交付风险'],
    benefits: [
      { title: '模型状态一眼可知', metric: '启用 / 风险', description: '哪些模型能用、哪些被限流、哪些因为风险级别被门禁拦住都能立刻看见。' },
      { title: '成本和额度透明', metric: '调用压力', description: '研发能更早发现成本飙升和额度问题，而不是上线后才补锅。' },
      { title: '合规接入可交付', metric: '上线更稳', description: '从模型准入到审计留痕，工作台帮助你把上线链条收紧。' },
    ],
    journey: [
      { step: '01', title: '核验模型可用性', description: '先确认模型状态、风险级别、配额和成本，避免接入后被临时拦截。' },
      { step: '02', title: '检查调用趋势', description: '看近期调用波动、失败率和预算曲线，决定是否需要扩容或换模型。' },
      { step: '03', title: '联动合规策略', description: '必要时回到脱敏、审计和审批模块完成交付闭环。' },
    ],
    quickActions: [
      { title: '影子AI发现', description: '检查未授权 AI 服务', route: '/shadow-ai' },
      { title: 'AI风险评级', description: '查看服务风险分级', route: '/ai/risk-rating' },
      { title: '员工AI行为监控', description: '关注高风险行为事件', route: '/ai/anomaly' },
    ],
    roleHints: ['developer', 'dev', 'engineer', 'model', 'ml', 'ai', '研发', '开发'],
  },
  businessOwner: {
    id: 'businessOwner',
    label: '业务负责人',
    kicker: 'BUSINESS VALUE LENS',
    signature: '围绕业务上线、数据使用边界、共享效率与交付风险构建的一线经营视角。',
    introSubtitle: 'Business Delivery Control Room',
    headline: 'Aegis Workbench 业务交付与治理协同台',
    subheadline: '你先看到哪些数据和模型能支撑业务上线，哪些审批与风险正在拖慢交付，而不是被底层运维细节淹没。',
    sceneTags: ['上线可行性', '数据共享效率', '风险阻塞点', '业务价值兑现'],
    benefits: [
      { title: '上线阻塞点可见', metric: '交付风险', description: '审批、共享、风险门禁与数据可用性会被汇总成可执行的业务语言。' },
      { title: '共享效率更清楚', metric: '跨部门流转', description: '你能快速判断是流程在卡、权限在卡，还是数据质量在卡。' },
      { title: '治理不再抽象', metric: '价值映射', description: '每个治理动作都能映射到业务收益、交付节奏和客户影响。' },
    ],
    journey: [
      { step: '01', title: '确认可上线能力', description: '先看资产、模型和共享链路是否满足当前业务投产需求。' },
      { step: '02', title: '识别阻塞节点', description: '定位审批、风险或主体请求是否正在拖慢交付与对外响应。' },
      { step: '03', title: '推动跨部门协同', description: '把问题拆给数据、安全、研发与治理负责人，加速业务闭环。' },
    ],
    quickActions: [
      { title: '审批管理', description: '跟进关键卡点审批', route: '/approval-manage' },
      { title: '数据资产', description: '查看可用资产与上传新数据', route: '/data-asset' },
    ],
    roleHints: ['business', 'owner', 'product', '运营', '业务', '业务负责人', '产品'],
  },
  employee: {
    id: 'employee',
    label: '普通员工',
    kicker: 'EMPLOYEE SAFE WORKSPACE',
    signature: '仅展示与本人设备和行为相关的检测信息，减少无关治理噪声。',
    introSubtitle: 'Employee Safety Workspace',
    headline: 'Aegis Workbench 员工安全工作区',
    subheadline: '你将看到和本人相关的设备检测与行为异常结果，帮助快速确认自身风险状态。',
    sceneTags: ['本机检测', '行为异常', '个人告警'],
    benefits: [
      { title: '只看本人数据', metric: '最小权限', description: '界面仅保留与你本人设备和行为相关的信息。' },
      { title: '检测始终开启', metric: '持续防护', description: '关键安全检测强制开启，避免误关导致防护缺口。' },
      { title: '快速自查', metric: '即时反馈', description: '可随时检查本机风险和异常行为记录。' },
    ],
    journey: [
      { step: '01', title: '查看本机状态', description: '进入影子AI发现，检查当前设备是否存在未授权AI服务。' },
      { step: '02', title: '查看行为记录', description: '在异常检测页查看本人近期行为是否触发风险。' },
      { step: '03', title: '按提示整改', description: '根据风险提示调整使用方式并联系管理员协同处理。' },
    ],
    quickActions: [
      { title: '影子AI发现', description: '检查本机未授权AI服务', route: '/shadow-ai' },
      { title: '员工AI行为监控', description: '查看本人行为与隐私告警', route: '/ai/anomaly' },
      { title: '个人资料', description: '维护个人信息', route: '/profile' },
    ],
    roleHints: ['employee', 'staff', '普通员工', '员工', '一线'],
  },
  governanceAdmin: {
    id: 'governanceAdmin',
    label: '治理管理员',
    kicker: 'TRUST GOVERNANCE MASTER VIEW',
    signature: '这是全功能治理总控台，适合平台管理员与合规负责人。',
    introSubtitle: 'Master Governance Command Deck',
    headline: 'Aegis Workbench 全域治理总控台',
    subheadline: '把资产、模型、风险、审批、审计和权限全部拉到一张作战图里，适合平台级治理者。',
    sceneTags: ['全域态势', '权限治理', '风险闭环', '模型与数据协同'],
    benefits: [
      { title: '全模块直达', metric: '全域控制', description: '你看到的是最完整的工作台，可以直接进入每条治理链路。' },
      { title: '跨角色协同', metric: '统一指挥', description: '同一块工作台可以同时给管理层、安全、数据和研发提供统一事实。' },
      { title: '设计与效率兼得', metric: '沉浸体验', description: '既适合真正日常使用，也足够在客户、学校或董事会面前演示。' },
    ],
    journey: [
      { step: '01', title: '看总控态势', description: '先掌握资产、风险、模型和履约的总体压力，再决定今天的治理主线。' },
      { step: '02', title: '按角色分派', description: '把风险、审批、共享和权限问题分发到安全、数据、研发与业务负责人。' },
      { step: '03', title: '验证闭环效果', description: '回到工作台看告警压降、成本收敛和主体权利履约是否真正改善。' },
    ],
    quickActions: [
      { title: '用户管理', description: '调整组织与角色', route: '/user-manage' },
      { title: '策略管理', description: '更新平台策略与门禁', route: '/policy-manage' },
      { title: '风险事件', description: '总览全域风险闭环', route: '/risk-event-manage' },
    ],
    roleHints: ['admin', 'governance', 'compliance', '管理员', '合规'],
  },
};

const MENU_SECTIONS = [
  {
    key: 'command',
    title: '指挥工作台',
    items: [
      { path: '/', label: '首页', icon: 'HomeFilled', audiences: ['governanceAdmin', 'secops', 'dataAdmin', 'executive', 'businessOwner', 'aiBuilder', 'employee'] },
      { path: '/operations-command', label: '运营指挥台', icon: 'Grid', audiences: ['governanceAdmin', 'executive'] },
    ],
  },
  {
    key: 'governance',
    title: '数据与模型',
    items: [
      { path: '/data-asset', label: '数据资产', icon: 'DataAnalysis', audiences: ['governanceAdmin', 'dataAdmin'] },
      { path: '/desense-preview', label: '脱敏预览', icon: 'Lock', audiences: ['governanceAdmin', 'secops', 'dataAdmin', 'aiBuilder'] },
    ],
  },
  {
    key: 'security',
    title: '安全与闭环',
    items: [
      { path: '/shadow-ai', label: '影子AI发现', icon: 'View', audiences: ['governanceAdmin', 'secops', 'aiBuilder'] },
      { path: '/threat-monitor', label: '实时威胁监控', icon: 'AlarmClock', audiences: ['governanceAdmin', 'secops'] },
      { path: '/ai/risk-rating', label: 'AI风险评级', icon: 'Histogram', audiences: ['governanceAdmin', 'secops', 'aiBuilder'] },
      { path: '/ai/anomaly', label: '员工AI行为监控', icon: 'AlarmClock', audiences: ['governanceAdmin', 'secops', 'executive', 'dataAdmin', 'aiBuilder', 'businessOwner', 'employee'] },
      { path: '/audit-log', label: '审计日志', icon: 'Timer', audiences: ['governanceAdmin', 'secops'] },
      { path: '/audit-report', label: '审计报告', icon: 'Document', audiences: ['governanceAdmin', 'secops', 'executive'] },
      { path: '/sensitive-scan', label: '敏感扫描', icon: 'Search', audiences: ['governanceAdmin', 'secops', 'dataAdmin'] },
    ],
  },
  {
    key: 'process',
    title: '流转与履约',
    items: [
      { path: '/approval-manage', label: '审批管理', icon: 'Finished', audiences: ['governanceAdmin', 'dataAdmin', 'businessOwner', 'employee'] },
      { path: '/risk-event-manage', label: '风险事件', icon: 'Warning', audiences: ['governanceAdmin', 'secops'] },
      { path: '/subject-request', label: '主体权利', icon: 'UserFilled', audiences: ['governanceAdmin'] },
      { path: '/policy-manage', label: '策略管理', icon: 'Document', audiences: ['governanceAdmin', 'secops', 'dataAdmin', 'aiBuilder'] },
    ],
  },
  {
    key: 'system',
    title: '平台控制',
    items: [
      { path: '/user-manage', label: '用户管理', icon: 'UserFilled', audiences: ['governanceAdmin'] },
      { path: '/role-manage', label: '角色管理', icon: 'Avatar', audiences: ['governanceAdmin'] },
      { path: '/permission-manage', label: '权限管理', icon: 'Key', audiences: ['governanceAdmin'] },
    ],
  },
];

const EXTRA_ROUTE_AUDIENCES = {
  '/profile': [ALL],
  '/settings': [ALL],
  '/operations-command': ['governanceAdmin', 'executive'],
  '/approval-manage': ['governanceAdmin', 'dataAdmin', 'businessOwner', 'employee'],
  '/risk-event-manage': ['governanceAdmin', 'secops'],
  '/ai/risk-rating': ['governanceAdmin', 'secops', 'aiBuilder'],
  '/ai/anomaly': ['governanceAdmin', 'secops', 'executive', 'dataAdmin', 'aiBuilder', 'businessOwner', 'employee'],
  '/shadow-ai': ['governanceAdmin', 'secops', 'aiBuilder'],
  '/threat-monitor': ['governanceAdmin', 'secops'],
};

const EMPLOYEE_ALLOWED_PATHS = new Set(['/ai/anomaly', '/approval-manage', '/profile', '/settings', '/login', '/']);

function normalizeText(user) {
  return [user?.roleCode, user?.roleName, user?.department, user?.username]
    .filter(Boolean)
    .join(' ')
    .toLowerCase();
}

export function inferPersona(user) {
  const explicitRole = String(user?.roleCode || '').trim().toUpperCase();
  if (explicitRole && ROLE_TO_PERSONA[explicitRole]) {
    return ROLE_TO_PERSONA[explicitRole];
  }

  const haystack = normalizeText(user);
  if (!haystack) {
    return 'employee';
  }

  const orderedIds = ['businessOwner', 'executive', 'secops', 'dataAdmin', 'aiBuilder', 'employee', 'governanceAdmin'];
  const matched = orderedIds.find(id => PERSONAS[id].roleHints.some(hint => haystack.includes(hint.toLowerCase())));
  return matched || 'employee';
}

export function getPersonaExperience(user) {
  return PERSONAS[inferPersona(user)];
}

function allows(audiences, personaId) {
  return audiences.includes(ALL) || audiences.includes(personaId);
}

export function getVisibleMenuSections(user) {
  const personaId = inferPersona(user);
  return MENU_SECTIONS
    .map(section => ({
      ...section,
      items: section.items.filter(item => allows(item.audiences, personaId)),
    }))
    .filter(section => section.items.length > 0);
}

export function canAccessPath(path, user) {
  if (!path || path === '/login') {
    return true;
  }
  const personaId = inferPersona(user);
  const menuItem = MENU_SECTIONS.flatMap(section => section.items).find(item => item.path === path);
  if (menuItem) {
    return allows(menuItem.audiences, personaId);
  }
  const explicit = EXTRA_ROUTE_AUDIENCES[path];
  if (explicit) {
    return allows(explicit, personaId);
  }
  if (personaId === 'employee') {
    return EMPLOYEE_ALLOWED_PATHS.has(path);
  }
  return true;
}

export function personalizeWorkbench(overview, user) {
  const persona = getPersonaExperience(user);
  const displayName = user?.nickname || user?.realName || user?.username || overview?.operator?.displayName;
  const roleName = user?.roleName || user?.roleCode || persona.label;
  const department = user?.department || overview?.operator?.department;
  const quickRoutes = new Set(persona.quickActions.map(item => item.route));
  const orderedTodos = Array.isArray(overview?.todos)
    ? [...overview.todos].sort((left, right) => Number(quickRoutes.has(right.route)) - Number(quickRoutes.has(left.route)))
    : [];

  return {
    ...overview,
    headline: persona.headline,
    subheadline: persona.subheadline,
    sceneTags: persona.sceneTags,
    operator: {
      ...overview?.operator,
      displayName: displayName || persona.label,
      roleName,
      department,
      avatar: user?.avatar || overview?.operator?.avatar || '',
    },
    todos: orderedTodos,
  };
}