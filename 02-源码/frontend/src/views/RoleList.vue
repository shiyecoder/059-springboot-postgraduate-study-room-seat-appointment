<template>
  <div class="view">
    <PageHead title="角色管理" desc="角色决定侧栏菜单与操作权限，数据范围控制可见记录">
      <el-button type="primary" size="small" :icon="Plus" @click="openCreate">新增角色</el-button>
    </PageHead>

    <div class="grid g2">
      <div class="card">
        <div class="hd"><h3>角色列表</h3><div class="sp"></div><span class="sm tx3">{{ rows.length }} 个角色</span></div>
        <el-table :data="rows" v-loading="loading" highlight-current-row @current-change="pick" style="width: 100%">
          <el-table-column prop="roleName" label="角色名称" min-width="140" />
          <el-table-column prop="roleCode" label="角色编码" width="140"><template #default="{ row }"><span class="mono sm">{{ row.roleCode }}</span></template></el-table-column>
          <el-table-column prop="dataScope" label="数据范围" width="100" />
          <el-table-column label="状态" width="82">
            <template #default="{ row }"><span class="tag" :class="row.status === 1 ? 'g' : ''">{{ row.status === 1 ? '启用' : '停用' }}</span></template>
          </el-table-column>
          <el-table-column label="操作" width="130" fixed="right">
            <template #default="{ row }">
              <div class="row-acts">
                <el-button link type="primary" size="small" @click="openEdit(row)">编辑</el-button>
                <el-button link type="primary" size="small" @click="openPerm(row)">分配菜单</el-button>
              </div>
            </template>
          </el-table-column>
        </el-table>
      </div>

      <div class="card">
        <div class="hd">
          <h3>菜单与权限</h3>
          <div class="sp"></div>
          <span class="sm tx3" v-if="cur">当前角色：{{ cur.roleName }}</span>
        </div>
        <div class="bd">
          <div v-if="!cur" class="empty"><b>请选择左侧角色</b>选择后可查看并调整菜单授权</div>
          <template v-else>
            <el-tree
              ref="treeRef"
              :data="menuTree"
              node-key="id"
              show-checkbox
              default-expand-all
              :props="{ label: 'name', children: 'children' }"
              :default-checked-keys="checked"
            >
              <template #default="{ data }">
                <span class="fx" style="gap: 8px">
                  <span>{{ data.name }}</span>
                  <span class="sm tx3 mono">{{ data.path }}</span>
                  <span class="tag" v-if="data.menuType === '目录'" style="font-size: 10px">目录</span>
                </span>
              </template>
            </el-tree>
            <div class="fx mt12">
              <el-button type="primary" size="small" :loading="saving" @click="savePerm">保存授权</el-button>
              <el-button size="small" @click="loadPerm(cur)">重置</el-button>
              <div class="fx1"></div>
              <span class="sm tx3">勾选父目录自动包含子菜单</span>
            </div>
          </template>
        </div>
      </div>
    </div>

    <el-dialog v-model="dlg" :title="form.id ? '编辑角色' : '新增角色'" width="460px" destroy-on-close>
      <el-form :model="form" label-width="88px">
        <el-form-item label="角色名称" required><el-input v-model="form.roleName" maxlength="20" /></el-form-item>
        <el-form-item label="角色编码" required><el-input v-model="form.roleCode" :disabled="!!form.id" placeholder="大写英文，如 LIB_ADMIN" /></el-form-item>
        <el-form-item label="数据范围">
          <el-select v-model="form.dataScope" style="width: 100%">
            <el-option v-for="s in ['全部', '本楼栋', '本学院', '本人']" :key="s" :label="s" :value="s" />
          </el-select>
        </el-form-item>
        <el-form-item label="状态">
          <el-radio-group v-model="form.status">
            <el-radio :value="1">启用</el-radio>
            <el-radio :value="0">停用</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="备注"><el-input v-model="form.remark" type="textarea" :rows="2" maxlength="80" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dlg = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="save">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { onMounted, reactive, ref } from 'vue'
import { Plus } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import PageHead from '../components/PageHead.vue'
import { systemApi } from '../api'

const rows = ref([])
const menuTree = ref([])
const loading = ref(false)
const saving = ref(false)
const cur = ref(null)
const checked = ref([])
const treeRef = ref(null)
const dlg = ref(false)
const form = reactive({})

async function load() {
  loading.value = true
  try {
    rows.value = await systemApi.roles()
    menuTree.value = await systemApi.menuTree()
  } finally {
    loading.value = false
  }
}

async function pick(row) {
  if (!row) return
  cur.value = row
  await loadPerm(row)
}

async function loadPerm(row) {
  const ids = await systemApi.roleMenus(row.id)
  checked.value = ids || []
  if (treeRef.value) treeRef.value.setCheckedKeys(checked.value)
}

function openCreate() {
  Object.assign(form, { id: null, roleName: '', roleCode: '', dataScope: '本人', status: 1, remark: '' })
  dlg.value = true
}

function openEdit(row) {
  Object.assign(form, row)
  dlg.value = true
}

async function save() {
  if (!form.roleName || !form.roleCode) return ElMessage.warning('角色名称与编码必填')
  saving.value = true
  try {
    await systemApi.saveRole({ ...form }, form.id)
    ElMessage.success('已保存')
    dlg.value = false
    load()
  } finally {
    saving.value = false
  }
}

function openPerm(row) {
  cur.value = row
  loadPerm(row)
}

async function savePerm() {
  const ids = [...treeRef.value.getCheckedKeys(), ...treeRef.value.getHalfCheckedKeys()]
  saving.value = true
  try {
    await systemApi.saveRoleMenus(cur.value.id, ids)
    ElMessage.success(`已为「${cur.value.roleName}」保存 ${ids.length} 项授权`)
  } finally {
    saving.value = false
  }
}

onMounted(load)
</script>
