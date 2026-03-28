<template>
  <div class="page-grid">
    <el-card class="card-glass">
      <div class="card-header">数据主体权利工单</div>
      <el-form :inline="true" @submit.prevent ref="formRef" :model="form" :rules="rules">
        <el-form-item label="用户ID" prop="userId"><el-input v-model="form.userId" /></el-form-item>
        <el-form-item label="类型" prop="type">
          <el-select v-model="form.type" style="width:140px">
            <el-option label="查询" value="access" />
            <el-option label="导出" value="export" />
            <el-option label="删除" value="delete" />
          </el-select>
        </el-form-item>
        <el-form-item label="备注" prop="comment"><el-input v-model="form.comment" style="width:220px" /></el-form-item>
        <el-button type="primary" :loading="saving" @click="create">提交申请</el-button>
      </el-form>
      <el-table :data="list" class="page-table" style="margin-top:12px" v-loading="loading">
        <el-table-column prop="id" label="ID" width="250">
          <template #default="scope">
            <div class="cell nowrap">{{ scope.row.id }}</div>
          </template>
        </el-table-column>
        <el-table-column prop="userId" label="用户ID" />
        <el-table-column prop="type" label="类型" />
        <el-table-column prop="status" label="状态" />
        <el-table-column prop="comment" label="备注">
          <template #default="scope">
            <div class="cell">
              {{ getSafeComment(scope.row.comment || '') }}
            </div>
          </template>
        </el-table-column>
        <el-table-column prop="handlerId" label="处理人" />
        <el-table-column label="处理" width="220">
          <template #default="scope">
            <el-select v-model="scope.row.status" size="small" style="width:140px" @change="update(scope.row)">
              <el-option label="pending" value="pending" />
              <el-option label="processing" value="processing" />
              <el-option label="done" value="done" />
              <el-option label="rejected" value="rejected" />
            </el-select>
            <el-button size="small" type="danger" @click="remove(scope.row.id)" style="margin-left:8px">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>
  </div>
</template>

<script setup>
import { ref } from 'vue';
import { ElMessage, ElMessageBox } from 'element-plus';
import request from '../api/request';
import { useUserStore } from '../store/user';

// 检测字符串是否为乱码
function isGarbled(str) {
  if (!str) return false;
  
  // 计算非ASCII字符的比例
  let nonAsciiCount = 0;
  let totalCount = 0;
  
  for (let i = 0; i < str.length; i++) {
    const charCode = str.charCodeAt(i);
    if (charCode > 127) {
      nonAsciiCount++;
    }
    totalCount++;
  }
  
  // 如果非ASCII字符比例过高，可能是乱码
  const nonAsciiRatio = nonAsciiCount / totalCount;
  
  // 检查是否包含大量连续的非ASCII字符
  const hasConsecutiveNonAscii = /[\x80-\xFF]{3,}/.test(str);
  
  // 检查是否包含常见的乱码模式
  const hasGarbledPattern = /[\xc0-\xff]{2,}/.test(str);
  
  return nonAsciiRatio > 0.5 || hasConsecutiveNonAscii || hasGarbledPattern;
}

// 增强的编码修复函数
function fixEncoding(str) {
  if (!str) return '';
  
  // 尝试多种编码修复方法
  const methods = [
    // 方法1: ISO-8859-1到UTF-8的转换
    () => decodeURIComponent(escape(str)),
    // 方法2: 直接使用TextDecoder
    () => {
      const encoder = new TextEncoder();
      const decoder = new TextDecoder('utf-8');
      return decoder.decode(encoder.encode(str));
    },
    // 方法3: 处理可能的双编码问题
    () => decodeURIComponent(decodeURIComponent(escape(str))),
    // 方法4: 手动处理常见乱码模式
    () => {
      let result = '';
      for (let i = 0; i < str.length; i++) {
        const charCode = str.charCodeAt(i);
        if (charCode < 128) {
          result += String.fromCharCode(charCode);
        } else if (charCode >= 192 && charCode <= 255) {
          // 处理ISO-8859-1编码
          result += String.fromCharCode(charCode);
        } else {
          // 对于其他字符，尝试保持原样
          result += String.fromCharCode(charCode);
        }
      }
      return result;
    },
    // 方法5: 尝试使用Buffer（Node.js环境）
    () => {
      if (typeof Buffer !== 'undefined') {
        return Buffer.from(str, 'binary').toString('utf-8');
      }
      throw new Error('Buffer not available');
    }
  ];
  
  // 尝试每种方法，直到成功
  for (const method of methods) {
    try {
      const result = method();
      // 检查结果是否合理（不是纯乱码）
      if (result.length > 0 && !isGarbled(result)) {
        return result;
      }
    } catch (e) {
      // 方法失败，继续尝试下一种
    }
  }
  
  // 所有方法都失败，返回原始字符串
  return str;
}

