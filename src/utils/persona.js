import { hasPermissionByUser } from './permission';

const ALL = 'all';

const ROLE_TO_PERSONA = {
  ADMIN: 'governanceAdmin',
  ADMIN_REVIEWER: 'governanceReviewer',
  ADMIN_OPS: 'governanceAdmin',
  SECOPS: 'secops',
  SECOPS_RESPONDER: 'secops',
  BUSINESS_OWNER: 'businessOwner',
  BUSINESS_OWNER_APPROVER: 'businessOwner',
  AUDIT: 'audit',
  SEC: 'secops',
};

const PERSONAS = {
  governanceAdmin: {
    id: 'governanceAdmin',
    label: '治理管理员',
    kicker: 'TRUST GOVERNANCE MASTER VIEW',
    signature: '全域治理总控台。',
    introSubtitle: 'Master Governance Command Deck',
    headline: 'Aegis Workbench 全域治理总控台',
    subheadline: '围绕 AI 全生命周期治理、风险管控与对抗防御的统一工作台。',
    sceneTags: ['全域态势', '权限治理', '风险闭环', '模型治理'],
    benefits: [
      { title: '全模块直达', metric: '全域控制', description: '统一查看并处置治理链路。' },
      { title: '跨角色协同', metric: '统一指挥', description: '审批、审计、风险在同一视图闭环。' },
    ],
    journey: [
      { step: '01', title: '查看总控态势', description: '确认今日治理重点。' },
      { step: '02', title: '分派治理动作', description: '推动审批与策略执行。' },
      { step: '03', title: '验证闭环结果', description: '核对风险压降与审计可追溯性。' },
    ],
    quickActions: [
      { title: '用户管理', description: '调整组织与角色', route: '/user-manage' },
      { title: '策略管理', description: '维护治理策略', route: '/policy-manage' },
    ],
    roleHints: ['admin', 'governance', '合规', '管理员'],
  },
  governanceReviewer: {
    id: 'governanceReviewer',
    label: '治理复核员',
    kicker: 'TRUST GOVERNANCE REVIEW VIEW',
    signature: '聚焦复核、审计与风险可视。',
    introSubtitle: 'Governance Review Deck',
    headline: 'Aegis Workbench 治理复核视图',
    subheadline: '用于复核治理申请、追踪审计证据与查看风险态势。',
    sceneTags: ['复核闭环', '审计追踪', '风险可视'],
    benefits: [
      { title: '复核链路集中', metric: '审批效率', description: '待复核事项可集中查看。' },
      { title: '证据链可回溯', metric: '审计一致性', description: '关键动作可追溯。' },
    ],
    journey: [
      { step: '01', title: '查看待复核', description: '确认治理申请与审批状态。' },
      { step: '02', title: '核验证据', description: '核验日志与审批意见。' },
      { step: '03', title: '输出结论', description: '形成复核结论并推动闭环。' },
    ],
    quickActions: [
      { title: '审计中心', description: '追踪证据链', route: '/audit-center' },
    ],
    roleHints: ['review', 'reviewer', '复核', '审计'],
  },
  secops: {
    id: 'secops',
    label: '安全运维',
    kicker: 'SECURITY OPERATIONS LENS',
    signature: '监控告警、处置风险、保障模型与数据安全。',
    introSubtitle: 'Security Operations War Room',
    headline: 'Aegis Workbench 安全运维战情台',
    subheadline: '围绕异常调用、告警闭环与证据追踪开展日常安全运维。',
    sceneTags: ['告警闭环', '异常调用', '证据追踪'],
    benefits: [
      { title: '高危优先', metric: '风险优先级', description: '高危事件优先呈现。' },
      { title: '证据可导出', metric: '审计复盘', description: '快速形成处置证据。' },
    ],
    journey: [
      { step: '01', title: '签收告警', description: '优先处理高危告警。' },
      { step: '02', title: '回溯证据', description: '关联日志与审计证据。' },
      { step: '03', title: '策略处置', description: '调整策略并完成闭环。' },
    ],
    quickActions: [
      { title: 'AI攻击实时防御', description: '查看攻击告警态势', route: '/threat-monitor' },
    ],
    roleHints: ['security', 'soc', 'secops', '运维', '安全'],
  },
  businessOwner: {
    id: 'businessOwner',
    label: '业务负责人',
    kicker: 'BUSINESS VALUE LENS',
    signature: '聚焦上线可行性、白名单与风险阻塞点。',
    introSubtitle: 'Business Delivery Control Room',
    headline: 'Aegis Workbench 业务交付协同台',
    subheadline: '支撑企业风控与模型治理流程中的发起、审批协同与结果跟踪。',
    sceneTags: ['上线可行性', '风险阻塞点', '审批协同'],
    benefits: [
      { title: '阻塞点可见', metric: '交付风险', description: '快速定位审批与风险卡点。' },
      { title: '治理动作可追踪', metric: '价值映射', description: '治理动作映射业务结果。' },
    ],
    journey: [
      { step: '01', title: '确认可上线能力', description: '检查模型与风控状态。' },
      { step: '02', title: '发起治理动作', description: '提交风险评级/白名单/模型发布变更。' },
      { step: '03', title: '跟踪审批与结果', description: '查看审批与风险处置闭环。' },
    ],
    quickActions: [
      { title: '影子AI发现与风险评级', description: '发起与查看风险评级', route: '/shadow-ai' },
    ],
    roleHints: ['business', 'owner', '业务', '产品'],
  },
  audit: {
    id: 'audit',
    label: '审计员',
    kicker: 'AUDIT EVIDENCE VIEW',
    signature: '聚焦审计可追溯与合规证据。',
    introSubtitle: 'Audit Evidence Deck',
    headline: 'Aegis Workbench 审计证据视图',
    subheadline: '面向审计员的证据链浏览与风险结果核验视图。',
    sceneTags: ['审计证据', '合规追溯', '风险核验'],
    benefits: [
      { title: '证据一体化', metric: '可追溯', description: '统一查看审批、日志、风险。' },
      { title: '审计口径稳定', metric: '一致性', description: '降低跨模块口径偏差。' },
    ],
    journey: [
      { step: '01', title: '查看审计中心', description: '抽样核验关键记录。' },
      { step: '02', title: '核验风险处置', description: '确认风险闭环结果。' },
      { step: '03', title: '形成审计结论', description: '输出审计意见。' },
    ],
    quickActions: [
      { title: '审计中心', description: '查看日志与报告', route: '/audit-center' },
    ],
    roleHints: ['audit', '审计'],
  },
};

