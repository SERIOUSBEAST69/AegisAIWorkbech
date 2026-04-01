<template>
  <div class="login-stage" ref="pageEl">
    <!-- Login Dynamic Background: Fast, moving, interactive -->
    <LiquidChrome 
      :baseColor="[0.01, 0.02, 0.04]" 
      :speed="0.4" 
      :amplitude="0.6" 
      :interactive="true" 
    />

    <div class="stage-center">
      <div class="portal-shell" :data-lifted="isPortalLifted || undefined">
        <div class="portal-shell-edge" aria-hidden="true"></div>

        <section class="hero-marquee" ref="heroTitleEl">
          <div class="title-container" ref="titleTextEl">
            <img src="../assets/logo.svg" class="login-logo" alt="logo" />
            <h1 class="login-title workbench-title-core" data-text="Aegis Workbench">Aegis Workbench</h1>
          </div>
          <p class="hero-subtitle">可信AI治理工作台</p>
          <div class="beam-track" ref="beamEl"></div>
        </section>

        <section class="wizard-shell card-glass" ref="panelEl">
          <div class="wizard-sheen" aria-hidden="true"></div>
        <header class="wizard-topbar">
          <span class="brand-chip">{{ currentStepMeta.kicker }}</span>
          <span class="panel-mini-meta">Real API</span>
        </header>

        <div class="step-indicator-row">
          <template v-for="(step, index) in wizardSteps" :key="step.id">
            <button
              type="button"
              class="step-indicator"
              :class="{
                active: currentStep === step.id,
                complete: currentStep > step.id,
                disabled: !canJumpToStep(step.id),
              }"
              @click="jumpToStep(step.id)"
            >
              <span class="step-indicator-inner">
                <svg v-if="currentStep > step.id" class="check-icon" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                  <path d="M5 13l4 4L19 7" stroke-linecap="round" stroke-linejoin="round" />
                </svg>
                <span v-else-if="currentStep === step.id" class="active-dot"></span>
                <span v-else>{{ step.id }}</span>
              </span>
              <span class="step-indicator-copy">{{ step.label }}</span>
            </button>
            <div v-if="index < wizardSteps.length - 1" class="step-connector">
              <div class="step-connector-inner" :class="{ complete: currentStep > step.id }"></div>
            </div>
          </template>
        </div>

        <div class="wizard-head">
          <strong>{{ currentStepMeta.title }}</strong>
          <p>{{ currentStepMeta.description }}</p>
        </div>

        <div ref="stepViewportEl" class="wizard-viewport">
          <Transition
            mode="out-in"
            :css="false"
            @before-enter="onStepBeforeEnter"
            @enter="onStepEnter"
            @leave="onStepLeave"
            @after-enter="onStepAfterEnter"
          >
            <section :key="currentStep" :ref="setActiveStepEl" class="step-screen">
              <template v-if="currentStep === 1">
                <div class="step-layout step-layout-choice">
                  <div class="choice-cluster">
                    <div class="cluster-title">01. 先决定你怎么进入</div>
                    <div class="flow-grid">
                      <button
                        v-for="flow in accessFlows"
                        :key="flow.value"
                        type="button"
                        class="flow-card clickable"
                        :class="{ active: flowType === flow.value }"
                        @click="selectFlow(flow.value)"
                      >
                        <span class="flow-accent">{{ flow.accent }}</span>
                        <strong>{{ flow.title }}</strong>
                        <p>{{ flow.description }}</p>
                      </button>
                    </div>
                  </div>
                </div>
              </template>

              <template v-else-if="currentStep === 2">
                <div class="step-layout step-layout-choice">
                  <div class="choice-cluster">
                    <div class="cluster-title">02. 再决定身份建立通道</div>
                    <div class="mode-grid">
                      <button
                        v-for="mode in accessModes"
                        :key="mode.value"
                        type="button"
                        class="mode-card clickable"
                        :class="{ active: activeMode === mode.value }"
                        @click="selectMode(mode.value)"
                      >
                        <span class="mode-icon-wrap">
                          <el-icon><component :is="mode.icon" /></el-icon>
                        </span>
                        <div class="mode-copy">
                          <strong>{{ mode.label }}</strong>
                          <p>{{ flowType === 'register' ? mode.registerHint : mode.loginHint }}</p>
                        </div>
                      </button>
                    </div>
                  </div>

                </div>
              </template>

              <template v-else-if="currentStep === 3">
                <div class="step-layout step-layout-form">
                  <div class="form-stage-meta">
                    <span>{{ currentFlowMeta.title }} · {{ currentModeMeta.label }}</span>
                    <strong>{{ formStageTitle }}</strong>
                    <p>{{ formStageDescription }}</p>
                  </div>

                  <form class="login-form" @submit.prevent="handlePrimaryAction" novalidate>
                    <template v-if="flowType === 'login' && activeMode === 'password'">
                      <div class="field-group">
                        <label for="login-username">用户名</label>
                        <input id="login-username" v-model.trim="passwordForm.username" class="field-input" type="text" autocomplete="username" placeholder="例如：admin" />
                      </div>

                      <div class="field-group">
                        <label for="login-password">密码</label>
                        <div class="password-wrap">
                          <input id="login-password" v-model="passwordForm.password" class="field-input" :type="showPwd ? 'text' : 'password'" autocomplete="current-password" placeholder="例如：admin" />
                          <button type="button" class="assist-button clickable" @click="showPwd = !showPwd">{{ showPwd ? '隐藏' : '显示' }}</button>
                        </div>
                      </div>

                      <div class="field-group">
                        <label for="login-captcha">验证码</label>
                        <div class="password-wrap">
                          <input id="login-captcha" v-model.trim="passwordForm.captcha" class="field-input" type="text" maxlength="4" autocomplete="one-time-code" placeholder="请输入验证码" />
                          <button type="button" class="assist-button clickable" @click="refreshCaptcha">{{ captchaCode }}</button>
                        </div>
                      </div>

                      <div class="form-options">
                        <label class="remember-row clickable">
                          <input v-model="remember" type="checkbox" />
                          <span>记住密码</span>
                        </label>
                        <button type="button" class="link-button clickable" @click="onForgot">忘记密码?</button>
                      </div>

                      <div class="identity-support" aria-label="supported-identities">
                        <span class="identity-support-label">演示账号快速选择</span>
                        <div class="identity-select-row">
                          <select v-model="selectedDemoRole" class="field-input field-select" @change="onDemoRoleChange">
                            <option value="">选择身份</option>
                            <option v-for="item in demoRoleOptions" :key="`demo-role-${item.roleCode}`" :value="item.roleCode">
                              {{ item.roleLabel }}
                            </option>
                          </select>
                          <select
                            v-model="selectedDemoUsername"
                            class="field-input field-select"
                            :disabled="!selectedDemoRole || demoAccountsForSelectedRole.length === 0"
                            @change="onDemoAccountChange"
                          >
                            <option value="">选择账号</option>
                            <option v-for="account in demoAccountsForSelectedRole" :key="`demo-user-${account.username}`" :value="account.username">
                              {{ account.label }} · {{ account.username }}
                            </option>
                          </select>
                        </div>
                      </div>
                    </template>



                    <template v-else-if="flowType === 'register' && activeMode === 'password'">
                      <div class="compact-grid">
                        <div class="field-group">
                          <label for="register-real-name">姓名</label>
                          <input id="register-real-name" v-model.trim="registerForm.realName" class="field-input" type="text" placeholder="请输入真实姓名" />
                        </div>
                        <div class="field-group">
                          <label for="register-nickname">展示名称</label>
                          <input id="register-nickname" v-model.trim="registerForm.nickname" class="field-input" type="text" placeholder="工作台显示名称" />
                        </div>
                      </div>

                      <div class="compact-grid">
                        <div class="field-group">
                          <label for="register-role">身份</label>
                          <select id="register-role" v-model="selectedRegisterRole" class="field-input field-select">
                            <option
                              v-for="item in registrationOptions.identities"
                              :key="`${item.id || 'na'}-${item.code}`"
                              :value="String(item.id || item.code)"
                            >
                              {{ item.label }}
                            </option>
                          </select>
                        </div>
                        <div class="field-group">
                          <label for="register-organization">组织类型</label>
                          <select id="register-organization" v-model="registerForm.organizationType" class="field-input field-select">
                            <option v-for="item in registrationOptions.organizations" :key="item.code" :value="item.code">{{ item.label }}</option>
                          </select>
                        </div>
                      </div>

                      <div class="compact-grid">
                        <div class="field-group">
                          <label for="register-invite-code">企业邀请码</label>
                          <input id="register-invite-code" v-model.trim="registerForm.inviteCode" class="field-input" type="text" placeholder="例如：AEGIS-1A2B3C4D5E" />
                        </div>
                        <div class="field-group">
                          <label for="register-company-name">公司名称</label>
                          <input id="register-company-name" v-model.trim="registerForm.companyName" class="field-input" type="text" placeholder="将根据邀请码自动识别" readonly />
                        </div>
                      </div>

                      <div class="field-group">
                        <label for="register-department">部门 / 团队</label>
                        <input id="register-department" v-model.trim="registerForm.department" class="field-input" type="text" placeholder="例如：安全运营中心 / AI研发部 / 教务处" />
                      </div>

                      <div class="compact-grid">
                        <div class="field-group">
                          <label for="register-username">用户名</label>
                          <input id="register-username" v-model.trim="registerForm.username" class="field-input" type="text" placeholder="自定义用户名" />
                        </div>
                        <div class="field-group">
                          <label for="register-password">密码</label>
                          <input id="register-password" v-model="registerForm.password" class="field-input" type="password" placeholder="至少 6 位" />
                        </div>
                      </div>

                      <div class="field-group">
                        <label for="register-password-confirm">确认密码</label>
                        <input id="register-password-confirm" v-model="registerForm.confirmPassword" class="field-input" type="password" placeholder="请再次输入密码" />
                      </div>

                      <div class="field-group">
                        <label for="register-email">邮箱（可选）</label>
                        <input id="register-email" v-model.trim="registerForm.email" class="field-input" type="email" placeholder="用于接收治理通知" />
                      </div>
                    </template>


                  </form>
                </div>
              </template>

              <template v-else-if="currentStep === 4">
                <div class="step-layout step-layout-review">
                  <div class="review-hero">
                    <span>READY TO ENTER</span>
                    <strong>{{ reviewTitle }}</strong>
                    <p>{{ reviewDescription }}</p>
                  </div>

                  <div class="review-grid">
                    <article v-for="item in reviewItems" :key="item.label" class="review-card">
                      <span>{{ item.label }}</span>
                      <strong>{{ item.value }}</strong>
                    </article>
                  </div>

                  <div class="review-note">
                    <em>当前流程会在进入后直接衔接首页标题动画。</em>
                    <span>如果要修改资料，点上一步即可，不会再弹出额外叠层。</span>
                  </div>
                </div>
              </template>
            </section>
          </Transition>
        </div>

        <Transition name="field-error">
          <div v-if="globalError" class="global-error" role="alert">{{ globalError }}</div>
        </Transition>

          <footer class="wizard-footer">
          <button v-if="currentStep !== 1" type="button" class="back-button clickable" @click="goBack">上一步</button>
          <button type="button" class="next-button clickable" :disabled="!canProceed || isLoading" @click="handlePrimaryAction">
            <span v-if="!isLoading">{{ primaryButtonLabel }}</span>
            <span v-else class="btn-loading"><i class="spinner"></i>{{ loadingLabel }}</span>
          </button>
          </footer>
        </section>
      </div>
    </div>

    <div class="stage-footer">© 2026 Aegis Workbench · 可信AI数据治理与隐私合规工作台</div>
  </div>
