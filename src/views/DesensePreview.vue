<template>
  <div class="page-grid">
    <!-- 脱敏预览 -->
    <el-card class="card-glass">
      <div class="card-header">脱敏规则与效果预览</div>
      <el-form :inline="true" @submit.prevent>
        <el-form-item label="样例数据"><el-input v-model="sample" style="width:260px" /></el-form-item>
        <el-form-item label="掩码符"><el-input v-model="mask" style="width:100px" /></el-form-item>
        <el-button type="primary" @click="preview">预览</el-button>
        <el-button type="success" :loading="executing" @click="executeDesense">一键脱敏</el-button>
        <el-button @click="loadRules">加载规则</el-button>
      </el-form>
      <el-divider />
      <el-form :inline="true" @submit.prevent>
        <el-form-item label="规则名"><el-input v-model="newRule.name" style="width:140px" /></el-form-item>
        <el-form-item label="匹配表达式"><el-input v-model="newRule.pattern" style="width:220px" /></el-form-item>
        <el-form-item label="掩码"><el-input v-model="newRule.mask" style="width:90px" /></el-form-item>
        <el-form-item label="示例"><el-input v-model="newRule.example" style="width:180px" /></el-form-item>
        <el-button type="primary" :loading="savingRule" @click="saveRule">保存规则</el-button>
      </el-form>
      <el-alert v-if="result" type="success" show-icon :closable="false" style="margin-top:10px;">
        <template #title>脱敏结果</template>
        <div>原文：{{ result.raw }}</div>
        <div>脱敏：{{ result.masked }}</div>
      </el-alert>
      <el-table :data="rules" style="margin-top:16px" v-loading="loading">
        <el-table-column prop="name" label="规则名" />
        <el-table-column prop="pattern" label="匹配" />
        <el-table-column prop="mask" label="掩码" />
        <el-table-column prop="example" label="示例" />
        <el-table-column label="操作" width="100">
          <template #default="scope">
            <el-button size="small" type="danger" @click="removeRule(scope.row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <!-- 动态脱敏策略推荐 -->
    <el-card class="card-glass">
      <div class="card-header">动态脱敏策略推荐</div>
      <p class="section-subtitle">选择数据类别与当前角色，系统将自动推荐优先级最高的脱敏策略。</p>
      <el-form :inline="true" @submit.prevent>
        <el-form-item label="数据类别">
          <el-select v-model="recCategory" style="width:150px" placeholder="请选择">
            <el-option v-for="cat in CATEGORIES" :key="cat.value" :label="cat.label" :value="cat.value" />
          </el-select>
        </el-form-item>
        <el-form-item label="用户角色">
          <el-select v-model="recRole" style="width:130px" placeholder="请选择">
            <el-option v-for="role in ROLES" :key="role.value" :label="role.label" :value="role.value" />
          </el-select>
        </el-form-item>
        <el-form-item label="敏感级别">
          <el-select v-model="recSensitivity" style="width:110px" placeholder="请选择">
            <el-option label="低" value="low" />
            <el-option label="中" value="medium" />
            <el-option label="高" value="high" />
            <el-option label="极高" value="critical" />
          </el-select>
        </el-form-item>
        <el-button type="primary" :loading="recLoading" @click="fetchRecommend">获取推荐</el-button>
      </el-form>

      <div v-if="recommendations.length" class="rec-list">
        <div
          v-for="rec in recommendations"
          :key="rec.ruleId"
          class="rec-item card-glass"
        >
          <div class="rec-header">
            <span class="rec-priority">P{{ rec.priority }}</span>
            <span class="rec-name">{{ rec.name }}</span>
            <span class="rec-strategy">{{ STRATEGY_LABELS[rec.strategy] || rec.strategy }}</span>
          </div>
          <p class="rec-reason">{{ rec.reason }}</p>
          <div class="rec-meta">
            <code class="rec-mask">掩码示例：{{ rec.mask }}</code>
          </div>
        </div>
      </div>
      <el-empty v-else-if="recQueried && !recLoading" description="暂无推荐规则" />
    </el-card>
  </div>
</template>

<script setup>
import { ref } from 'vue';
import { ElMessage, ElMessageBox } from 'element-plus';
import { desenseApi } from '../api/desense';

const CATEGORIES = [
  { label: '身份证号', value: 'id_card' },
  { label: '手机号', value: 'phone' },
  { label: '银行卡号', value: 'bank_card' },
  { label: '电子邮箱', value: 'email' },
  { label: '地址', value: 'address' },
  { label: '姓名', value: 'name' },
  { label: '通用字段', value: 'default' },
];

