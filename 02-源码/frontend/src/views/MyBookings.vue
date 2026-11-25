<template>
  <div class="view">
    <div class="fx mb12">
      <h2 style="font-size: 17px; font-weight: 600">我的预约</h2>
      <div class="sp fx1"></div>
      <el-button type="primary" size="small" @click="$router.push('/portal/select')">去选座</el-button>
    </div>

    <div class="card">
      <div class="bd">
        <div class="filters">
          <div class="seg">
            <a v-for="t in tabs" :key="t.value" :class="{ on: status === t.value }" @click="switchTab(t.value)">{{ t.label }}</a>
          </div>
          <div class="fx1"></div>
          <span class="sm tx3">共 {{ total }} 条预约记录</span>
        </div>
      </div>
    </div>

    <div class="card mt12">
      <div class="bd" v-loading="loading">
        <div v-if="rows.length" class="bk-list">
          <div v-for="b in rows" :key="b.id" class="pcard" :class="{ hot: b.status === '学习中' }">
            <div class="bh">
              <span class="code">{{ b.bookingNo }}</span>
              <div class="sp"></div>
              <span class="tag" :class="tone(b.riskLevel)">{{ b.riskLevel }}</span>
              <span class="tag" :class="tone(b.status)">{{ b.status }}</span>
            </div>
            <div class="brow">
              <span>座位 <b class="mono">{{ b.seatNo }}</b></span>
              <span>{{ b.roomName }} · {{ b.zoneName }}</span>
            </div>
            <div class="brow">
              <span>日期 <b>{{ b.bookingDate }}</b></span>
              <span>时段 <b>{{ b.timeRange }}</b></span>
              <span>来源 {{ b.source }}</span>
            </div>
            <div class="bft">
              <span class="sm tx3">
                <template v-if="b.status === '学习中'">已签到 {{ dt(b.checkinTime) }}</template>
                <template v-else-if="b.status === '已完成'">签到 {{ hhmm(b.checkinTime) }} · 签退 {{ hhmm(b.checkoutTime) }}</template>
                <template v-else-if="b.status === '已取消'">{{ b.cancelReason || '已取消' }}</template>
                <template v-else-if="b.status === '已违约'">已判定违约，可在「我的违约」查看或申诉</template>
                <template v-else>预约码 {{ b.bookingCode }}</template>
              </span>
              <div class="sp"></div>
              <el-button v-if="b.status === '待签到'" size="small" type="primary" @click="$router.push('/portal/checkin')">去签到</el-button>
              <el-button v-if="b.status === '学习中'" size="small" @click="checkout(b)">签退</el-button>
              <el-button v-if="['待生效', '待签到'].includes(b.status)" size="small" @click="cancel(b)">取消</el-button>
            </div>
          </div>
        </div>
        <div class="empty" v-else><b>暂无记录</b>切换状态筛选或去选一个座位</div>
      </div>
      <el-pagination
        v-model:current-page="page"
        :page-size="size"
        :total="total"
        layout="total, prev, pager, next"
        background
        @current-change="load"
      />
    </div>
  </div>
</template>

<script setup>
import { onMounted, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { bookingApi } from '../api'
import { dt, hhmm, tone } from '../utils/format'

const tabs = [
  { label: '全部', value: '' },
  { label: '待签到', value: '待签到' },
  { label: '学习中', value: '学习中' },
  { label: '已完成', value: '已完成' },
  { label: '已取消', value: '已取消' },
  { label: '已违约', value: '已违约' }
]

const status = ref('')
const rows = ref([])
const total = ref(0)
const page = ref(1)
const size = 6
const loading = ref(false)

async function load() {
  loading.value = true
  try {
    const d = await bookingApi.portalBookings({ page: page.value, size, status: status.value || undefined })
    rows.value = d.records || []
    total.value = d.total || 0
  } finally {
    loading.value = false
  }
}

function switchTab(v) {
  status.value = v
  page.value = 1
  load()
}

async function cancel(b) {
  const { value } = await ElMessageBox.prompt('请填写取消原因（开始前 30 分钟内取消将计违约一次）', '取消预约', {
    inputPlaceholder: '如：临时有课，改约晚上',
    confirmButtonText: '确认取消',
    cancelButtonText: '返回'
  })
  if (!value || value.trim().length < 2) return ElMessage.warning('请填写取消原因')
  const r = await bookingApi.cancel(b.id, { reason: value })
  ElMessage[r.lateCancel ? 'warning' : 'success'](r.lateCancel ? '已取消，本次计违约一次' : '已取消，本次不计违约')
  load()
}

async function checkout(b) {
  await bookingApi.checkout(b.id)
  ElMessage.success('已签退，学习时长已计入报告')
  load()
}

onMounted(load)
</script>

<style scoped>
.bk-list {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 12px;
}
</style>