</template>

<script setup>
import { computed, nextTick, onBeforeUnmount, onMounted, reactive, ref, watch } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import gsap from 'gsap';
import { ElMessage, ElMessageBox } from 'element-plus';
import { UserFilled } from '@element-plus/icons-vue';
import { authApi } from '../api/auth';
import { useUserStore } from '../store/user';
import LiquidChrome from '../components/LiquidChrome.vue';
import { acceptEmployeeAgreement, hasAcceptedEmployeeAgreement, isEmployeeUser } from '../utils/employeePolicy';

const router = useRouter();
const route = useRoute();
const userStore = useUserStore();

const pageEl = ref(null);
const panelEl = ref(null);
const heroTitleEl = ref(null);
const titleTextEl = ref(null);
const beamEl = ref(null);
const stepViewportEl = ref(null);
const activeStepEl = ref(null);

const wizardSteps = [
  { id: 1, label: '入口' },
  { id: 2, label: '方式' },
  { id: 3, label: '资料' },
  { id: 4, label: '确认' },
];

const accessFlows = [
  { value: 'login', accent: 'RETURNING ACCESS', title: '登录工作台', description: '已有账号时，从这里直接进入。' },
  { value: 'register', accent: 'NEW GOVERNANCE ID', title: '创建治理身份', description: '第一次进入时按步骤建立工作台身份，不再把注册表单堆到页面侧边。' },
];

const accessModes = [
  { value: 'password', label: '账号密码', icon: UserFilled, loginHint: '通过账号与密码建立安全会话。', registerHint: '创建一组适合长期使用的工作台凭据。' },
];
const CAPTCHA_CHARS = 'ABCDEFGHJKMNPQRSTUVWXYZ23456789';

const currentStep = ref(1);
const stepDirection = ref(1);
const flowType = ref('login');
const activeMode = ref('');
const modeChosen = ref(false);
const isLoading = ref(false);
const globalError = ref('');
const showPwd = ref(false);
const remember = ref(true);
const captchaCode = ref('');


const passwordForm = reactive({ username: '', password: '', captcha: '' });

const registerForm = reactive({
  accountType: 'real',
  loginType: 'password',
  companyName: '',
  inviteCode: '',
  username: '',
  password: '',
  confirmPassword: '',
  realName: '',
  nickname: '',
  roleId: null,
  roleCode: '',
  organizationType: 'enterprise',
  department: '',
  phone: '',
  phoneCode: '',
  email: '',
  wechatOpenId: '',
});

const DEFAULT_IDENTITIES = [
  { code: 'ADMIN', label: '治理管理员' },
  { code: 'EXECUTIVE', label: '管理层' },
  { code: 'SECOPS', label: '安全运维' },
  { code: 'DATA_ADMIN', label: '数据管理员' },
  { code: 'AI_BUILDER', label: 'AI应用开发者' },
  { code: 'BUSINESS_OWNER', label: '业务负责人' },
  { code: 'EMPLOYEE', label: '普通员工' },
];

const DEFAULT_ORGANIZATIONS = [
  { code: 'enterprise', label: '企业' },
  { code: 'school', label: '学校' },
  { code: 'ai-team', label: 'AI应用团队' },
  { code: 'public-sector', label: '政企/公共机构' },
];

function normalizeOptions(options, fallback) {
  if (!Array.isArray(options) || options.length === 0) {
    return fallback.map(item => ({ id: item.id ?? null, code: item.code, label: item.label }));
  }
  const normalized = options
    .map(item => ({
      id: item?.id ?? null,
      code: String(item?.code || '').trim(),
      label: String(item?.label || '').trim(),
    }))
    .filter(item => item.code && item.label);
  return normalized.length > 0
    ? normalized
    : fallback.map(item => ({ id: item.id ?? null, code: item.code, label: item.label }));
}

const registrationOptions = reactive({
  identities: [...DEFAULT_IDENTITIES],
  organizations: [...DEFAULT_ORGANIZATIONS],
  demoAccounts: [],
});

const registerRoleOptions = computed(() => registrationOptions.identities || []);
const selectedRegisterRole = ref('');

function syncRegisterRoleSelection(selectedValue) {
  const selected = registerRoleOptions.value.find(item => String(item.id ?? item.code) === String(selectedValue));
  if (!selected) {
    registerForm.roleId = null;
    registerForm.roleCode = '';
    return;
  }
  registerForm.roleId = selected.id || null;
  registerForm.roleCode = selected.code || '';
}

const selectedDemoRole = ref('');
const selectedDemoUsername = ref('');

