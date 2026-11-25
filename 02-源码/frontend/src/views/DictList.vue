<template>
  <div class="view">
    <PageHead title="数据字典" desc="状态、类型等枚举统一维护，前端标签与下拉均从此取值">
      <el-button size="small" @click="load">刷新</el-button>
    </PageHead>

    <div class="grid" style="grid-template-columns: 280px 1fr">
      <div class="card">
        <div class="hd"><h3>字典类型</h3><div class="sp"></div><span class="sm tx3">{{ types.length }}</span></div>
        <div class="bd" style="padding: 6px">
          <div
            v-for="t in types"
            :key="t.id"
            class="dict-item"
            :class="{ on: current === t.dictType }"
            @click="pickType(t)"
          >
            <div>
              <div style="font-size: 12.5px">{{ t.dictName }}</div>
              <div class="sm tx3 mono">{{ t.dictType }}</div>
            </div>
            <span class="tag">{{ countOf(t.dictType) }} 项</span>
          </div>
        </div>
      </div>

      <div class="card">
        <div class="hd">
          <h3>{{ currentName }} · 字典项</h3>
          <div class="sp"></div>
          <el-button size="small" type="primary" :icon="Plus" @click="openCreate">新增字典项</el-button>
        </div>
        <el-table :data="items" v-loading="loading" style="width: 100%">
          <el-table-column prop="dictLabel" label="标签" min-width="150" />
          <el-table-column prop="dictValue" label="值" width="150"><template #default="{ row }"><span class="mono">{{ row.dictValue }}</span></template></el-table-column>
          <el-table-column label="标签样式" width="120">
            <template #default="{ row }"><span class="tag" :class="row.tagType">{{ row.tagType || '默认' }}</span></template>
          </el-table-column>
          <el-table-column prop="sortNo" label="排序" width="80" align="right" />
          <el-table-column label="状态" width="90">
            <template #default="{ row }"><span class="tag" :class="row.status === 1 ? 'g' : ''">{{ row.status === 1 ? '启用' : '停用' }}</span></template>
          </el-table-column>
          <el-table-column label="操作" width="90" fixed="right">
            <template #default="{ row }">
              <el-button link type="primary" size="small" @click="openEdit(row)">编辑</el-button>
            </template>
          </el-table-column>
        </el-table>
      </div>
    </div>

    <el-dialog v-model="dlg" :title="form.id ? '编辑字典项' : '新增字典项'" width="440px" destroy-on-close>
      <el-form :model="form" label-width="88px">
        <el-form-item label="所属类型"><el-input v-model="form.dictType" disabled /></el-form-item>
        <el-form-item label="标签" required><el-input v-model="form.dictLabel" maxlength="20" /></el-form-item>
        <el-form-item label="值" required><el-input v-model="form.dictValue" maxlength="30" /></el-form-item>
        <el-form-item label="标签样式">
          <el-select v-model="form.tagType" style="width: 160px">
            <el-option v-for="t in ['', 'b', 'g', 'a', 'r', 'o']" :key="t" :label="t || '默认'" :value="t" />
          </el-select>
        </el-form-item>
        <el-form-item label="排序"><el-input-number v-model="form.sortNo" :min="1" :max="99" controls-position="right" /></el-form-item>
        <el-form-item label="状态">
          <el-radio-group v-model="form.status">
            <el-radio :value="1">启用</el-radio>
            <el-radio :value="0">停用</el-radio>
          </el-radio-group>
        </el-form-item>
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
import { Plus } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import PageHead from '../components/PageHead.vue'
import { systemApi } from '../api'

const types = ref([])
const all = ref({})
const current = ref('')
const items = ref([])
const loading = ref(false)
const saving = ref(false)
const dlg = ref(false)
const form = reactive({})

const currentName = computed(() => types.value.find((t) => t.dictType === current.value)?.dictName || '字典项')

function countOf(type) {
  return (all.value[type] || []).length
}

async function pickType(t) {
  current.value = t.dictType
  items.value = all.value[t.dictType] || []
  if (!items.value.length) {
    items.value = await systemApi.dictData(t.dictType)
    all.value[t.dictType] = items.value
  }
}

function openCreate() {
  Object.assign(form, { id: null, dictType: current.value, dictLabel: '', dictValue: '', tagType: '', sortNo: items.value.length + 1, status: 1 })
  dlg.value = true
}

function openEdit(row) {
  Object.assign(form, row)
  dlg.value = true
}

async function save() {
  if (!form.dictLabel || !form.dictValue) return ElMessage.warning('标签与值必填')
  saving.value = true
  try {
    await systemApi.saveDictData({ ...form })
    ElMessage.success('已保存')
    dlg.value = false
    all.value[form.dictType] = await systemApi.dictData(form.dictType)
    items.value = all.value[form.dictType]
  } finally {
    saving.value = false
  }
}

async function load() {
  loading.value = true
  try {
    const [t, d] = await Promise.all([systemApi.dictTypes(), systemApi.dicts()])
    types.value = t || []
    all.value = d || {}
    if (types.value.length) {
      current.value = current.value || types.value[0].dictType
      items.value = all.value[current.value] || (await systemApi.dictData(current.value))
    }
  } finally {
    loading.value = false
  }
}

onMounted(load)
</script>

<style scoped>
.dict-item {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 8px 10px;
  border-radius: 6px;
  cursor: pointer;
  border-left: 3px solid transparent;
}
.dict-item:hover {
  background: #fafbfb;
}
.dict-item.on {
  background: var(--pri-l);
  border-left-color: var(--pri);
}
</style>
