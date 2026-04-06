<template>
  <div class="asset-page">
    <section class="asset-hero card-glass">
      <div>
        <div class="hero-kicker">GOVERNANCE INGESTION</div>
        <h1>数据上传即纳管</h1>
        <p>
          {{ operatorName }} 可以直接上传待治理数据，平台会自动登记为数据资产并触发扫描与风险记录。
        </p>
      </div>
      <div class="hero-stats">
        <article>
          <span>当前资产数</span>
          <strong>{{ assets.length }}</strong>
        </article>
        <article>
          <span>我的身份</span>
          <strong>{{ userStore.roleName }}</strong>
        </article>
        <article>
          <span>默认负责人</span>
          <strong>{{ operatorName }}</strong>
        </article>
      </div>
    </section>

    <el-tabs v-model="activeTab" class="asset-tabs" @tab-change="handleTabChange">
      <el-tab-pane label="数据资产" name="asset" />
      <el-tab-pane label="敏感数据治理" name="sensitive" />
    </el-tabs>

    <template v-if="activeTab === 'asset'">

    <section class="asset-grid">
      <el-card v-if="canWriteAsset" class="upload-card">
        <template #header>
          <div class="card-header">
            <div>
              <strong>上传治理数据</strong>
              <p>支持表格、文档、接口文件。上传后自动抽取文本摘要并进入治理链路。</p>
            </div>
          </div>
        </template>

        <el-form label-position="top" class="upload-form">
          <el-form-item label="资产名称">
            <el-input v-model="uploadForm.assetName" placeholder="留空则使用文件名" />
          </el-form-item>
          <el-form-item label="资产类型">
            <el-select v-model="uploadForm.type" placeholder="自动识别或手动选择">
              <el-option label="自动识别" value="" />
              <el-option label="table" value="table" />
              <el-option label="document" value="document" />
              <el-option label="api" value="api" />
              <el-option label="file" value="file" />
            </el-select>
          </el-form-item>
          <el-form-item label="敏感等级">
            <el-select v-model="uploadForm.sensitivityLevel" clearable placeholder="请手动选择或留空后使用系统推荐">
              <el-option v-for="item in sensitivityOptions" :key="item" :label="item" :value="item" />
            </el-select>
          </el-form-item>
          <el-form-item label="治理说明">
            <el-input v-model="uploadForm.description" type="textarea" :rows="4" placeholder="描述这批数据的业务背景、来源或治理要求" />
          </el-form-item>
          <el-form-item label="数据文件">
            <input ref="fileInputRef" class="file-input" type="file" @change="handleFileChange" />
            <div class="file-tip">{{ selectedFile ? `已选择：${selectedFile.name}` : '请选择需要纳管的数据文件' }}</div>
          </el-form-item>
          <el-alert
            v-if="recommendedSensitivityLevel"
            type="info"
            :closable="false"
            show-icon
            :title="`推荐等级：${recommendedSensitivityLevel}（可在资产清单中确认或调整）`"
            style="margin-bottom: 12px;"
          />
          <el-button
            type="primary"
            class="full-width"
            :loading="uploading"
            :disabled="!selectedFile || uploading"
            @click="uploadAsset"
          >
            {{ uploading ? '上传中' : '上传并纳管' }}
          </el-button>
        </el-form>
      </el-card>

      <el-card v-else class="upload-card">
        <template #header>
          <div class="card-header">
            <div>
              <strong>上传治理数据</strong>
              <p>当前身份仅可查看资产清单，不具备上传或修改权限。</p>
            </div>
          </div>
        </template>
        <el-empty description="暂无可执行操作" :image-size="72" />
      </el-card>

      <el-card class="table-card">
        <template #header>
          <div class="card-header toolbar-row">
            <div>
              <strong>资产清单</strong>
              <p>查询、编辑与删除已纳管的数据资产。</p>
            </div>
            <div class="toolbar-actions">
              <el-input v-model="query.name" placeholder="搜索资产名称" clearable @keyup.enter="fetchAssets" />
              <el-button type="primary" :loading="loading" @click="fetchAssets">查询</el-button>
              <el-button v-if="canWriteAsset" @click="openAdd">手工登记</el-button>
            </div>
          </div>
        </template>

        <el-table :data="assets" style="width: 100%" v-loading="loading" empty-text="暂无记录">
          <el-table-column prop="id" label="ID" width="80">
            <template #default="scope">
              <div class="cell nowrap">{{ scope.row.id }}</div>
            </template>
          </el-table-column>
          <el-table-column prop="name" label="名称" min-width="180">
            <template #default="scope">{{ normalizeDisplayText(scope.row.name) }}</template>
          </el-table-column>
          <el-table-column prop="type" label="类型" width="110" />
          <el-table-column prop="sensitivityLevel" label="敏感等级" width="110" />
          <el-table-column label="DIA" min-width="190">
            <template #default="scope">
              <el-space wrap>
                <el-tag v-if="scope.row.diaRiskLevel" :type="diaTagType(scope.row.diaRiskLevel)">{{ String(scope.row.diaRiskLevel).toUpperCase() }}</el-tag>
                <span>{{ scope.row.diaScore ?? '-' }}</span>
                <span class="dia-meta" v-if="scope.row.diaFramework">{{ scope.row.diaFramework }}</span>
              </el-space>
            </template>
          </el-table-column>
          <el-table-column prop="description" label="治理摘要" min-width="350" show-overflow-tooltip>
            <template #default="scope">{{ normalizeDisplayText(scope.row.description) }}</template>
          </el-table-column>
          <el-table-column prop="location" label="位置 / 来源" min-width="220" show-overflow-tooltip />
          <el-table-column prop="createTime" label="创建时间" min-width="170" />
          <el-table-column v-if="canWriteAsset || canDeleteAsset" label="操作" width="390" fixed="right">
            <template #default="scope">
              <el-button size="small" type="primary" plain @click="viewAssetDetail(scope.row.id)">详情</el-button>
              <el-button
                v-if="canWriteAsset"
                size="small"
                type="warning"
                plain
                :loading="diaRunningAssetId === scope.row.id"
                @click="runDiaAssess(scope.row)"
              >
                DIA评估
              </el-button>
              <el-button size="small" plain @click="viewDiaLatest(scope.row)">DIA结果</el-button>
              <el-button v-if="canWriteAsset" size="small" @click="editAsset(scope.row)">编辑</el-button>
              <el-button v-if="canDeleteAsset" size="small" type="danger" @click="deleteAsset(scope.row.id)">删除</el-button>
            </template>
          </el-table-column>
        </el-table>
      </el-card>
    </section>

    <el-dialog v-model="showAdd" title="手工登记数据资产" width="560px">
      <el-form :model="addForm" :rules="rules" ref="addFormRef" label-position="top">
        <el-form-item label="名称" prop="name"><el-input v-model="addForm.name" /></el-form-item>
        <el-form-item label="类型" prop="type"><el-input v-model="addForm.type" /></el-form-item>
        <el-form-item label="敏感等级" prop="sensitivityLevel"><el-input v-model="addForm.sensitivityLevel" /></el-form-item>
        <el-form-item label="位置"><el-input v-model="addForm.location" placeholder="如：crm/customer.csv 或 S3/OSS 路径" /></el-form-item>
        <el-form-item label="说明"><el-input v-model="addForm.description" type="textarea" :rows="3" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="showAdd = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="addAsset">保存</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="showEdit" title="编辑数据资产" width="560px">
      <el-form :model="editForm" :rules="rules" ref="editFormRef" label-position="top">
        <el-form-item label="名称" prop="name"><el-input v-model="editForm.name" /></el-form-item>
        <el-form-item label="类型" prop="type"><el-input v-model="editForm.type" /></el-form-item>
        <el-form-item label="敏感等级" prop="sensitivityLevel"><el-input v-model="editForm.sensitivityLevel" /></el-form-item>
        <el-form-item label="位置"><el-input v-model="editForm.location" /></el-form-item>
        <el-form-item label="说明"><el-input v-model="editForm.description" type="textarea" :rows="3" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="showEdit = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="updateAsset">保存</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="showDetail" title="资产详情" width="680px">
      <el-skeleton :loading="detailLoading" :rows="6" animated>
        <div v-if="detailData" class="detail-grid">
          <div><strong>ID：</strong>{{ detailData.id }}</div>
          <div><strong>名称：</strong>{{ normalizeDisplayText(detailData.name) }}</div>
          <div><strong>类型：</strong>{{ detailData.type }}</div>
          <div><strong>敏感等级：</strong>{{ detailData.sensitivityLevel }}</div>
          <div><strong>位置：</strong>{{ detailData.location }}</div>
          <div><strong>负责人：</strong>{{ detailData.ownerId }}</div>
          <div><strong>创建时间：</strong>{{ detailData.createTime }}</div>
          <div><strong>更新时间：</strong>{{ detailData.updateTime }}</div>
          <div><strong>DIA评分：</strong>{{ detailData.diaScore ?? '—' }}</div>
          <div><strong>DIA等级：</strong>{{ detailData.diaRiskLevel || '—' }}</div>
          <div><strong>DIA框架：</strong>{{ detailData.diaFramework || '—' }}</div>
          <div><strong>DIA时间：</strong>{{ detailData.diaUpdatedAt || '—' }}</div>
          <div class="detail-span"><strong>治理摘要：</strong>{{ normalizeDisplayText(detailData.description) || '—' }}</div>
          <div class="detail-span"><strong>调用次数：</strong>{{ detailData.callCount ?? 0 }}</div>
        </div>
      </el-skeleton>
      <template #footer>
        <el-button @click="showDetail = false">关闭</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="showDiaDetail" title="DIA评估详情" width="560px">
      <div v-if="diaDetail" class="detail-grid">
        <div><strong>资产ID：</strong>{{ diaDetail.assetId }}</div>
        <div><strong>框架：</strong>{{ diaDetail.framework || 'PIPL' }}</div>
        <div><strong>评分：</strong>{{ diaDetail.impactScore }}</div>
        <div><strong>等级：</strong>{{ diaDetail.riskLevel || '-' }}</div>
        <div><strong>更新时间：</strong>{{ diaDetail.updatedAt || '-' }}</div>
        <div class="detail-span"><strong>因子：</strong>{{ formatDiaFactors(diaDetail.riskFactors) }}</div>
      </div>
      <template #footer>
        <el-button @click="showDiaDetail = false">关闭</el-button>
      </template>
    </el-dialog>
    </template>
    <SensitiveDataGovernance v-else />
  </div>