const demoRoleOptions = computed(() => registrationOptions.demoAccounts || []);
const demoAccountsForSelectedRole = computed(() => {
  const role = demoRoleOptions.value.find(item => item.roleCode === selectedDemoRole.value);
  return role?.accounts || [];
});

function onDemoRoleChange() {
  selectedDemoUsername.value = '';
  const first = demoAccountsForSelectedRole.value[0];
  if (first) {
    selectedDemoUsername.value = first.username;
    onDemoAccountChange();
  }
}

function onDemoAccountChange() {
  const account = demoAccountsForSelectedRole.value.find(item => item.username === selectedDemoUsername.value);
  if (!account) return;
  passwordForm.username = account.username;
  passwordForm.password = account.password;
  globalError.value = '';
}

const currentFlowMeta = computed(() => accessFlows.find(item => item.value === flowType.value) || accessFlows[0]);
const currentModeMeta = computed(() => accessModes.find(item => item.value === activeMode.value) || accessModes[0]);
const isPortalLifted = computed(() => currentStep.value >= 3 || (currentStep.value === 2 && modeChosen.value));

const formStageTitle = computed(() => {
  if (flowType.value === 'register') {
    return `用${currentModeMeta.value.label}建立新的治理身份`;
  }
  return `用${currentModeMeta.value.label}进入现有工作台`;
});

const formStageDescription = computed(() => {
  if (flowType.value === 'register') {
    return currentModeMeta.value.registerHint;
  }
  return currentModeMeta.value.loginHint;
});

const currentStepMeta = computed(() => {
  if (currentStep.value === 1) {
    return {
      kicker: 'STEP 01 · ACCESS PATH',
      title: '先决定当前是登录还是注册',
      description: '这一屏只做一件事，先确认你是回到工作台，还是首次建立新的治理身份。',
    };
  }
  if (currentStep.value === 2) {
    return {
      kicker: 'STEP 02 · AUTH CHANNEL',
      title: '再选择身份建立通道',
      description: '账号密码登录',
    };
  }
  if (currentStep.value === 3) {
    return {
      kicker: 'STEP 03 · PROFILE INPUT',
      title: formStageTitle.value,
      description: formStageDescription.value,
    };
  }
  return {
    kicker: 'STEP 04 · FINAL CHECK',
    title: '确认这次进入将以什么身份落到工作台',
    description: '确认后当前卡片会退场，直接接入首页标题与工作台主场景。',
  };
});

const reviewTitle = computed(() => flowType.value === 'register' ? '新身份已准备好进入工作台' : '当前登录会直接落到工作台正标题');
const reviewDescription = computed(() => flowType.value === 'register'
  ? '确认后系统会先创建身份，再把这个身份抬升到首页工作台。'
  : '确认后不会再弹出新的面板，当前主卡片会退场，接着进入首页。');

const reviewPrimaryIdentity = computed(() => {
  if (flowType.value === 'login' && activeMode.value === 'password') return passwordForm.username || '未填写';
  if (flowType.value === 'register' && activeMode.value === 'password') return registerForm.username || registerForm.realName || '未填写';
  return registerForm.username || registerForm.realName || '未填写';
});

const reviewRole = computed(() => {
  const matched = registerRoleOptions.value.find(item => String(item.code) === String(registerForm.roleCode));
  return matched?.label || registerForm.roleCode || '未选择';
});

const reviewOrganization = computed(() => {
  return registerForm.organizationType || 'enterprise';
});

const reviewDepartment = computed(() => {
  if (flowType.value === 'register') return registerForm.department || '进入后补充';
  return '当前已有身份';
});

const reviewCompany = computed(() => {
  if (flowType.value === 'register') {
    return registerForm.companyName || `邀请码: ${registerForm.inviteCode || '未填写'}`;
  }
  return '已绑定公司';
});

const reviewItems = computed(() => [
  { label: '当前流程', value: currentFlowMeta.value.title },
  { label: '认证方式', value: currentModeMeta.value.label },
  { label: '主标识', value: reviewPrimaryIdentity.value },
  { label: '公司', value: reviewCompany.value },
  { label: '角色 / 身份', value: reviewRole.value },
  { label: '组织类型', value: reviewOrganization.value },
  { label: '部门 / 团队', value: reviewDepartment.value },
]);

const primaryButtonLabel = computed(() => {
  if (currentStep.value === 4) {
    return flowType.value === 'register' ? '创建并进入工作台' : '进入工作台';
  }
  return '下一步';
});

const loadingLabel = computed(() => {
  if (flowType.value === 'register') return '正在创建身份';
  if (activeMode.value === 'password') return '正在建立安全会话';
  return '正在建立安全会话';
});

const canProceed = computed(() => {
  if (currentStep.value === 1) return Boolean(flowType.value);
  if (currentStep.value === 2) return Boolean(activeMode.value);
  if (currentStep.value === 3) return !getFormValidationError();
  return true;
});

function setActiveStepEl(el) {
  if (el) {
    activeStepEl.value = el;
  }
}

function syncStepHeight(immediate = false) {
  if (!stepViewportEl.value || !activeStepEl.value) return;
  const nextHeight = activeStepEl.value.offsetHeight;
  gsap.to(stepViewportEl.value, {
    height: nextHeight,
    duration: immediate ? 0 : 0.46,
    ease: 'expo.out',
  });
}

function onStepBeforeEnter(el) {
  gsap.set(el, {
    opacity: 0,
    x: stepDirection.value >= 0 ? 88 : -88,
    filter: 'blur(12px)',
  });
}

function onStepEnter(el, done) {
  gsap.to(el, {
    opacity: 1,
    x: 0,
    filter: 'blur(0px)',
    duration: 0.46,
    ease: 'expo.out',
    onComplete: done,
  });
}

function onStepLeave(el, done) {
  gsap.to(el, {
    opacity: 0,
    x: stepDirection.value >= 0 ? -46 : 46,
    filter: 'blur(10px)',
    duration: 0.28,
    ease: 'power2.in',
    onComplete: done,
  });
}

function onStepAfterEnter() {
  syncStepHeight();
}

function refreshCaptcha() {
  captchaCode.value = Array.from({ length: 4 }, () => CAPTCHA_CHARS[Math.floor(Math.random() * CAPTCHA_CHARS.length)]).join('');
}



function ensureRegistrationBasics() {
  if (!registerForm.realName || !registerForm.organizationType) {
    throw new Error('请先填写姓名、身份与组织类型');
  }
  if (!registerForm.roleId && !registerForm.roleCode) {
    throw new Error('请选择身份');
  }
  if (!registerForm.inviteCode) {
    throw new Error('请输入企业邀请码');
  }
  if (!registerForm.department) {
    throw new Error('请填写部门或团队');
  }
}

function getFormValidationError() {
  if (flowType.value === 'login') {
    if (activeMode.value === 'password') {
      if (!passwordForm.username || !passwordForm.password || passwordForm.password.length < 4) {
        return '请输入正确的用户名与密码';
      }
      if (!passwordForm.captcha) {
        return '请输入验证码';
      }
      if (passwordForm.captcha.toUpperCase() !== captchaCode.value) {
        return '验证码错误';
      }
      return '';
    }
    return '';
  }

  try {
    ensureRegistrationBasics();
  } catch (error) {
    return error.message;
  }

  if (activeMode.value === 'password') {
    if (!registerForm.username || !registerForm.password || registerForm.password.length < 6) {
      return '账号注册需要用户名和至少 6 位密码';
    }
    if (!registerForm.confirmPassword || registerForm.confirmPassword !== registerForm.password) {
      return '请确认两次输入的密码一致';
    }
    return '';
  }

  return '';
}

function canJumpToStep(step) {
  if (step === 1) return true;
  if (step === 2) return Boolean(flowType.value);
  if (step === 3) return Boolean(flowType.value && activeMode.value);
  return Boolean(flowType.value && activeMode.value) && !getFormValidationError();
}

function goToStep(step) {
  if (step === currentStep.value || !canJumpToStep(step)) return;
  stepDirection.value = step > currentStep.value ? 1 : -1;
  currentStep.value = step;
  globalError.value = '';
}

