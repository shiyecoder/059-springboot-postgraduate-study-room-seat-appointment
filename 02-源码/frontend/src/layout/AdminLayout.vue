<template>
  <div class="app">
    <aside class="side">
      <div class="brand">
        <div class="mk">静</div>
        <div>
          <b>静研座位</b>
          <i>STUDY SEAT OPS</i>
        </div>
      </div>
      <nav class="nav">
        <template v-for="g in menus" :key="g.id">
          <div class="grp">{{ g.name }}</div>
          <router-link v-for="m in g.children || []" :key="m.id" :to="m.path" :class="{ on: isOn(m.path) }">
            <svg class="ic" viewBox="0 0 24 24"><path :d="iconOf(m.path)" /></svg>
            <span>{{ m.name }}</span>
            <em v-if="badge(m.path)" class="bdg" :class="{ g: badge(m.path).tone === 'g' }">{{ badge(m.path).n }}</em>
          </router-link>
        </template>
      </nav>
      <div class="foot">自习室运营 · 场馆运维组 v1.0</div>
    </aside>

    <div class="main">
      <header class="top">
        <div class="crumb">{{ route.meta.group || '场馆运营' }} / <b>{{ route.meta.title }}</b></div>
        <div class="sp"></div>
        <div class="ico-btn" title="站内消息" @click="go('/me/message')">
          <svg viewBox="0 0 24 24"><path d="M4 5h16v11H8l-4 4z" /></svg>
          <em v-if="unread > 0" class="dot"></em>
        </div>
        <div class="ico-btn" title="公告通知" @click="go('/me/notice')">
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
              <div class="r">{{ user?.roleName || roleLabel }}</div>
            </div>
          </div>
          <template #dropdown>
            <el-dropdown-menu>
              <el-dropdown-item command="profile">个人中心</el-dropdown-item>
              <el-dropdown-item command="logout" divided>退出登录</el-dropdown-item>
            </el-dropdown-menu>
          </template>
        </el-dropdown>
      </header>

      <main class="content">
        <router-view v-slot="{ Component }">
          <component :is="Component" :key="route.fullPath" />
        </router-view>
      </main>
    </div>
  </div>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { useUserStore, ROLE_LABEL } from '../store/user'
import { authApi, bookingApi, violationApi, systemApi } from '../api'

const route = useRoute()
const router = useRouter()
const store = useUserStore()
const user = computed(() => store.state.user)
const roleLabel = computed(() => ROLE_LABEL[store.state.user?.roleCode] || '')
const unread = ref(0)
const counts = ref({ checkin: 0, wait: 0, black: 0 })

const menus = computed(() => store.state.menus || [])

const ICONS = {
  '/dashboard/index': 'M3 3h7v7H3zM14 3h7v5h-7zM14 12h7v9h-7zM3 14h7v7H3z',
  '/room/list': 'M4 20V7l8-4 8 4v13M9 20v-6h6v6',
  '/room/seat-map': 'M6 10V7a2 2 0 012-2h8a2 2 0 012 2v3M4 10h16v5H4zM6 15v5M18 15v5',
  '/booking/list': 'M7 4h10v17H7zM10 4V3h4v1M10 9h4M10 13h4M10 17h3',
  '/booking/checkin': 'M4 12l5 5L20 6',
  '/booking/waitlist': 'M12 7v5l3 2M12 3a9 9 0 100 18 9 9 0 000-18',
  '/violation/list': 'M12 3l9 16H3zM12 9v5M12 17h.01',
  '/violation/blacklist': 'M9 11a4 4 0 100-8 4 4 0 000 8M3 21c0-3.3 2.7-5 6-5M17 14l4 4M21 14l-4 4',
  '/insight/heatmap': 'M12 3c3 4 6 6 6 10a6 6 0 11-12 0c0-2 1-3.5 2-5 0 2 1 3 2 3s2-1 2-2c0-2-1-3 0-6z',
  '/insight/report': 'M4 20V4M4 20h16M8 16v-4M12 16V8M16 16v-6',
  '/insight/rule': 'M6 4v6M6 14v6M12 4v10M12 18v2M18 4v2M18 10v10',
  '/insight/college': 'M3 9l9-4 9 4-9 4-9-4M7 11v4c0 1.7 2.2 3 5 3s5-1.3 5-3v-4',
  '/system/user': 'M12 11a4 4 0 100-8 4 4 0 000 8M4 21c0-3.9 3.6-6 8-6s8 2.1 8 6',
  '/system/role': 'M12 3l8 3v6c0 5-3.5 8-8 9-4.5-1-8-4-8-9V6l8-3z',
  '/system/menu': 'M4 6h16M4 12h16M4 18h16',
  '/system/dict': 'M4 5a2 2 0 012-2h13v18H6a2 2 0 01-2-2zM8 7h7M8 11h5',
  '/system/log': 'M7 3h10a2 2 0 012 2v14l-3-2-2 2-2-2-2 2-3-2V5a2 2 0 012-2M9 8h6M9 12h6',
  '/me/notice': 'M18 8a6 6 0 10-12 0c0 7-3 8-3 8h18s-3-1-3-8M10 21h4',
  '/me/message': 'M4 5h16v11H8l-4 4z',
  '/me/profile': 'M12 12a4 4 0 100-8 4 4 0 000 8M5 21c0-4 3-6 7-6s7 2 7 6'
}

function iconOf(path) {
  return ICONS[path] || 'M4 6h16M4 12h16M4 18h16'
}

function isOn(path) {
  const active = route.meta.active || route.path
  return active === path || (path === '/room/list' && route.path.startsWith('/room/detail'))
}

function badge(path) {
  if (path === '/booking/checkin' && counts.value.checkin) return { n: counts.value.checkin, tone: 'a' }
  if (path === '/booking/waitlist' && counts.value.wait) return { n: counts.value.wait, tone: 'a' }
  if (path === '/violation/blacklist' && counts.value.black) return { n: counts.value.black, tone: 'g' }
  return null
}

function go(path) {
  router.push(path)
}

function onCommand(cmd) {
  if (cmd === 'profile') return router.push('/me/profile')
  ElMessageBox.confirm('确认退出当前账号？', '退出登录', { type: 'warning', confirmButtonText: '退出', cancelButtonText: '取消' })
    .then(async () => {
      try {
        await authApi.logout()
      } catch (e) {
        /* 令牌已失效也照常退出 */
      }
      store.logout()
      ElMessage.success('已退出')
      router.push('/login')
    })
    .catch(() => {})
}

async function loadCounts() {
  try {
    const [pending, wait, black, msgs] = await Promise.all([
      bookingApi.page({ page: 1, size: 1, status: '待签到' }),
      bookingApi.waitlist(),
      violationApi.blacklist({ page: 1, size: 1, status: '生效中' }),
      systemApi.messages({ page: 1, size: 20 })
    ])
    counts.value.checkin = pending?.total || 0
    counts.value.wait = (wait || []).length
    counts.value.black = black?.total || 0
    unread.value = (msgs?.records || []).filter((m) => !m.readFlag).length
  } catch (e) {
    /* 无权访问的菜单不计徽标 */
  }
}

onMounted(loadCounts)
</script>
