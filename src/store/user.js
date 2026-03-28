import { defineStore } from 'pinia';
import { authApi } from '../api/auth';
import { userApi } from '../api/user';
import { clearSession, getSession, isMockSession, setSession } from '../utils/auth';

function buildMockUser(session) {
  return {
    id: session.user?.id || 1,
    companyId: session.user?.companyId || 1,
    username: session.user?.username || 'demo-admin',
    nickname: session.user?.nickname || '演示管理员',
    realName: session.user?.realName || '演示管理员',
    email: session.user?.email || 'demo@aegis.workbench',
    phone: session.user?.phone || '13800138000',
    department: session.user?.department || '创新实验室',
    companyName: session.user?.companyName || 'Aegis 默认公司',
    roleName: session.user?.roleName || '演示管理员',
    roleCode: session.user?.roleCode || 'ADMIN',
    accountType: session.user?.accountType || 'demo',
    accountStatus: session.user?.accountStatus || 'active',
    lastActiveAt: new Date().toISOString(),
    avatar: session.user?.avatar || ''
  };
}

function mergeUser(...segments) {
  return segments.reduce((result, segment) => {
    if (!segment) return result;
    return { ...result, ...segment };
  }, {});
}

export const useUserStore = defineStore('user', {
  state: () => ({
    initialized: false,
    loading: false,
    userInfo: null,
    token: '',
    sessionMode: 'real'
  }),
  getters: {
    isAuthenticated: state => Boolean(state.token),
    isMockMode: state => state.sessionMode === 'mock',
    displayName: state => state.userInfo?.nickname || state.userInfo?.realName || state.userInfo?.username || '访客',
    avatar: state => state.userInfo?.avatar || '',
    roleName: state => state.userInfo?.roleName || state.userInfo?.roleCode || '未分配身份',
    identityLine: state => [state.userInfo?.roleName || state.userInfo?.roleCode, state.userInfo?.department].filter(Boolean).join(' · ') || '等待身份上下文'
  },
  actions: {
    persistSessionState() {
      if (!this.token) {
        return null;
      }
      return setSession({
        token: this.token,
        user: this.userInfo,
        mode: this.sessionMode
      });
    },
    applySession(sessionLike) {
      const session = setSession({
        token: sessionLike?.token,
        user: sessionLike?.user || null,
        mode: sessionLike?.mode || 'real'
      });
      this.token = session.token;
      this.sessionMode = session.mode;
      this.userInfo = session.user || null;
      return session;
    },
    setUser(info) {
      this.userInfo = info ? mergeUser(this.userInfo, info) : null;
      this.persistSessionState();
    },
    setToken(token) {
      this.token = token;
      this.persistSessionState();
    },
    setSessionMode(mode) {
      this.sessionMode = mode || 'real';
      this.persistSessionState();
    },
    reset() {
      this.userInfo = null;
      this.token = '';
      this.sessionMode = 'real';
    },
    hydrateFromSession() {
      const session = getSession();
      this.token = session?.token || '';
      this.sessionMode = session?.mode || 'real';
      this.userInfo = session?.user || null;
      return session;
    },
    async establishSession(sessionLike) {
      this.loading = true;
      try {
        const session = this.applySession(sessionLike);
        if (session.mode === 'mock') {
          this.userInfo = buildMockUser(session);
          this.persistSessionState();
          return this.userInfo;
        }

        const current = await authApi.getCurrentUser();
        this.userInfo = mergeUser(this.userInfo, current?.user);
        this.persistSessionState();
        return this.userInfo;
      } catch (error) {
        clearSession(error?.sessionExpired ? 'expired' : 'login-bootstrap-failed');
        this.reset();
        throw error;
      } finally {
        this.initialized = true;
        this.loading = false;
      }
    },
    async bootstrapSession() {
      this.loading = true;
      try {
        const session = this.hydrateFromSession();
        if (!session?.token) {
          this.reset();
          return null;
        }
        if (isMockSession()) {
          this.userInfo = buildMockUser(session);
          this.persistSessionState();
          return this.userInfo;
        }

        const current = await authApi.getCurrentUser();
        this.token = session.token;
        this.sessionMode = 'real';
        this.userInfo = mergeUser(this.userInfo, current?.user);
        this.persistSessionState();
        return this.userInfo;
      } catch (error) {
        clearSession(error?.sessionExpired ? 'expired' : 'bootstrap-failed');
        this.reset();
        return null;
      } finally {
        this.initialized = true;
        this.loading = false;
      }
    },
    async fetchProfile() {
      if (this.isMockMode) {
        return this.userInfo;
      }
      const profile = await userApi.getProfile();
      this.userInfo = mergeUser(this.userInfo, profile);
      this.persistSessionState();
      return this.userInfo;
    },
    async updateProfile(payload) {
      const profile = await userApi.updateProfile(payload);
      this.userInfo = mergeUser(this.userInfo, profile);
      this.persistSessionState();
      return this.userInfo;
    },
    async logout() {
      try {
        if (!this.isMockMode && this.token) {
          await authApi.logout();
        }
      } catch {
        // Ignore logout failures and always clear local session.
      }
      clearSession('manual-logout');
      this.reset();
    }
  }
});
