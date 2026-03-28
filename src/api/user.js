import request from './request';

export const userApi = {
  async getProfile() {
    return request.get('/user/profile');
  },

  async updateProfile(payload) {
    const formData = new FormData();
    Object.entries(payload || {}).forEach(([key, value]) => {
      if (value !== undefined && value !== null && value !== '') {
        formData.append(key, value);
      }
    });

    return request.put('/user/profile', formData, {
      headers: {
        'Content-Type': 'multipart/form-data',
      },
    });
  },

  changePassword(payload) {
    return request.post('/user/change-password', payload);
  },
};
