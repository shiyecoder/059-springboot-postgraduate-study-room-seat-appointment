<template>
  <div class="view">
    <div class="card mb12">
      <div class="bd">
        <div class="steps">
          <div :class="{ on: step === 0, done: step > 0 }">选择自习室</div>
          <div :class="{ on: step === 1, done: step > 1 }">选择日期时段</div>
          <div :class="{ on: step === 2 }">选择座位</div>
        </div>
        <div class="filters">
          <el-select v-model="roomId" style="width: 240px" @change="onRoomChange">
            <el-option v-for="r in rooms" :key="r.id" :label="`${r.roomName}（${r.buildingName}）`" :value="r.id" />
          </el-select>
          <el-date-picker v-model="date" type="date" value-format="YYYY-MM-DD" :disabled-date="disabledDate" style="width: 148px" @change="loadMap" />
          <el-select v-model="slot" style="width: 190px" @change="loadMap">
            <el-option v-for="s in slotOptions" :key="s.start" :label="`${hhmm(s.start)}-${hhmm(s.end)}`" :value="s.start" />
          </el-select>
          <div class="fx1"></div>
          <div class="legend">
            <span><i style="background: #E9F2F1"></i>可选</span>
            <span><i style="background: #DCE7F0"></i>已被预约</span>
            <span><i style="background: #1D5B63"></i>使用中</span>
            <span><i style="background: #F0F1F2"></i>停用</span>
            <span><i style="background: #F3E9DC"></i>已选中</span>
          </div>
        </div>
      </div>
    </div>

    <div class="grid" style="grid-template-columns: 1fr 300px; align-items: start">
      <div class="card">
        <div class="hd">
          <h3>{{ room?.roomName || '--' }}</h3>
          <div class="sp"></div>
          <span class="sm tx3">可用 {{ map.summary?.available ?? 0 }} / {{ map.summary?.total ?? 0 }} 座</span>
        </div>
        <div class="bd">
          <div class="roomcard" v-if="room">
            <img :src="room.coverImage" alt="自习室实景" />
            <div>
              <div class="fx" style="gap: 8px">
                <b>{{ room.roomName }}</b>
                <span class="tag" :class="tone(room.status)">{{ room.status }}</span>
                <span class="tag b">{{ room.quietLevel }}</span>
              </div>
              <div class="sm tx2 mt8">
                {{ room.buildingName }} {{ room.floorNo }} 层 · 开放 {{ hhmm(room.openStart) }}-{{ hhmm(room.openEnd) }} ·
                今日已约 {{ room.todayBooked }} 人次
              </div>
              <div class="sm tx3 mt8">选好座位后系统锁定 10 分钟，请在锁定期内确认，逾期自动释放。</div>
            </div>
          </div>
          <div class="divider"></div>
          <div class="stage">讲台 / 入口方向</div>
          <div class="seatwrap" v-loading="loading">
            <div v-for="z in zones" :key="z.name" style="margin-bottom: 18px">
              <div class="fx mb8">
                <b style="font-size: 12.5px">{{ z.name }}</b>
                <span class="sm tx3">{{ z.list.length }} 座 · {{ z.type }}</span>
                <div class="fx1"></div>
                <span class="sm tx3">可选 {{ z.free }}</span>
              </div>
              <div class="sgrid" :style="{ gridTemplateColumns: `repeat(${z.cols}, 34px)` }">
                <button
                  v-for="s in z.list"
                  :key="s.id"
                  class="seat"
                  :class="[seatClass(s), { mine: picked?.id === s.id }]"
                  :disabled="s.status !== '可用'"
                  :title="`${s.seatNo} · ${s.status}`"
                  @click="pickSeat(s, z.name)"
                >
                  {{ s.colNo }}
                </button>
              </div>
            </div>
          </div>
        </div>
      </div>

      <div class="card" style="position: sticky; top: 80px">
        <div class="hd"><h3>预约确认</h3></div>
        <div class="bd">
          <dl class="dl">
            <dt>自习室</dt><dd>{{ room?.roomName || '--' }}</dd>
            <dt>日期</dt><dd>{{ date }}</dd>
            <dt>时段</dt><dd class="mono">{{ slotLabel }}</dd>
            <dt>座位</dt><dd>{{ picked ? `${picked.seatNo} · ${picked.zone}` : '未选择' }}</dd>
            <dt>座位类型</dt><dd>{{ picked ? picked.seatType : '--' }}</dd>
          </dl>
          <div class="alert mt12" v-if="picked">请确认本人使用，签到需输入预约码后 4 位；占用他人座位将判定违约。</div>
          <div class="alert a mt12" v-else>点击座位图中的空位进行选择</div>
          <el-button type="primary" style="width: 100%; margin-top: 12px" :disabled="!picked" :loading="saving" @click="submit">
            确认预约
          </el-button>
          <el-button v-if="picked" style="width: 100%; margin: 8px 0 0" @click="picked = null">重新选择</el-button>
          <div class="divider"></div>
          <div class="sm tx3" style="line-height: 1.8">
            规则提醒：同一时段仅可预约 1 个座位；开始后 15 分钟未签到判定违约；近 30 天累计 3 次违约进入黑名单。
          </div>
        </div>
      </div>
    </div>

    <el-dialog v-model="okDlg" title="预约成功" width="420px">
      <div class="alert g" style="background: var(--ok-l); color: var(--ok); border-color: #CFE2DF">
        <div>
          预约号 <b class="mono">{{ booked.bookingNo }}</b><br />
          座位 {{ booked.seatNo }} · {{ booked.timeRange }}<br />
          预约码后 4 位 <b class="mono" style="font-size: 16px">{{ booked.bookingCode }}</b>
        </div>
      </div>
      <div class="sm tx2 mt12">已为你锁定座位，请按时到馆并在开始前 15 分钟内完成签到。</div>
      <template #footer>
        <el-button @click="okDlg = false">继续选座</el-button>
        <el-button type="primary" @click="$router.push('/portal/bookings')">查看我的预约</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { bookingApi, roomApi } from '../api'
