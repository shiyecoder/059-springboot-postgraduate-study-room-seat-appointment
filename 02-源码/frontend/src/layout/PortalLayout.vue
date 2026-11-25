<template>
  <div class="portal">
    <nav class="pnav">
      <div class="lg-brand" style="cursor: pointer" @click="router.push('/portal/home')">
        <div class="mk">静</div>
        <b>静研自习室</b>
      </div>
      <div class="lk">
        <router-link to="/portal/home" :class="{ on: route.path === '/portal/home' }">首页</router-link>
        <router-link to="/portal/select" :class="{ on: route.path === '/portal/select' }">选座预约</router-link>
        <router-link to="/portal/bookings" :class="{ on: route.path === '/portal/bookings' }">我的预约</router-link>
        <router-link to="/portal/checkin" :class="{ on: route.path === '/portal/checkin' }">签到</router-link>
        <router-link to="/portal/report" :class="{ on: route.path === '/portal/report' }">学习报告</router-link>
        <router-link to="/portal/violation" :class="{ on: route.path === '/portal/violation' }">我的违约</router-link>
      </div>
      <div class="sp"></div>
      <div class="ico-btn" title="站内消息" @click="router.push('/portal/message')">
        <svg viewBox="0 0 24 24"><path d="M4 5h16v11H8l-4 4z" /></svg>
        <em v-if="unread > 0" class="dot"></em>
      </div>
      <div class="ico-btn" title="公告通知" @click="router.push('/portal/notice')">
        <svg viewBox="0 0 24 24"><path d="M18 8a6 6 0 10-12 0c0 7-3 8-3 8h18s-3-1-3-8M10 21h4" /></svg>
      </div>
      <el-dropdown trigger="click" @command="onCommand">
        <div class="who" style="cursor: pointer">
          <div class="av">
            <img v-if="user?.avatar" :src="user.avatar" alt="" />
            <template v-else>{{ (user?.realName || '·').slice(0, 1) }}</template>
          </div>
          <div>
            <div class="n">{{ user?.realName }}</div>
            <div class="r">{{ user?.userNo }} · {{ user?.deptName }}</div>
          </div>
        </div>
        <template #dropdown>
          <el-dropdown-menu>
            <el-dropdown-item command="profile">个人中心</el-dropdown-item>
            <el-dropdown-item command="logout" divided>退出登录</el-dropdown-item>
          </el-dropdown-menu>
        </template>
      </el-dropdown>
    </nav>
    <div class="pwrap">
      <router-view v-slot="{ Component }">
        <component :is="Component" :key="route.fullPath" />
      </router-view>
    </div>
  </div>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { useUserStore } from '../store/user'
import { authApi, systemApi } from '../api'

const route = useRoute()
const router = useRouter()
const store = useUserStore()
const user = computed(() => store.state.user)
const unread = ref(0)

function onCommand(cmd) {
  if (cmd === 'profile') return router.push('/portal/profile')
  ElMessageBox.confirm('确认退出当前账号？', '退出登录', { type: 'warning', confirmButtonText: '退出', cancelButtonText: '取消' })
    .then(async () => {
      try {
        await authApi.logout()
      } catch (e) {
        /* 令牌失效也照常退出 */
      }
      store.logout()
      ElMessage.success('已退出')
      router.push('/login')
    })
    .catch(() => {})
}

onMounted(async () => {
  try {
    const msgs = await systemApi.messages({ page: 1, size: 20 })
    unread.value = (msgs?.records || []).filter((m) => !m.readFlag).length
  } catch (e) {
    /* 忽略 */
  }
})
</script>