</template>

<script setup>
import { computed, onMounted, ref, watch } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import { ElMessage, ElMessageBox } from 'element-plus';
import request from '../api/request';
import { useUserStore } from '../store/user';
import { hasRole } from '../utils/roleBoundary';
import SensitiveDataGovernance from './SensitiveDataGovernance.vue';

const userStore = useUserStore();
const route = useRoute();
const router = useRouter();
const activeTab = ref(route.query?.tab === 'sensitive' ? 'sensitive' : 'asset');
const assets = ref([]);
const loading = ref(false);
const uploading = ref(false);
const showAdd = ref(false);
const showEdit = ref(false);
const showDetail = ref(false);
const saving = ref(false);
const selectedFile = ref(null);
const fileInputRef = ref(null);
const detailData = ref(null);
const detailLoading = ref(false);
const recommendedSensitivityLevel = ref('');
const diaRunningAssetId = ref(null);
const showDiaDetail = ref(false);
const diaDetail = ref(null);

const query = ref({ name: '' });
const addForm = ref({ name: '', type: '', sensitivityLevel: 'medium', location: '', description: '' });
const editForm = ref({});
const uploadForm = ref({ assetName: '', type: '', sensitivityLevel: '', description: '' });
const addFormRef = ref();
const editFormRef = ref();

