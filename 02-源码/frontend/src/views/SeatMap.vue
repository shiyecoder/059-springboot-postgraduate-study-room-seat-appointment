<template>
  <div class="view">
    <PageHead title="座位平面图" desc="按区域查看座位实时状态，停用或维修座位需填写原因并留痕">
      <el-button size="small" @click="reload">刷新</el-button>
    </PageHead>

    <div class="card">
      <div class="bd">
        <div class="filters">
          <el-select v-model="roomId" style="width: 230px" @change="loadMap">
            <el-option v-for="r in rooms" :key="r.id" :label="`${r.roomName}（${r.seatCount} 座）`" :value="r.id" />
          </el-select>
          <el-date-picker v-model="date" type="date" value-format="YYYY-MM-DD" style="width: 148px" @change="loadMap" />
          <el-select v-model="startTime" style="width: 180px" @change="loadMap">
            <el-option v-for="s in slotOptions" :key="s.start" :label="`${hhmm(s.start)}-${hhmm(s.end)}`" :value="s.start" />
          </el-select>
          <div class="fx1"></div>
          <div class="legend">
            <span><i style="background: #E9F2F1"></i>可用 {{ summary.available }}</span>
            <span><i style="background: #DCE7F0"></i>已预约 {{ summary.booked }}</span>
            <span><i style="background: #1D5B63"></i>学习中 {{ summary.inUse }}</span>
            <span><i style="background: #F0F1F2"></i>停用/维修</span>
            <span><i style="background: #F3E9DC"></i>本人座位</span>
          </div>
        </div>
      </div>
    </div>

    <div class="grid mt12" style="grid-template-columns: 1fr 268px">
      <div class="card">
        <div class="hd">
          <h3>{{ map.room?.roomName || '--' }}</h3>
          <div class="sp"></div>
          <span class="sm tx3">{{ date }} {{ slotLabel }} · 共 {{ summary.total }} 座</span>
        </div>
        <div class="bd">
          <div class="stage">讲台 / 入口方向</div>
          <div class="seatwrap" v-loading="loading">
            <div v-for="z in zones" :key="z.name" style="margin-bottom: 18px">
              <div class="fx mb8">
                <b style="font-size: 12.5px">{{ z.name }}</b>
                <span class="sm tx3">{{ z.list.length }} 座 · {{ z.type }}</span>
                <div class="fx1"></div>
                <span class="sm tx3">已用 {{ z.used }}</span>
              </div>
              <div class="sgrid" :style="{ gridTemplateColumns: `repeat(${z.cols}, 34px)` }">
                <button
                  v-for="s in z.list"
                  :key="s.id"
                  class="seat"
                  :class="seatClass(s)"
                  :title="`${s.seatNo} · ${s.status}`"
                  @click="openSeat(s)"
                >
                  {{ s.colNo }}
                </button>
              </div>
            </div>
          </div>
        </div>
      </div>

      <div class="card">
        <div class="hd"><h3>座位详情</h3></div>
        <div class="bd">
          <template v-if="cur">
            <div class="fx mb8">
              <b class="mono" style="font-size: 14px">{{ cur.seatNo }}</b>
              <span class="tag" :class="cur.status === '可用' ? 'g' : cur.status === '使用中' ? 'b' : cur.status === '已预约' ? 'o' : ''">{{ cur.status }}</span>
            </div>
            <dl class="dl">
              <dt>区域</dt><dd>{{ cur.zone }}</dd>
              <dt>类型</dt><dd>{{ cur.seatType }}</dd>
              <dt>电源</dt><dd>{{ cur.hasPower ? '有' : '无' }}</dd>
              <dt>台灯</dt><dd>{{ cur.hasLamp ? '有' : '无' }}</dd>
              <dt>排/列</dt><dd>{{ cur.rowNo }} 排 {{ cur.colNo }} 列</dd>
              <dt>使用人</dt><dd>{{ cur.studentName || '--' }}</dd>
            </dl>
            <div class="divider"></div>
            <div v-if="cur.bookingId" class="sm tx2 mb8">
              占用预约：<router-link :to="`/booking/detail/${cur.bookingId}`" style="color: var(--pri)">查看预约 {{ cur.bookingId }}</router-link>
            </div>
            <el-button type="danger" plain size="small" style="width: 100%" :disabled="cur.status === '已预约' || cur.status === '使用中'" @click="disableDlg = true">
              停用 / 报修
            </el-button>
            <div class="sm tx3 mt8">停用后该座位不再参与派位，已有预约自动改派同区域空位。</div>
          </template>
          <div class="empty" v-else><b>未选中座位</b>点击左侧任一座位查看详情</div>
        </div>
      </div>
    </div>

    <el-dialog v-model="disableDlg" title="停用 / 报修座位" width="440px">
      <div class="alert a mb12" v-if="cur">当前座位 <b class="mono">{{ cur.seatNo }}</b>，停用期间不接受预约。</div>
      <el-form label-width="80px">
        <el-form-item label="处理类型">
          <el-radio-group v-model="disableForm.status">
            <el-radio-button value="停用">停用</el-radio-button>
            <el-radio-button value="维修">维修</el-radio-button>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="原因" required>
          <el-input v-model="disableForm.reason" type="textarea" :rows="3" maxlength="120" show-word-limit placeholder="至少 5 个字，例如：台灯损坏，待更换灯管" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="disableDlg = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="doDisable">确认</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { useRoute } from 'vue-router'
