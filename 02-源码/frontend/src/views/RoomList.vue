<template>
  <div class="view">
    <PageHead title="自习室档案" desc="维护自习室位置、开放时段、时段长度与座位规模">
      <el-button type="primary" size="small" :icon="Plus" @click="openCreate">新增自习室</el-button>
    </PageHead>

    <div class="card">
      <div class="bd">
        <div class="filters">
          <el-input v-model="q.keyword" placeholder="自习室编号 / 名称" clearable style="width: 210px" @keyup.enter="search">
            <template #prefix><el-icon><Search /></el-icon></template>
          </el-input>
          <el-select v-model="q.buildingName" placeholder="全部楼栋" clearable style="width: 150px">
            <el-option v-for="b in buildings" :key="b" :label="b" :value="b" />
          </el-select>
          <el-select v-model="q.status" placeholder="全部状态" clearable style="width: 120px">
            <el-option v-for="s in statuses" :key="s" :label="s" :value="s" />
          </el-select>
          <el-select v-model="q.quietLevel" placeholder="全部等级" clearable style="width: 130px">
            <el-option v-for="s in levels" :key="s" :label="s" :value="s" />
          </el-select>
          <el-button type="primary" @click="search">查询</el-button>
          <el-button @click="reset">重置</el-button>
          <div class="fx1"></div>
          <span class="sm tx3">在管 {{ total }} 间 · 座位 {{ seatSum }} 个</span>
        </div>
      </div>
    </div>

    <div class="card mt12">
      <el-table :data="rows" v-loading="loading" style="width: 100%">
        <el-table-column prop="roomNo" label="自习室编号" width="104">
          <template #default="{ row }"><span class="mono">{{ row.roomNo }}</span></template>
        </el-table-column>
        <el-table-column prop="roomName" label="名称" min-width="150" />
        <el-table-column prop="buildingName" label="楼栋" width="104" />
        <el-table-column label="楼层" width="60">
          <template #default="{ row }">{{ row.floorNo }} 层</template>
        </el-table-column>
        <el-table-column label="座位数" width="80" align="right">
          <template #default="{ row }">{{ row.seatCount }} 个</template>
        </el-table-column>
        <el-table-column label="开放时段 / 时段长度" width="186">
          <template #default="{ row }">
            <span class="mono">{{ hhmm(row.openStart) }}-{{ hhmm(row.openEnd) }}</span>
            <span class="tx3"> / {{ row.slotMinutes }} 分钟</span>
          </template>
        </el-table-column>
        <el-table-column prop="quietLevel" label="安静等级" width="90" />
        <el-table-column prop="managerName" label="管理员" width="80" />
        <el-table-column label="状态" width="84">
          <template #default="{ row }"><span class="tag" :class="tone(row.status)">{{ row.status }}</span></template>
        </el-table-column>
        <el-table-column label="操作" width="176">
          <template #default="{ row }">
            <div class="row-acts">
              <el-button link type="primary" size="small" @click="$router.push(`/room/detail/${row.id}`)">详情</el-button>
              <el-button link type="primary" size="small" @click="$router.push({ path: '/room/seat-map', query: { roomId: row.id } })">座位图</el-button>
              <el-button link type="primary" size="small" @click="openEdit(row)">编辑</el-button>
              <el-button link type="danger" size="small" :disabled="row.status === '已关闭'" @click="close(row)">停用</el-button>
            </div>
          </template>
        </el-table-column>
      </el-table>
      <el-pagination
        v-model:current-page="q.page"
        v-model:page-size="q.size"
        :total="total"
        :page-sizes="[10, 20, 50]"
        layout="total, sizes, prev, pager, next"
        background
        @current-change="load"
        @size-change="load"
      />
    </div>

    <el-dialog v-model="dlg" :title="form.id ? '编辑自习室' : '新增自习室'" width="560px" destroy-on-close>
      <el-form :model="form" :rules="rules" ref="formRef" label-width="104px">
        <el-form-item label="自习室编号" prop="roomNo">
          <el-input v-model="form.roomNo" placeholder="如 ZX0601" :disabled="!!form.id" maxlength="16" />
        </el-form-item>
        <el-form-item label="名称" prop="roomName">
          <el-input v-model="form.roomName" placeholder="如 图书馆六层自习室" maxlength="30" />
        </el-form-item>
        <el-form-item label="楼栋" prop="buildingName">
          <el-select v-model="form.buildingName" placeholder="选择楼栋" style="width: 100%">
            <el-option v-for="b in buildings" :key="b" :label="b" :value="b" />
          </el-select>
        </el-form-item>
        <el-form-item label="楼层" prop="floorNo">
          <el-input-number v-model="form.floorNo" :min="1" :max="12" controls-position="right" />
        </el-form-item>
        <el-form-item label="开放时段" prop="openStart">
          <div class="fx">
            <el-time-select v-model="form.openStart" start="06:00" step="00:30" end="12:00" placeholder="开始" style="width: 120px" />
            <span class="tx3">至</span>
            <el-time-select v-model="form.openEnd" start="12:00" step="00:30" end="23:59" placeholder="结束" style="width: 120px" />
          </div>
        </el-form-item>
        <el-form-item label="时段长度" prop="slotMinutes">
          <el-radio-group v-model="form.slotMinutes">
            <el-radio-button :value="120">120 分钟</el-radio-button>
            <el-radio-button :value="180">180 分钟</el-radio-button>
            <el-radio-button :value="240">240 分钟</el-radio-button>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="安静等级" prop="quietLevel">
          <el-select v-model="form.quietLevel" style="width: 160px">
            <el-option v-for="s in levels" :key="s" :label="s" :value="s" />
          </el-select>
        </el-form-item>
        <el-form-item label="状态" prop="status">
          <el-select v-model="form.status" style="width: 160px">
            <el-option v-for="s in statuses" :key="s" :label="s" :value="s" />
          </el-select>
        </el-form-item>
        <div class="alert" style="margin-left: 104px">
          座位在「座位平面图」页按区域批量维护；新增自习室初始座位数为 0，需先建座位再开放预约。
        </div>
      </el-form>
      <template #footer>
        <el-button @click="dlg = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="save">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { Plus, Search } from '@element-plus/icons-vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import PageHead from '../components/PageHead.vue'
