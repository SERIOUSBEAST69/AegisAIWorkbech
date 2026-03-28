import request from './request';
import { getSessionUserFallback, shouldUseApiFallback } from './fallback';

export const userApi = {
  async getProfile() {
    try {
      return await request.get('/user/profile');
    } catch (error) {
      if (shouldUseApiFallback(error)) {
        return getSessionUserFallback() || {};
      }
      throw error;
    }
  },

  async updateProfile(payload) {
    const formData = new FormData();
    Object.entries(payload || {}).forEach(([key, value]) => {
      if (value !== undefined && value !== null && value !== '') {
        formData.append(key, value);
      }
    });

    try {
      return await request.put('/user/profile', formData, {
        headers: {
          'Content-Type': 'multipart/form-data',
        },
      });
    } catch (error) {
      if (shouldUseApiFallback(error)) {
        return getSessionUserFallback(payload) || { ...(payload || {}) };
      }
      throw error;
    }
  },

  changePassword(payload) {
    return request.post('/user/change-password', payload);
  },
};
