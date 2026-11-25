<template>
  <div class="view">
    <PageHead title="学院到馆" desc="按学院统计到馆人数、到座率与违约率，供学工老师约谈与资源调配">
      <el-radio-group v-model="days" size="small" @change="load">
        <el-radio-button :value="7">近 7 天</el-radio-button>
        <el-radio-button :value="30">近 30 天</el-radio-button>
      </el-radio-group>
    </PageHead>

    <div class="grid g2 mb12">
      <div class="card">
        <div class="hd"><h3>各学院到馆人次</h3><div class="sp"></div><span class="sm tx3">近 {{ days }} 天</span></div>
        <div class="bd" style="padding: 6px 8px">
          <ChartBox :option="bookedOption" height="220px" />
        </div>
      </div>
      <div class="card">
        <div class="hd"><h3>到座率与违约率</h3><div class="sp"></div><span class="sm tx3">按学院</span></div>
        <div class="bd" style="padding: 6px 8px">
          <ChartBox :option="rateOption" height="220px" />
        </div>
      </div>
    </div>

    <div class="card">
      <div class="hd"><h3>学院明细</h3><div class="sp"></div><span class="sm tx3">{{ rows.length }} 个学院</span></div>
      <el-table :data="rows" v-loading="loading" style="width: 100%">
        <el-table-column prop="college" label="学院" min-width="180" />
        <el-table-column prop="students" label="活跃学生" width="106" align="right" />
        <el-table-column prop="booked" label="预约数" width="96" align="right" />
        <el-table-column prop="arrived" label="到座数" width="96" align="right" />
        <el-table-column label="到座率" width="150">
          <template #default="{ row }">
            <div class="fx">
              <span class="bar" style="flex: 1"><i :style="{ width: Math.min(100, row.arriveRate) + '%' }"></i></span>
              <span class="sm">{{ pct(row.arriveRate, 1) }}</span>
            </div>
          </template>
        </el-table-column>
        <el-table-column prop="violated" label="违约数" width="96" align="right" />
        <el-table-column label="违约率" width="100"><template #default="{ row }">{{ pct(row.violationRate, 1) }}</template></el-table-column>
        <el-table-column label="评估" width="120">
          <template #default="{ row }">
            <span class="tag" :class="row.violationRate > 8 ? 'r' : row.arriveRate < 80 ? 'a' : 'g'">
              {{ row.violationRate > 8 ? '违约偏高' : row.arriveRate < 80 ? '到座偏低' : '正常' }}
            </span>
          </template>
        </el-table-column>
      </el-table>
    </div>
  </div>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import PageHead from '../components/PageHead.vue'
import ChartBox from '../components/ChartBox.vue'
import { statApi } from '../api'
import { pct } from '../utils/format'
import { C, axisStyle, tooltip, legend } from '../utils/chart'

const days = ref(7)
const rows = ref([])
const loading = ref(false)

const labels = computed(() => rows.value.map((r) => r.college.replace('学院', '')))

const bookedOption = computed(() => ({
  grid: { left: 32, right: 12, top: 14, bottom: 34 },
  tooltip: tooltip({ axisPointer: { type: 'shadow' } }),
  xAxis: { type: 'category', data: labels.value, ...axisStyle({ splitLine: { show: false }, axisLabel: { color: C.tx2, fontSize: 10, interval: 0 } }) },
  yAxis: { type: 'value', ...axisStyle({ axisLine: { show: false } }) },
  series: [
    {
      type: 'bar',
      barWidth: 26,
      itemStyle: {
        borderRadius: [4, 4, 0, 0],
        color: {
          type: 'linear',
          x: 0, y: 0, x2: 0, y2: 1,
          colorStops: [
            { offset: 0, color: C.priL },
            { offset: 1, color: C.pri }
          ]
        }
      },
      data: rows.value.map((r) => r.arrived)
    }
  ]
}))

const rateOption = computed(() => ({
  color: [C.pri, C.acc],
  grid: { left: 32, right: 12, top: 26, bottom: 34 },
  tooltip: tooltip({ axisPointer: { type: 'shadow' } }),
  legend: legend(0),
  xAxis: { type: 'category', data: labels.value, ...axisStyle({ splitLine: { show: false }, axisLabel: { color: C.tx2, fontSize: 10, interval: 0 } }) },
  yAxis: { type: 'value', max: 100, ...axisStyle({ axisLine: { show: false }, axisLabel: { color: C.tx3, fontSize: 10, formatter: '{value}%' } }) },
  series: [
    { name: '到座率', type: 'line', smooth: true, symbol: 'circle', symbolSize: 5, lineStyle: { width: 2 }, data: rows.value.map((r) => Number((r.arriveRate || 0).toFixed(1))) },
    { name: '违约率', type: 'bar', barWidth: 12, itemStyle: { borderRadius: [2, 2, 0, 0] }, data: rows.value.map((r) => Number((r.violationRate || 0).toFixed(1))) }
  ]
}))

async function load() {
  loading.value = true
  try {
    rows.value = await statApi.college(days.value)
  } finally {
    loading.value = false
  }
}

onMounted(load)
</script>
