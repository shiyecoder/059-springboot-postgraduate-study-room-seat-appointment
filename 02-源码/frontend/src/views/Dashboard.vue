<template>
  <div class="view">
    <PageHead title="场馆看板" :desc="`数据截至 ${stamp} · 每 10 分钟刷新`">
      <div class="seg">
        <a v-for="d in [7, 30]" :key="d" :class="{ on: days === d }" @click="switchDays(d)">近 {{ d }} 天</a>
        <a :class="{ on: days === 90 }" @click="switchDays(90)">本学期</a>
      </div>
      <el-button size="small" :icon="Download" @click="exportCsv">导出</el-button>
    </PageHead>

    <div class="kpi-row">
      <div v-for="k in kpis" :key="k.label" class="kpi" :class="k.tone">
        <div class="lb">{{ k.label }}</div>
        <div class="vl">{{ k.value }}<small>{{ k.unit }}</small></div>
        <div class="df" :class="k.trend">{{ k.hint }}</div>
      </div>
    </div>

    <div class="grid g21 mt12">
      <div class="card">
        <div class="hd">
          <h3>近 {{ days }} 天预约与到座趋势</h3>
          <div class="sp"></div>
          <span class="sm tx3">预约 {{ sumBooked }} · 到座 {{ sumArrived }}</span>
        </div>
        <div class="bd" style="padding: 6px 8px 8px">
          <ChartBox :option="trendOption" height="132px" />
        </div>
      </div>
      <div class="card">
        <div class="hd">
          <h3>违约类型占比</h3>
          <div class="sp"></div>
          <span class="tag r">{{ violationTotal }} 次</span>
        </div>
        <div class="bd" style="padding: 4px 8px 8px">
          <ChartBox :option="donutOption" height="132px" />
        </div>
      </div>
    </div>

    <div class="grid g21 mt12">
      <div class="card">
        <div class="hd">
          <h3>各时段座位占用率</h3>
          <div class="sp"></div>
          <span class="sm tx3">按时段均值，红色为超 90% 紧张时段</span>
        </div>
        <div class="bd" style="padding: 8px 12px">
          <table class="heat">
            <thead>
              <tr>
                <th class="rowh">自习室</th>
                <th v-for="s in slots" :key="s">{{ s }}</th>
              </tr>
            </thead>
            <tbody>
              <tr v-for="row in heatRows" :key="row.roomName">
                <th class="rowh">{{ row.short }}</th>
                <td v-for="(cell, i) in row.cells" :key="i" :class="cell.rate === null ? 'h0' : heatClass(cell.rate)">
                  {{ cell.rate === null ? '—' : cell.rate + '%' }}
                </td>
              </tr>
            </tbody>
          </table>
        </div>
      </div>
      <div class="card">
        <div class="hd">
          <h3>自习室利用率</h3>
          <div class="sp"></div>
          <span class="tag a" v-if="idleRoom">空置 {{ idleRoom }}</span>
        </div>
        <div class="bd" style="padding: 10px 12px">
          <div class="hbar" v-for="u in utilTop" :key="u.roomId" style="margin-bottom: 9px">
            <span class="nm">{{ u.roomName }}</span>
            <span class="tr"><i :style="{ width: barWidth(u.utilization) }"></i></span>
            <span class="vv">{{ pct(u.utilization, 0) }}</span>
          </div>
        </div>
      </div>
    </div>

    <div class="grid g2 mt12">
      <div class="card">
        <div class="hd"><h3>有效学习时长分布</h3><div class="sp"></div><span class="sm tx3">人次</span></div>
        <div class="bd" style="padding: 4px 8px 6px">
          <ChartBox :option="durationOption" height="112px" />
        </div>
      </div>
      <div class="card">
        <div class="hd"><h3>各学院到馆人数</h3><div class="sp"></div><span class="sm tx3">近 {{ days }} 天</span></div>
        <div class="bd" style="padding: 4px 8px 6px">
          <ChartBox :option="collegeOption" height="112px" />
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import { Download } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import PageHead from '../components/PageHead.vue'
import ChartBox from '../components/ChartBox.vue'
import { statApi } from '../api'
import { pct } from '../utils/format'
import { C, PALETTE, axisStyle, tooltip, legend, areaFill, hex } from '../utils/chart'

