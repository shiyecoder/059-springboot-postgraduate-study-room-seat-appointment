<template>
  <div class="view" v-loading="loading">
    <div class="ph">
      <img v-if="room.coverImage" class="cover" :src="room.coverImage" alt="自习室实景" />
      <div>
        <h1>{{ room.roomName }}</h1>
        <p>{{ room.roomNo }} · {{ room.buildingName }}{{ room.floorNo }} 层 · 时段 {{ room.slotMinutes }} 分钟 · {{ room.quietLevel }}</p>
      </div>
      <div class="sp"></div>
      <el-button size="small" @click="$router.push('/room/list')">返回列表</el-button>
      <el-button size="small" type="danger" plain :disabled="room.status === '已关闭'" @click="closeRoom">停用自习室</el-button>
      <el-button size="small" type="primary" @click="$router.push({ path: '/room/seat-map', query: { roomId: room.id } })">座位平面图</el-button>
    </div>

    <div class="grid g21">
      <div class="card">
        <div class="hd"><h3>基本信息</h3><div class="sp"></div><span class="tag" :class="tone(room.status)">{{ room.status }}</span></div>
        <div class="bd">
          <dl class="dl">
            <dt>自习室编号</dt><dd class="mono">{{ room.roomNo }}</dd>
            <dt>名称</dt><dd>{{ room.roomName }}</dd>
            <dt>位置</dt><dd>{{ room.buildingName }} {{ room.floorNo }} 层</dd>
            <dt>开放时段</dt><dd>{{ hhmm(room.openStart) }}-{{ hhmm(room.openEnd) }}（每段 {{ room.slotMinutes }} 分钟，每日 {{ slots.length }} 个时段）</dd>
            <dt>座位总数</dt><dd>{{ room.seatCount }} 个（{{ zoneText }}）</dd>
            <dt>安静等级</dt><dd>{{ room.quietLevel }}</dd>
            <dt>负责人</dt><dd>{{ room.managerName || '--' }}</dd>
            <dt>可用座位</dt><dd>{{ room.seatAvailable }} 个 · 停用/维修 {{ (room.seatTotal || 0) - (room.seatAvailable || 0) }} 个</dd>
          </dl>
          <div class="divider"></div>
          <div class="fx" style="gap: 26px">
            <div><div class="sm tx3">今日预约</div><b style="font-size: 17px">{{ room.todayBooked }}</b> <span class="sm tx3">人次</span></div>
            <div><div class="sm tx3">今日占用率</div><b style="font-size: 17px">{{ pct(room.occupancyRate, 1) }}</b></div>
            <div><div class="sm tx3">近 7 天到座</div><b style="font-size: 17px">{{ pct(avgArrive, 1) }}</b></div>
            <div class="fx1"></div>
            <el-button link type="primary" @click="$router.push({ path: '/insight/heatmap', query: { roomId: room.id } })">查看需求热力</el-button>
          </div>
        </div>
      </div>

      <div class="card">
        <div class="hd"><h3>区域占用</h3><div class="sp"></div><span class="sm tx3">今日已约座位</span></div>
        <div class="bd">
          <div class="hbar" v-for="z in zoneStats" :key="z.name" style="margin-bottom: 13px">
            <span class="nm">{{ z.name }}</span>
            <span class="tr"><i :style="{ width: z.rate + '%' }"></i></span>
            <span class="vv">{{ z.rate }}%</span>
          </div>
          <div class="alert a mt8" v-if="tightZone">
            {{ tightZone }} 在 {{ tightSlot }} 的 7 天占用率超 90%，已标记为紧张时段，建议增设座位或延长开放。
          </div>
          <div class="alert mt8" v-else>
            各区域占用均衡，暂无连续紧张时段。座位停用/维修请到座位平面图操作，需填写原因并留痕。
          </div>
        </div>
      </div>
    </div>

    <div class="card mt12">
      <div class="hd"><h3>时段配置与今日到座</h3><div class="sp"></div><span class="sm tx3">共 {{ slots.length }} 个时段</span></div>
      <el-table :data="slots" style="width: 100%">
        <el-table-column type="index" label="#" width="52" />
        <el-table-column label="开始" width="100"><template #default="{ row }"><span class="mono">{{ hhmm(row.start) }}</span></template></el-table-column>
        <el-table-column label="结束" width="100"><template #default="{ row }"><span class="mono">{{ hhmm(row.end) }}</span></template></el-table-column>
        <el-table-column prop="minutes" label="时长（分）" width="100" />
        <el-table-column prop="booked" label="预约数" width="90" align="right" />
        <el-table-column prop="arrived" label="到座数" width="90" align="right" />
        <el-table-column label="到座率" width="110">
          <template #default="{ row }">{{ pct(row.arriveRate, 1) }}</template>
        </el-table-column>
        <el-table-column label="状态" min-width="100">
          <template #default="{ row }">
            <span class="tag" :class="row.arriveRate >= 90 ? 'a' : 'g'">{{ row.arriveRate >= 90 ? '紧张' : '正常' }}</span>
          </template>
        </el-table-column>
      </el-table>
    </div>

    <div class="grid g2 mt12">
      <div class="card">
        <div class="hd"><h3>近 7 天占用率</h3></div>
        <div class="bd" style="padding: 4px 8px 6px">
          <ChartBox :option="trendOption" height="132px" />
        </div>
      </div>
      <div class="card">
        <div class="hd"><h3>座位设施构成</h3><div class="sp"></div><span class="sm tx3">共 {{ room.seatCount }} 个</span></div>
        <div class="bd">
          <div class="hbar" v-for="f in facilities" :key="f.label" style="margin-bottom: 10px">
            <span class="nm">{{ f.label }}</span>
            <span class="tr"><i :style="{ width: f.rate + '%' }"></i></span>
            <span class="vv">{{ f.count }} 个</span>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import ChartBox from '../components/ChartBox.vue'
