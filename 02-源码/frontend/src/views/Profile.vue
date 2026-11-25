<template>
  <div class="view">
    <PageHead title="个人中心" desc="维护联系方式与头像，修改登录密码">
      <el-tag type="info" effect="plain">{{ roleLabel }}</el-tag>
    </PageHead>

    <div class="grid g21">
      <div class="card">
        <div class="hd"><h3>基本信息</h3></div>
        <div class="bd">
          <div class="fx mb12" style="gap: 16px">
            <div class="av" style="width: 56px; height: 56px; font-size: 18px">
              <img v-if="info.avatar" :src="info.avatar" alt="" />
              <template v-else>{{ (info.realName || '·').slice(0, 1) }}</template>
            </div>
            <div>
              <div style="font-size: 15px; font-weight: 600">{{ info.realName }}</div>
              <div class="sm tx3">{{ info.username }} · {{ info.userNo }}</div>
            </div>
            <div class="fx1"></div>
            <el-upload :show-file-list="false" :http-request="uploadAvatar" accept="image/*">
              <el-button size="small">更换头像</el-button>
            </el-upload>
          </div>
          <el-form :model="form" label-width="88px">
            <el-form-item label="姓名"><el-input v-model="form.realName" maxlength="20" /></el-form-item>
            <el-form-item label="手机"><el-input v-model="form.phone" maxlength="11" /></el-form-item>
            <el-form-item label="邮箱"><el-input v-model="form.email" maxlength="60" /></el-form-item>
            <el-form-item label="部门 / 学院"><el-input v-model="form.deptName" disabled /></el-form-item>
            <el-form-item label="角色"><el-input :model-value="roleLabel" disabled /></el-form-item>
            <el-form-item>
              <el-button type="primary" size="small" :loading="saving" @click="save">保存修改</el-button>
            </el-form-item>
          </el-form>
        </div>
      </div>

      <div class="card">
        <div class="hd"><h3>账号安全</h3></div>
        <div class="bd">
          <el-form :model="pwd" label-width="88px">
            <el-form-item label="原密码"><el-input v-model="pwd.oldPassword" type="password" placeholder="请输入原密码" /></el-form-item>
            <el-form-item label="新密码"><el-input v-model="pwd.newPassword" type="password" placeholder="6-20 位，建议字母 + 数字" /></el-form-item>
            <el-form-item label="确认密码"><el-input v-model="pwd.confirm" type="password" placeholder="再次输入新密码" /></el-form-item>
            <el-form-item>
              <el-button type="primary" size="small" :loading="savingPwd" @click="changePwd">修改密码</el-button>
            </el-form-item>
          </el-form>
          <div class="divider"></div>
          <dl class="dl">
            <dt>信用分</dt>
            <dd>
              <b :style="{ color: (info.creditScore || 100) < 80 ? 'var(--dngr)' : 'var(--ok)' }">{{ info.creditScore ?? 100 }}</b>
              <span class="sm tx3"> / 100，低于 80 分限制热门座位预约</span>
            </dd>
            <dt>登录账号</dt><dd class="mono">{{ info.username }}</dd>
            <dt>上次登录</dt><dd>{{ dt(info.lastLoginTime) }}</dd>
          </dl>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import PageHead from '../components/PageHead.vue'
import { authApi, fileApi } from '../api'
import { dt } from '../utils/format'
import { useUserStore, ROLE_LABEL } from '../store/user'

const store = useUserStore()
const info = ref({})
const saving = ref(false)
const savingPwd = ref(false)
const form = reactive({ realName: '', phone: '', email: '', deptName: '' })
const pwd = reactive({ oldPassword: '', newPassword: '', confirm: '' })
const roleLabel = computed(() => ROLE_LABEL[info.value.roleCode] || info.value.roleName || '')

async function load() {
  const d = await authApi.me()
  // 后端 /auth/me 返回 { user, roleCode, unread }，这里摊平成一份便于模板直接读取
  info.value = { ...(d.user || {}), roleCode: d.roleCode }
  Object.assign(form, { realName: d.user?.realName, phone: d.user?.phone, email: d.user?.email, deptName: d.user?.deptName })
}

async function save() {
  saving.value = true
  try {
    await authApi.profile({ ...form })
    ElMessage.success('已保存')
    store.setUser({ realName: form.realName })
    load()
  } finally {
    saving.value = false
  }
}

async function uploadAvatar(opt) {
  const r = await fileApi.upload(opt.file, 'avatar')
  await authApi.profile({ avatar: r.url })
  ElMessage.success('头像已更新')
  store.setUser({ avatar: r.url })
  load()
}

async function changePwd() {
  if (!pwd.oldPassword || !pwd.newPassword) return ElMessage.warning('请填写原密码与新密码')
  if (pwd.newPassword.length < 6) return ElMessage.warning('新密码不少于 6 位')
  if (pwd.newPassword !== pwd.confirm) return ElMessage.warning('两次输入的新密码不一致')
  savingPwd.value = true
  try {
    await authApi.password({ oldPassword: pwd.oldPassword, newPassword: pwd.newPassword })
    ElMessage.success('密码已修改，请重新登录')
    Object.assign(pwd, { oldPassword: '', newPassword: '', confirm: '' })
  } finally {
    savingPwd.value = false
  }
}

onMounted(load)
</script>
