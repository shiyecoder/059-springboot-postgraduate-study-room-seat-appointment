<template>
  <div class="view">
    <PageHead title="预约管理" desc="覆盖待生效、待签到、学习中、已完成、已取消与已违约全状态">
      <div class="seg">
        <a v-for="t in tabs" :key="t.value" :class="{ on: q.status === t.value }" @click="switchTab(t.value)">{{ t.label }}</a>
      </div>
      <el-button size="small" :icon="Download" @click="exportCsv">导出</el-button>
    </PageHead>

    <div class="card">
      <div class="bd">
        <div class="filters">
          <el-input v-model="q.keyword" placeholder="预约号 / 学号 / 姓名" clearable style="width: 210px" @keyup.enter="search">
            <template #prefix><el-icon><Search /></el-icon></template>
          </el-input>
          <el-select v-model="q.roomId" placeholder="全部自习室" clearable style="width: 200px">
            <el-option v-for="r in rooms" :key="r.id" :label="r.roomName" :value="r.id" />
          </el-select>
          <el-date-picker v-model="q.date" type="date" value-format="YYYY-MM-DD" placeholder="预约日期" style="width: 148px" />
          <el-button type="primary" @click="search">查询</el-button>
          <el-button @click="reset">重置</el-button>
          <div class="fx1"></div>
          <span class="sm tx3">共 {{ total }} 条记录</span>
        </div>
      </div>
    </div>

    <div class="card mt12">
      <el-table :data="rows" v-loading="loading" style="width: 100%">
        <el-table-column prop="bookingNo" label="预约号" width="130">
          <template #default="{ row }">
            <router-link :to="`/booking/detail/${row.id}`" class="mono" style="color: var(--pri)">{{ row.bookingNo }}</router-link>
          </template>
        </el-table-column>
        <el-table-column label="学生" width="160">
          <template #default="{ row }">
            <div>{{ row.studentName }}</div>
            <div class="sm tx3">{{ row.studentNo }} · {{ row.college }}</div>
          </template>
        </el-table-column>
        <el-table-column label="座位 / 自习室" min-width="176">
          <template #default="{ row }">
            <div class="mono">{{ row.seatNo }}</div>
            <div class="sm tx3">{{ row.roomName }} · {{ row.zoneName }}</div>
          </template>
        </el-table-column>
        <el-table-column label="时段" width="150">
          <template #default="{ row }">
            <div>{{ row.bookingDate }}</div>
            <div class="sm tx3">{{ row.timeRange }}</div>
          </template>
        </el-table-column>
        <el-table-column label="状态" width="88">
          <template #default="{ row }"><span class="tag" :class="tone(row.status)">{{ row.status }}</span></template>
        </el-table-column>
        <el-table-column label="风险" width="78">
          <template #default="{ row }"><span class="tag" :class="tone(row.riskLevel)">{{ row.riskLevel }}</span></template>
        </el-table-column>
        <el-table-column prop="source" label="来源" width="88" />
        <el-table-column label="操作" width="190" fixed="right">
          <template #default="{ row }">
            <div class="row-acts">
              <el-button link type="primary" size="small" @click="$router.push(`/booking/detail/${row.id}`)">详情</el-button>
              <el-button link type="primary" size="small" :disabled="!row.canCheckin" @click="openCheckin(row)">核验签到</el-button>
              <el-button link type="primary" size="small" :disabled="!['待生效', '待签到'].includes(row.status)" @click="openCancel(row)">取消</el-button>
              <el-button link type="danger" size="small" :disabled="row.status !== '学习中'" @click="openClear(row)">清座</el-button>
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

    <el-dialog v-model="cancelDlg" title="取消预约" width="460px">
      <div class="alert a mb12">
        {{ cancelRow?.bookingNo }} · {{ cancelRow?.studentName }} · {{ cancelRow?.bookingDate }} {{ cancelRow?.timeRange }}
        <br />开始前 30 分钟内取消将判定「占座不来」违约并扣信用分。
      </div>
      <el-input v-model="cancelReason" type="textarea" :rows="3" maxlength="120" show-word-limit placeholder="请填写取消原因（不少于 2 个字）" />
      <template #footer>
        <el-button @click="cancelDlg = false">返回</el-button>
        <el-button type="primary" :loading="saving" @click="doCancel">确认取消</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="checkinDlg" title="核验签到（管理员代操作）" width="420px">
      <div class="alert mb12">{{ checkinRow?.bookingNo }} · {{ checkinRow?.studentName }} · {{ checkinRow?.timeRange }}</div>
      <el-form label-width="76px">
        <el-form-item label="预约码">
          <el-input v-model="checkinCode" maxlength="4" placeholder="后 4 位（可留空由管理员直接放行）" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="checkinDlg = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="doCheckin">确认签到</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="clearDlg" title="管理员清座" width="480px">
      <div class="alert r mb12">清座将判定「占座不来」违约（扣 5 分）并释放座位，请确认现场无人。</div>
      <el-form label-width="76px">
        <el-form-item label="清座原因" required>
          <el-input v-model="clearReason" type="textarea" :rows="3" maxlength="120" show-word-limit placeholder="不少于 5 个字，例如：物品留置超 30 分钟，现场无人" />
        </el-form-item>
        <el-form-item label="现场照片">
          <el-upload :show-file-list="false" :http-request="uploadPhoto" accept="image/*">
            <img v-if="clearPhoto" :src="clearPhoto" class="thumb" style="width: 64px; height: 64px" alt="照片" />
            <el-button v-else size="small" :icon="Camera">拍照留痕</el-button>
          </el-upload>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="clearDlg = false">取消</el-button>
        <el-button type="danger" :loading="saving" @click="doClear">确认清座</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { onMounted, reactive, ref } from 'vue'