const ROLES = [
  { label: '审计员', value: 'auditor' },
  { label: '分析师', value: 'analyst' },
  { label: '管理员', value: 'admin' },
  { label: '开发者', value: 'developer' },
];

const STRATEGY_LABELS = {
  mask: '全字段遮蔽',
  partial: '部分脱敏',
  hash: '哈希替换',
  generalize: '泛化处理',
  tokenize: '令牌化',
};

const sample = ref('13800138000');
const mask = ref('*');
const result = ref(null);
const rules = ref([]);
const loading = ref(false);
const executing = ref(false);
const savingRule = ref(false);
const newRule = ref({ name: '', pattern: '', mask: '***', example: '' });

// 推荐相关状态
const recCategory = ref('phone');
const recRole = ref('analyst');
const recSensitivity = ref('medium');
const recLoading = ref(false);
const recQueried = ref(false);
const recommendations = ref([]);

async function preview() {
  try {
    result.value = await desenseApi.preview({ text: sample.value, mask: mask.value });
  } catch (err) {
    ElMessage.error(err?.message || '预览失败');
  }
}

async function executeDesense() {
  executing.value = true;
  try {
    result.value = await desenseApi.execute({ text: sample.value, mask: mask.value });
    ElMessage.success('脱敏执行成功');
  } catch (err) {
    ElMessage.error(err?.message || '脱敏执行失败');
  } finally {
    executing.value = false;
  }
}

async function loadRules() {
  loading.value = true;
  try {
    rules.value = await desenseApi.listRules();
  } catch (err) {
    ElMessage.error(err?.message || '加载规则失败');
  } finally {
    loading.value = false;
  }
}

async function saveRule() {
  if (!newRule.value.name || !newRule.value.pattern) {
    ElMessage.warning('规则名和匹配表达式不能为空');
    return;
  }
  savingRule.value = true;
  try {
    await desenseApi.saveRule({ ...newRule.value });
    ElMessage.success('规则已保存');
    newRule.value = { name: '', pattern: '', mask: '***', example: '' };
    await loadRules();
  } catch (err) {
    ElMessage.error(err?.message || '规则保存失败');
  } finally {
    savingRule.value = false;
  }
}

async function removeRule(row) {
  if (!row?.id) {
    ElMessage.warning('该规则缺少 ID，无法删除');
    return;
  }
  try {
    await ElMessageBox.confirm('确认删除该脱敏规则吗？', '提示', { type: 'warning' });
    await desenseApi.deleteRule(row.id);
    ElMessage.success('规则已删除');
    await loadRules();
  } catch (err) {
    if (err !== 'cancel') {
      ElMessage.error(err?.message || '规则删除失败');
    }
  }
}

async function fetchRecommend() {
  recLoading.value = true;
  recQueried.value = true;
  try {
    const data = await desenseApi.recommend(recCategory.value, recRole.value, recSensitivity.value);
    recommendations.value = Array.isArray(data) ? data : [];
    if (!recommendations.value.length) {
      ElMessage.info('当前类别暂无推荐策略');
    }
  } catch (err) {
    ElMessage.error(err?.message || '获取推荐失败');
  } finally {
    recLoading.value = false;
  }
}

loadRules();
</script>

<style scoped>
.page-grid { display: grid; gap: 16px; }
.card-header { font-weight: 600; margin-bottom: 8px; }
.section-subtitle { font-size: 13px; color: var(--color-text-muted); margin: 0 0 16px; }

.rec-list {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(280px, 1fr));
  gap: 14px;
  margin-top: 16px;
}

.rec-item {
  padding: 16px 18px;
  border-radius: 12px;
  border: 1px solid var(--color-border-light);
}

.rec-header {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-bottom: 8px;
}

.rec-priority {
  padding: 2px 8px;
  border-radius: 999px;
  background: rgba(100, 160, 255, 0.15);
  color: var(--color-primary-light);
  font-size: 11px;
  font-weight: 700;
  letter-spacing: 0.05em;
}

.rec-name {
  font-weight: 600;
  font-size: 14px;
  color: var(--color-text);
  flex: 1;
}

.rec-strategy {
  font-size: 11px;
  padding: 2px 8px;
  border-radius: 999px;
  background: rgba(79, 227, 193, 0.12);
  color: #4fe3c1;
}

.rec-reason {
  font-size: 13px;
  color: var(--color-text-secondary);
  margin: 0 0 10px;
  line-height: 1.6;
}

.rec-meta {
  display: flex;
  align-items: center;
  gap: 8px;
}

.rec-mask {
  font-size: 12px;
  padding: 3px 8px;
  border-radius: 6px;
  background: rgba(255,255,255,0.06);
  color: var(--color-text-muted);
  font-family: monospace;
}
</style>

