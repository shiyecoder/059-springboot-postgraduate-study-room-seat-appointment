<template>
  <div class="view">
    <PageHead title="操作日志" desc="关键业务操作留痕：代签、清座、规则调整、违约处理与黑名单解除">
      <el-tag type="info" effect="plain">共 {{ total }} 条</el-tag>
    </PageHead>

    <div class="card">
      <div class="bd">
        <div class="filters">
          <el-input v-model="q.keyword" placeholder="业务单号 / 操作内容" clearable style="width: 220px" @keyup.enter="search">
            <template #prefix><el-icon><Search /></el-icon></template>
          </el-input>
          <el-select v-model="q.module" placeholder="全部模块" clearable style="width: 150px">
            <el-option v-for="m in modules" :key="m" :label="m" :value="m" />
          </el-select>
          <el-button type="primary" @click="search">查询</el-button>
          <el-button @click="reset">重置</el-button>
        </div>
      </div>
    </div>

    <div class="card mt12">
      <el-table :data="rows" v-loading="loading" style="width: 100%">
        <el-table-column label="时间" width="168"><template #default="{ row }">{{ dt(row.operTime) }}</template></el-table-column>
        <el-table-column prop="module" label="模块" width="120" />
        <el-table-column label="操作类型" width="110">
          <template #default="{ row }"><span class="tag b">{{ row.operType }}</span></template>
        </el-table-column>
        <el-table-column label="业务单号" width="150"><template #default="{ row }"><span class="mono sm">{{ row.bizNo || '--' }}</span></template></el-table-column>
        <el-table-column prop="content" label="操作内容" min-width="260" />
        <el-table-column prop="operName" label="操作人" width="110" />
        <el-table-column prop="ip" label="IP" width="130"><template #default="{ row }"><span class="mono sm">{{ row.ip || '--' }}</span></template></el-table-column>
      </el-table>
      <el-pagination
        v-model:current-page="q.page"
        v-model:page-size="q.size"
        :total="total"
        :page-sizes="[10, 20, 50]"
        layout="total, sizes, prev, pager, next"
        background
        @current-change="load"
        @size-change="load"
      />
    </div>
  </div>
</template>

<script setup>
import { onMounted, reactive, ref } from 'vue'
import { Search } from '@element-plus/icons-vue'
import PageHead from '../components/PageHead.vue'
import { systemApi } from '../api'
import { dt } from '../utils/format'

const modules = ['预约管理', '违约管理', '规则配置', '黑名单', '自习室', '系统管理']
const q = reactive({ page: 1, size: 10, keyword: '', module: '' })
const rows = ref([])
const total = ref(0)
const loading = ref(false)

async function load() {
  loading.value = true
  try {
    const d = await systemApi.logs({ ...q, module: q.module || undefined })
    rows.value = d.records || []
    total.value = d.total || 0
  } finally {
    loading.value = false
  }
}

function search() {
  q.page = 1
  load()
}

function reset() {
  Object.assign(q, { page: 1, keyword: '', module: '' })
  load()
}

onMounted(load)
</script>
