<template>
  <div class="view">
    <div class="fx mb12">
      <h2 style="font-size: 17px; font-weight: 600">签到</h2>
      <div class="sp fx1"></div>
      <span class="sm tx3">签到窗口：时段开始前 15 分钟至开始后 15 分钟</span>
    </div>

    <div class="grid g21" style="align-items: start">
      <div class="card">
        <div class="hd"><h3>可签到预约</h3><div class="sp"></div><span class="tag a">{{ list.length }} 条</span></div>
        <div class="bd np" v-loading="loading">
          <div v-for="b in list" :key="b.id" class="ci-item" :class="{ on: cur?.id === b.id }" @click="pick(b)">
            <div class="fx">
              <span class="mono code">{{ b.bookingNo }}</span>
              <div class="fx1"></div>
              <span class="tag" :class="tone(b.status)">{{ b.status }}</span>
            </div>
            <div class="sm tx2" style="margin-top: 4px">
              {{ b.roomName }} · {{ b.seatNo }} · {{ b.timeRange }}
            </div>
          </div>
          <div class="empty" v-if="!loading && !list.length"><b>暂无可签到预约</b>预约时段开始前 15 分钟开放签到</div>
        </div>
      </div>

      <div class="card">
        <div class="hd"><h3>输入预约码</h3></div>
        <div class="bd">
          <div v-if="cur" class="alert mb12">
            <div>
              <b>{{ cur.roomName }}</b> · {{ cur.seatNo }}<br />
              <span class="sm">{{ cur.bookingDate }} {{ cur.timeRange }} · 预约码后 4 位 <b class="mono">{{ cur.bookingCode }}</b></span>
            </div>
          </div>
          <div v-else class="alert a mb12">请先选择一条待签到预约</div>

          <div class="pinbox">
            <span v-for="i in 4" :key="i" :class="{ act: code.length === i - 1 }">{{ code[i - 1] || '' }}</span>
          </div>
          <div class="keypad">
            <button v-for="n in 9" :key="n" @click="type(String(n))">{{ n }}</button>
            <button @click="code = ''">清空</button>
            <button @click="type('0')">0</button>
            <button @click="code = code.slice(0, -1)">←</button>
          </div>
          <el-button type="primary" style="width: 100%; margin-top: 14px" :loading="saving" :disabled="!cur || code.length < 4" @click="submit">
            确认签到
          </el-button>
          <div class="sm tx3 mt8">超过开始后 15 分钟未签到，系统判定「未签到」违约并释放座位。</div>
        </div>
      </div>
    </div>

    <div class="card mt12">
      <div class="hd"><h3>签到记录</h3><div class="sp"></div><span class="sm tx3">最近 10 条</span></div>
      <el-table :data="recent" style="width: 100%">
        <el-table-column prop="bookingNo" label="预约号" width="140"><template #default="{ row }"><span class="mono">{{ row.bookingNo }}</span></template></el-table-column>
        <el-table-column prop="seatNo" label="座位" width="120" />
        <el-table-column prop="roomName" label="自习室" min-width="160" />
        <el-table-column label="时段" width="120"><template #default="{ row }">{{ row.timeRange }}</template></el-table-column>
        <el-table-column label="签到时间" width="160"><template #default="{ row }">{{ dt(row.checkinTime) }}</template></el-table-column>
        <el-table-column label="状态" width="96"><template #default="{ row }"><span class="tag" :class="tone(row.status)">{{ row.status }}</span></template></el-table-column>
      </el-table>
    </div>
  </div>
</template>

<script setup>
import { onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { bookingApi } from '../api'
import { dt, tone } from '../utils/format'

const list = ref([])
const recent = ref([])
const loading = ref(false)
const saving = ref(false)
const cur = ref(null)
const code = ref('')

async function load() {
  loading.value = true
  try {
    const [d, r] = await Promise.all([
      bookingApi.portalBookings({ page: 1, size: 30, status: '待签到' }),
      bookingApi.portalBookings({ page: 1, size: 10 })
    ])
    list.value = d.records || []
    recent.value = (r.records || []).filter((x) => x.checkinTime)
    if (list.value.length && !cur.value) cur.value = list.value[0]
  } finally {
    loading.value = false
  }
}

function pick(b) {
  cur.value = b
  code.value = ''
}

function type(n) {
  if (code.value.length >= 4) return
  code.value += n
}

async function submit() {
  if (!cur.value) return ElMessage.warning('请先选择预约')
  if (code.value.length < 4) return ElMessage.warning('请输入 4 位预约码')
  saving.value = true
  try {
    await bookingApi.checkin(cur.value.id, { code: code.value })
    ElMessage.success('签到成功，开始学习吧')
    cur.value = null
    code.value = ''
    load()
  } finally {
    saving.value = false
  }
}

onMounted(load)
</script>

<style scoped>
.ci-item {
  padding: 11px 16px;
  border-bottom: 1px solid #f0f2f3;
  cursor: pointer;
}
.ci-item:last-child {
  border-bottom: 0;
}
.ci-item:hover {
  background: #fbfcfc;
}
.ci-item.on {
  background: var(--pri-l);
  border-left: 3px solid var(--pri);
  padding-left: 13px;
}
.ci-item .code {
  color: var(--pri);
  font-weight: 600;
}
.pinbox span {
  flex: 1;
}
</style>