function jumpToStep(step) {
  goToStep(step);
}

function goBack() {
  if (currentStep.value > 1) {
    goToStep(currentStep.value - 1);
  }
}

function selectFlow(value) {
  flowType.value = value;
  activeMode.value = '';
  modeChosen.value = false;
  registerForm.loginType = 'password';
  globalError.value = '';
}

function selectMode(value) {
  activeMode.value = value;
  modeChosen.value = true;
  registerForm.loginType = value;
  globalError.value = '';
}

async function establishAndRoute(response) {
  const user = await userStore.establishSession(response);
  await enforceEmployeeAgreement(user);
  await playCinematicSuccess();
  sessionStorage.setItem('aegis.transition.origin', 'login');
  const redirect = typeof router.currentRoute.value.query.redirect === 'string' ? router.currentRoute.value.query.redirect : '/';

  // Use the modern View Transitions API if supported for silky smooth transition
  if (document.startViewTransition) {
    document.startViewTransition(async () => {
      await router.push(redirect);
      await nextTick();
    });
  } else {
    createTitleGhost();
    await router.push(redirect);
  }
}

async function enforceEmployeeAgreement(user) {
  if (!isEmployeeUser(user) || hasAcceptedEmployeeAgreement(user)) {
    return;
  }
  try {
    await ElMessageBox.confirm(
      '作为普通员工，首次登录需同意《终端检测与隐私合规协议》。同意后，基础检测能力将保持开启，无法在员工端关闭。',
      '员工使用协议',
      {
        confirmButtonText: '同意并继续',
        cancelButtonText: '取消登录',
        closeOnClickModal: false,
        closeOnPressEscape: false,
        distinguishCancelAndClose: true,
        type: 'warning',
      }
    );
    acceptEmployeeAgreement(user);
  } catch {
    await userStore.logout();
    throw new Error('未同意员工协议，已取消登录');
  }
}

function createTitleGhost() {
  if (!titleTextEl.value) {
    return;
  }

  document.querySelectorAll('[data-transition-title="login"]').forEach(node => node.remove());

  const rect = titleTextEl.value.getBoundingClientRect();
  const ghost = titleTextEl.value.cloneNode(true);
  ghost.setAttribute('data-transition-title', 'login');
  ghost.classList.add('route-transition-title-ghost');
  ghost.style.position = 'fixed';
  ghost.style.left = `${rect.left}px`;
  ghost.style.top = `${rect.top}px`;
  ghost.style.width = `${rect.width}px`;
  ghost.style.height = `${rect.height}px`;
  ghost.style.margin = '0';
  ghost.style.zIndex = '26';
  ghost.style.pointerEvents = 'none';
  ghost.style.transform = 'none';
  ghost.style.opacity = '1';
  document.body.appendChild(ghost);
}

async function submitPasswordLogin() {
  const validationError = getFormValidationError();
  if (validationError) {
    globalError.value = validationError;
    shakePanel();
    return;
  }

  isLoading.value = true;
  try {
    const res = await authApi.login(passwordForm);
    if (remember.value) {
      localStorage.setItem('login_cache', JSON.stringify({ username: passwordForm.username, password: passwordForm.password }));
    } else {
      localStorage.removeItem('login_cache');
    }
    await establishAndRoute(res);
  } catch (err) {
    refreshCaptcha();
    passwordForm.captcha = '';
    globalError.value = err?.message || '登录失败，请稍后重试';
    shakePanel();
  } finally {
    isLoading.value = false;
  }
}

async function submitPhoneLogin() {
  const validationError = getFormValidationError();
  if (validationError) {
    globalError.value = validationError;
    shakePanel();
    return;
  }

  isLoading.value = true;
  try {
    const res = await authApi.loginByPhone(phoneForm);
    await establishAndRoute(res);
  } catch (err) {
    globalError.value = err?.message || '手机号登录失败';
    shakePanel();
  } finally {
    isLoading.value = false;
  }
}

async function submitWechatLogin() {
  const validationError = getFormValidationError();
  if (validationError) {
    globalError.value = validationError;
    shakePanel();
    return;
  }

  isLoading.value = true;
  try {
    const res = await authApi.loginByWechat({
      ...wechatForm,
      wechatOpenId: buildWechatIdentity(wechatForm),
    });
    await establishAndRoute(res);
  } catch (err) {
    globalError.value = err?.message || '微信登录失败';
    shakePanel();
  } finally {
    isLoading.value = false;
  }
}

async function submitRegistration() {
  const validationError = getFormValidationError();
  if (validationError) {
    globalError.value = validationError;
    shakePanel();
    return;
  }

  isLoading.value = true;
  try {
    const payload = {
      ...registerForm,
      loginType: activeMode.value,
      accountType: 'real',
      roleId: registerForm.roleId || null,
      roleCode: registerForm.roleCode || '',
    };
    const res = await authApi.register(payload);
    if (res?.pendingApproval || !res?.token) {
      ElMessage.success(res?.message || '注册申请已提交，等待管理员审批');
      flowType.value = 'login';
      activeMode.value = 'password';
      modeChosen.value = true;
      currentStep.value = 3;
      passwordForm.username = registerForm.username || registerForm.phone || '';
      passwordForm.password = '';
      return;
    }
    await establishAndRoute(res);
  } catch (err) {
    globalError.value = err?.message || '注册失败';
    shakePanel();
  } finally {
    isLoading.value = false;
  }
}

async function sendPhoneCode(target) {
  const phone = target === 'login' ? phoneForm.phone : registerForm.phone;
  if (!ensurePhone(phone)) {
    globalError.value = '请先输入正确手机号';
    return;
  }
  try {
    const result = await authApi.sendPhoneCode({ phone });
    if (result?.codeHint) {
      if (target === 'login') {
        phoneForm.code = result.codeHint;
      } else {
        registerForm.phoneCode = result.codeHint;
      }
    }
    ElMessage.success(result?.message || '验证码已发送');
    startCountdown(target);
    return result;
  } catch (err) {
    globalError.value = err?.message || '验证码发送失败';
    return null;
  }
}

function getPortalPose(lifted) {
  if (lifted) {
    return {
      heroY: -14,
      heroScale: 1,
      panelY: -10,
      panelScale: 1,
      beamOpacity: 0.66,
      beamScaleX: 0.96,
    };
  }

  return {
    heroY: -14,
    heroScale: 1,
    panelY: 80,
    panelScale: 0.996,
    beamOpacity: 0.34,
    beamScaleX: 0.74,
  };
}

function syncPortalPose(immediate = false) {
  if (!heroTitleEl.value || !panelEl.value || !beamEl.value) {
    return;
  }

  const pose = getPortalPose(isPortalLifted.value);

  if (immediate) {
    gsap.set(heroTitleEl.value, {
      y: pose.heroY,
      scale: pose.heroScale,
      opacity: 1,
      filter: 'blur(0px)',
      transformOrigin: '50% 50%',
    });
    gsap.set(panelEl.value, {
      y: pose.panelY,
      scale: pose.panelScale,
      opacity: 1,
      filter: 'blur(0px)',
      transformOrigin: '50% 50%',
    });
    gsap.set(beamEl.value, {
      opacity: pose.beamOpacity,
      scaleX: pose.beamScaleX,
      filter: 'blur(20px)',
      transformOrigin: '50% 50%',
    });
    return;
  }

  gsap.killTweensOf([heroTitleEl.value, panelEl.value, beamEl.value]);

  const tl = gsap.timeline({ defaults: { ease: 'expo.out' } });
  tl.to(heroTitleEl.value, {
    y: pose.heroY,
    scale: pose.heroScale,
    duration: 0.56,
    ease: 'power3.out',
  }, 0);
  tl.to(panelEl.value, {
    y: pose.panelY,
    scale: pose.panelScale,
    duration: 0.62,
    ease: 'power3.out',
  }, 0.02);
  tl.to(beamEl.value, {
    opacity: pose.beamOpacity,
    scaleX: pose.beamScaleX,
    duration: 0.44,
    ease: 'power2.out',
  }, 0.06);
}



