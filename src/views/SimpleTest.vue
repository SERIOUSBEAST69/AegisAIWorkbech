<template>
  <div class="test-page">
    <h1>测试页面</h1>
    <p>这是一个测试页面，用于验证路由是否正常工作</p>
    <el-button type="primary" @click="goBack">返回首页</el-button>
  </div>
</template>

<script setup>
import { useRouter } from 'vue-router';

const router = useRouter();

const goBack = () => {
  router.push('/');
};
</script>

<style scoped>
.test-page {
  padding: 20px;
  text-align: center;
}

h1 {
  font-size: 24px;
  color: var(--color-text);
  margin-bottom: 20px;
}

p {
  font-size: 16px;
  color: var(--color-text-muted);
  margin-bottom: 30px;
}
</style>