const operatorName = computed(() => userStore.displayName || userStore.userInfo?.username || '当前用户');
const roleCode = computed(() => String(userStore.userInfo?.roleCode || '').toUpperCase());
const isDataAdmin = computed(() => hasRole(userStore.userInfo, 'DATA_ADMIN'));
const canWriteAsset = computed(() => {
  if (hasRole(userStore.userInfo, 'ADMIN')) return true;
  return isDataAdmin.value;
});
const canDeleteAsset = computed(() => {
  if (hasRole(userStore.userInfo, 'ADMIN')) return true;
  return isDataAdmin.value;
});
const sensitivityOptions = ['low', 'medium', 'high', 'critical'];
const rules = {
  name: [{ required: true, message: '名称不能为空', trigger: 'blur' }],
  type: [{ required: true, message: '类型不能为空', trigger: 'blur' }],
  sensitivityLevel: [{ required: true, message: '敏感等级不能为空', trigger: 'blur' }],
};

function handleTabChange(tabName) {
  const normalized = tabName === 'sensitive' ? 'sensitive' : 'asset';
  router.replace({ query: { ...route.query, tab: normalized } });
}

watch(() => route.query?.tab, (tab) => {
  activeTab.value = tab === 'sensitive' ? 'sensitive' : 'asset';
});

async function fetchAssets() {
  if (loading.value) return;
  loading.value = true;
  try {
    const res = await request.get('/data-asset/list', { params: query.value });
    assets.value = Array.isArray(res) ? res : [];
  } catch (err) {
    ElMessage.error(err?.message || '加载失败');
  } finally {
    loading.value = false;
  }
}

