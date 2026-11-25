<template>
  <div ref="el" class="chartbox" :style="{ height }"></div>
</template>

<script setup>
import { onBeforeUnmount, onMounted, ref, watch, nextTick } from 'vue'
import * as echarts from 'echarts'

/**
 * 轻量图表容器：统一初始化、随容器尺寸自适应、option 变化时重建。
 * 页面只负责传 option，避免每个视图重复写 resize 监听。
 */
const props = defineProps({
  option: { type: Object, required: true },
  height: { type: String, default: '160px' }
})

const el = ref(null)
let chart = null
let ro = null

function render() {
  if (!el.value) return
  if (!chart) chart = echarts.init(el.value, null, { renderer: 'canvas' })
  chart.setOption(props.option, true)
}

onMounted(() => {
  nextTick(render)
  ro = new ResizeObserver(() => chart && chart.resize())
  if (el.value) ro.observe(el.value)
})

watch(() => props.option, render, { deep: true })

onBeforeUnmount(() => {
  ro && ro.disconnect()
  chart && chart.dispose()
  chart = null
})
</script>

<style scoped>
.chartbox {
  width: 100%;
}
</style>
