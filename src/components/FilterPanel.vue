<template>
  <div class="filter-panel">
    <div class="filter-header">
      <div class="filter-title">
        <Filter />
        <strong>筛选条件</strong>
      </div>
      <div class="filter-actions">
        <button class="filter-reset" @click="resetFilters">
          <RefreshLeft />
          重置
        </button>
        <button class="filter-apply" @click="applyFilters">
          <Check />
          应用
        </button>
      </div>
    </div>

    <div class="filter-content">
      <div class="filter-group" v-for="group in filterGroups" :key="group.key">
        <div class="filter-group-title">{{ group.label }}</div>
        
        <div v-if="group.type === 'search'" class="filter-search">
          <input
            v-model="localFilters[group.key]"
            type="text"
            :placeholder="group.placeholder"
            @input="handleSearch"
          />
        </div>

        <div v-else-if="group.type === 'select'" class="filter-select">
          <select v-model="localFilters[group.key]" @change="handleSelect">
            <option value="">全部</option>
            <option v-for="option in group.options" :key="option.value" :value="option.value">
              {{ option.label }}
            </option>
          </select>
        </div>

        <div v-else-if="group.type === 'date'" class="filter-date">
          <input
            v-model="localFilters[group.key]"
            type="date"
            @change="handleDate"
          />
        </div>

        <div v-else-if="group.type === 'date-range'" class="filter-date-range">
          <input
            v-model="localFilters[`${group.key}Start`]"
            type="date"
            placeholder="开始日期"
            @change="handleDateRange"
          />
          <span>至</span>
          <input
            v-model="localFilters[`${group.key}End`]"
            type="date"
            placeholder="结束日期"
            @change="handleDateRange"
          />
        </div>

        <div v-else-if="group.type === 'checkbox'" class="filter-checkbox-group">
          <label
            v-for="option in group.options"
            :key="option.value"
            class="filter-checkbox"
          >
            <input
              type="checkbox"
              v-model="localFilters[group.key]"
              :value="option.value"
              @change="handleCheckbox"
            />
            <span>{{ option.label }}</span>
          </label>
        </div>

        <div v-else-if="group.type === 'radio'" class="filter-radio-group">
          <label
            v-for="option in group.options"
            :key="option.value"
            class="filter-radio"
          >
            <input
              type="radio"
              v-model="localFilters[group.key]"
              :value="option.value"
              @change="handleRadio"
            />
            <span>{{ option.label }}</span>
          </label>
        </div>

        <div v-else-if="group.type === 'range'" class="filter-range">
          <input
            v-model="localFilters[`${group.key}Min`]"
            type="number"
            :placeholder="group.minLabel || '最小值'"
            @input="handleRange"
          />
          <span>-</span>
          <input
            v-model="localFilters[`${group.key}Max`]"
            type="number"
            :placeholder="group.maxLabel || '最大值'"
            @input="handleRange"
          />
        </div>
      </div>
    </div>

    <div class="filter-footer">
      <div class="active-filters">
        <span class="active-label">已选:</span>
        <span
          v-for="(value, key) in activeFilters"
          :key="key"
          class="active-filter-tag"
        >
          {{ getFilterLabel(key, value) }}
          <button @click="removeFilter(key)">×</button>
        </span>
      </div>
      <div class="filter-count">
        共 {{ totalCount }} 条记录
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, computed, watch } from 'vue';
import { Filter, RefreshLeft, Check } from '@element-plus/icons-vue';

const props = defineProps({
  filterGroups: {
    type: Array,
    required: true
  },
  totalCount: {
    type: Number,
    default: 0
  }
});

const emit = defineEmits(['filter-change', 'filter-reset']);

const localFilters = ref({});

const activeFilters = computed(() => {
  const active = {};
  Object.keys(localFilters.value).forEach(key => {
    const value = localFilters.value[key];
    if (value !== '' && value !== null && value !== undefined) {
      if (Array.isArray(value) && value.length === 0) {
        return;
      }
      active[key] = value;
    }
  });
  return active;
});

function initializeFilters() {
  const filters = {};
  props.filterGroups.forEach(group => {
    if (group.type === 'checkbox') {
      filters[group.key] = [];
    } else {
      filters[group.key] = '';
    }
    
    if (group.type === 'date-range') {
      filters[`${group.key}Start`] = '';
      filters[`${group.key}End`] = '';
    }
    
    if (group.type === 'range') {
      filters[`${group.key}Min`] = '';
      filters[`${group.key}Max`] = '';
    }
  });
  localFilters.value = filters;
}

function handleSearch() {
  emitFilterChange();
}

function handleSelect() {
  emitFilterChange();
}

function handleDate() {
  emitFilterChange();
}

function handleDateRange() {
  emitFilterChange();
}

function handleCheckbox() {
  emitFilterChange();
}

function handleRadio() {
  emitFilterChange();
}

function handleRange() {
  emitFilterChange();
}

function emitFilterChange() {
  emit('filter-change', { ...localFilters.value });
}

function applyFilters() {
  emit('filter-change', { ...localFilters.value });
}

function resetFilters() {
  initializeFilters();
  emit('filter-reset');
}

