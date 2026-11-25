<template>
  <div class="view">
    <div class="phero">
      <div>
        <h2>{{ greeting }}，{{ ov.user?.realName }} · 距初试还有 {{ ov.seedDays || '--' }} 天</h2>
        <p>
          <template v-if="nextBooking">
            今日 {{ nextBooking.timeRange }} 在{{ nextBooking.roomName }} {{ nextBooking.seatNo }} 已预约，
            {{ nextBooking.status === '学习中' ? '已完成签到，保持节奏。' : `请在 ${hhmm(nextBooking.startTime)} 前 15 分钟内签到。` }}
          </template>
          <template v-else>今日暂无预约，馆内 {{ ov.openCount }} 间自习室开放中，挑一个安静的角落开始今天的复习。</template>
        </p>
      </div>
      <div class="sp"></div>
      <div class="cnt">
        <div><b>{{ ov.weekValidHours ?? 0 }}</b><span>本周有效(h)</span></div>
        <div><b>{{ ov.continuousDays ?? 0 }}</b><span>连续学习天数</span></div>
        <div><b>{{ ov.user?.creditScore ?? 100 }}</b><span>信用分</span></div>
      </div>
    </div>

    <div class="pkpi">
      <div class="kpi">
        <div class="lb">本周有效时长</div>
        <div class="vl">{{ ov.weekValidHours ?? 0 }}<small>h</small></div>
        <div class="df">已扣除临时离座</div>
      </div>
      <div class="kpi s">
        <div class="lb">本周场次</div>
        <div class="vl">{{ ov.weekSessions ?? 0 }}<small>场</small></div>
        <div class="df">按完成的预约统计</div>
      </div>
      <div class="kpi g">
        <div class="lb">平均单次时长</div>
        <div class="vl">{{ ov.avgHours ?? 0 }}<small>h</small></div>
        <div class="df">近 7 天均值</div>
      </div>
      <div class="kpi a">
        <div class="lb">今日预约</div>
        <div class="vl">{{ (ov.today || []).length }}<small>场</small></div>
        <div class="df">待签到 {{ pendingCount }} 场</div>
      </div>
    </div>

    <div class="grid g21 mt12" style="align-items: start">
      <div>
        <div class="card">
          <div class="hd">
            <h3>今天的预约</h3>
            <div class="sp"></div>
            <el-button link type="primary" size="small" @click="$router.push('/portal/bookings')">全部预约</el-button>
          </div>
          <div class="bd">
            <div class="pbook" v-if="(ov.today || []).length">
              <div v-for="b in ov.today" :key="b.id" class="pcard" :class="{ hot: b.status === '学习中' }">
                <div class="bh">
                  <span class="code">{{ b.bookingNo }}</span>
                  <div class="sp"></div>
                  <span class="tag" :class="tone(b.status)">{{ b.status }}</span>
                </div>
                <div class="brow"><span>座位 <b class="mono">{{ b.seatNo }}</b></span><span>{{ b.roomName }} · {{ b.zoneName }}</span></div>
                <div class="brow"><span>时段 <b>{{ b.timeRange }}</b></span><span>预约码 <b class="mono">{{ b.bookingCode }}</b></span></div>
                <div class="bft">
                  <span class="sm tx3" v-if="b.status === '学习中'">已签到 {{ dt(b.checkinTime) }}</span>
                  <span class="sm tx3" v-else-if="b.status === '待签到'">开始前 15 分钟开放签到</span>
                  <span class="sm tx3" v-else>等待时段生效</span>
                  <div class="sp"></div>
                  <el-button v-if="b.status === '学习中'" size="small" @click="checkout(b)">签退</el-button>
                  <el-button v-if="b.status === '学习中'" size="small" @click="$router.push('/portal/checkin')">签到页</el-button>
                  <el-button v-if="['待生效', '待签到'].includes(b.status)" size="small" @click="cancel(b)">取消预约</el-button>
                  <el-button v-if="b.status === '待签到'" size="small" type="primary" @click="$router.push('/portal/checkin')">去签到</el-button>
                </div>
              </div>
            </div>
            <div class="empty" v-else>
              <b>今天还没有预约</b>去选一个安静的座位吧
              <div class="mt8"><el-button type="primary" size="small" @click="$router.push('/portal/select')">去选座</el-button></div>
            </div>
          </div>
        </div>

        <div class="card mt12">
          <div class="hd">
            <h3>本周学习时长</h3>
            <div class="sp"></div>
            <span class="sm tx3">{{ (report.daily || []).length }} 天有记录</span>
            <el-button link type="primary" size="small" @click="$router.push('/portal/report')">学习报告</el-button>
          </div>
          <div class="bd" style="padding: 6px 8px 8px">
            <ChartBox :option="weekOption" height="150px" />
          </div>
        </div>
      </div>

      <div>
        <div class="card">
          <div class="hd"><h3>推荐时段</h3><div class="sp"></div><span class="sm tx3">基于近 7 天热力</span></div>
          <div class="bd">
            <div class="sm tx2 mb8">近 7 天各时段占用率，越低越宽松</div>
            <div class="hbar" v-for="s in suggestion" :key="s.slot" style="margin-bottom: 11px">
              <span class="nm">{{ s.slot }}</span>
              <span class="tr"><i :style="{ width: Math.min(100, s.rate) + '%' }"></i></span>
              <span class="vv">{{ s.rate }}%</span>
            </div>
            <el-button type="primary" style="width: 100%; margin-top: 6px" @click="$router.push('/portal/select')">去选座</el-button>
            <div class="alert mt8" v-if="risk.unmet?.length" style="font-size: 11.5px">
              当前风险：{{ risk.unmet[0] }}
            </div>
          </div>
        </div>

        <div class="card mt12">
          <div class="hd"><h3>公告</h3><div class="sp"></div><el-button link type="primary" size="small" @click="$router.push('/portal/notice')">更多</el-button></div>
          <div class="bd">
            <div v-for="n in notices" :key="n.id" style="padding: 7px 0; border-bottom: 1px dashed var(--line)">
              <div style="font-size: 12.5px">{{ n.title }}</div>
              <div class="sm tx3">{{ d(n.publishTime) }} · {{ n.noticeType }}</div>
            </div>
            <div class="empty" v-if="!notices.length"><b>暂无公告</b></div>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import ChartBox from '../components/ChartBox.vue'
