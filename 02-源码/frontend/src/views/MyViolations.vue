<template>
  <div class="view">
    <div class="fx mb12">
      <h2 style="font-size: 17px; font-weight: 600">我的违约</h2>
      <div class="sp fx1"></div>
      <span class="sm tx3">申诉期：违约发生后 3 日内，限 1 次</span>
    </div>

    <div class="grid g4 mb12">
      <div class="kpi d">
        <div class="lb">累计违约</div>
        <div class="vl">{{ total }}<small>次</small></div>
        <div class="df">近 30 天 {{ recent30 }} 次</div>
      </div>
      <div class="kpi a">
        <div class="lb">扣减信用分</div>
        <div class="vl">{{ deduct }}<small>分</small></div>
        <div class="df">当前信用分 {{ credit }}</div>
      </div>
      <div class="kpi s">
        <div class="lb">申诉处理中</div>
        <div class="vl">{{ appealing }}<small>条</small></div>
        <div class="df">学工老师 3 个工作日内处理</div>
      </div>
      <div class="kpi g">
        <div class="lb">已撤销</div>
        <div class="vl">{{ revoked }}<small>条</small></div>
        <div class="df">申诉成功或经核实撤销</div>
      </div>
    </div>

    <div class="card">
      <div class="bd np" v-loading="loading">
        <div v-for="v in rows" :key="v.id" class="vio">
          <div class="fx">
            <span class="tag r">{{ v.violationType }}</span>
            <b class="mono sm">{{ v.recordNo }}</b>
            <div class="fx1"></div>
            <span class="tag" :class="tone(v.handleStatus)">{{ v.handleStatus }}</span>
          </div>
          <div class="sm tx2 mt8">
            发生时间 {{ dt(v.occurTime) }} · 关联预约 <span class="mono">{{ v.bookingNo }}</span> · 扣 {{ v.deductScore }} 分
          </div>
          <div class="sm tx3" style="margin-top: 4px">申诉截止 {{ dt(v.appealDeadline) }}</div>
          <div class="alert a mt8" v-if="v.appealReason">
            <div>
              <b>我的申诉（{{ dt(v.appealTime) }}）</b><br />{{ v.appealReason }}
            </div>
          </div>
          <div class="alert mt8" v-if="v.handleResult">
            <div><b>处理意见</b><br />{{ v.handleResult }}</div>
          </div>
          <div class="fx mt8">
            <el-button v-if="v.canAppeal" size="small" type="primary" @click="openAppeal(v)">提交申诉</el-button>
            <span v-else class="sm tx3">{{ v.appealReason ? '已提交申诉，等待处理' : '已过申诉期或无需申诉' }}</span>
            <div class="fx1"></div>
            <el-button link type="primary" size="small" @click="$router.push('/portal/report')">查看学习记录</el-button>
          </div>
        </div>
        <div class="empty" v-if="!loading && !rows.length"><b>没有违约记录</b>保持按时签到签退，信用分就不会掉</div>
      </div>
    </div>

    <el-dialog v-model="dlg" title="提交申诉" width="480px">
      <div class="alert a mb12" v-if="cur">
        {{ cur.recordNo }} · {{ cur.violationType }} · 发生 {{ dt(cur.occurTime) }}
      </div>
      <el-input v-model="reason" type="textarea" :rows="4" maxlength="200" show-word-limit placeholder="请说明情况并写明可核实的凭证（不少于 10 个字），如：当天急性肠胃炎在医务室就诊，已上传就诊记录" />
      <template #footer>
        <el-button @click="dlg = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="doAppeal">提交申诉</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { authApi, violationApi } from '../api'
import { dt, tone } from '../utils/format'

const rows = ref([])
const total = ref(0)
const loading = ref(false)
const saving = ref(false)
const dlg = ref(false)
const cur = ref(null)
const reason = ref('')
const credit = ref(100)

const deduct = computed(() => rows.value.reduce((s, v) => s + (v.deductScore || 0), 0))
const appealing = computed(() => rows.value.filter((v) => v.handleStatus === '申诉中').length)
const revoked = computed(() => rows.value.filter((v) => v.handleStatus === '已撤销').length)
const recent30 = computed(() => {
  const limit = Date.now() - 30 * 86400000
  return rows.value.filter((v) => new Date(String(v.occurTime).replace(' ', 'T')).getTime() >= limit).length
})

async function load() {
  loading.value = true
  try {
    const me = await authApi.me()
    credit.value = me.user?.creditScore ?? 100
    const d = await violationApi.page({ page: 1, size: 50, studentId: me.user?.id })
    rows.value = d.records || []
    total.value = d.total || 0
  } finally {
    loading.value = false
  }
}

function openAppeal(v) {
  cur.value = v
  reason.value = ''
  dlg.value = true
}

async function doAppeal() {
  if (reason.value.trim().length < 10) return ElMessage.warning('申诉理由不少于 10 个字')
  saving.value = true
  try {
    await violationApi.appeal(cur.value.id, { reason: reason.value })
    ElMessage.success('申诉已提交，等待学工老师处理')
    dlg.value = false
    load()
  } finally {
    saving.value = false
  }
}

onMounted(load)
</script>

<style scoped>
.vio {
  padding: 13px 16px;
  border-bottom: 1px solid #f0f2f3;
}
.vio:last-child {
  border-bottom: 0;
}
</style>
