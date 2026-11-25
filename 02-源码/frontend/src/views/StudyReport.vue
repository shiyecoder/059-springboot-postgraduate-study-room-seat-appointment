<template>
  <div class="view">
    <div class="fx mb12">
      <h2 style="font-size: 17px; font-weight: 600">学习报告</h2>
      <div class="sp fx1"></div>
      <el-radio-group v-model="days" size="small" @change="load">
        <el-radio-button :value="7">近 7 天</el-radio-button>
        <el-radio-button :value="30">近 30 天</el-radio-button>
        <el-radio-button :value="90">本学期</el-radio-button>
      </el-radio-group>
      <el-button size="small" :icon="Download" @click="exportCsv">导出</el-button>
    </div>

    <div class="pkpi mb12">
      <div class="kpi">
        <div class="lb">有效学习时长</div>
        <div class="vl">{{ report.validHours ?? 0 }}<small>h</small></div>
        <div class="df">已扣除临时离座</div>
      </div>
      <div class="kpi s">
        <div class="lb">学习场次</div>
        <div class="vl">{{ report.sessions ?? 0 }}<small>场</small></div>
        <div class="df">按签到签退配对统计</div>
      </div>
      <div class="kpi g">
        <div class="lb">平均单次时长</div>
        <div class="vl">{{ report.avgHours ?? 0 }}<small>h</small></div>
        <div class="df">越高越说明专注度稳定</div>
      </div>
      <div class="kpi a">
        <div class="lb">最长单次</div>
        <div class="vl">{{ maxHours }}<small>h</small></div>
        <div class="df">{{ maxDay }}</div>
      </div>
    </div>

    <div class="card">
      <div class="hd"><h3>每日有效学习时长</h3><div class="sp"></div><span class="sm tx3">近 {{ days }} 天</span></div>
      <div class="bd" style="padding: 6px 8px 8px">
        <ChartBox :option="dailyOption" height="220px" />
      </div>
    </div>

    <div class="card mt12">
      <div class="hd"><h3>场次明细</h3><div class="sp"></div><span class="sm tx3">{{ rows.length }} 场</span></div>
      <el-table :data="rows" style="width: 100%">
        <el-table-column prop="bookingDate" label="日期" width="110" />
        <el-table-column prop="roomName" label="自习室" min-width="170" />
        <el-table-column prop="seatNo" label="座位" width="120"><template #default="{ row }"><span class="mono">{{ row.seatNo }}</span></template></el-table-column>
        <el-table-column prop="timeRange" label="时段" width="120" />
        <el-table-column label="签到" width="90"><template #default="{ row }">{{ hhmm(row.checkinTime) }}</template></el-table-column>
        <el-table-column label="签退" width="90"><template #default="{ row }">{{ hhmm(row.checkoutTime) }}</template></el-table-column>
        <el-table-column label="有效时长" width="100">
          <template #default="{ row }">{{ row.totalMinutes ? (row.totalMinutes / 60).toFixed(1) + ' h' : '--' }}</template>
        </el-table-column>
        <el-table-column label="状态" min-width="96">
          <template #default="{ row }"><span class="tag" :class="tone(row.status)">{{ row.status }}</span></template>
        </el-table-column>
      </el-table>
    </div>
  </div>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import { Download } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import ChartBox from '../components/ChartBox.vue'
import { bookingApi } from '../api'
import { hhmm, tone } from '../utils/format'
import { C, axisStyle, tooltip, areaFill } from '../utils/chart'

const days = ref(30)
const report = ref({})
const rows = computed(() => report.value.records || [])

const maxHours = computed(() => {
  const daily = report.value.daily || []
  if (!daily.length) return 0
  return Math.max(...daily.map((x) => Number((x.validMinutes / 60).toFixed(1))))
})
const maxDay = computed(() => {
  const daily = report.value.daily || []
  if (!daily.length) return '--'
  const best = daily.reduce((a, b) => (a.validMinutes >= b.validMinutes ? a : b))
  return String(best.statDate).slice(5) + ' 单日最高'
})

const dailyOption = computed(() => {
  const daily = [...(report.value.daily || [])].sort((a, b) => String(a.statDate).localeCompare(String(b.statDate)))
  return {
    grid: { left: 34, right: 12, top: 16, bottom: 26 },
    tooltip: tooltip({ formatter: (p) => `${p[0].name}<br/>有效学习 ${p[0].value} 小时` }),
    xAxis: { type: 'category', data: daily.map((x) => String(x.statDate).slice(5)), ...axisStyle({ splitLine: { show: false } }) },
    yAxis: { type: 'value', ...axisStyle({ axisLine: { show: false } }) },
    series: [
      {
        type: 'line',
        smooth: true,
        symbol: 'circle',
        symbolSize: 4,
        lineStyle: { color: C.pri, width: 2 },
        areaStyle: areaFill(C.pri, 0.24, 0.01),
        data: daily.map((x) => Number((x.validMinutes / 60).toFixed(1)))
      }
    ]
  }
})

async function load() {
  report.value = await bookingApi.portalReport(days.value)
}

function exportCsv() {
  const head = ['日期', '自习室', '座位', '时段', '签到', '签退', '有效时长(h)', '状态']
  const body = rows.value.map((r) => [r.bookingDate, r.roomName, r.seatNo, r.timeRange, hhmm(r.checkinTime), hhmm(r.checkoutTime), r.totalMinutes ? (r.totalMinutes / 60).toFixed(1) : '', r.status])
  const csv = [head, ...body].map((r) => r.join(',')).join('\n')
  const blob = new Blob([`\ufeff${csv}`], { type: 'text/csv;charset=utf-8' })
  const a = document.createElement('a')
  a.href = URL.createObjectURL(blob)
  a.download = `学习报告_近${days.value}天.csv`
  a.click()
  URL.revokeObjectURL(a.href)
  ElMessage.success('已导出学习报告')
}

onMounted(load)
</script>
