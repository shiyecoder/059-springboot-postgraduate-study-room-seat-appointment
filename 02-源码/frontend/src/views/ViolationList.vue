<template>
  <div class="view">
    <PageHead title="违约记录" desc="未签到、迟到、提前离座、未签退、占座不来五类违约，支持申诉与撤销">
      <el-button size="small" :icon="Download" @click="exportCsv">导出</el-button>
    </PageHead>

    <div class="card">
      <div class="bd">
        <div class="filters">
          <el-input v-model="q.keyword" placeholder="违约单号 / 学号 / 姓名" clearable style="width: 210px" @keyup.enter="search">
            <template #prefix><el-icon><Search /></el-icon></template>
          </el-input>
          <el-select v-model="q.type" placeholder="全部违约类型" clearable style="width: 168px">
            <el-option v-for="t in types" :key="t" :label="t" :value="t" />
          </el-select>
          <el-select v-model="q.handleStatus" placeholder="全部处理状态" clearable style="width: 150px">
            <el-option v-for="s in statuses" :key="s" :label="s" :value="s" />
          </el-select>
          <el-button type="primary" @click="search">查询</el-button>
          <el-button @click="reset">重置</el-button>
          <div class="fx1"></div>
          <span class="sm tx3">待处理 {{ pendingCount }} 条 · 共 {{ total }} 条</span>
        </div>
      </div>
    </div>

    <div class="card mt12">
      <el-table :data="rows" v-loading="loading" style="width: 100%">
        <el-table-column prop="recordNo" label="违约单号" width="132">
          <template #default="{ row }">
            <router-link :to="`/violation/detail/${row.id}`" class="mono" style="color: var(--pri)">{{ row.recordNo }}</router-link>
          </template>
        </el-table-column>
        <el-table-column label="学生" width="158">
          <template #default="{ row }">
            <div>{{ row.studentName }}</div>
            <div class="sm tx3">{{ row.studentNo }} · {{ row.college }}</div>
          </template>
        </el-table-column>
        <el-table-column prop="violationType" label="违约类型" width="126" />
        <el-table-column label="发生时间" width="152"><template #default="{ row }">{{ dt(row.occurTime) }}</template></el-table-column>
        <el-table-column label="扣分" width="76" align="right">
          <template #default="{ row }"><span style="color: var(--dngr)">-{{ row.deductScore }}</span></template>
        </el-table-column>
        <el-table-column label="当前信用分" width="106" align="right">
          <template #default="{ row }">
            <span :style="{ color: row.creditScore < 80 ? 'var(--dngr)' : 'var(--tx)' }">{{ row.creditScore }}</span>
          </template>
        </el-table-column>
        <el-table-column label="处理状态" width="104">
          <template #default="{ row }"><span class="tag" :class="tone(row.handleStatus)">{{ row.handleStatus }}</span></template>
        </el-table-column>
        <el-table-column label="申诉截止" width="120"><template #default="{ row }">{{ d(row.appealDeadline) }}</template></el-table-column>
        <el-table-column label="操作" width="182" fixed="right">
          <template #default="{ row }">
            <div class="row-acts">
              <el-button link type="primary" size="small" @click="$router.push(`/violation/detail/${row.id}`)">详情</el-button>
              <el-button link type="primary" size="small" :disabled="row.handleStatus === '已撤销' || row.handleStatus === '已确认'" @click="openHandle(row)">处理</el-button>
              <el-button link type="primary" size="small" @click="$router.push(`/insight/risk/${row.studentId}`)">风险评分</el-button>
            </div>
          </template>
        </el-table-column>
      </el-table>
      <el-pagination
        v-model:current-page="q.page"
        v-model:page-size="q.size"
        :total="total"
        :page-sizes="[7, 14, 35]"
        layout="total, sizes, prev, pager, next"
        background
        @current-change="load"
        @size-change="load"
      />
    </div>

    <el-dialog v-model="dlg" title="处理违约" width="480px">
      <div class="alert mb12" v-if="cur">
        {{ cur.recordNo }} · {{ cur.studentName }} · {{ cur.violationType }} · 扣 {{ cur.deductScore }} 分
        <template v-if="cur.appealReason"><br />申诉理由：{{ cur.appealReason }}</template>
      </div>
      <el-form label-width="84px">
        <el-form-item label="处理结论">
          <el-radio-group v-model="form.revoke">
            <el-radio-button :value="false">维持违约</el-radio-button>
            <el-radio-button :value="true">撤销违约</el-radio-button>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="处理意见">
          <el-input v-model="form.result" type="textarea" :rows="3" maxlength="120" show-word-limit placeholder="不少于 5 个字，将通知学生" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dlg = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="doHandle">提交</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { onMounted, reactive, ref } from 'vue'
import { Download, Search } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import PageHead from '../components/PageHead.vue'
import { violationApi } from '../api'
import { d, dt, tone } from '../utils/format'

const types = ['未签到', '迟到超15分钟', '提前离座', '未签退', '占座不来']
const statuses = ['待处理', '申诉中', '已确认', '已撤销']

const q = reactive({ page: 1, size: 7, keyword: '', type: '', handleStatus: '' })
const rows = ref([])
const total = ref(0)
const pendingCount = ref(0)
const loading = ref(false)
const dlg = ref(false)
const saving = ref(false)
const cur = ref(null)
const form = reactive({ revoke: false, result: '' })

async function load() {
  loading.value = true
  try {
    const [d1, d2] = await Promise.all([
      violationApi.page(q),
      violationApi.page({ page: 1, size: 1, handleStatus: '待处理' })
    ])
    rows.value = d1.records || []
    total.value = d1.total || 0
    pendingCount.value = d2.total || 0
  } finally {
    loading.value = false
  }
}

function search() {
  q.page = 1
  load()
}

function reset() {
  Object.assign(q, { page: 1, keyword: '', type: '', handleStatus: '' })
  load()
}

function openHandle(row) {
  cur.value = row
  form.revoke = false
  form.result = ''
  dlg.value = true
}

async function doHandle() {
  if (form.result.trim().length < 5) return ElMessage.warning('处理意见不少于 5 个字')
  saving.value = true
  try {
    await violationApi.handle(cur.value.id, { revoke: form.revoke, result: form.result })
    ElMessage.success(form.revoke ? '已撤销违约并恢复信用分' : '已维持违约，已通知学生')
    dlg.value = false
    load()
  } finally {
    saving.value = false
  }
}

function exportCsv() {
  const head = ['违约单号', '学生', '学号', '学院', '类型', '发生时间', '扣分', '信用分', '状态']
  const body = rows.value.map((r) => [r.recordNo, r.studentName, r.studentNo, r.college, r.violationType, dt(r.occurTime), r.deductScore, r.creditScore, r.handleStatus])
  const csv = [head, ...body].map((r) => r.join(',')).join('\n')
  const blob = new Blob([`\ufeff${csv}`], { type: 'text/csv;charset=utf-8' })
  const a = document.createElement('a')
  a.href = URL.createObjectURL(blob)
  a.download = '违约记录.csv'
  a.click()
  URL.revokeObjectURL(a.href)
  ElMessage.success('已导出当前页')
}

onMounted(load)
</script>