import { ElMessage } from 'element-plus'
import PageHead from '../components/PageHead.vue'
import { roomApi } from '../api'
import { hhmm, localDate } from '../utils/format'

const route = useRoute()
const rooms = ref([])
const roomId = ref(null)
const date = ref(localDate())
const startTime = ref('')
const map = ref({})
const summary = ref({ available: 0, booked: 0, inUse: 0, total: 0 })
const loading = ref(false)
const cur = ref(null)
const disableDlg = ref(false)
const saving = ref(false)
const disableForm = reactive({ status: '停用', reason: '' })

const slotOptions = computed(() => map.value.slots || [])
const slotLabel = computed(() => {
  const s = slotOptions.value.find((x) => x.start === startTime.value)
  return s ? `${hhmm(s.start)}-${hhmm(s.end)}` : ''
})

const zones = computed(() => {
  const zs = map.value.zones || {}
  return Object.entries(zs).map(([name, list]) => {
    const cols = Math.max(...list.map((s) => s.colNo || 1), 1)
    const used = list.filter((s) => s.status !== '可用').length
    return { name, list, cols, used, type: list[0]?.seatType || '普通' }
  })
})

function seatClass(s) {
  const base = { 可用: '', 已预约: 'bk', 使用中: 'us', 停用: 'off', 维修: 'fx', 本人: 'mine' }
  return base[s.status] ?? 'off'
}

function openSeat(s) {
  cur.value = { ...s, zone: zones.value.find((z) => z.list.some((x) => x.id === s.id))?.name || '' }
  disableForm.reason = ''
}

async function loadMap() {
  if (!roomId.value) return
  loading.value = true
  try {
    const d = await roomApi.seatMap({ roomId: roomId.value, date: date.value, startTime: startTime.value || undefined })
    map.value = d
    summary.value = d.summary || summary.value
    if (!startTime.value && d.slots?.length) startTime.value = d.slots[0].start
    cur.value = null
  } finally {
    loading.value = false
  }
}

function reload() {
  loadMap()
  ElMessage.success('已刷新座位状态')
}

async function doDisable() {
  if (!disableForm.reason || disableForm.reason.trim().length < 5) return ElMessage.warning('请填写不少于 5 个字的原因')
  saving.value = true
  try {
    const r = await roomApi.disableSeat(cur.value.id, { ...disableForm })
    ElMessage.success(`已${disableForm.status}${r.reassigned ? `，${r.reassigned} 条预约已改派` : ''}`)
    disableDlg.value = false
    loadMap()
  } finally {
    saving.value = false
  }
}

onMounted(async () => {
  const d = await roomApi.page({ page: 1, size: 50 })
  rooms.value = d.records || []
  roomId.value = Number(route.query.roomId) || rooms.value[0]?.id
  await loadMap()
})
</script>