import { roomApi } from '../api'
import { hhmm, tone } from '../utils/format'

const buildings = ['图书馆', '第一教学楼', '第二教学楼', '第三教学楼', '第五教学楼']
const statuses = ['开放', '满员', '维护中', '已关闭']
const levels = ['安静', '较安静', '可讨论']

const q = reactive({ page: 1, size: 10, keyword: '', buildingName: '', status: '', quietLevel: '' })
const rows = ref([])
const total = ref(0)
const loading = ref(false)
const seatSum = computed(() => rows.value.reduce((s, r) => s + (r.seatCount || 0), 0))

const dlg = ref(false)
const saving = ref(false)
const formRef = ref(null)
const empty = () => ({
  id: null, roomNo: '', roomName: '', buildingName: '图书馆', floorNo: 1,
  openStart: '07:30', openEnd: '22:30', slotMinutes: 240, quietLevel: '安静', status: '开放'
})
const form = reactive(empty())
const rules = {
  roomNo: [{ required: true, message: '请输入自习室编号', trigger: 'blur' }],
  roomName: [{ required: true, message: '请输入名称', trigger: 'blur' }],
  buildingName: [{ required: true, message: '请选择楼栋', trigger: 'change' }],
  openStart: [{ required: true, message: '请选择开放开始时间', trigger: 'change' }],
  openEnd: [{ required: true, message: '请选择开放结束时间', trigger: 'change' }]
}

async function load() {
  loading.value = true
  try {
    const d = await roomApi.page(q)
    rows.value = d.records || []
    total.value = d.total || 0
  } finally {
    loading.value = false
  }
}

function search() {
  q.page = 1
  load()
}

function reset() {
  Object.assign(q, { page: 1, keyword: '', buildingName: '', status: '', quietLevel: '' })
  load()
}

function openCreate() {
  Object.assign(form, empty())
  dlg.value = true
}

function openEdit(row) {
  Object.assign(form, {
    id: row.id, roomNo: row.roomNo, roomName: row.roomName, buildingName: row.buildingName,
    floorNo: row.floorNo, openStart: hhmm(row.openStart), openEnd: hhmm(row.openEnd),
    slotMinutes: row.slotMinutes, quietLevel: row.quietLevel, status: row.status
  })
  dlg.value = true
}

async function save() {
  await formRef.value.validate()
  saving.value = true
  try {
    await roomApi.save({ ...form }, form.id)
    ElMessage.success(form.id ? '已保存' : '已新增，请到座位平面图补充座位')
    dlg.value = false
    load()
  } finally {
    saving.value = false
  }
}

async function close(row) {
  await ElMessageBox.confirm(
    `停用后「${row.roomName}」不可再被预约，已产生的预约需人工改约。确认停用？`,
    '停用自习室',
    { type: 'warning', confirmButtonText: '停用', cancelButtonText: '取消' }
  )
  await roomApi.save({ status: '已关闭' }, row.id)
  ElMessage.success('已停用')
  load()
}

onMounted(load)
</script>
