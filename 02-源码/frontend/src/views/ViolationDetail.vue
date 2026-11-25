<template>
  <div class="view" v-loading="loading">
    <div class="ph">
      <div>
        <h1>{{ v.recordNo }}</h1>
        <p>{{ v.studentName }} · {{ v.studentNo }} · {{ v.college }}</p>
      </div>
      <div class="sp"></div>
      <span class="tag" :class="tone(v.handleStatus)" style="align-self: center">{{ v.handleStatus }}</span>
      <el-button size="small" @click="$router.push('/violation/list')">返回列表</el-button>
      <el-button size="small" type="primary" :disabled="v.handleStatus === '已确认' || v.handleStatus === '已撤销'" @click="openHandle">处理</el-button>
    </div>

    <div class="grid g21">
      <div class="card">
        <div class="hd"><h3>违约信息</h3><div class="sp"></div><span class="sm tx3">来源预约 {{ v.bookingNo }}</span></div>
        <div class="bd">
          <dl class="dl">
            <dt>违约单号</dt><dd class="mono">{{ v.recordNo }}</dd>
            <dt>学生</dt><dd>{{ v.studentName }}（{{ v.studentNo }}）</dd>
            <dt>所属学院</dt><dd>{{ v.college }}</dd>
            <dt>违约类型</dt><dd>{{ v.violationType }}</dd>
            <dt>发生时间</dt><dd>{{ dt(v.occurTime) }}</dd>
            <dt>扣减分值</dt><dd><span style="color: var(--dngr)">-{{ v.deductScore }} 分</span> · 当前信用分 <b>{{ v.creditScore }}</b></dd>
            <dt>关联预约</dt><dd><router-link :to="`/booking/detail/${v.bookingId}`" style="color: var(--pri)">{{ v.bookingNo }}</router-link></dd>
            <dt>申诉截止</dt><dd>{{ dt(v.appealDeadline) }}（发生起 3 日内可申诉一次）</dd>
            <dt>处理状态</dt><dd><span class="tag" :class="tone(v.handleStatus)">{{ v.handleStatus }}</span></dd>
            <dt>处理意见</dt><dd>{{ v.handleResult || '--' }}</dd>
            <dt>处理时间</dt><dd>{{ dt(v.handleTime) }}</dd>
          </dl>
        </div>
      </div>

      <div class="card">
        <div class="hd"><h3>申诉与处理</h3></div>
        <div class="bd">
          <div v-if="v.appealReason" class="alert a mb12">
            <div>
              <b>学生申诉</b>（{{ dt(v.appealTime) }}）<br />
              {{ v.appealReason }}
            </div>
          </div>
          <div v-else class="alert mb12">
            学生在 {{ dt(v.appealDeadline) }} 前可提交一次申诉，理由不少于 10 个字并需说明凭证。
          </div>
          <dl class="dl">
            <dt>信用分影响</dt><dd>扣分后信用分 {{ v.creditScore }}，低于 80 分将限制热门座位预约</dd>
            <dt>黑名单判定</dt><dd>近 30 天累计 3 次违约自动加入黑名单 7 天</dd>
          </dl>
          <div class="divider"></div>
          <el-button size="small" @click="$router.push(`/insight/risk/${v.studentId}`)">查看该生违约风险评分</el-button>
          <el-button size="small" @click="$router.push('/violation/blacklist')">黑名单管理</el-button>
        </div>
      </div>
    </div>

    <el-dialog v-model="dlg" title="处理违约" width="480px">
      <el-form label-width="84px">
        <el-form-item label="处理结论">
          <el-radio-group v-model="form.revoke">
            <el-radio-button :value="false">维持违约</el-radio-button>
            <el-radio-button :value="true">撤销违约</el-radio-button>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="处理意见">
          <el-input v-model="form.result" type="textarea" :rows="3" maxlength="120" show-word-limit placeholder="不少于 5 个字，将推送给学生" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dlg = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="doHandle">提交</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { onMounted, reactive, ref } from 'vue'
import { useRoute } from 'vue-router'
import { ElMessage } from 'element-plus'
import { violationApi } from '../api'
import { dt, tone } from '../utils/format'

const route = useRoute()
const loading = ref(false)
const saving = ref(false)
const v = ref({})
const dlg = ref(false)
const form = reactive({ revoke: false, result: '' })

async function load() {
  loading.value = true
  try {
    v.value = await violationApi.detail(route.params.id)
  } finally {
    loading.value = false
  }
}

function openHandle() {
  form.revoke = false
  form.result = ''
  dlg.value = true
}

async function doHandle() {
  if (form.result.trim().length < 5) return ElMessage.warning('处理意见不少于 5 个字')
  saving.value = true
  try {
    await violationApi.handle(v.value.id, { revoke: form.revoke, result: form.result })
    ElMessage.success(form.revoke ? '已撤销并恢复信用分' : '已维持违约')
    dlg.value = false
    load()
  } finally {
    saving.value = false
  }
}

onMounted(load)
</script>