import { roomApi } from '../api'
import { hhmm, pct, tone } from '../utils/format'
import { C, axisStyle, tooltip, areaFill } from '../utils/chart'

const route = useRoute()
const router = useRouter()
const loading = ref(false)
const room = ref({})
const slots = ref([])
const zoneRows = ref([])
const trend = ref([])

const zoneText = computed(() =>
  Object.entries(room.value.zones || {}).map(([k, v]) => `${k.replace('区', ' 区')} ${v}`).join(' · ')
)
const avgArrive = computed(() => {
  if (!trend.value.length) return 0
  return trend.value.reduce((s, x) => s + (x.rate || 0), 0) / trend.value.length
})

const zoneStats = computed(() => {
  return Object.entries(room.value.zones || {}).map(([name, count]) => {
    const seats = zoneRows.value.filter((s) => s.zone === name)
    const used = seats.filter((s) => s.status !== '可用').length
    const rate = seats.length ? Math.round((used * 100) / seats.length) : 0
    return { name, count, rate: Math.max(rate, 0) }
  })
})

const tightZone = computed(() => {
  const hot = zoneStats.value.find((z) => z.rate >= 90)
  return hot ? hot.name : ''
})
const tightSlot = computed(() => (slots.value[0] ? `${hhmm(slots.value[0].start)}-${hhmm(slots.value[0].end)}` : ''))

const facilities = computed(() => {
  const all = zoneRows.value
  const power = all.filter((s) => s.hasPower).length
  const lamp = all.filter((s) => s.hasLamp).length
  const win = all.filter((s) => s.seatType === '靠窗').length
  const board = all.filter((s) => s.seatType === '独立隔板').length
  const base = all.length || 1
  return [
    { label: '带电源', count: power, rate: Math.round((power * 100) / base) },
    { label: '带台灯', count: lamp, rate: Math.round((lamp * 100) / base) },
    { label: '靠窗座位', count: win, rate: Math.round((win * 100) / base) },
    { label: '独立隔板', count: board, rate: Math.round((board * 100) / base) }
  ]
})

const trendOption = computed(() => ({
  grid: { left: 34, right: 12, top: 16, bottom: 22 },
  tooltip: tooltip({ formatter: (p) => `${p[0].name}<br/>到座率 ${p[0].value}%` }),
  xAxis: { type: 'category', boundaryGap: false, data: trend.value.map((t) => t.label), ...axisStyle({ splitLine: { show: false } }) },
  yAxis: { type: 'value', max: 100, ...axisStyle({ axisLine: { show: false } }) },
  series: [
    {
      type: 'line',
      smooth: true,
      symbol: 'none',
      lineStyle: { color: C.pri, width: 2 },
      areaStyle: areaFill(C.pri, 0.2, 0.01),
      data: trend.value.map((t) => t.rate)
    }
  ]
}))

async function closeRoom() {
  await ElMessageBox.confirm('停用后该自习室不可再被预约，已产生的预约需人工改约。确认停用？', '停用自习室', {
    type: 'warning',
    confirmButtonText: '停用',
    cancelButtonText: '取消'
  })
  await roomApi.save({ status: '已关闭' }, room.value.id)
  ElMessage.success('已停用')
  router.push('/room/list')
}

onMounted(async () => {
  loading.value = true
  try {
    const id = route.params.id
    const [d, map, heat] = await Promise.all([roomApi.detail(id), roomApi.seatMap({ roomId: id }), roomApi.heatmap(id, 7)])
    room.value = d
    slots.value = d.slots || []
    zoneRows.value = Object.entries(map.zones || {}).flatMap(([zone, list]) => list.map((s) => ({ ...s, zone })))
    trend.value = (heat.matrix || []).map((m) => {
      const cells = Object.values(m.cells || {})
      const booked = cells.reduce((s, c) => s + (c.booked || 0), 0)
      const arrived = cells.reduce((s, c) => s + (c.arrived || 0), 0)
      return { label: String(m.date).slice(5), rate: booked ? Math.round((arrived * 100) / booked) : 0 }
    })
  } finally {
    loading.value = false
  }
})
</script>

<style scoped>
.cover {
  width: 46px;
  height: 46px;
  border-radius: 8px;
  object-fit: cover;
  border: 1px solid var(--line);
  flex: 0 0 46px;
}
</style>