function openAdd() {
  addForm.value = { name: '', type: '', sensitivityLevel: 'medium', location: '', description: '' };
  showAdd.value = true;
}

function handleFileChange(event) {
  const [file] = event.target.files || [];
  selectedFile.value = file || null;
  recommendedSensitivityLevel.value = '';
  if (file && !uploadForm.value.assetName) {
    uploadForm.value.assetName = file.name.replace(/\.[^.]+$/, '');
  }
}

async function uploadAsset() {
  if (uploading.value) return;
  if (!selectedFile.value) {
    ElMessage.warning('请先选择要上传的数据文件');
    return;
  }
  uploading.value = true;
  try {
    const formData = new FormData();
    formData.append('file', selectedFile.value);
    if (uploadForm.value.assetName) formData.append('assetName', uploadForm.value.assetName);
    if (uploadForm.value.type) formData.append('type', uploadForm.value.type);
    if (uploadForm.value.sensitivityLevel) formData.append('sensitivityLevel', uploadForm.value.sensitivityLevel);
    if (uploadForm.value.description) formData.append('description', uploadForm.value.description);
    const uploadResult = await request.post('/data-asset/upload', formData, {
      headers: { 'Content-Type': 'multipart/form-data' },
    });

    const recommended = uploadResult?.recommendedSensitivityLevel || uploadResult?.recommendedLevel || '';
    recommendedSensitivityLevel.value = recommended;
    if (recommended) {
      ElMessage.success(`上传成功，推荐等级：${recommended}`);
    } else {
      ElMessage.success('上传成功，已进入治理链路');
    }

    selectedFile.value = null;
    if (fileInputRef.value) {
      fileInputRef.value.value = '';
    }
    uploadForm.value = { assetName: '', type: '', sensitivityLevel: '', description: '' };
    await fetchAssets();
    await syncCurrentUser();
  } catch (err) {
    ElMessage.error(err?.message || '上传失败');
  } finally {
    uploading.value = false;
  }
}

async function addAsset() {
  if (!addFormRef.value) return;
  addFormRef.value.validate(async (valid) => {
    if (!valid) return;
    saving.value = true;
    try {
      await request.post('/data-asset/register', addForm.value);
      ElMessage.success('登记成功');
      showAdd.value = false;
      await fetchAssets();
    } catch (err) {
      ElMessage.error(err?.message || '保存失败');
    } finally {
      saving.value = false;
    }
  });
}

function editAsset(row) {
  editForm.value = { ...row };
  showEdit.value = true;
}

async function updateAsset() {
  if (!editFormRef.value) return;
  editFormRef.value.validate(async (valid) => {
    if (!valid) return;
    saving.value = true;
    try {
      await request.post('/data-asset/update', editForm.value);
      ElMessage.success('更新成功');
      showEdit.value = false;
      await fetchAssets();
    } catch (err) {
      ElMessage.error(err?.message || '更新失败');
    } finally {
      saving.value = false;
    }
  });
}

async function deleteAsset(id) {
  try {
    await ElMessageBox.confirm('确认删除该资产吗？', '提示', { type: 'warning' });
    await request.post('/data-asset/delete', { id });
    ElMessage.success('删除成功');
    await fetchAssets();
  } catch (err) {
    if (err !== 'cancel') {
      ElMessage.error(err?.message || '删除失败');
    }
  }
}

async function viewAssetDetail(id) {
  detailLoading.value = true;
  detailData.value = null;
  showDetail.value = true;
  try {
    detailData.value = await request.get(`/data-asset/${id}`);
  } catch (err) {
    ElMessage.error(err?.message || '加载资产详情失败');
    showDetail.value = false;
  } finally {
    detailLoading.value = false;
  }
}

async function runDiaAssess(row) {
  if (!row?.id || diaRunningAssetId.value === row.id) return;
  diaRunningAssetId.value = row.id;
  try {
    const res = await request.post(`/data-asset/${row.id}/privacy-assess`, { framework: 'PIPL' });
    ElMessage.success(`DIA评估完成：${(res?.riskLevel || '-').toUpperCase()} / ${res?.impactScore ?? '-'} 分`);
    await fetchAssets();
    if (detailData.value?.id === row.id && showDetail.value) {
      detailData.value = await request.get(`/data-asset/${row.id}`);
    }
  } catch (err) {
    ElMessage.error(err?.message || 'DIA评估失败');
  } finally {
    diaRunningAssetId.value = null;
  }
}

