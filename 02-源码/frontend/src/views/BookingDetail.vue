<template>
  <div class="view" v-loading="loading">
    <div class="ph">
      <div>
        <h1>{{ b.bookingNo }}</h1>
        <p>{{ b.studentName }} · {{ b.studentNo }} · {{ b.college }} · {{ b.roomName }} {{ b.seatNo }}</p>
      </div>
      <div class="sp"></div>
      <span class="tag" :class="tone(b.status)" style="align-self: center">{{ b.status }}</span>
      <el-button size="small" @click="$router.push('/booking/list')">返回列表</el-button>
      <el-button size="small" type="primary" :disabled="!b.canCheckin" @click="checkin">签到</el-button>
      <el-button size="small" :disabled="!b.canCheckout" @click="checkout">签退</el-button>
    </div>

    <div class="grid g21">
      <div class="card">
        <div class="hd"><h3>预约信息</h3><div class="sp"></div><span class="sm tx3">{{ b.source }} · 创建于 {{ dt(b.createTime) }}</span></div>
        <div class="bd">
          <dl class="dl">
            <dt>预约号</dt><dd class="mono">{{ b.bookingNo }}</dd>
            <dt>学生</dt><dd>{{ b.studentName }}（{{ b.studentNo }}，{{ b.college }}）</dd>
            <dt>自习室</dt><dd>{{ b.roomName }}</dd>
            <dt>座位</dt><dd class="mono">{{ b.seatNo }} · {{ b.zoneName }} · {{ b.seatType }}</dd>
            <dt>预约日期</dt><dd>{{ b.bookingDate }}</dd>
            <dt>时段</dt><dd>{{ b.timeRange }}</dd>
            <dt>预约码</dt><dd class="mono">**** {{ b.bookingCode }}</dd>
            <dt>风险等级</dt><dd><span class="tag" :class="tone(b.riskLevel)">{{ b.riskLevel }}</span> <el-button link type="primary" size="small" @click="$router.push(`/insight/risk/${b.studentId}`)">查看评分依据</el-button></dd>
            <dt>签到时间</dt><dd>{{ dt(b.checkinTime) }}</dd>
            <dt>签退时间</dt><dd>{{ dt(b.checkoutTime) }}</dd>
            <dt>取消原因</dt><dd>{{ b.cancelReason || '--' }}</dd>
          </dl>
        </div>
      </div>

      <div class="card">
        <div class="hd"><h3>操作轨迹</h3><div class="sp"></div><span class="sm tx3">{{ records.length }} 条</span></div>
        <div class="bd">
          <div class="tl">
            <div v-for="(t, i) in timeline" :key="i" class="it" :class="t.state">
              <div class="t">{{ dt(t.time) }}</div>
              <div class="d">{{ t.text }}</div>
            </div>
          </div>
          <div class="empty" v-if="!timeline.length"><b>暂无轨迹</b>预约提交后自动生成</div>
        </div>
      </div>
    </div>

    <div class="card mt12">
      <div class="hd"><h3>签到记录</h3><div class="sp"></div><span class="sm tx3">含临时离座与返回</span></div>
      <el-table :data="records" style="width: 100%">
        <el-table-column label="动作" width="120">
          <template #default="{ row }"><span class="tag" :class="row.actionType === '签到' ? 'g' : row.actionType === '签退' ? 'b' : row.actionType === '管理员清座' ? 'r' : 'a'">{{ row.actionType }}</span></template>
        </el-table-column>
        <el-table-column label="时间" width="170"><template #default="{ row }">{{ dt(row.actionTime) }}</template></el-table-column>
        <el-table-column prop="seatNo" label="座位" width="120" />
        <el-table-column prop="operatorType" label="操作人" width="110" />
        <el-table-column label="校验码" width="100"><template #default="{ row }">{{ row.verifyCode || '--' }}</template></el-table-column>
        <el-table-column prop="remark" label="备注" min-width="180"><template #default="{ row }">{{ row.remark || '--' }}</template></el-table-column>
      </el-table>
    </div>

    <div class="card mt12" v-if="b.status === '学习中'">
      <div class="hd"><h3>在座处置</h3></div>
      <div class="bd fx" style="gap: 10px">
        <el-button size="small" @click="leave">登记临时离座（保留 30 分钟）</el-button>
        <el-button size="small" @click="back">登记返回</el-button>
        <div class="fx1"></div>
        <span class="sm tx3">离座超 30 分钟可由自习室管理员现场清座并判定违约</span>
      </div>
    </div>
  </div>
</template>

<script setup>
import { onMounted, ref } from 'vue'
import { useRoute } from 'vue-router'
import { ElMessage } from 'element-plus'
import { bookingApi } from '../api'
import { dt, tone } from '../utils/format'

const route = useRoute()
const loading = ref(false)
const b = ref({})
const records = ref([])
const timeline = ref([])

async function load() {
  loading.value = true
  try {
    const d = await bookingApi.detail(route.params.id)
    b.value = d
    records.value = d.records || []
    timeline.value = d.timeline || []
  } finally {
    loading.value = false
  }
}

async function checkin() {
  await bookingApi.checkin(b.value.id, { code: b.value.bookingCode, operator: 'admin', remark: '后台核验预约信息后代签' })
  ElMessage.success('已签到')
  load()
}

async function checkout() {
  await bookingApi.checkout(b.value.id)
  ElMessage.success('已签退，学习时长已写入日汇总')
  load()
}

async function leave() {
  const r = await bookingApi.leave(b.value.id, { remark: '临时离座' })
  ElMessage.success(`已登记离座，座位保留 ${r.keepMinutes} 分钟`)
  load()
}

async function back() {
  await bookingApi.back(b.value.id)
  ElMessage.success('已登记返回')
  load()
}

onMounted(load)
</script>
