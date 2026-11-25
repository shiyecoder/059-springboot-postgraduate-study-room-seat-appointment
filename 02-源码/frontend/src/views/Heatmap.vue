<template>
  <div class="view">
    <PageHead title="需求热力" desc="按日期 × 时段统计座位需求强度，识别持续紧张的时段与座位缺口">
      <el-select v-model="roomId" style="width: 220px" size="small" @change="load">
        <el-option v-for="r in rooms" :key="r.id" :label="r.roomName" :value="r.id" />
      </el-select>
      <el-radio-group v-model="days" size="small" @change="load">
        <el-radio-button :value="7">近 7 天</el-radio-button>
        <el-radio-button :value="14">近 14 天</el-radio-button>
        <el-radio-button :value="30">近 30 天</el-radio-button>
      </el-radio-group>
    </PageHead>

    <div class="grid g4 mb12">
      <div class="kpi">
        <div class="lb">峰值占用率</div>
        <div class="vl">{{ peak }}<small>%</small></div>
        <div class="df">{{ peakSlot }}</div>
      </div>
      <div class="kpi a">
        <div class="lb">紧张时段</div>
        <div class="vl">{{ tight.length }}<small>个</small></div>
        <div class="df">7 天均值超 85%</div>
      </div>
      <div class="kpi s">
        <div class="lb">考季系数</div>
        <div class="vl">{{ heat.seasonFactor || 1 }}<small>×</small></div>
        <div class="df">初试倒计时加权</div>
      </div>
      <div class="kpi g">
        <div class="lb">平均到座率</div>
        <div class="vl">{{ avgArrive }}<small>%</small></div>
        <div class="df">已到 / 已约</div>
      </div>
    </div>

    <div class="grid g21">
      <div class="card">
        <div class="hd">
          <h3>{{ heat.room?.roomName }} 需求热力矩阵</h3>
          <div class="sp"></div>
          <span class="sm tx3">行 = 日期，列 = 时段，数值为占用率</span>
        </div>
        <div class="bd" v-loading="loading">
          <table class="heat">
            <thead>
              <tr>
                <th class="rowh">日期</th>
                <th v-for="s in slots" :key="s">{{ s }}</th>
              </tr>
            </thead>
            <tbody>
              <tr v-for="m in matrix" :key="m.date">
                <th class="rowh">{{ String(m.date).slice(5) }}</th>
                <td v-for="s in slots" :key="s" :class="cellClass(m.cells[s])">
                  {{ cellText(m.cells[s]) }}
                </td>
              </tr>
            </tbody>
          </table>
          <div class="legend mt12">
            <span><i style="background: #E3ECEC"></i>空闲 &lt;30%</span>
            <span><i style="background: #A9C8C6"></i>30-50%</span>
            <span><i style="background: #6FA3A0"></i>50-65%</span>
            <span><i style="background: #3F8A93"></i>65-80%</span>
            <span><i style="background: #1D5B63"></i>80-90%</span>
            <span><i style="background: #8C3A2E"></i>超 90%</span>
          </div>
        </div>
      </div>

      <div class="card">
        <div class="hd"><h3>紧张时段与建议</h3></div>
        <div class="bd">
          <div v-for="(t, i) in tight" :key="i" class="fx" style="justify-content: space-between; margin-bottom: 10px">
            <span class="mono">{{ t.slot }}</span>
            <span class="tag r">{{ t.avgOccupancy }}%</span>
          </div>
          <div class="empty" v-if="!tight.length"><b>无持续紧张时段</b>需求分布平稳</div>
          <div class="divider"></div>
          <div class="alert a" v-if="tight.length">
            {{ tight[0].slot }} 连续 {{ days }} 天占用率超 85%，建议该时段增设 {{ suggestSeats }} 个座位、或将相邻自习室临时并入同一预约池。
          </div>
          <div class="alert" v-else>可维持当前开放时段与座位规模，无需调整。</div>
          <div class="divider"></div>
          <dl class="dl">
            <dt>自习室</dt><dd>{{ heat.room?.roomName }}</dd>
            <dt>开放时段</dt><dd>{{ hhmm(heat.room?.openStart) }}-{{ hhmm(heat.room?.openEnd) }}</dd>
            <dt>座位数</dt><dd>{{ heat.room?.seatCount }} 个</dd>
            <dt>时段长度</dt><dd>{{ heat.room?.slotMinutes }} 分钟</dd>
          </dl>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import { useRoute } from 'vue-router'
import PageHead from '../components/PageHead.vue'
import { roomApi } from '../api'
import { hhmm } from '../utils/format'

const route = useRoute()
const rooms = ref([])
const roomId = ref(null)
const days = ref(7)
const heat = ref({})
const matrix = ref([])
const slots = ref([])
const loading = ref(false)

const tight = computed(() => heat.value.tightSlots || [])
const peak = computed(() => {
  let max = 0
  matrix.value.forEach((m) => Object.values(m.cells || {}).forEach((c) => (max = Math.max(max, c.occupancy ?? 0))))
  return max.toFixed(0)
})
const peakSlot = computed(() => {
  let best = ''
  let max = -1
  matrix.value.forEach((m) =>
    Object.entries(m.cells || {}).forEach(([s, c]) => {
      if ((c.occupancy ?? 0) > max) {
        max = c.occupancy ?? 0
        best = s
      }
    })
  )
  return best || '--'
})
const avgArrive = computed(() => {
  let booked = 0
  let arrived = 0
  matrix.value.forEach((m) => Object.values(m.cells || {}).forEach((c) => {
    booked += c.booked || 0
    arrived += c.arrived || 0
  }))
  return booked ? ((arrived * 100) / booked).toFixed(0) : '0'
})
const suggestSeats = computed(() => {
  const r = heat.value.room || {}
  return Math.max(4, Math.round(((r.seatCount || 60) * 0.08) / 2) * 2)
})

function cellValue(c) {
  if (!c) return 0
  return Math.round(c.occupancy ?? 0)
}

function cellText(c) {
  return c ? `${cellValue(c)}%` : '--'
}

function cellClass(c) {
  const n = cellValue(c)
  if (n >= 90) return 'h6'
  if (n >= 80) return 'h5'
  if (n >= 65) return 'h4'
  if (n >= 50) return 'h3'
  if (n >= 30) return 'h2'
  return 'h1'
}

async function load() {
  if (!roomId.value) return
  loading.value = true
  try {
    const d = await roomApi.heatmap(roomId.value, days.value)
    heat.value = d
    matrix.value = d.matrix || []
    slots.value = d.slots || Object.keys(matrix.value[0]?.cells || {})
  } finally {
    loading.value = false
  }
}

onMounted(async () => {
  const d = await roomApi.page({ page: 1, size: 50 })
  rooms.value = d.records || []
  roomId.value = Number(route.query.roomId) || rooms.value[0]?.id
  await load()
})
</script>