import { hhmm, localDate, tone } from '../utils/format'

const rooms = ref([])
const roomId = ref(null)
const date = ref(localDate())
const slot = ref('')
const map = ref({})
const rooms2 = ref({})
const loading = ref(false)
const saving = ref(false)
const picked = ref(null)
const okDlg = ref(false)
const booked = ref({})

const room = computed(() => rooms.value.find((r) => r.id === roomId.value))
const slotOptions = computed(() => map.value.slots || [])
const slotLabel = computed(() => {
  const s = slotOptions.value.find((x) => x.start === slot.value)
  return s ? `${hhmm(s.start)}-${hhmm(s.end)}` : '--'
})
const zones = computed(() =>
  Object.entries(map.value.zones || {}).map(([name, list]) => ({
    name,
    list,
    cols: Math.max(...list.map((s) => s.colNo || 1), 1),
    free: list.filter((s) => s.status === '可用').length,
    type: list[0]?.seatType || '普通'
  }))
)

function disabledDate(d0) {
  const today = new Date()
  today.setHours(0, 0, 0, 0)
  const max = new Date(today.getTime() + 7 * 86400000)
  return d0 < today || d0 > max
}

function seatClass(s) {
  return { 可用: '', 已预约: 'bk', 使用中: 'us', 停用: 'off', 维修: 'fx' }[s.status] ?? 'off'
}

function pickSeat(s, zone) {
  if (s.status !== '可用') return
  picked.value = { ...s, zone }
}

async function loadMap() {
  if (!roomId.value) return
  loading.value = true
  try {
    const d = await roomApi.seatMap({ roomId: roomId.value, date: date.value, startTime: slot.value || undefined })
    map.value = d
    if (!slot.value && d.slots?.length) slot.value = d.slots[0].start
    picked.value = null
  } finally {
    loading.value = false
  }
}

function onRoomChange() {
  slot.value = ''
  loadMap()
}

async function submit() {
  saving.value = true
  try {
    const s = slotOptions.value.find((x) => x.start === slot.value)
    const r = await bookingApi.book(picked.value.id, {
      bookingDate: date.value,
      startTime: s.start,
      endTime: s.end
    })
    booked.value = r
    okDlg.value = true
    loadMap()
  } finally {
    saving.value = false
  }
}

onMounted(async () => {
  const d = await roomApi.page({ page: 1, size: 50, status: '开放' })
  rooms.value = d.records || []
  roomId.value = rooms.value[0]?.id
  await loadMap()
})
</script>

<style scoped>
.roomcard {
  display: flex;
  gap: 14px;
  align-items: center;
  background: #fbfcfc;
  border: 1px solid var(--line);
  border-radius: 8px;
  padding: 10px 12px;
}
.roomcard img {
  width: 132px;
  height: 82px;
  object-fit: cover;
  border-radius: 6px;
  border: 1px solid var(--line);
  flex: 0 0 132px;
}
</style>