const MENU_SECTIONS = [
  {
    key: 'command',
    title: '指挥工作台',
    items: [
      { path: '/', label: '首页', icon: 'HomeFilled', audiences: ['governanceAdmin', 'governanceReviewer', 'secops', 'businessOwner', 'audit'] },
      { path: '/operations-command', label: '安全指挥台', icon: 'TrendCharts', audiences: ['governanceAdmin', 'governanceReviewer', 'secops', 'businessOwner', 'audit'] },
      { path: '/ops-observability', label: '运维观测', icon: 'TrendCharts', audiences: ['governanceAdmin', 'secops', 'governanceReviewer'] },
    ],
  },
  {
    key: 'security',
    title: '安全与闭环',
    items: [
      { path: '/shadow-ai', label: '影子AI发现与风险评级', icon: 'View', audiences: ['governanceAdmin', 'governanceReviewer', 'secops', 'businessOwner', 'audit'] },
      { path: '/threat-monitor', label: 'AI攻击实时防御', icon: 'AlarmClock', audiences: ['governanceAdmin', 'secops'] },
      { path: '/privacy-monitor', label: 'AI使用合规监控', icon: 'Warning', audiences: ['governanceAdmin', 'governanceReviewer', 'secops'] },
      { path: '/audit-center', label: '审计中心', icon: 'Document', audiences: ['governanceAdmin', 'governanceReviewer', 'secops', 'businessOwner', 'audit'] },
    ],
  },
  {
    key: 'process',
    title: '流转与履约',
    items: [
      { path: '/approval-center', label: '审批中心', icon: 'Files', audiences: ['governanceAdmin', 'governanceReviewer'] },
    ],
  },
  {
    key: 'system',
    title: '平台控制',
    items: [
      { path: '/user-manage', label: '用户管理', icon: 'UserFilled', audiences: ['governanceAdmin', 'governanceReviewer'] },
      { path: '/role-manage', label: '角色管理', icon: 'Avatar', audiences: ['governanceAdmin', 'governanceReviewer'] },
      { path: '/permission-manage', label: '权限管理', icon: 'Key', audiences: ['governanceAdmin', 'governanceReviewer'] },
      { path: '/policy-manage', label: '策略管理', icon: 'Document', audiences: ['governanceAdmin', 'governanceReviewer', 'secops', 'businessOwner', 'audit'] },
    ],
  },
];

