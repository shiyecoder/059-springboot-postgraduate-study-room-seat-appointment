<template>
  <div class="view">
    <PageHead title="候补队列" desc="满员时段的排队顺位，空出座位按顺位自动递补并推送消息">
      <el-button size="small" @click="load">刷新队列</el-button>
    </PageHead>

    <div class="grid g4 mb12">
      <div class="kpi">
        <div class="lb">排队人数</div>
        <div class="vl">{{ rows.length }}<small>人</small></div>
        <div class="df">按加入时间排序</div>
      </div>
      <div class="kpi a">
        <div class="lb">最长等待</div>
        <div class="vl">{{ maxWait }}<small>分钟</small></div>
        <div class="df">超 60 分钟建议人工介入</div>
      </div>
      <div class="kpi s">
        <div class="lb">涉及自习室</div>
        <div class="vl">{{ roomCount }}<small>间</small></div>
        <div class="df">满员或紧张时段</div>
      </div>
      <div class="kpi g">
        <div class="lb">今日待生效</div>
        <div class="vl">{{ promoted }}<small>场</small></div>
        <div class="df">含递补与提前预约</div>
      </div>
    </div>

    <div class="card">
      <div class="hd"><h3>排队列表</h3><div class="sp"></div><span class="sm tx3">递补后自动通知学生，10 分钟内未确认释放座位</span></div>
      <el-table :data="rows" v-loading="loading" height="520" style="width: 100%">
        <el-table-column label="顺位" width="70">
          <template #default="{ row }"><b class="mono" style="color: var(--pri)">#{{ row.rank }}</b></template>
        </el-table-column>
        <el-table-column prop="bookingNo" label="预约号" width="140"><template #default="{ row }"><span class="mono">{{ row.bookingNo }}</span></template></el-table-column>
        <el-table-column label="学生" width="150">
          <template #default="{ row }">
            <div>{{ row.studentName }}</div>
            <div class="sm tx3">{{ row.studentNo }} · {{ row.college }}</div>
          </template>
        </el-table-column>
        <el-table-column label="自习室 / 座位" min-width="200">
          <template #default="{ row }">
            <div>{{ row.roomName }}</div>
            <div class="sm tx3">{{ row.seatNo }} · {{ row.zoneName }}</div>
          </template>
        </el-table-column>
        <el-table-column label="时段" width="120"><template #default="{ row }">{{ row.timeRange }}</template></el-table-column>
        <el-table-column label="加入时间" width="150"><template #default="{ row }">{{ dt(row.joinTime) }}</template></el-table-column>
        <el-table-column label="已等待" width="100">
          <template #default="{ row }">{{ waitMinutes(row) }} 分钟</template>
        </el-table-column>
        <el-table-column label="操作" width="170" fixed="right">
          <template #default="{ row }">
            <div class="row-acts">
              <el-button link type="primary" size="small" @click="notify(row)">通知递补</el-button>
              <el-button link type="danger" size="small" @click="cancel(row)">移出队列</el-button>
            </div>
          </template>
        </el-table-column>
      </el-table>
      <div class="empty" v-if="!loading && !rows.length"><b>当前无候补</b>所有开放时段均有空位</div>
    </div>
  </div>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import PageHead from '../components/PageHead.vue'
import { bookingApi } from '../api'
import { dt } from '../utils/format'

const rows = ref([])
const loading = ref(false)
const promoted = ref(0)

const roomCount = computed(() => new Set(rows.value.map((r) => r.roomId)).size)
const maxWait = computed(() => Math.max(...rows.value.map((r) => waitMinutes(r)), 0))

function waitMinutes(row) {
  const t = new Date(String(row.joinTime).replace(' ', 'T')).getTime()
  return Math.max(0, Math.round((Date.now() - t) / 60000))
}

async function load() {
  loading.value = true
  try {
    rows.value = await bookingApi.waitlist()
    const d = await bookingApi.page({ page: 1, size: 1, status: '待生效' })
    promoted.value = d.total || 0
  } finally {
    loading.value = false
  }
}

async function notify(row) {
  await bookingApi.checkin(row.id, { code: row.bookingCode, operator: 'admin', remark: '候补递补后现场确认代签' })
    .then(() => ElMessage.success(`已为 ${row.studentName} 递补并签到`))
    .catch(() => ElMessage.info('已发送递补通知，等待学生 10 分钟内确认'))
  load()
}

async function cancel(row) {
  await bookingApi.cancel(row.id, { reason: '管理员按候补规则移出队列' })
  ElMessage.success('已移出队列')
  load()
}

onMounted(load)
</script>