// 获取安全的备注内容
function getSafeComment(comment) {
  if (!comment) return '';
  
  // 尝试1: 直接返回原始内容（如果不是乱码）
  if (!isGarbled(comment)) {
    return comment;
  }
  
  // 尝试2: 使用增强的编码修复函数
  try {
    const fixedComment = fixEncoding(comment);
    if (!isGarbled(fixedComment)) {
      return fixedComment;
    }
  } catch (e) {
    // 修复失败，继续尝试其他方法
  }
  
  // 尝试3: 移除所有非ASCII字符
  try {
    const asciiComment = comment.replace(/[^\x00-\x7F]/g, '');
    if (asciiComment.length > 0) {
      return asciiComment;
    }
  } catch (e) {
    // 处理失败，继续尝试其他方法
  }
  
  // 尝试4: 只保留常见的中文和英文字符
  try {
    const cleanComment = comment.replace(/[^\u4e00-\u9fa5a-zA-Z0-9\s，。！？；："'（）【】]/g, '');
    if (cleanComment.length > 0) {
      return cleanComment;
    }
  } catch (e) {
    // 处理失败，继续尝试其他方法
  }
  
  // 所有尝试都失败，返回空字符串
  return '';
}

const form = ref({ userId: '', type: 'access', comment: '' });
const list = ref([]);
const loading = ref(false);
const saving = ref(false);
const formRef = ref();
const userStore = useUserStore();
const rules = {
  type: [{ required: true, message: '类型不能为空', trigger: 'change' }],
  comment: [{ required: true, message: '备注不能为空', trigger: 'blur' }]
};

function removeRequestFromList(id) {
  list.value = list.value.filter(item => item.id !== id);
}

async function load() {
  loading.value = true;
  try {
    const data = await request.get('/subject-request/list');
    // 处理编码问题
    list.value = data.map(item => {
      if (item.comment) {
        try {
          // 使用安全的备注处理函数
          item.comment = getSafeComment(item.comment);
        } catch (e) {
          // 如果失败，设置为空字符串
          item.comment = '';
        }
      }
      return item;
    });
  } catch (err) {
    ElMessage.error(err?.message || '加载失败');
  } finally {
    loading.value = false;
  }
}

async function create() {
  if (!formRef.value) return;
  formRef.value.validate(async valid => {
    if (!valid) return;
    saving.value = true;
    try {
      const payload = {
        ...form.value,
        userId: form.value.userId || userStore.userInfo?.id || null,
      };
      await request.post('/subject-request/create', payload);
      ElMessage.success('提交成功');
      await load();
    } catch (err) {
      ElMessage.error(err?.message || '提交失败');
    } finally {
      saving.value = false;
    }
  });
}

async function update(row) {
  try {
    const payload = {
      ...row,
      handlerId: row.handlerId || userStore.userInfo?.id || null,
    };
    await request.post('/subject-request/process', payload);
    ElMessage.success('更新成功');
    await load();
  } catch (err) {
    ElMessage.error(err?.message || '更新失败');
  }
}

async function remove(id) {
  try {
    await ElMessageBox.confirm('确认删除该工单吗？', '提示', { type: 'warning' });
    await request.post('/subject-request/delete', { id });
    removeRequestFromList(id);
    ElMessage.success('删除成功');
    await load();
  } catch (err) {
    if (err !== 'cancel' && err !== 'close') ElMessage.error(err?.message || '删除失败');
  }
}

load();

if (!form.value.userId && userStore.userInfo?.id) {
  form.value.userId = userStore.userInfo.id;
}
</script>

<style scoped>
.page-grid { display: grid; gap: 16px; }
.card-header { font-weight: 600; margin-bottom: 12px; color: var(--color-text); }

:deep(.page-table) {
  --el-table-bg-color: transparent;
  --el-table-tr-bg-color: transparent;
  --el-table-row-hover-bg-color: rgba(255, 255, 255, 0.04);
  --el-table-header-bg-color: rgba(255, 255, 255, 0.04);
  --el-table-border-color: var(--color-border-light);
  --el-table-text-color: var(--color-text);
  --el-table-header-text-color: var(--color-text-secondary);
}
</style>
