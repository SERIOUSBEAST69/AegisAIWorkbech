<template>
  <div class="test-page">
    <h1>测试页面</h1>
    <p>这是一个测试页面，用于验证路由是否正常工作</p>
  </div>
</template>

<script setup>
import { onMounted } from 'vue';

onMounted(() => {
  console.log('测试页面已加载');
});
</script>

<style scoped>
.test-page {
  padding: 20px;
}

h1 {
  color: #333;
  font-size: 24px;
  margin-bottom: 20px;
}

p {
  color: #666;
  font-size: 16px;
}
</style>