function onForgot() {
  globalError.value = '当前版本请联系治理管理员重置密码';
}

function shakePanel() {
  gsap.fromTo(panelEl.value, { x: -8 }, { x: 0, duration: 0.34, ease: 'power3.out' });
}

function playCinematicSuccess() {
  return new Promise((resolve) => {
    gsap.timeline({ onComplete: resolve })
      .to(beamEl.value, {
        opacity: 1,
        scaleX: 1.14,
        filter: 'blur(20px) brightness(1.3)',
        duration: 0.5,
        ease: 'power3.inOut',
      })
      .to(heroTitleEl.value, {
        y: -8,
        scale: 1,
        duration: 0.5,
        ease: 'power2.out',
      }, 0)
      .to(panelEl.value, {
        y: 10,
        opacity: 0,
        duration: 0.4,
        ease: 'power2.out',
      }, 0);
  });
}

async function handlePrimaryAction() {
  globalError.value = '';

  if (currentStep.value === 1) {
    goToStep(2);
    return;
  }

  if (currentStep.value === 2) {
    goToStep(3);
    return;
  }

  if (currentStep.value === 3) {
    const validationError = getFormValidationError();
    if (validationError) {
      globalError.value = validationError;
      shakePanel();
      return;
    }
    goToStep(4);
    return;
  }

  if (flowType.value === 'register') {
    await submitRegistration();
    return;
  }

  await submitPasswordLogin();
}

watch([flowType, activeMode], async () => {
  registerForm.loginType = activeMode.value;
  globalError.value = '';
  await nextTick();
  syncStepHeight();
});

watch(
  () => selectedRegisterRole.value,
  (value) => {
    syncRegisterRoleSelection(value);
  }
);

watch(isPortalLifted, async () => {
  await nextTick();
  syncPortalPose();
});

let viewportFrame = 0;

function handleViewportChange() {
  if (viewportFrame) {
    window.cancelAnimationFrame(viewportFrame);
  }

  viewportFrame = window.requestAnimationFrame(async () => {
    viewportFrame = 0;
    await nextTick();
    syncPortalPose(true);
    syncStepHeight(true);
  });
}

onMounted(async () => {
  refreshCaptcha();

  const cached = localStorage.getItem('login_cache');
  if (cached) {
    try {
      const { username, password } = JSON.parse(cached);
      passwordForm.username = username || '';
      passwordForm.password = password || '';
    } catch {
    }
  }

  try {
    const companyIdFromQuery = Number(route.query.companyId || 0);
    const inviteCodeFromQuery = String(route.query.inviteCode || '').trim();
    if (inviteCodeFromQuery) {
      registerForm.inviteCode = inviteCodeFromQuery;
    }
    const roleResult = companyIdFromQuery > 0
      ? await authApi.getPublicRoles(companyIdFromQuery)
      : null;
    const result = await authApi.getRegistrationOptions(
      companyIdFromQuery > 0 ? companyIdFromQuery : undefined,
      inviteCodeFromQuery || undefined
    );
    const roleOptions = Array.isArray(roleResult) && roleResult.length > 0 ? roleResult : result?.identities;
    if (result?.companyName) {
      registerForm.companyName = result.companyName;
    }
    registrationOptions.identities = normalizeOptions(result?.identities, DEFAULT_IDENTITIES);
    registrationOptions.organizations = normalizeOptions(result?.organizations, DEFAULT_ORGANIZATIONS);
    registrationOptions.demoAccounts = Array.isArray(result?.demoAccounts) && result.demoAccounts.length > 0
      ? result.demoAccounts
      : [];
    if (Array.isArray(roleOptions) && roleOptions.length > 0) {
      registrationOptions.identities = normalizeOptions(roleOptions, registrationOptions.identities);
    }
    const defaultRole = registrationOptions.identities[0];
    registerForm.roleCode = defaultRole?.code || '';
    registerForm.roleId = defaultRole?.id || null;
    selectedRegisterRole.value = String(defaultRole?.id || defaultRole?.code || '');
  } catch {
    registrationOptions.identities = [...DEFAULT_IDENTITIES];
    registrationOptions.organizations = [...DEFAULT_ORGANIZATIONS];
    registrationOptions.demoAccounts = [];
    registerForm.roleCode = registrationOptions.identities[0]?.code || '';
    registerForm.roleId = registrationOptions.identities[0]?.id || null;
    selectedRegisterRole.value = String(registerForm.roleId || registerForm.roleCode || '');
  }

  const initialPose = getPortalPose(false);

  gsap.set(heroTitleEl.value, {
    opacity: 0,
    y: initialPose.heroY + 12,
    scale: initialPose.heroScale,
    filter: 'blur(8px)',
    transformOrigin: '50% 50%',
  });
  gsap.set(panelEl.value, {
    opacity: 0,
    y: initialPose.panelY + 18,
    scale: initialPose.panelScale,
    filter: 'blur(10px)',
    transformOrigin: '50% 50%',
  });
  gsap.set(beamEl.value, {
    opacity: 0,
    scaleX: 0.38,
    filter: 'blur(22px)',
    transformOrigin: '50% 50%',
  });

  gsap.timeline({ defaults: { ease: 'expo.out' } })
    .to(heroTitleEl.value, {
      opacity: 1,
      y: initialPose.heroY,
      scale: initialPose.heroScale,
      filter: 'blur(0px)',
      duration: 0.58,
      ease: 'power3.out',
    })
    .to(panelEl.value, {
      opacity: 1,
      y: initialPose.panelY,
      scale: initialPose.panelScale,
      filter: 'blur(0px)',
      duration: 0.62,
      ease: 'power3.out',
    }, 0.08)
    .to(beamEl.value, {
      opacity: initialPose.beamOpacity,
      scaleX: initialPose.beamScaleX,
      duration: 0.46,
      ease: 'power2.out',
    }, 0.18);

  await nextTick();
  syncStepHeight(true);

  window.addEventListener('resize', handleViewportChange, { passive: true });
  window.addEventListener('orientationchange', handleViewportChange);
});

onBeforeUnmount(() => {
  window.removeEventListener('resize', handleViewportChange);
  window.removeEventListener('orientationchange', handleViewportChange);

  if (viewportFrame) {
    window.cancelAnimationFrame(viewportFrame);
    viewportFrame = 0;
  }
});
</script>

<style scoped>
.login-stage {
  position: fixed;
  inset: 0;
  min-height: 100vh;
  min-height: 100dvh;
  overflow-x: hidden;
  overflow-y: auto;
  overscroll-behavior-y: contain;
  -webkit-overflow-scrolling: touch;
  background: #03060b;
}

.atmosphere {
  position: absolute;
  inset: 0;
  pointer-events: none;
}

.atmosphere-grid {
  background:
    radial-gradient(circle at 18% 22%, rgba(109, 149, 255, 0.28), transparent 28%),
    radial-gradient(circle at 80% 16%, rgba(217, 231, 255, 0.12), transparent 22%),
    linear-gradient(rgba(255,255,255,0.05) 1px, transparent 1px),
    linear-gradient(90deg, rgba(255,255,255,0.05) 1px, transparent 1px);
  background-size: auto, auto, 86px 86px, 86px 86px;
  opacity: 0.25;
}

.atmosphere-noise {
  inset: -18%;
  opacity: 0.035;
  mix-blend-mode: screen;
  background-image: radial-gradient(rgba(255,255,255,0.18) 0.7px, transparent 0.7px);
  background-size: 4px 4px;
  animation: noiseShift 2.8s steps(4) infinite;
}

.atmosphere-vignette {
  background: radial-gradient(circle at center, transparent 0%, rgba(0,0,0,0.26) 44%, rgba(0,0,0,0.92) 100%);
}

.atmosphere-aurora {
  background:
    radial-gradient(circle at 50% 8%, rgba(123, 171, 255, 0.2), transparent 24%),
    conic-gradient(from 180deg at 50% 0%, rgba(69, 117, 255, 0.14), rgba(0, 0, 0, 0) 34%, rgba(94, 214, 255, 0.14) 62%, rgba(0, 0, 0, 0) 100%);
  opacity: 0.62;
}

