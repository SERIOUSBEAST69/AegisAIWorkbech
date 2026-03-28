<template>
  <el-card class="card-glass" v-loading="loading">
    <div class="card-header">个人资料</div>
    <el-form :model="userInfo" label-width="120px">
      <el-form-item label="用户名">
        <el-input v-model="userInfo.username" disabled />
      </el-form-item>
      <el-form-item label="真实姓名">
        <el-input v-model="userInfo.realName" />
      </el-form-item>
      <el-form-item label="显示名称">
        <el-input v-model="userInfo.nickname" />
      </el-form-item>
      <el-form-item label="邮箱">
        <el-input v-model="userInfo.email" />
      </el-form-item>
      <el-form-item label="电话">
        <el-input v-model="userInfo.phone" />
      </el-form-item>
      <el-form-item label="部门">
        <el-input v-model="userInfo.department" />
      </el-form-item>
      <el-form-item label="角色">
        <el-input v-model="userInfo.role" disabled />
      </el-form-item>
      <el-form-item label="最后登录时间">
        <el-input v-model="userInfo.lastLogin" disabled />
      </el-form-item>
      <el-form-item>
        <el-button type="primary" @click="saveProfile">保存修改</el-button>
        <el-button @click="resetForm">重置</el-button>
      </el-form-item>
    </el-form>
  </el-card>
</template>

<script setup>
import { computed, reactive, onMounted } from 'vue';
import { ElMessage } from 'element-plus';
import { useUserStore } from '../store/user';

const userStore = useUserStore();
const loading = computed(() => userStore.loading);
const userInfo = reactive({
  username: '',
  realName: '',
  nickname: '',
  email: '',
  phone: '',
  department: '',
  role: '',
  lastLogin: ''
});

function syncForm(profile) {
  userInfo.username = profile?.username || '';
  userInfo.realName = profile?.realName || '';
  userInfo.nickname = profile?.nickname || '';
  userInfo.email = profile?.email || '';
  userInfo.phone = profile?.phone || '';
  userInfo.department = profile?.department || '';
  userInfo.role = profile?.roleName || '未分配角色';
  userInfo.lastLogin = profile?.lastActiveAt || '';
}

async function saveProfile() {
  try {
    await userStore.updateProfile({
      nickname: userInfo.nickname,
      realName: userInfo.realName,
      email: userInfo.email,
      phone: userInfo.phone,
      department: userInfo.department
    });
    syncForm(userStore.userInfo);
    ElMessage.success('个人资料保存成功');
  } catch (error) {
    ElMessage.error(error?.message || '个人资料保存失败');
  }
}

const resetForm = () => {
  syncForm(userStore.userInfo);
  ElMessage.info('表单已重置');
};

onMounted(async () => {
  try {
    await userStore.fetchProfile();
    syncForm(userStore.userInfo);
  } catch (error) {
    ElMessage.error(error?.message || '用户资料加载失败');
  }
});
</script>

<style scoped>
.card-header {
  font-weight: 600;
  margin-bottom: 16px;
  color: var(--color-text);
  font-size: 18px;
}
</style>