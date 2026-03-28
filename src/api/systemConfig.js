import request from './request';

export const SYSTEM_CONFIG_KEYS = {
  systemName: 'basic.system.name',
  apiUrl: 'basic.api.url',
  backupFrequency: 'basic.backup.frequency',
  passwordPolicy: 'security.password.policy',
  loginAttempts: 'security.login.attempts',
  sessionTimeout: 'security.session.timeout',
  notificationEmail: 'notification.email.enabled',
  notificationSms: 'notification.sms.enabled',
  notificationSystem: 'notification.system.enabled',
};

function toRequest(configKey, configValue, description) {
  return {
    configKey,
    configValue: String(configValue),
    description,
  };
}

export const systemConfigApi = {
  list() {
    return request.get('/system/config');
  },

  create(configKey, configValue, description) {
    return request.post('/system/config', toRequest(configKey, configValue, description));
  },

  update(configKey, configValue, description) {
    return request.put(`/system/config/${encodeURIComponent(configKey)}`, toRequest(configKey, configValue, description));
  },

  async upsert(configKey, configValue, description) {
    try {
      return await this.update(configKey, configValue, description);
    } catch (error) {
      if (error?.code === 40400 || error?.status === 404) {
        return await this.create(configKey, configValue, description);
      }
      throw error;
    }
  },
};