.atmosphere-scanline {
  opacity: 0.06;
  background: linear-gradient(180deg, rgba(255,255,255,0.16) 0, rgba(255,255,255,0.02) 1px, transparent 2px, transparent 6px);
  background-size: 100% 6px;
  mix-blend-mode: soft-light;
}

.cinema-halo {
  position: absolute;
  width: 38vw;
  height: 38vw;
  border-radius: 50%;
  filter: blur(56px);
  pointer-events: none;
  opacity: 0.26;
}

.cinema-halo-left {
  top: 12%;
  left: -8%;
  background: radial-gradient(circle, rgba(66, 113, 255, 0.34), transparent 66%);
}

.cinema-halo-right {
  right: -6%;
  bottom: 10%;
  background: radial-gradient(circle, rgba(198, 222, 255, 0.18), transparent 64%);
}

.stage-center {
  --stage-block-padding: clamp(20px, 3vh, 32px);
  --stage-inline-padding: clamp(16px, 2.8vw, 28px);
  position: relative;
  z-index: 1;
  min-height: 100vh;
  min-height: 100dvh;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: clamp(14px, 2vh, 22px);
  width: 100%;
  box-sizing: border-box;
  padding:
    calc(env(safe-area-inset-top, 0px) + var(--stage-block-padding))
    calc(env(safe-area-inset-right, 0px) + var(--stage-inline-padding))
    calc(env(safe-area-inset-bottom, 0px) + var(--stage-block-padding))
    calc(env(safe-area-inset-left, 0px) + var(--stage-inline-padding));
}

.portal-shell {
  position: relative;
  width: min(1080px, 100%);
  max-width: 1080px;
  min-height: min(860px, calc(100vh - 48px));
  min-height: min(860px, calc(100dvh - (var(--stage-block-padding) * 2) - env(safe-area-inset-top, 0px) - env(safe-area-inset-bottom, 0px)));
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: clamp(10px, 1.4vh, 18px);
  padding: 0;
}

.portal-shell[data-lifted] {
  gap: 8px;
}

.portal-shell-edge {
  position: absolute;
  inset: 8% 8% 10%;
  border-radius: 40px;
  border: 1px solid rgba(169, 196, 255, 0.08);
  box-shadow: inset 0 0 0 1px rgba(255,255,255,0.03), 0 0 0 1px rgba(12, 18, 30, 0.3);
  mask-image: linear-gradient(180deg, transparent 0%, rgba(0,0,0,0.9) 22%, rgba(0,0,0,0.9) 78%, transparent 100%);
  pointer-events: none;
}

.hero-marquee {
  position: relative;
  width: min(100%, 920px);
  display: grid;
  justify-items: center;
  text-align: center;
  will-change: transform, opacity;
  min-width: 0;
  gap: clamp(4px, 0.7vh, 8px);
  padding: 0 clamp(6px, 1.4vw, 18px) clamp(16px, 2.2vh, 22px);
  overflow: visible;
}

.hero-kicker {
  color: rgba(220, 233, 255, 0.84);
  font-size: 12px;
  font-weight: 800;
  letter-spacing: 0.34em;
  text-transform: uppercase;
}

.title-container {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 16px;
  margin: 6px 0;
}

.login-logo {
  width: clamp(40px, 6vw, 64px);
  height: clamp(40px, 6vw, 64px);
  object-fit: contain;
  filter: drop-shadow(0 0 16px rgba(59, 130, 246, 0.4));
  animation: logoFloat 6s ease-in-out infinite;
}

@keyframes logoFloat {
  0%, 100% { transform: translateY(0) scale(1); }
  50% { transform: translateY(-4px) scale(1.02); }
}

.login-title {
  margin: 0;
  max-width: 100%;
  font-size: clamp(38px, 6.5vw, 82px);
  font-weight: 900;
  letter-spacing: -0.05em;
  line-height: 1.05;
  padding: 0 0 0.12em 0;
  text-wrap: balance;
  color: transparent;
  background: 
    url('data:image/svg+xml;utf8,<svg width="24" height="24" viewBox="0 0 24 24" xmlns="http://www.w3.org/2000/svg"><circle cx="12" cy="12" r="2" fill="rgba(255,255,255,0.2)"/></svg>'),
    linear-gradient(135deg, #ffffff 0%, #edf4ff 35%, #93c5fd 75%, #3b82f6 100%);
  background-size: 16px 16px, 100% 100%;
  -webkit-background-clip: text;
  background-clip: text;
  text-shadow: 0 8px 32px rgba(0, 0, 0, 0.3);
  overflow: visible;
}

.hero-subtitle {
  margin: 0;
  max-width: min(100%, 28ch);
  color: #e6f0ff;
  font-size: clamp(14px, 1.5vw, 16px);
  font-weight: 700;
  letter-spacing: 0.18em;
  text-transform: uppercase;
  text-wrap: balance;
}

.story-kicker,
.brand-chip,
.panel-mini-meta {
  display: inline-flex;
  align-items: center;
  min-height: 34px;
  padding: 0 14px;
  border-radius: 999px;
  border: 1px solid rgba(113, 170, 255, 0.24);
  background: rgba(20, 30, 48, 0.72);
  color: #d8e6ff;
  font-size: 11px;
  font-weight: 700;
  letter-spacing: 0.18em;
  text-transform: uppercase;
}

.beam-track {
  position: absolute;
  left: 50%;
  bottom: clamp(-22px, -2.1vh, -12px);
  width: min(58vw, 560px);
  max-width: calc(100% - 28px);
  height: 60px;
  transform: translateX(-50%);
  border-radius: 999px;
  background: linear-gradient(90deg, rgba(72, 119, 255, 0), rgba(105, 208, 255, 0.68), rgba(72, 119, 255, 0));
  filter: blur(22px);
  opacity: 0.72;
}

.wizard-shell {
  position: relative;
  overflow: hidden;
  margin-top: 0;
  width: min(780px, 100%);
  min-width: 0;
  max-height: min(680px, calc(100vh - 220px));
  max-height: min(720px, calc(100dvh - 220px - env(safe-area-inset-top, 0px) - env(safe-area-inset-bottom, 0px)));
  display: flex;
  flex-direction: column;
  padding: clamp(18px, 2.6vh, 22px) clamp(18px, 2.8vw, 26px) clamp(20px, 3vh, 24px);
  border-radius: clamp(24px, 3vw, 32px);
  border: 1px solid rgba(186, 210, 255, 0.18);
  background:
    radial-gradient(circle at 12% 0%, rgba(120, 166, 255, 0.12), transparent 28%),
    linear-gradient(155deg, rgba(11, 17, 29, 0.985), rgba(6, 10, 18, 0.96));
  box-shadow: 0 28px 82px rgba(0,0,0,0.54), inset 0 1px 0 rgba(255,255,255,0.08), inset 0 -20px 34px rgba(0,0,0,0.14);
  backdrop-filter: blur(12px) saturate(112%);
}

.wizard-shell::after {
  content: '';
  position: absolute;
  inset: 0;
  border-radius: inherit;
  border: 1px solid rgba(255,255,255,0.04);
  pointer-events: none;
}

.wizard-sheen {
  position: absolute;
  top: -24%;
  left: -8%;
  width: 48%;
  height: 160px;
  background: linear-gradient(135deg, rgba(255,255,255,0.18), rgba(255,255,255,0));
  filter: blur(12px);
  opacity: 0.3;
  pointer-events: none;
}

.wizard-topbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  flex-wrap: wrap;
  gap: 10px 12px;
}

.step-indicator-row {
  display: flex;
  align-items: center;
  width: 100%;
  margin-top: 16px;
  padding: 16px 0 8px;
}

.step-indicator {
  display: grid;
  justify-items: center;
  gap: 10px;
  background: transparent;
  border: none;
  color: #92a4c4;
  padding: 0;
}

.step-indicator.disabled {
  cursor: default;
}

.step-indicator:not(.disabled) {
  cursor: pointer;
}

.step-indicator-inner {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 42px;
  height: 42px;
  border-radius: 999px;
  border: 1px solid rgba(169, 196, 255, 0.16);
  background: rgba(255,255,255,0.04);
  color: #c7d6ee;
  font-size: 14px;
  font-weight: 700;
  transition: all 0.28s ease;
}

