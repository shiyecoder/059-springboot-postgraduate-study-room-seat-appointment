<template>
  <div class="view">
    <PageHead title="黑名单管理" desc="近 30 天累计 3 次违约自动进入黑名单，学工老师可减免解除">
      <el-tag type="info" effect="plain">生效中 {{ activeCount }} 人</el-tag>
    </PageHead>

    <div class="card">
      <div class="bd">
        <div class="filters">
          <el-input v-model="q.keyword" placeholder="学号 / 姓名" clearable style="width: 200px" @keyup.enter="search">
            <template #prefix><el-icon><Search /></el-icon></template>
          </el-input>
          <el-select v-model="q.status" placeholder="全部状态" clearable style="width: 130px">
            <el-option v-for="s in statuses" :key="s" :label="s" :value="s" />
          </el-select>
          <el-select v-model="q.reason" placeholder="全部原因" clearable style="width: 170px">
            <el-option v-for="s in reasons" :key="s" :label="s" :value="s" />
          </el-select>
          <el-button type="primary" @click="search">查询</el-button>
          <el-button @click="reset">重置</el-button>
        </div>
      </div>
    </div>

    <div class="card mt12">
      <el-table :data="rows" v-loading="loading" style="width: 100%">
        <el-table-column label="学生" width="160">
          <template #default="{ row }">
            <div>{{ row.studentName }}</div>
            <div class="sm tx3">{{ row.studentNo }}</div>
          </template>
        </el-table-column>
        <el-table-column prop="college" label="学院" width="140" />
        <el-table-column label="加入原因" width="130">
          <template #default="{ row }"><span class="tag r">{{ row.reasonType }}</span></template>
        </el-table-column>
        <el-table-column label="起止日期" width="190">
          <template #default="{ row }">{{ row.startDate }} 至 {{ row.endDate }}</template>
        </el-table-column>
        <el-table-column label="时长" width="80"><template #default="{ row }">{{ row.days }} 天</template></el-table-column>
        <el-table-column label="关联违约单" min-width="200">
          <template #default="{ row }">
            <span class="mono sm">{{ row.violationNos || '--' }}</span>
          </template>
        </el-table-column>
        <el-table-column label="解除方式" width="110"><template #default="{ row }">{{ row.releaseType }}</template></el-table-column>
        <el-table-column label="状态" width="92">
          <template #default="{ row }"><span class="tag" :class="tone(row.status)">{{ row.status }}</span></template>
        </el-table-column>
        <el-table-column label="操作" width="150" fixed="right">
          <template #default="{ row }">
            <div class="row-acts">
              <el-button link type="primary" size="small" :disabled="row.status !== '生效中'" @click="openRelease(row)">减免解除</el-button>
              <el-button link type="danger" size="small" :disabled="row.status !== '生效中'" @click="releaseNow(row)">立即解除</el-button>
            </div>
          </template>
        </el-table-column>
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

    <el-dialog v-model="dlg" title="减免解除黑名单" width="460px">
      <div class="alert a mb12" v-if="cur">
        {{ cur.studentName }} · {{ cur.reasonType }} · 原截止 {{ cur.endDate }}
      </div>
      <el-form label-width="90px">
        <el-form-item label="减免天数">
          <el-input-number v-model="form.days" :min="0" :max="30" controls-position="right" />
          <span class="sm tx3" style="margin-left: 8px">0 表示立即解除</span>
        </el-form-item>
        <el-form-item label="减免说明">
          <el-input v-model="form.remark" type="textarea" :rows="3" maxlength="120" show-word-limit placeholder="如：已参加场馆志愿服务，经学工办同意减免" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dlg = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="doRelease">确认</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { onMounted, reactive, ref } from 'vue'
import { Search } from '@element-plus/icons-vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import PageHead from '../components/PageHead.vue'
import { violationApi } from '../api'
import { tone } from '../utils/format'

const statuses = ['生效中', '已解除', '已过期']
const reasons = ['违约3次', '违约5次', '占座不来', '管理员人工加入']

const q = reactive({ page: 1, size: 10, keyword: '', status: '', reason: '' })
const rows = ref([])
const total = ref(0)
const activeCount = ref(0)
const loading = ref(false)
const dlg = ref(false)
const saving = ref(false)
const cur = ref(null)
const form = reactive({ days: 0, remark: '' })

async function load() {
  loading.value = true
  try {
    const [d1, d2] = await Promise.all([violationApi.blacklist(q), violationApi.blacklist({ page: 1, size: 1, status: '生效中' })])
    rows.value = d1.records || []
    total.value = d1.total || 0
    activeCount.value = d2.total || 0
  } finally {
    loading.value = false
  }
}

function search() {
  q.page = 1
  load()
}

function reset() {
  Object.assign(q, { page: 1, keyword: '', status: '', reason: '' })
  load()
}

function openRelease(row) {
  cur.value = row
  form.days = 0
  form.remark = ''
  dlg.value = true
}

async function doRelease() {
  if (form.remark.trim().length < 5) return ElMessage.warning('请填写不少于 5 个字的减免说明')
  saving.value = true
  try {
    await violationApi.release({ id: cur.value.id, days: form.days, remark: form.remark })
    ElMessage.success('已解除并通知学生')
    dlg.value = false
    load()
  } finally {
    saving.value = false
  }
}

async function releaseNow(row) {
  await ElMessageBox.confirm(`确认立即解除 ${row.studentName} 的黑名单限制？`, '立即解除', { type: 'warning', confirmButtonText: '解除' })
  await violationApi.release({ id: row.id, days: 0, remark: '管理员核实后立即解除' })
  ElMessage.success('已解除')
  load()
}

onMounted(load)
</script>