const EXTRA_ROUTE_AUDIENCES = {
  '/profile': [ALL],
  '/settings': [ALL],
  '/operations-command': ['governanceAdmin', 'governanceReviewer', 'secops', 'businessOwner', 'audit'],
  '/ops-observability': ['governanceAdmin', 'governanceReviewer', 'secops'],
  '/audit-center': ['governanceAdmin', 'governanceReviewer', 'secops', 'businessOwner', 'audit'],
  '/approval-center': ['governanceAdmin', 'governanceReviewer'],
  '/policy-manage': ['governanceAdmin', 'governanceReviewer', 'secops', 'businessOwner', 'audit'],
  '/ai/risk-rating': ['governanceAdmin', 'governanceReviewer', 'secops', 'businessOwner', 'audit'],
  '/shadow-ai': ['governanceAdmin', 'governanceReviewer', 'secops', 'businessOwner', 'audit'],
  '/threat-monitor': ['governanceAdmin', 'secops'],
  '/privacy-monitor': ['governanceAdmin', 'governanceReviewer', 'secops'],
};

const ROLE_PATH_ALLOWLIST = {
  ADMIN: [
    '/', '/operations-command', '/ops-observability', '/shadow-ai', '/threat-monitor', '/privacy-monitor', '/ai/risk-rating', '/audit-center', '/approval-center',
    '/policy-manage', '/user-manage', '/role-manage', '/permission-manage', '/profile', '/settings',
  ],
  ADMIN_REVIEWER: [
    '/', '/operations-command', '/ops-observability', '/shadow-ai', '/privacy-monitor', '/ai/risk-rating', '/audit-center', '/approval-center',
    '/policy-manage', '/user-manage', '/role-manage', '/permission-manage', '/profile', '/settings',
  ],
  ADMIN_OPS: [
    '/', '/operations-command', '/ops-observability', '/shadow-ai', '/threat-monitor', '/privacy-monitor', '/ai/risk-rating', '/audit-center',
    '/policy-manage', '/profile', '/settings',
  ],
  SECOPS: [
    '/', '/operations-command', '/ops-observability', '/shadow-ai', '/threat-monitor', '/privacy-monitor', '/ai/risk-rating', '/audit-center',
    '/policy-manage', '/profile', '/settings',
  ],
  SECOPS_RESPONDER: [
    '/', '/operations-command', '/ops-observability', '/shadow-ai', '/threat-monitor', '/privacy-monitor', '/ai/risk-rating', '/audit-center',
    '/policy-manage', '/profile', '/settings',
  ],
  BUSINESS_OWNER: [
    '/', '/operations-command', '/shadow-ai', '/ai/risk-rating', '/audit-center', '/policy-manage', '/profile', '/settings',
  ],
  BUSINESS_OWNER_APPROVER: [
    '/', '/operations-command', '/shadow-ai', '/ai/risk-rating', '/audit-center', '/policy-manage', '/profile', '/settings',
  ],
  AUDIT: [
    '/', '/operations-command', '/shadow-ai', '/ai/risk-rating', '/audit-center', '/policy-manage', '/profile', '/settings',
  ],
  SEC: [
    '/', '/operations-command', '/ops-observability', '/shadow-ai', '/threat-monitor', '/privacy-monitor', '/audit-center', '/profile', '/settings',
  ],
};