.step-indicator-copy {
  font-size: 12px;
  letter-spacing: 0.1em;
  text-transform: uppercase;
}

.step-indicator.active .step-indicator-inner,
.step-indicator.complete .step-indicator-inner {
  background: linear-gradient(135deg, rgba(240, 246, 255, 0.18), rgba(95, 135, 255, 0.3));
  border-color: rgba(169, 196, 255, 0.32);
  color: #f7fbff;
  box-shadow: 0 12px 24px rgba(58, 92, 194, 0.18);
}

.step-indicator.active .step-indicator-copy,
.step-indicator.complete .step-indicator-copy {
  color: #e5edfb;
}

.active-dot {
  width: 12px;
  height: 12px;
  border-radius: 999px;
  background: #ffffff;
}

.check-icon {
  width: 16px;
  height: 16px;
}

.step-connector {
  position: relative;
  flex: 1;
  height: 2px;
  margin: 0 12px 22px;
  border-radius: 999px;
  background: rgba(113, 128, 154, 0.46);
  overflow: hidden;
}

.step-connector-inner {
  position: absolute;
  inset: 0;
  width: 0;
  background: linear-gradient(90deg, #cfdfff, #5f87ff 56%, #7ad2ff 100%);
  transition: width 0.38s ease;
}

.step-connector-inner.complete {
  width: 100%;
}

.wizard-head {
  margin-top: 8px;
}

.wizard-head strong {
  display: block;
  color: #f7fbff;
  font-size: clamp(22px, 2.6vw, 26px);
  line-height: 1.1;
}

.wizard-head p {
  margin: 8px 0 0;
  color: #c3d4ee;
  font-size: 14px;
  line-height: 1.7;
  min-height: 0;
}

.wizard-viewport {
  position: relative;
  flex: 1 1 auto;
  min-height: 0;
  overflow-x: hidden;
  overflow-y: auto;
  margin-top: clamp(12px, 1.6vh, 16px);
  max-height: min(380px, 40vh);
  max-height: min(420px, 42dvh);
  padding-right: 2px;
  scrollbar-width: none;
  overscroll-behavior: contain;
}

.wizard-viewport::-webkit-scrollbar {
  display: none;
}

.step-screen {
  width: 100%;
  padding-bottom: 2px;
}

.step-layout {
  display: grid;
  gap: 16px;
}

.cluster-title,
.review-hero span,
.form-stage-meta span {
  color: #89a6dd;
  font-size: 11px;
  font-weight: 800;
  letter-spacing: 0.18em;
  text-transform: uppercase;
}

.choice-cluster,
.review-card,
.review-note,
.flow-card,
.mode-card {
  border-radius: 24px;
  border: 1px solid rgba(186, 210, 255, 0.16);
  background: linear-gradient(160deg, rgba(12, 18, 31, 0.96), rgba(7, 12, 21, 0.88));
}

.flow-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(min(220px, 100%), 1fr));
  gap: 14px;
  margin-top: 12px;
}

.flow-card,
.mode-card {
  width: 100%;
  text-align: left;
  padding: 18px;
  transition: transform 0.22s ease, border-color 0.22s ease, background 0.22s ease, box-shadow 0.22s ease;
}

.flow-card,
.mode-card,
.back-button,
.next-button,
.assist-button,
.link-button {
  border: none;
}

.flow-card:hover,
.mode-card:hover {
  transform: translateY(-2px);
}

.flow-card.active,
.mode-card.active {
  border-color: rgba(186, 210, 255, 0.34);
  background: linear-gradient(160deg, rgba(18, 29, 48, 0.96), rgba(10, 17, 29, 0.82));
  box-shadow: 0 22px 38px rgba(11, 26, 52, 0.32);
}

.flow-accent {
  display: inline-flex;
  align-items: center;
  min-height: 30px;
  padding: 0 12px;
  border-radius: 999px;
  background: rgba(255,255,255,0.04);
  color: #b2c7ec;
  font-size: 10px;
  font-weight: 800;
  letter-spacing: 0.16em;
}

.flow-card strong,
.mode-copy strong,
.review-hero strong,
.review-card strong,
.form-stage-meta strong {
  display: block;
  color: #f6fbff;
}

.flow-card strong,
.mode-copy strong {
  margin-top: 14px;
  font-size: 22px;
}

.flow-card p,
.mode-copy p,
.form-stage-meta p,
.review-hero p,
.review-note span {
  margin: 10px 0 0;
  color: #c1d1e8;
  line-height: 1.75;
}

.mode-grid {
  display: grid;
  gap: 12px;
  margin-top: 12px;
}

.mode-card {
  display: grid;
  grid-template-columns: minmax(48px, 56px) minmax(0, 1fr);
  gap: 14px;
  align-items: center;
}

.mode-icon-wrap {
  width: 56px;
  height: 56px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  border-radius: 18px;
  border: 1px solid rgba(169, 196, 255, 0.12);
  background: rgba(255,255,255,0.04);
  color: #d6e4fb;
  font-size: 24px;
}

.step-layout-form {
  gap: 22px;
}

.form-stage-meta {
  padding: 16px 18px;
  border-radius: 22px;
  border: 1px solid rgba(169, 196, 255, 0.12);
  background: rgba(255,255,255,0.03);
}

.form-stage-meta strong {
  margin-top: 10px;
  font-size: 24px;
}

.login-form {
  display: grid;
  gap: 16px;
}

.field-group {
  display: grid;
  gap: 8px;
  max-width: 480px;
}

.field-group label,
.remember-row,
.link-button,
.review-note em {
  color: #e3ecfb;
}

.compact-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(min(220px, 100%), 1fr));
  gap: 14px;
  max-width: 480px;
}

.field-input {
  width: 100%;
  max-width: 480px;
  height: clamp(48px, 6.6vh, 54px);
  padding: 0 16px;
  border-radius: 16px;
  border: 1px solid rgba(186, 210, 255, 0.18);
  background: rgba(14, 22, 36, 0.86);
  color: #f6f8fe;
  outline: none;
  box-shadow: inset 0 1px 0 rgba(255,255,255,0.04);
  transition: border-color 0.18s ease, box-shadow 0.18s ease, background 0.18s ease;
  box-sizing: border-box;
}

.field-input:focus {
  border-color: rgba(110, 166, 255, 0.62);
  box-shadow: 0 0 0 4px rgba(69, 111, 255, 0.12);
}

.field-input::placeholder {
  color: #9aabca;
}

.field-select {
  appearance: none;
}

.password-wrap {
  display: grid;
  grid-template-columns: 1fr auto;
  gap: 12px;
  max-width: 480px;
}

.assist-button {
  min-width: 108px;
  padding: 0 16px;
  height: clamp(48px, 6.6vh, 54px);
  border-radius: 16px;
  background: rgba(255,255,255,0.1);
  color: #f0f5ff;
  display: flex;
  align-items: center;
  justify-content: center;
  box-sizing: border-box;
}

.assist-button:disabled {
  opacity: 0.6;
}

.form-options {
  display: flex;
  align-items: center;
  justify-content: space-between;
  max-width: 480px;
  margin-top: 8px;
  margin-bottom: 8px;
}

.identity-support {
  display: flex;
  flex-direction: column;
  align-items: flex-start;
  gap: 10px;
  width: 100%;
}

.identity-support-label {
  color: #8ea3c8;
  font-size: 12px;
  font-weight: 700;
}

.identity-select-row {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 8px;
  width: 100%;
}

@media (max-width: 820px) {
  .identity-select-row {
    grid-template-columns: 1fr;
  }
}

.remember-row {
  display: inline-flex;
  align-items: center;
  gap: 8px;
}

.remember-row input {
  accent-color: #6aa6ff;
}

.link-button {
  padding: 0;
  background: transparent;
}

.step-layout-review {
  gap: 18px;
}

.review-hero {
  padding: 18px 20px;
  border-radius: 24px;
  border: 1px solid rgba(169, 196, 255, 0.12);
  background: linear-gradient(160deg, rgba(17, 29, 49, 0.92), rgba(8, 14, 24, 0.76));
}