import { bookingApi, riskApi, roomApi, systemApi } from '../api'
import { d, dt, hhmm, tone } from '../utils/format'
import { C, axisStyle, tooltip } from '../utils/chart'
import { useUserStore } from '../store/user'

const store = useUserStore()
const ov = ref({})
const report = ref({})
const notices = ref([])
const risk = ref({})
const heat = ref({})

const greeting = computed(() => {
  const h = new Date().getHours()
  if (h < 6) return '夜深了'
  if (h < 11) return '早上好'
  if (h < 14) return '中午好'
  if (h < 18) return '下午好'
  return '晚上好'
})

const nextBooking = computed(() => ov.value.current || (ov.value.today || [])[0])
const pendingCount = computed(() => (ov.value.today || []).filter((b) => b.status === '待签到').length)

const suggestion = computed(() => {
  const slots = heat.value.slots || []
  const tight = heat.value.tightSlots || []
  return slots.map((s) => {
    const hit = tight.find((t) => t.slot === s)
    const rate = hit ? hit.avgOccupancy : 55
    return { slot: s, rate, label: rate >= 90 ? '紧张' : rate >= 75 ? '偏紧' : '宽松' }
  })
})

const weekOption = computed(() => {
  const daily = report.value.daily || []
  const days = []
  for (let i = 6; i >= 0; i--) {
    const dt0 = new Date(Date.now() - i * 86400000)
    const key = dt0.toISOString().slice(0, 10)
    const hit = daily.find((x) => String(x.statDate).slice(0, 10) === key)
    days.push({ label: key.slice(5), hours: hit ? Number((hit.validMinutes / 60).toFixed(1)) : 0, today: i === 0 })
  }
  return {
    grid: { left: 30, right: 10, top: 10, bottom: 22 },
    tooltip: tooltip({ formatter: (p) => `${p[0].name}<br/>有效学习 ${p[0].value} 小时` }),
    xAxis: { type: 'category', data: days.map((x) => x.label), ...axisStyle({ splitLine: { show: false } }) },
    yAxis: { type: 'value', ...axisStyle({ axisLine: { show: false } }) },
    series: [
      {
        type: 'bar',
        barWidth: 18,
        itemStyle: { borderRadius: [3, 3, 0, 0], color: (p) => (days[p.dataIndex].today ? C.acc : C.pri) },
        data: days.map((x) => x.hours)
      }
    ]
  }
})

async function cancel(b) {
  const { value } = await ElMessageBox.prompt('请填写取消原因（开始前 30 分钟内取消将计违约一次）', '取消预约', {
    inputPlaceholder: '如：课程冲突，改约明天',
    confirmButtonText: '确认取消',
    cancelButtonText: '返回'
  })
  if (!value || value.trim().length < 2) return ElMessage.warning('请填写取消原因')
  const r = await bookingApi.cancel(b.id, { reason: value })
  ElMessage[r.lateCancel ? 'warning' : 'success'](r.lateCancel ? '已取消，本次计违约一次' : '已取消，本次不计违约')
  load()
}

async function checkout(b) {
  await bookingApi.checkout(b.id)
  ElMessage.success('已签退，学习时长已记录')
  load()
}

async function load() {
  ov.value = await bookingApi.portalOverview()
  report.value = await bookingApi.portalReport(7)
  notices.value = (await systemApi.notices()).slice(0, 3)
  const uid = ov.value.current?.studentId || store.state.user?.userId
  if (uid) risk.value = await riskApi.student(uid)
  const roomId = ov.value.current?.roomId || 1
  heat.value = await roomApi.heatmap(roomId, 7)
}

onMounted(load)
</script>

<style scoped>
.pbook {
  grid-template-columns: 1fr;
}
</style>
