/** ECharts 主题与常用 option 片段：与设计系统同一套色板，避免各页面各写一套坐标轴 */

export const C = {
  pri: '#1D5B63',
  priL: '#3F8A93',
  acc: '#C97B2E',
  sec: '#6FA3A0',
  ok: '#2B7955',
  dngr: '#B02A37',
  line: '#E3E7E9',
  tx2: '#5B6770',
  tx3: '#8A959C'
}

export const PALETTE = [C.pri, C.acc, C.sec, '#8C3A2E', C.ok, '#2C5F82', '#A9C8C6', '#E0A860']

/** 统一坐标轴：浅虚线网格、无轴线、小字号 */
export function axisStyle(extra = {}) {
  return {
    axisLine: { lineStyle: { color: C.line } },
    axisTick: { show: false },
    axisLabel: { color: C.tx3, fontSize: 10 },
    splitLine: { lineStyle: { color: '#F0F2F3' } },
    ...extra
  }
}

export function tooltip(extra = {}) {
  return {
    trigger: 'axis',
    backgroundColor: 'rgba(18,60,66,.94)',
    borderWidth: 0,
    padding: [7, 10],
    textStyle: { color: '#fff', fontSize: 11.5 },
    axisPointer: { type: 'line', lineStyle: { color: '#CFE2DF', type: 'dashed' } },
    ...extra
  }
}

export function legend(top = 0) {
  return {
    top,
    right: 0,
    itemWidth: 8,
    itemHeight: 8,
    itemGap: 12,
    icon: 'roundRect',
    textStyle: { color: C.tx2, fontSize: 11 }
  }
}

/** 渐变填充（折线面积） */
export function areaFill(color, from = 0.28, to = 0.02) {
  return {
    color: {
      type: 'linear',
      x: 0,
      y: 0,
      x2: 0,
      y2: 1,
      colorStops: [
        { offset: 0, color: hex(color, from) },
        { offset: 1, color: hex(color, to) }
      ]
    }
  }
}

function hex(c, a) {
  const n = parseInt(c.replace('#', ''), 16)
  return `rgba(${(n >> 16) & 255},${(n >> 8) & 255},${n & 255},${a})`
}

export { hex }