.review-hero strong {
  margin-top: 10px;
  font-size: 28px;
}

.review-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(min(220px, 100%), 1fr));
  gap: 12px;
}

.review-card {
  padding: 16px 18px;
}

.review-card span {
  color: #b9cbea;
  font-size: 11px;
  font-weight: 800;
  letter-spacing: 0.12em;
  text-transform: uppercase;
}

.review-card strong {
  margin-top: 10px;
  font-size: 20px;
  line-height: 1.45;
}

.review-note {
  display: grid;
  gap: 8px;
  padding: 16px 18px;
}

.review-note em {
  font-style: normal;
  font-weight: 700;
}

.global-error {
  margin-top: 18px;
  padding: 12px 14px;
  border-radius: 14px;
  border: 1px solid rgba(255, 107, 107, 0.22);
  background: rgba(255, 107, 107, 0.08);
  color: #ffc4c4;
}

.wizard-footer {
  margin-top: 16px;
  display: flex;
  justify-content: space-between;
  gap: 12px;
  padding-top: 16px;
  border-top: 1px solid rgba(169, 196, 255, 0.12);
  background: linear-gradient(180deg, rgba(8, 14, 24, 0), rgba(8, 14, 24, 0.72) 22%, rgba(8, 14, 24, 0.98) 100%);
}

.back-button,
.next-button {
  min-height: 58px;
  border-radius: 18px;
  padding: 0 26px;
  font-size: 15px;
  font-weight: 800;
  letter-spacing: 0.08em;
  transition: transform 0.2s ease, box-shadow 0.2s ease, opacity 0.2s ease;
}

.back-button {
  background: rgba(255,255,255,0.04);
  color: #d8e6ff;
  border: 1px solid rgba(169, 196, 255, 0.12);
}

.next-button {
  margin-left: auto;
  background: linear-gradient(135deg, #eef5ff 0%, #9fc0ff 18%, #6e95ff 58%, #3f67d4 100%);
  color: #06111f;
  box-shadow: 0 18px 34px rgba(37, 92, 255, 0.34);
  min-width: 196px;
}

.back-button:hover,
.next-button:hover:not(:disabled) {
  transform: translateY(-2px);
}

.next-button:hover:not(:disabled) {
  box-shadow: 0 24px 48px rgba(37, 92, 255, 0.42);
}

.next-button:disabled {
  opacity: 0.62;
}

.btn-loading {
  display: inline-flex;
  align-items: center;
  gap: 10px;
}

.spinner {
  width: 16px;
  height: 16px;
  border: 2px solid rgba(6, 17, 31, 0.22);
  border-top-color: rgba(6, 17, 31, 0.9);
  border-radius: 50%;
  animation: spin 0.8s linear infinite;
}

.stage-footer {
  position: relative;
  margin: 0 auto 16px;
  z-index: 1;
  text-align: center;
  color: #68758c;
  font-size: 12px;
  letter-spacing: 0.08em;
  text-transform: uppercase;
}

.field-error-enter-active,
.field-error-leave-active {
  transition: all 0.2s ease;
}

.field-error-enter-from,
.field-error-leave-to {
  opacity: 0;
  transform: translateY(-4px);
}

@keyframes noiseShift {
  0% { transform: translate3d(0, 0, 0); }
  100% { transform: translate3d(1.4%, -1.2%, 0); }
}

@keyframes spin {
  to { transform: rotate(360deg); }
}

@media (max-width: 980px) {
  .portal-shell {
    width: min(100%, 820px);
  }

  .login-title {
    font-size: clamp(58px, 10vw, 126px);
  }

  .wizard-shell {
    width: min(100%, 720px);
    max-height: min(72vh, 740px);
    max-height: min(740px, calc(100dvh - 200px));
  }

  .flow-grid,
  .review-grid,
  .compact-grid {
    grid-template-columns: 1fr;
  }

  .mode-card {
    grid-template-columns: 1fr;
  }

  .mode-icon-wrap {
    width: 48px;
    height: 48px;
  }
}

@media (max-width: 768px) {
  .stage-center {
    justify-content: flex-start;
    gap: 14px;
    --stage-block-padding: 18px;
    --stage-inline-padding: 16px;
  }

  .portal-shell {
    min-height: auto;
    width: 100%;
  }

  .hero-kicker {
    font-size: 11px;
    letter-spacing: 0.24em;
  }

  .portal-shell-edge {
    inset: 10% 0 8%;
    border-radius: 28px;
  }

  .login-title {
    font-size: clamp(46px, 12vw, 82px);
    line-height: 1.03;
  }

  .hero-subtitle {
    font-size: 13px;
    letter-spacing: 0.12em;
  }

  .wizard-shell {
    padding: 16px;
    border-radius: 26px;
    max-height: none;
  }

  .wizard-viewport {
    max-height: min(46vh, 420px);
    max-height: min(46dvh, 420px);
  }

  .wizard-topbar,
  .wizard-footer,
  .form-options,
  .password-wrap {
    flex-direction: column;
    align-items: stretch;
  }

  .password-wrap {
    display: grid;
    grid-template-columns: 1fr;
  }

  .field-input,
  .field-group,
  .compact-grid,
  .password-wrap,
  .form-options {
    max-width: 100%;
  }

  .step-indicator-row {
    gap: 8px;
  }

  .step-indicator-copy {
    display: none;
  }

  .step-connector {
    margin: 0 2px;
  }

  .wizard-head strong,
  .review-hero strong,
  .form-stage-meta strong {
    font-size: 24px;
  }

  .next-button,
  .back-button {
    width: 100%;
  }

  .stage-footer {
    width: calc(100vw - 32px);
    font-size: 11px;
  }

  .next-button {
    min-width: 100%;
  }
}

@media (max-width: 480px) {
  .stage-center {
    --stage-block-padding: 16px;
    --stage-inline-padding: 12px;
  }

  .hero-marquee {
    padding-inline: 4px;
    padding-bottom: 16px;
  }

  .login-title {
    font-size: clamp(38px, 13.8vw, 58px);
    letter-spacing: -0.06em;
  }

  .hero-subtitle {
    font-size: 12px;
    letter-spacing: 0.12em;
  }

  .wizard-shell {
    padding: 14px;
    border-radius: 22px;
  }

  .story-kicker,
  .brand-chip,
  .panel-mini-meta {
    min-height: 30px;
    padding: 0 12px;
    font-size: 10px;
    letter-spacing: 0.12em;
  }

  .step-indicator-inner {
    width: 38px;
    height: 38px;
  }

  .flow-card,
  .mode-card,
  .review-card,
  .review-note {
    padding: 14px;
  }

  .flow-card strong,
  .mode-copy strong {
    font-size: 18px;
  }

  .wizard-head strong,
  .review-hero strong,
  .form-stage-meta strong {
    font-size: 22px;
  }

  .back-button,
  .next-button {
    min-height: 52px;
    padding: 0 20px;
  }
}

@media (max-height: 860px) {
  .stage-center {
    justify-content: flex-start;
  }

  .portal-shell {
    min-height: auto;
  }

  .login-title {
    font-size: clamp(52px, 8.2vh, 136px);
  }

  .wizard-shell {
    max-height: min(640px, calc(100vh - 190px));
    max-height: min(640px, calc(100dvh - 190px));
  }

  .wizard-viewport {
    max-height: min(40vh, 400px);
    max-height: min(40dvh, 400px);
  }
}

@media (max-height: 720px) {
  .stage-center {
    gap: 12px;
  }

  .hero-marquee {
    padding-bottom: 18px;
  }

  .login-title {
    font-size: clamp(42px, 7.8vh, 96px);
  }

  .hero-subtitle {
    font-size: 12px;
  }

  .wizard-shell {
    padding: 16px;
    border-radius: 24px;
  }

  .wizard-head p {
    line-height: 1.6;
  }

  .wizard-viewport {
    max-height: min(46vh, 320px);
    max-height: min(46dvh, 320px);
  }

  .back-button,
  .next-button {
    min-height: 52px;
  }
}
</style>