import { Camera, Download, Search } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import PageHead from '../components/PageHead.vue'
import { bookingApi, fileApi, roomApi } from '../api'
import { tone } from '../utils/format'

const tabs = [
  { label: '全部', value: '' },
  { label: '待签到', value: '待签到' },
  { label: '学习中', value: '学习中' },
  { label: '已完成', value: '已完成' },
  { label: '已违约', value: '已违约' }
]

const q = reactive({ page: 1, size: 7, keyword: '', status: '', roomId: null, date: '' })
const rows = ref([])
const total = ref(0)
const loading = ref(false)
const saving = ref(false)
const rooms = ref([])

const cancelDlg = ref(false)
const cancelRow = ref(null)
const cancelReason = ref('')
const checkinDlg = ref(false)
const checkinRow = ref(null)
const checkinCode = ref('')
const clearDlg = ref(false)
const clearRow = ref(null)
const clearReason = ref('')
const clearPhoto = ref('')

async function load() {
  loading.value = true
  try {
    const d = await bookingApi.page({ ...q, roomId: q.roomId || undefined, status: q.status || undefined })
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

function switchTab(status) {
  q.status = status
  q.page = 1
  load()
}

function reset() {
  Object.assign(q, { page: 1, keyword: '', status: '', roomId: null, date: '' })
  load()
}

function openCancel(row) {
  cancelRow.value = row
  cancelReason.value = ''
  cancelDlg.value = true
}

async function doCancel() {
  if (cancelReason.value.trim().length < 2) return ElMessage.warning('请填写取消原因')
  saving.value = true
  try {
    const r = await bookingApi.cancel(cancelRow.value.id, { reason: cancelReason.value })
    ElMessage[r.lateCancel ? 'warning' : 'success'](r.lateCancel ? '已取消，因临近开始时间计违约一次' : '已取消，本次不计违约')
    cancelDlg.value = false
    load()
  } finally {
    saving.value = false
  }
}

function openCheckin(row) {
  checkinRow.value = row
  checkinCode.value = ''
  checkinDlg.value = true
}

async function doCheckin() {
  saving.value = true
  try {
    await bookingApi.checkin(checkinRow.value.id, { code: checkinCode.value, operator: 'admin' })
    ElMessage.success('已签到，状态更新为学习中')
    checkinDlg.value = false
    load()
  } finally {
    saving.value = false
  }
}

function openClear(row) {
  clearRow.value = row
  clearReason.value = ''
  clearPhoto.value = ''
  clearDlg.value = true
}

async function uploadPhoto(opt) {
  const r = await fileApi.upload(opt.file, 'clear')
  clearPhoto.value = r.url
  ElMessage.success('照片已上传')
}

async function doClear() {
  if (clearReason.value.trim().length < 5) return ElMessage.warning('清座原因不少于 5 个字')
  saving.value = true
  try {
    await bookingApi.clear(clearRow.value.id, { reason: clearReason.value, photo: clearPhoto.value })
    ElMessage.success('已清座并记录违约')
    clearDlg.value = false
    load()
  } finally {
    saving.value = false
  }
}

function exportCsv() {
  const head = ['预约号', '学生', '学号', '学院', '座位', '自习室', '日期', '时段', '状态', '来源']
  const body = rows.value.map((r) => [r.bookingNo, r.studentName, r.studentNo, r.college, r.seatNo, r.roomName, r.bookingDate, r.timeRange, r.status, r.source])
  const csv = [head, ...body].map((r) => r.join(',')).join('\n')
  const blob = new Blob([`\ufeff${csv}`], { type: 'text/csv;charset=utf-8' })
  const a = document.createElement('a')
  a.href = URL.createObjectURL(blob)
  a.download = '预约管理.csv'
  a.click()
  URL.revokeObjectURL(a.href)
  ElMessage.success('已导出当前页')
}

onMounted(async () => {
  const d = await roomApi.page({ page: 1, size: 50 })
  rooms.value = d.records || []
  load()
})
</script>
