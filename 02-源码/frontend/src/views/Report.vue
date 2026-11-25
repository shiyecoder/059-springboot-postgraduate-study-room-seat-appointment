<template>
  <div class="view">
    <PageHead title="利用率报表" desc="按自习室汇总预约量、到座率、平均时长与空置判定">
      <el-radio-group v-model="days" size="small" @change="load">
        <el-radio-button :value="7">近 7 天</el-radio-button>
        <el-radio-button :value="30">近 30 天</el-radio-button>
        <el-radio-button :value="90">本学期</el-radio-button>
      </el-radio-group>
      <el-button size="small" :icon="Download" @click="exportCsv">导出</el-button>
    </PageHead>

    <div class="grid g4 mb12">
      <div class="kpi">
        <div class="lb">总预约量</div>
        <div class="vl">{{ sum.booked }}<small>人次</small></div>
        <div class="df">近 {{ days }} 天</div>
      </div>
      <div class="kpi g">
        <div class="lb">平均到座率</div>
        <div class="vl">{{ sum.arrive }}<small>%</small></div>
        <div class="df">已到 / 已约</div>
      </div>
      <div class="kpi s">
        <div class="lb">平均有效时长</div>
        <div class="vl">{{ sum.hours }}<small>h</small></div>
        <div class="df">按完成预约统计</div>
      </div>
      <div class="kpi a">
        <div class="lb">空置自习室</div>
        <div class="vl">{{ idleCount }}<small>间</small></div>
        <div class="df">时段利用率低于 18%</div>
      </div>
    </div>

    <div class="grid g2 mb12">
      <div class="card">
        <div class="hd"><h3>各自习室利用率</h3><div class="sp"></div><span class="sm tx3">占比越高越紧张</span></div>
        <div class="bd" style="padding: 6px 8px">
          <ChartBox :option="utilOption" height="210px" />
        </div>
      </div>
      <div class="card">
        <div class="hd"><h3>到座率与违约率对比</h3><div class="sp"></div><span class="sm tx3">按自习室</span></div>
        <div class="bd" style="padding: 6px 8px">
          <ChartBox :option="rateOption" height="210px" />
        </div>
      </div>
    </div>

    <div class="card">
      <div class="hd"><h3>明细</h3><div class="sp"></div><span class="sm tx3">共 {{ rows.length }} 间</span></div>
      <el-table :data="rows" v-loading="loading" style="width: 100%">
        <el-table-column prop="roomName" label="自习室" min-width="180" />
        <el-table-column label="座位数" width="90" align="right"><template #default="{ row }">{{ row.seatCount }}</template></el-table-column>
        <el-table-column label="预约数" width="90" align="right"><template #default="{ row }">{{ row.booked }}</template></el-table-column>
        <el-table-column label="利用率" width="150">
          <template #default="{ row }">
            <div class="fx">
              <span class="bar" style="flex: 1"><i :style="{ width: Math.min(100, row.utilization > 1 ? row.utilization : row.utilization * 100) + '%' }"></i></span>
              <span class="sm">{{ pct(row.utilization, 1) }}</span>
            </div>
          </template>
        </el-table-column>
        <el-table-column label="平均时长" width="100"><template #default="{ row }">{{ row.avgHours }} h</template></el-table-column>
        <el-table-column label="到座率" width="96"><template #default="{ row }">{{ pct(row.arriveRate, 1) }}</template></el-table-column>
        <el-table-column label="违约率" width="96"><template #default="{ row }">{{ pct(row.violationRate, 1) }}</template></el-table-column>
        <el-table-column label="判定" width="100">
          <template #default="{ row }">
            <span class="tag" :class="row.verdict === '空置' ? 'a' : row.verdict === '紧张' ? 'r' : 'g'">{{ row.verdict }}</span>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="120" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" size="small" @click="$router.push({ path: '/insight/heatmap', query: { roomId: row.roomId } })">需求热力</el-button>
          </template>
        </el-table-column>
      </el-table>
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
import { C, axisStyle, tooltip, legend, hex } from '../utils/chart'