const PATH_PERMISSION_REQUIREMENTS = {
  '/audit-center': ['audit:log:view', 'audit:report:view'],
  '/user-manage': ['user:manage'],
  '/role-manage': ['role:manage'],
  '/permission-manage': ['permission:manage'],
  '/approval-center': [
    'govern:change:create',
    'govern:change:review',
    'approval:operate',
    'approval:operate:data',
    'approval:operate:business',
    'approval:operate:governance',
  ],
};

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
    return 'governanceReviewer';
  }
  const orderedIds = ['businessOwner', 'secops', 'audit', 'governanceReviewer', 'governanceAdmin'];
  const matched = orderedIds.find(id => PERSONAS[id].roleHints.some(hint => haystack.includes(hint.toLowerCase())));
  return matched || 'governanceReviewer';
}

export function getPersonaExperience(user) {
  return PERSONAS[inferPersona(user)] || PERSONAS.governanceReviewer;
}

function allows(audiences, personaId) {
  return audiences.includes(ALL) || audiences.includes(personaId);
}

function isPlatformAdmin(user) {
  return String(user?.roleCode || '').trim().toUpperCase() === 'ADMIN';
}

function isGovernanceReviewer(user) {
  return String(user?.roleCode || '').trim().toUpperCase() === 'ADMIN_REVIEWER';
}

function hasAnyPermission(user, permissionCodes = []) {
  return permissionCodes.some(code => hasPermissionByUser(user, code));
}

function isPathPermissionAllowed(path, user) {
  const requiredAny = PATH_PERMISSION_REQUIREMENTS[path];
  if (!Array.isArray(requiredAny) || requiredAny.length === 0) {
    return true;
  }
  if (isPlatformAdmin(user)) {
    return true;
  }
  if (isGovernanceReviewer(user) && ['/user-manage', '/role-manage', '/permission-manage', '/policy-manage'].includes(path)) {
    return true;
  }
  return hasAnyPermission(user, requiredAny);
}

function rolePathAllowlist(user) {
  const roleCode = String(user?.roleCode || '').trim().toUpperCase();
  const allowedPaths = ROLE_PATH_ALLOWLIST[roleCode];
  if (!Array.isArray(allowedPaths) || allowedPaths.length === 0) {
    return null;
  }
  return new Set(allowedPaths);
}

export function getVisibleMenuSections(user) {
  const roleAllowlist = rolePathAllowlist(user);
  const personaId = inferPersona(user);
  return MENU_SECTIONS
    .map(section => ({
      ...section,
      items: section.items.filter(item => (
        (roleAllowlist ? roleAllowlist.has(item.path) : allows(item.audiences, personaId))
        && isPathPermissionAllowed(item.path, user)
      )),
    }))
    .filter(section => section.items.length > 0);
}

export function canAccessPath(path, user) {
  if (!path || path === '/login') {
    return true;
  }
  const roleAllowlist = rolePathAllowlist(user);
  const personaId = inferPersona(user);
  const menuItem = MENU_SECTIONS.flatMap(section => section.items).find(item => item.path === path);
  if (menuItem) {
    return (roleAllowlist ? roleAllowlist.has(path) : allows(menuItem.audiences, personaId)) && isPathPermissionAllowed(path, user);
  }
  const explicit = EXTRA_ROUTE_AUDIENCES[path];
  if (explicit) {
    return (roleAllowlist ? roleAllowlist.has(path) : allows(explicit, personaId)) && isPathPermissionAllowed(path, user);
  }
  return false;
}

export function resolveDefaultLandingPath(user) {
  const roleCode = String(user?.roleCode || '').trim().toUpperCase();
  if (roleCode === 'ADMIN_REVIEWER' && canAccessPath('/approval-center', user)) {
    return '/approval-center';
  }
  return '/';
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