async function viewDiaLatest(row) {
  if (!row?.id) return;
  try {
    const res = await request.get(`/data-asset/${row.id}/privacy-assess/latest`);
    if (!res?.exists) {
      ElMessage.warning('该资产暂无DIA评估记录');
      return;
    }
    diaDetail.value = {
      ...res,
      riskFactors: parseJsonMaybe(res?.riskFactors),
    };
    showDiaDetail.value = true;
  } catch (err) {
    ElMessage.error(err?.message || '获取DIA结果失败');
  }
}

function parseJsonMaybe(value) {
  if (!value) return {};
  if (typeof value === 'object') return value;
  try {
    return JSON.parse(String(value));
  } catch {
    return { raw: String(value) };
  }
}

function formatDiaFactors(value) {
  const factors = parseJsonMaybe(value);
  try {
    return JSON.stringify(factors, null, 2);
  } catch {
    return String(value || '{}');
  }
}

function diaTagType(level) {
  const key = String(level || '').toLowerCase();
  if (key === 'high') return 'danger';
  if (key === 'medium') return 'warning';
  if (key === 'low') return 'success';
  return 'info';
}

function normalizeDisplayText(text) {
  if (!text) return text;
  return String(text)
    .replace(/\[\s*DEMO(?:-SEED)?\s*\]/gi, '')
    .replace(/\bDEMO[_-]?SEED\b/gi, '')
    .replace(/\bDEMO[_-]?\d{4,}\b/gi, '')
    .replace(/^\s*[_-]+\s*/g, '')
    .replace(/\s{2,}/g, ' ')
    .trim();
}

async function syncCurrentUser() {
  try {
    const profile = await request.get('/auth/me');
    userStore.setUser(profile?.user || profile || {});
  } catch {
    // Ignore profile refresh errors; existing session context stays available.
  }
}

onMounted(async () => {
  await syncCurrentUser();
  await fetchAssets();
});
</script>

<style scoped>
.asset-page {
  display: grid;
  gap: 18px;
}

.asset-hero {
  display: grid;
  grid-template-columns: minmax(0, 1.2fr) minmax(280px, 420px);
  gap: 18px;
  padding: 28px;
}

.hero-kicker {
  color: #7d9bc4;
  font-size: 12px;
  font-weight: 700;
  letter-spacing: 0.18em;
  text-transform: uppercase;
}

.asset-hero h1 {
  margin: 10px 0 12px;
  color: var(--color-text);
  font-size: clamp(30px, 4vw, 42px);
}

.asset-hero p {
  margin: 0;
  color: var(--color-text-secondary);
  line-height: 1.8;
}

.hero-stats {
  display: grid;
  gap: 12px;
}

.hero-stats article {
  border-radius: 22px;
  padding: 18px 20px;
  border: 1px solid var(--color-border-light);
  background: rgba(255,255,255,0.03);
}

.hero-stats span {
  color: var(--color-text-muted);
  font-size: 13px;
}

.hero-stats strong {
  display: block;
  margin-top: 10px;
  color: var(--color-text);
  font-size: 24px;
}

.asset-grid {
  display: grid;
  grid-template-columns: 360px minmax(0, 1fr);
  gap: 18px;
}

.upload-card,
.table-card {
  border-radius: 28px;
}

.card-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 18px;
}

.card-header strong {
  color: var(--color-text);
}

.card-header p {
  margin: 8px 0 0;
  color: var(--color-text-secondary);
}

.toolbar-row {
  align-items: flex-start;
}

.toolbar-actions {
  display: flex;
  gap: 10px;
  min-width: 360px;
}

.upload-form {
  display: grid;
}

.full-width {
  width: 100%;
}

.file-input {
  width: 100%;
  border-radius: 16px;
  border: 1px dashed var(--color-border);
  padding: 14px;
  background: rgba(255,255,255,0.03);
}

.file-tip {
  margin-top: 8px;
  color: var(--color-text-muted);
  font-size: 13px;
}

.dia-meta {
  color: var(--color-text-muted);
  font-size: 12px;
}

.detail-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 10px 14px;
  color: var(--color-text);
  font-size: 13px;
  line-height: 1.6;
}

.detail-span {
  grid-column: 1 / -1;
}

@media (max-width: 1100px) {
  .asset-hero,
  .asset-grid {
    grid-template-columns: 1fr;
  }

  .toolbar-actions {
    min-width: 0;
    width: 100%;
    flex-wrap: wrap;
  }
}
</style>