const days = ref(30)
const ov = ref({})
const ch = ref({ trend: [], heat: [], utilization: [], durationDist: [], violationTypes: [], college: [] })
const stamp = new Date().toLocaleString('zh-CN', { hour12: false }).replace(/\//g, '-').slice(0, 16)

const kpis = computed(() => {
  const o = ov.value || {}
  return [
    { label: '今日预约', value: o.todayBooked ?? 0, unit: '人次', hint: '含管理员代订与候补递补' },
    { label: '实时在座', value: o.studyingNow ?? 0, unit: '人', hint: '已签到且未签退', tone: 's' },
    { label: '到座率', value: (o.arriveRate ?? 0).toFixed(1), unit: '%', hint: `总座位 ${o.seatTotal ?? 0} 个`, tone: 'g' },
    { label: '日均有效时长', value: (o.avgValidHours ?? 0).toFixed(1), unit: 'h', hint: '近 30 天学生日均' },
    { label: '违约率', value: (o.violationRate ?? 0).toFixed(1), unit: '%', hint: '近 30 天违约 / 预约', tone: 'd' },
    { label: '黑名单', value: o.blacklistCount ?? 0, unit: '人', hint: '当前生效中', tone: 'd' },
    { label: '座位覆盖率', value: (o.seatUtilization ?? 0).toFixed(1), unit: '%', hint: '今日被约座位 / 总座位' },
    { label: '候补等待', value: o.waitlistCount ?? 0, unit: '人', hint: '满员房间排队', tone: 'a' }
  ]
})

const sumBooked = computed(() => ch.value.trend.reduce((s, x) => s + (x.booked || 0), 0))
const sumArrived = computed(() => ch.value.trend.reduce((s, x) => s + (x.arrived || 0), 0))

const violationTotal = computed(() => ch.value.violationTypes.reduce((s, x) => s + (x.count || 0), 0))

// 各自习室时段长度不同（120/180/240 分钟），列取并集并按开始时间排序
const slots = computed(() => {
  const set = new Set()
  ch.value.heat.forEach((r) => r.cells.forEach((c) => set.add(c.slot)))
  return [...set].sort((a, b) => a.localeCompare(b))
})

const heatRows = computed(() =>
  ch.value.heat.map((r) => ({
    roomName: r.roomName,
    short: r.roomName.length > 9 ? r.roomName.slice(0, 2) + r.roomName.slice(-5) : r.roomName,
    cells: slots.value.map((s) => {
      const hit = r.cells.find((c) => c.slot === s)
      return { rate: hit ? Number(hit.rate).toFixed(0) : null, slot: s }
    })
  }))
)

function heatClass(rate) {
  const n = Number(rate)
  if (n >= 90) return 'h6'
  if (n >= 80) return 'h5'
  if (n >= 65) return 'h4'
  if (n >= 50) return 'h3'
  if (n >= 30) return 'h2'
  return 'h1'
}

const utilTop = computed(() => [...ch.value.utilization].sort((a, b) => b.utilization - a.utilization).slice(0, 5))
const idleRoom = computed(() => (ch.value.utilization.find((u) => u.verdict === '空置') || {}).roomName || '')
function barWidth(v) {
  // 后端 utilization 已是百分数（如 19.4），仅在 0-1 之间时才需要放大
  const n = Number(v) || 0
  const p = n > 1 ? n : n * 100
  return `${Math.min(100, Math.max(3, p))}%`
}

const trendOption = computed(() => ({
  color: [C.pri, C.acc],
  grid: { left: 34, right: 12, top: 26, bottom: 20 },
  tooltip: tooltip(),
  legend: legend(0),
  xAxis: { type: 'category', boundaryGap: false, data: ch.value.trend.map((t) => t.label), ...axisStyle({ splitLine: { show: false } }) },
  yAxis: { type: 'value', ...axisStyle({ axisLine: { show: false } }) },
  series: [
    {
      name: '预约数',
      type: 'line',
      smooth: true,
      symbol: 'none',
      lineStyle: { width: 2 },
      areaStyle: areaFill(C.pri, 0.22, 0.01),
      data: ch.value.trend.map((t) => t.booked)
    },
    {
      name: '到座数',
      type: 'line',
      smooth: true,
      symbol: 'none',
      lineStyle: { width: 1.6 },
      data: ch.value.trend.map((t) => t.arrived)
    }
  ]
}))

const donutOption = computed(() => ({
  color: [C.pri, C.acc, C.sec, '#8C3A2E', C.ok],
  tooltip: { trigger: 'item', backgroundColor: 'rgba(18,60,66,.94)', borderWidth: 0, textStyle: { color: '#fff', fontSize: 11.5 } },
  legend: { orient: 'vertical', right: 4, top: 'center', itemWidth: 8, itemHeight: 8, icon: 'circle', itemGap: 8, textStyle: { color: C.tx2, fontSize: 11 } },
  series: [
    {
      type: 'pie',
      radius: ['52%', '76%'],
      center: ['34%', '50%'],
      avoidLabelOverlap: true,
      itemStyle: { borderColor: '#fff', borderWidth: 2 },
      label: { show: false },
      data: ch.value.violationTypes.map((v) => ({ name: v.type, value: v.count }))
    }
  ],
  graphic: [
    {
      type: 'text',
      left: '27%',
      top: '42%',
      style: { text: `${violationTotal.value}`, fill: C.pri, fontSize: 18, fontWeight: 700, textAlign: 'center' }
    },
    { type: 'text', left: '27.5%', top: '58%', style: { text: '次', fill: C.tx3, fontSize: 10 } }
  ]
}))

const durationOption = computed(() => ({
  grid: { left: 42, right: 10, top: 14, bottom: 20 },
  tooltip: tooltip(),
  xAxis: { type: 'category', data: ch.value.durationDist.map((d) => d.label), ...axisStyle({ splitLine: { show: false } }) },
  yAxis: { type: 'value', ...axisStyle({ axisLine: { show: false } }) },
  series: [
    {
      type: 'bar',
      barWidth: 22,
      itemStyle: {
        borderRadius: [3, 3, 0, 0],
        color: (p) => (p.dataIndex === 2 ? C.acc : hex(C.pri, 0.86))
      },
      data: ch.value.durationDist.map((d) => d.count)
    }
  ]
}))

const collegeOption = computed(() => ({
  grid: { left: 42, right: 10, top: 14, bottom: 24 },
  tooltip: tooltip(),
  xAxis: {
    type: 'category',
    data: ch.value.college.map((c) => c.college.replace('学院', '')),
    ...axisStyle({ splitLine: { show: false }, axisLabel: { color: C.tx2, fontSize: 10, interval: 0 } })
  },
  yAxis: { type: 'value', ...axisStyle({ axisLine: { show: false } }) },
  series: [
    {
      type: 'bar',
      barWidth: 18,
      itemStyle: { borderRadius: [3, 3, 0, 0], color: (p) => (p.dataIndex === 0 ? C.acc : hex(C.sec, 0.92)) },
      data: ch.value.college.map((c) => c.arrived)
    }
  ]
}))

async function load() {
  const [o, c] = await Promise.all([statApi.overview(), statApi.charts(days.value)])
  ov.value = o
  ch.value = c
}

function switchDays(d) {
  days.value = d
  load()
}

function exportCsv() {
  const rows = [['自习室', ...slots.value]]
  heatRows.value.forEach((r) => rows.push([r.roomName, ...r.cells.map((c) => `${c.rate}%`)]))
  const csv = rows.map((r) => r.join(',')).join('\n')
  const blob = new Blob([`\ufeff${csv}`], { type: 'text/csv;charset=utf-8' })
  const a = document.createElement('a')
  a.href = URL.createObjectURL(blob)
  a.download = `时段占用率_近${days.value}天.csv`
  a.click()
  URL.revokeObjectURL(a.href)
  ElMessage.success('已导出时段占用率')
}

onMounted(load)
</script>

<style scoped>
.kpi-row {
  display: grid;
  grid-template-columns: repeat(8, minmax(0, 1fr));
  gap: 10px;
}
.kpi .vl {
  font-size: 19px;
}
</style>
