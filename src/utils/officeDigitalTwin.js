export const OFFICE_DESK_COUNT = 24;

const DEPT_NODE_MAP = {
  research: 'Dept_Research',
  product: 'Dept_Product',
  marketing: 'Dept_Marketing',
  finance: 'Dept_Finance',
  ops: 'Dept_Ops',
};

const ROLE_TIER_MAP = {
  ADMIN: 'management',
  SEC: 'security',
  SECOPS: 'security',
  DATA_ADMIN: 'data',
  AUDIT: 'audit',
  EMPLOYEE: 'employee',
};

const DEPT_NODE_LABEL_MAP = {
  Dept_Research: '研发/技术',
  Dept_Product: '产品/策略',
  Dept_Marketing: '市场/运营',
  Dept_Finance: '财务/合规',
  Dept_Ops: '运营/支撑',
};

const ROLE_TIER_LABEL_MAP = {
  management: '管理层',
  security: '安全层',
  data: '数据层',
  audit: '审计层',
  employee: '业务层',
};

export function makeDeskId(index) {
  const value = Math.max(1, Number(index || 1));
  return `Desk_${String(value).padStart(3, '0')}`;
}

export function normalizeDeskId(rawId) {
  const text = String(rawId || '').trim();
  if (!text) return '';
  if (/^Desk_\d{3}$/i.test(text)) {
    const num = Number(text.slice(5));
    return makeDeskId(num);
  }
  const seatMatch = text.match(/^seat-(\d+)$/i);
  if (seatMatch) {
    return makeDeskId(Number(seatMatch[1]));
  }
  const digits = text.match(/(\d{1,3})$/);
  if (digits) {
    return makeDeskId(Number(digits[1]));
  }
  return text;
}

export function hashToDeskId(seed, seatCount = OFFICE_DESK_COUNT) {
  const text = String(seed || 'employee');
  let hash = 0;
  for (let i = 0; i < text.length; i += 1) {
    hash = (hash * 31 + text.charCodeAt(i)) % 7919;
  }
  const index = (Math.abs(hash) % Math.max(1, Number(seatCount || OFFICE_DESK_COUNT))) + 1;
  return makeDeskId(index);
}

export function mapDepartmentToNode(department) {
  const text = String(department || '').toLowerCase();
  if (text.includes('研') || text.includes('tech') || text.includes('engineer') || text.includes('it') || text.includes('数据')) {
    return DEPT_NODE_MAP.research;
  }
  if (text.includes('产品') || text.includes('product') || text.includes('strategy')) {
    return DEPT_NODE_MAP.product;
  }
  if (text.includes('市场') || text.includes('marketing') || text.includes('运营') || text.includes('growth')) {
    return DEPT_NODE_MAP.marketing;
  }
  if (text.includes('财') || text.includes('finance') || text.includes('审计') || text.includes('audit') || text.includes('合规')) {
    return DEPT_NODE_MAP.finance;
  }
  return DEPT_NODE_MAP.ops;
}

export function mapRoleToTier(roleCode) {
  const key = String(roleCode || '').toUpperCase();
  return ROLE_TIER_MAP[key] || 'employee';
}

export function mapDeptNodeToZoneCode(deptNode) {
  const key = String(deptNode || '').trim();
  if (key === 'Dept_Research') return 'engineering';
  if (key === 'Dept_Product') return 'strategy';
  if (key === 'Dept_Marketing') return 'operation';
  if (key === 'Dept_Finance') return 'finance';
  if (key === 'Dept_Ops') return 'operation';
  return 'strategy';
}

export function describeDeptNode(deptNode) {
  return DEPT_NODE_LABEL_MAP[String(deptNode || '').trim()] || '综合办公';
}

export function describeRoleTier(roleTier) {
  return ROLE_TIER_LABEL_MAP[String(roleTier || '').trim()] || '业务层';
}

export function requiredNodeNames(seatCount = OFFICE_DESK_COUNT) {
  const nodes = [];
  for (let i = 1; i <= seatCount; i += 1) {
    nodes.push(makeDeskId(i));
  }
  nodes.push('Dept_Research', 'Dept_Product', 'Dept_Marketing', 'Dept_Finance', 'Dept_Ops', 'Control_Root', 'Pulse_Center');
  return nodes;
}

export function validateNodeNames(existingNames = [], seatCount = OFFICE_DESK_COUNT) {
  const existing = new Set((existingNames || []).map(name => String(name || '').trim()).filter(Boolean));
  const required = requiredNodeNames(seatCount);
  const missing = required.filter(name => !existing.has(name));
  return {
    ok: missing.length === 0,
    missing,
    requiredCount: required.length,
  };
}
