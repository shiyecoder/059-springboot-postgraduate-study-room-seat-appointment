<template>
  <div class="view">
    <PageHead title="签到核验台" desc="预约码后 4 位核验，签到窗口为时段开始前 15 分钟至开始后 15 分钟">
      <el-radio-group v-model="roomFilter" size="small" @change="load">
        <el-radio-button :value="null">全部自习室</el-radio-button>
        <el-radio-button v-for="r in rooms.slice(0, 4)" :key="r.id" :value="r.id">{{ r.roomName }}</el-radio-button>
      </el-radio-group>
    </PageHead>

    <div class="grid g21">
      <div class="card">
        <div class="hd">
          <h3>今日待签到预约</h3>
          <div class="sp"></div>
          <span class="tag a">待签到 {{ pending.length }}</span>
          <span class="tag g">已完成 {{ doneCount }}</span>
        </div>
        <div class="bd np">
          <el-table :data="pending" v-loading="loading" height="420" highlight-current-row @current-change="pick">
            <el-table-column prop="bookingNo" label="预约号" width="132">
              <template #default="{ row }"><span class="mono">{{ row.bookingNo }}</span></template>
            </el-table-column>
            <el-table-column label="学生" width="130">
              <template #default="{ row }">
                <div>{{ row.studentName }}</div>
                <div class="sm tx3">{{ row.studentNo }}</div>
              </template>
            </el-table-column>
            <el-table-column label="座位" width="120">
              <template #default="{ row }"><span class="mono">{{ row.seatNo }}</span><div class="sm tx3">{{ row.zoneName }}</div></template>
            </el-table-column>
            <el-table-column label="时段" width="110"><template #default="{ row }">{{ row.timeRange }}</template></el-table-column>
            <el-table-column label="窗口" min-width="150">
              <template #default="{ row }">
                <span class="tag" :class="windowState(row).tone">{{ windowState(row).text }}</span>
              </template>
            </el-table-column>
          </el-table>
        </div>
      </div>

      <div class="card">
        <div class="hd"><h3>核验签到</h3><div class="sp"></div><span class="sm tx3">预约码后 4 位</span></div>
        <div class="bd">
          <div v-if="cur" class="alert mb12">
            <div>
              <b>{{ cur.studentName }}</b> · {{ cur.seatNo }} · {{ cur.timeRange }}<br />
              <span class="sm">{{ cur.roomName }} · {{ cur.bookingDate }}</span>
            </div>
          </div>
          <div v-else class="alert a mb12">请先在左侧选择一条待签到预约</div>

          <div class="pinbox">
            <span v-for="i in 4" :key="i" :class="{ act: code.length === i - 1 }">{{ code[i - 1] || '' }}</span>
          </div>

          <div class="keypad">
            <button v-for="n in 9" :key="n" @click="type(String(n))">{{ n }}</button>
            <button @click="clearCode">清空</button>
            <button @click="type('0')">0</button>
            <button @click="backspace">←</button>
          </div>

          <el-button type="primary" style="width: 100%; margin-top: 14px" :loading="saving" :disabled="!cur || code.length < 4" @click="submit">
            确认签到
          </el-button>
          <div class="sm tx3 mt8">管理员代签可不校验预约码；逾期未签到将自动判定「未签到」违约并释放座位。</div>
        </div>
      </div>
    </div>

    <div class="card mt12">
      <div class="hd"><h3>今日签到流水</h3><div class="sp"></div><span class="sm tx3">最近 12 条</span></div>
      <el-table :data="recent" style="width: 100%">
        <el-table-column prop="bookingNo" label="预约号" width="140"><template #default="{ row }"><span class="mono">{{ row.bookingNo }}</span></template></el-table-column>
        <el-table-column prop="studentName" label="学生" width="100" />
        <el-table-column prop="seatNo" label="座位" width="120" />
        <el-table-column label="签到时间" width="160"><template #default="{ row }">{{ dt(row.checkinTime) }}</template></el-table-column>
        <el-table-column label="时段" width="120"><template #default="{ row }">{{ row.timeRange }}</template></el-table-column>
        <el-table-column label="状态" min-width="100"><template #default="{ row }"><span class="tag" :class="tone(row.status)">{{ row.status }}</span></template></el-table-column>
      </el-table>
    </div>
  </div>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import PageHead from '../components/PageHead.vue'
import { bookingApi, roomApi } from '../api'
import { dt, tone } from '../utils/format'

const rooms = ref([])
const roomFilter = ref(null)
const pending = ref([])
const recent = ref([])
const doneCount = ref(0)
const loading = ref(false)
const saving = ref(false)
const cur = ref(null)
const code = ref('')

function windowState(row) {
  const now = new Date()
  const start = new Date(`${row.bookingDate}T${row.startTime}`)
  const open = new Date(start.getTime() - 15 * 60000)
  const close = new Date(start.getTime() + 15 * 60000)
  if (now < open) return { text: `未开放（${open.toTimeString().slice(0, 5)} 开放）`, tone: '' }
  if (now > close) return { text: '已超时', tone: 'r' }
  return { text: '可签到', tone: 'g' }
}

function pick(row) {
  if (!row) return
  cur.value = row
  code.value = ''
}

function type(n) {
  if (code.value.length >= 4) return
  code.value += n
  if (code.value.length === 4) submit()
}

function backspace() {
  code.value = code.value.slice(0, -1)
}

function clearCode() {
  code.value = ''
}

async function submit() {
  if (!cur.value) return ElMessage.warning('请先选择预约')
  if (code.value.length < 4) return ElMessage.warning('请输入 4 位预约码')
  saving.value = true
  try {
    await bookingApi.checkin(cur.value.id, { code: code.value, operator: 'admin', remark: '核验台现场核对预约码后代签' })
    ElMessage.success(`${cur.value.studentName} 签到成功`)
    cur.value = null
    code.value = ''
    load()
  } finally {
    saving.value = false
  }
}

async function load() {
  loading.value = true
  try {
    const [p, d, r] = await Promise.all([
      bookingApi.page({ page: 1, size: 50, status: '待签到', roomId: roomFilter.value || undefined }),
      bookingApi.page({ page: 1, size: 1, status: '学习中' }),
      bookingApi.page({ page: 1, size: 12, status: '已完成' })
    ])
    pending.value = p.records || []
    doneCount.value = d.total || 0
    recent.value = (r.records || []).filter((x) => x.checkinTime)
  } finally {
    loading.value = false
  }
}

onMounted(async () => {
  const d = await roomApi.page({ page: 1, size: 20 })
  rooms.value = d.records || []
  load()
})
</script>

<style scoped>
.pinbox span {
  flex: 1;
}
</style>
