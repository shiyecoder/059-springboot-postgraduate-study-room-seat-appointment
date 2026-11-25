<template>
  <div class="view">
    <PageHead title="用户管理" desc="账号、角色与所属部门维护，密码重置为初始口令">
      <el-button type="primary" size="small" :icon="Plus" @click="openCreate">新增用户</el-button>
    </PageHead>

    <div class="card">
      <div class="bd">
        <div class="filters">
          <el-input v-model="q.keyword" placeholder="账号 / 姓名 / 学工号" clearable style="width: 210px" @keyup.enter="search">
            <template #prefix><el-icon><Search /></el-icon></template>
          </el-input>
          <el-select v-model="q.roleId" placeholder="全部角色" clearable style="width: 160px">
            <el-option v-for="r in roles" :key="r.id" :label="r.roleName" :value="r.id" />
          </el-select>
          <el-select v-model="q.status" placeholder="全部状态" clearable style="width: 120px">
            <el-option label="启用" :value="1" />
            <el-option label="停用" :value="0" />
          </el-select>
          <el-button type="primary" @click="search">查询</el-button>
          <el-button @click="reset">重置</el-button>
          <div class="fx1"></div>
          <span class="sm tx3">共 {{ total }} 个账号</span>
        </div>
      </div>
    </div>

    <div class="card mt12">
      <el-table :data="rows" v-loading="loading" style="width: 100%">
        <el-table-column label="用户" min-width="180">
          <template #default="{ row }">
            <div class="fx">
              <img v-if="row.avatar" class="thumb" :src="row.avatar" alt="" />
              <div>
                <div>{{ row.realName }}</div>
                <div class="sm tx3">{{ row.username }}</div>
              </div>
            </div>
          </template>
        </el-table-column>
        <el-table-column prop="userNo" label="学工号" width="110" />
        <el-table-column prop="roleName" label="角色" width="130" />
        <el-table-column prop="deptName" label="部门 / 学院" min-width="150" />
        <el-table-column prop="title" label="职务" width="100" />
        <el-table-column prop="phone" label="手机" width="130" />
        <el-table-column label="状态" width="86">
          <template #default="{ row }">
            <span class="tag" :class="row.status === 1 ? 'g' : ''">{{ row.status === 1 ? '启用' : '停用' }}</span>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="196" fixed="right">
          <template #default="{ row }">
            <div class="row-acts">
              <el-button link type="primary" size="small" @click="openEdit(row)">编辑</el-button>
              <el-button link type="primary" size="small" @click="resetPwd(row)">重置密码</el-button>
              <el-button link :type="row.status === 1 ? 'danger' : 'success'" size="small" @click="toggle(row)">{{ row.status === 1 ? '停用' : '启用' }}</el-button>
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

    <el-dialog v-model="dlg" :title="form.id ? '编辑用户' : '新增用户'" width="520px" destroy-on-close>
      <el-form :model="form" label-width="88px">
        <el-form-item label="账号" required>
          <el-input v-model="form.username" :disabled="!!form.id" placeholder="登录账号，如 stu2024210399" />
        </el-form-item>
        <el-form-item label="姓名" required><el-input v-model="form.realName" maxlength="20" /></el-form-item>
        <el-form-item label="学工号"><el-input v-model="form.userNo" maxlength="20" /></el-form-item>
        <el-form-item label="角色" required>
          <el-select v-model="form.roleId" style="width: 100%">
            <el-option v-for="r in roles" :key="r.id" :label="r.roleName" :value="r.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="部门 / 学院"><el-input v-model="form.deptName" maxlength="30" /></el-form-item>
        <el-form-item label="职务"><el-input v-model="form.title" maxlength="20" /></el-form-item>
        <el-form-item label="手机"><el-input v-model="form.phone" maxlength="11" /></el-form-item>
        <el-form-item label="邮箱"><el-input v-model="form.email" maxlength="60" /></el-form-item>
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
import { onMounted, reactive, ref } from 'vue'
import { Plus, Search } from '@element-plus/icons-vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import PageHead from '../components/PageHead.vue'
import { systemApi } from '../api'

const q = reactive({ page: 1, size: 10, keyword: '', roleId: null, status: null })
const roles = ref([])
const rows = ref([])
const total = ref(0)
const loading = ref(false)
const saving = ref(false)
const dlg = ref(false)
const form = reactive({})

const empty = () => ({ id: null, username: '', realName: '', userNo: '', roleId: 5, deptName: '', title: '', phone: '', email: '', status: 1 })

async function load() {
  loading.value = true
  try {
    const d = await systemApi.users({ ...q, roleId: q.roleId || undefined, status: q.status === null ? undefined : q.status })
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
  Object.assign(q, { page: 1, keyword: '', roleId: null, status: null })
  load()
}

function openCreate() {
  Object.assign(form, empty())
  dlg.value = true
}

function openEdit(row) {
  Object.assign(form, row)
  dlg.value = true
}

async function save() {
  if (!form.username || !form.realName) return ElMessage.warning('账号与姓名必填')
  saving.value = true
  try {
    await systemApi.saveUser({ ...form }, form.id)
    ElMessage.success(form.id ? '已保存' : '已新增，初始密码 123456')
    dlg.value = false
    load()
  } finally {
    saving.value = false
  }
}

async function resetPwd(row) {
  await ElMessageBox.confirm(`将 ${row.realName} 的密码重置为 123456？`, '重置密码', { type: 'warning' })
  await systemApi.resetPassword(row.id)
  ElMessage.success('已重置为 123456')
}

async function toggle(row) {
  await systemApi.saveUser({ ...row, status: row.status === 1 ? 0 : 1 }, row.id)
  ElMessage.success('已更新状态')
  load()
}

onMounted(async () => {
  roles.value = await systemApi.roles()
  load()
})
</script>