const days = ref(7)
const rows = ref([])
const loading = ref(false)

const sum = computed(() => {
  const booked = rows.value.reduce((s, r) => s + (r.booked || 0), 0)
  const arrive = rows.value.length ? rows.value.reduce((s, r) => s + (r.arriveRate || 0), 0) / rows.value.length : 0
  const hours = rows.value.length ? rows.value.reduce((s, r) => s + (r.avgHours || 0), 0) / rows.value.length : 0
  return { booked, arrive: arrive.toFixed(1), hours: hours.toFixed(1) }
})
const idleCount = computed(() => rows.value.filter((r) => (r.utilization || 0) < 18).length)

const utilOption = computed(() => ({
  grid: { left: 130, right: 40, top: 10, bottom: 16 },
  tooltip: tooltip({ axisPointer: { type: 'shadow' } }),
  xAxis: { type: 'value', max: 100, ...axisStyle({ axisLabel: { color: C.tx3, fontSize: 10, formatter: '{value}%' } }) },
  yAxis: { type: 'category', data: rows.value.map((r) => r.roomName), ...axisStyle({ splitLine: { show: false }, axisLabel: { color: C.tx2, fontSize: 10.5 } }) },
  series: [
    {
      type: 'bar',
      barWidth: 12,
      itemStyle: { borderRadius: [0, 3, 3, 0], color: (p) => (p.value >= 70 ? C.pri : hex(C.sec, 0.75)) },
      label: { show: true, position: 'right', fontSize: 10, color: C.tx2, formatter: (p) => `${p.value.toFixed(0)}%` },
      data: rows.value.map((r) => Number(((r.utilization || 0) * 100).toFixed(1)))
    }
  ]
}))

const rateOption = computed(() => ({
  color: [C.pri, C.acc],
  grid: { left: 32, right: 12, top: 26, bottom: 44 },
  tooltip: tooltip({ axisPointer: { type: 'shadow' } }),
  legend: legend(0),
  xAxis: {
    type: 'category',
    data: rows.value.map((r) => r.roomName.replace('自习室', '').replace('教学楼', '教')),
    ...axisStyle({ splitLine: { show: false }, axisLabel: { color: C.tx3, fontSize: 9.5, interval: 0, rotate: 22 } })
  },
  yAxis: { type: 'value', max: 100, ...axisStyle({ axisLine: { show: false }, axisLabel: { color: C.tx3, fontSize: 10, formatter: '{value}%' } }) },
  series: [
    { name: '到座率', type: 'bar', barWidth: 9, itemStyle: { borderRadius: [2, 2, 0, 0] }, data: rows.value.map((r) => Number((r.arriveRate || 0).toFixed(1))) },
    { name: '违约率', type: 'bar', barWidth: 9, itemStyle: { borderRadius: [2, 2, 0, 0] }, data: rows.value.map((r) => Number((r.violationRate || 0).toFixed(1))) }
  ]
}))

async function load() {
  loading.value = true
  try {
    rows.value = await statApi.utilization(days.value)
  } finally {
    loading.value = false
  }
}

function exportCsv() {
  const head = ['自习室', '座位数', '预约数', '利用率', '平均时长(h)', '到座率', '违约率', '判定']
  const body = rows.value.map((r) => [r.roomName, r.seatCount, r.booked, pct(r.utilization, 1), r.avgHours, pct(r.arriveRate, 1), pct(r.violationRate, 1), r.verdict])
  const csv = [head, ...body].map((r) => r.join(',')).join('\n')
  const blob = new Blob([`\ufeff${csv}`], { type: 'text/csv;charset=utf-8' })
  const a = document.createElement('a')
  a.href = URL.createObjectURL(blob)
  a.download = `利用率报表_近${days.value}天.csv`
  a.click()
  URL.revokeObjectURL(a.href)
  ElMessage.success('已导出报表')
}

onMounted(load)
</script>