function removeFilter(key) {
  localFilters.value[key] = '';
  emitFilterChange();
}

function getFilterLabel(key, value) {
  const group = props.filterGroups.find(g => g.key === key);
  if (!group) return value;
  
  if (group.type === 'select' || group.type === 'radio') {
    const option = group.options.find(o => o.value === value);
    return option ? `${group.label}: ${option.label}` : value;
  }
  
  if (group.type === 'checkbox' && Array.isArray(value)) {
    const labels = value.map(v => {
      const option = group.options.find(o => o.value === v);
      return option ? option.label : v;
    });
    return `${group.label}: ${labels.join(', ')}`;
  }
  
  return `${group.label}: ${value}`;
}

watch(() => props.filterGroups, () => {
  initializeFilters();
}, { immediate: true });
</script>

<style scoped>
.filter-panel {
  background: rgba(255, 255, 255, 0.03);
  border: 1px solid rgba(255, 255, 255, 0.1);
  border-radius: 12px;
  padding: 20px;
  margin-bottom: 20px;
}

.filter-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 20px;
  padding-bottom: 16px;
  border-bottom: 1px solid rgba(255, 255, 255, 0.1);
}

.filter-title {
  display: flex;
  align-items: center;
  gap: 8px;
}

.filter-title svg {
  font-size: 20px;
  color: #5f87ff;
}

.filter-title strong {
  font-size: 16px;
  color: #fff;
}

.filter-actions {
  display: flex;
  gap: 12px;
}

.filter-reset,
.filter-apply {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 8px 16px;
  border-radius: 6px;
  font-size: 14px;
  cursor: pointer;
  transition: all 0.3s ease;
  border: none;
}

.filter-reset {
  background: rgba(255, 255, 255, 0.05);
  color: rgba(255, 255, 255, 0.7);
}

.filter-reset:hover {
  background: rgba(255, 255, 255, 0.1);
  color: #fff;
}

.filter-apply {
  background: rgba(95, 135, 255, 0.2);
  color: #5f87ff;
}

.filter-apply:hover {
  background: rgba(95, 135, 255, 0.3);
}

.filter-content {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(280px, 1fr));
  gap: 20px;
}

.filter-group {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.filter-group-title {
  font-size: 14px;
  font-weight: 500;
  color: rgba(255, 255, 255, 0.8);
}

.filter-search input,
.filter-select select,
.filter-date input,
.filter-date-range input,
.filter-range input {
  width: 100%;
  padding: 10px 12px;
  background: rgba(255, 255, 255, 0.05);
  border: 1px solid rgba(255, 255, 255, 0.1);
  border-radius: 6px;
  color: #fff;
  font-size: 14px;
  outline: none;
  transition: all 0.3s ease;
}

.filter-search input:focus,
.filter-select select:focus,
.filter-date input:focus,
.filter-date-range input:focus,
.filter-range input:focus {
  border-color: rgba(95, 135, 255, 0.3);
  background: rgba(255, 255, 255, 0.08);
}

.filter-search input::placeholder,
.filter-range input::placeholder {
  color: rgba(255, 255, 255, 0.4);
}

.filter-date-range {
  display: flex;
  align-items: center;
  gap: 8px;
}

.filter-date-range span {
  color: rgba(255, 255, 255, 0.5);
}

.filter-range {
  display: flex;
  align-items: center;
  gap: 8px;
}

.filter-range span {
  color: rgba(255, 255, 255, 0.5);
}

.filter-checkbox-group,
.filter-radio-group {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.filter-checkbox,
.filter-radio {
  display: flex;
  align-items: center;
  gap: 8px;
  cursor: pointer;
  color: rgba(255, 255, 255, 0.8);
  font-size: 14px;
  transition: all 0.2s ease;
}

.filter-checkbox:hover,
.filter-radio:hover {
  color: #fff;
}

.filter-checkbox input,
.filter-radio input {
  cursor: pointer;
}

.filter-footer {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding-top: 16px;
  border-top: 1px solid rgba(255, 255, 255, 0.1);
  margin-top: 20px;
}

.active-filters {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
}

.active-label {
  font-size: 14px;
  color: rgba(255, 255, 255, 0.6);
}

.active-filter-tag {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 6px 12px;
  background: rgba(95, 135, 255, 0.1);
  border: 1px solid rgba(95, 135, 255, 0.2);
  border-radius: 16px;
  font-size: 13px;
  color: #5f87ff;
}

.active-filter-tag button {
  background: transparent;
  border: none;
  color: #5f87ff;
  cursor: pointer;
  font-size: 16px;
  line-height: 1;
  padding: 0;
  margin-left: 4px;
}

.active-filter-tag button:hover {
  color: #ff6384;
}

.filter-count {
  font-size: 14px;
  color: rgba(255, 255, 255, 0.6);
}

@media (max-width: 768px) {
  .filter-content {
    grid-template-columns: 1fr;
  }
  
  .filter-header {
    flex-direction: column;
    align-items: flex-start;
    gap: 12px;
  }
  
  .filter-footer {
    flex-direction: column;
    align-items: flex-start;
    gap: 12px;
  }
}
</style>