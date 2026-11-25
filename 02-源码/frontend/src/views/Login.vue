<template>
  <div class="lg-page">
    <section class="lg-left">
      <div class="bgimg" :style="{ backgroundImage: `url(${bg})` }"></div>
      <div class="mask"></div>
      <div class="lg-brand">
        <div class="mk">静</div>
        <div>
          <b>静研自习室</b>
          <i>SEAT BOOKING &amp; STUDY TRACKING</i>
        </div>
      </div>
      <div>
        <h2>把座位留给<em>真正坐下来</em>的人</h2>
        <p class="sub">
          分时段座位预约、预约码签到、学习时长客观记录与违约信用管理，<br />
          让每一张桌子都被认真使用，让每一小时专注都有据可查。
        </p>
        <div class="stats">
          <div><b>{{ hero.rooms }}</b><span>在管自习室</span></div>
          <div><b>{{ hero.seats }}</b><span>可预约座位</span></div>
          <div><b>{{ hero.rate }}</b><span>平均到座率</span></div>
        </div>
      </div>
      <div class="cp">© 2026 静研自习室 · 校园场馆运营中心</div>
    </section>

    <section class="lg-right">
      <div class="lg-form">
        <h3>欢迎回来</h3>
        <div class="tip">使用学号或工号登录，首次登录请先完善个人信息</div>

        <div class="fd user">
          <label>账号</label>
          <input v-model.trim="form.username" class="inp" placeholder="学号 / 工号" autocomplete="username" @keyup.enter="submit" />
        </div>
        <div class="fd pwd">
          <label>密码</label>
          <input v-model="form.password" class="inp" type="password" placeholder="请输入密码" autocomplete="current-password" @keyup.enter="submit" />
        </div>

        <div class="row">
          <label><input v-model="remember" type="checkbox" />记住我（7 天）</label>
          <a href="javascript:;" @click="forgot">忘记密码？</a>
        </div>

        <button class="sub-btn" :disabled="loading" @click="submit">{{ loading ? '登录中…' : '登 录' }}</button>

        <div class="alert r mt12" v-if="err" style="font-size: 12px">{{ err }}</div>

        <div class="roles">
          <a v-for="r in quick" :key="r.username" :class="{ on: form.username === r.username }" @click="pick(r)">
            {{ r.label }}
          </a>
        </div>
        <div class="foot">登录即表示同意《自习室使用规则》与《违约处理办法》</div>
      </div>
    </section>
  </div>
</template>

<script setup>
import { reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { authApi } from '../api'
import { useUserStore } from '../store/user'

const router = useRouter()
const route = useRoute()
const store = useUserStore()
const bg = '/uploads/seed/room/02.jpg'
const loading = ref(false)
const remember = ref(true)
const err = ref('')
const form = reactive({ username: '', password: '123456' })
/** 左侧展示数据取种子库真实规模（8 间自习室 / 756 个座位），到座率固定展示最近统计口径 */
const hero = reactive({ rooms: 8, seats: 756, rate: '91.4%' })

const quick = [
  { label: '考研学生', username: 'stu2024210318' },
  { label: '自习室管理员', username: 'manager01' },
  { label: '学工老师', username: 'teacher01' },
  { label: '运营主管', username: 'operator' },
  { label: '系统管理员', username: 'admin' }
]

function pick(r) {
  form.username = r.username
  form.password = '123456'
  err.value = ''
}

function forgot() {
  ElMessage.info('请联系场馆运营中心（图书馆一层服务台）重置密码')
}

async function submit() {
  if (!form.username) return (err.value = '请输入学号或工号')
  if (!form.password) return (err.value = '请输入登录密码')
  loading.value = true
  err.value = ''
  try {
    const data = await authApi.login({ username: form.username, password: form.password })
    store.setLogin(data)
    ElMessage.success(`欢迎回来，${data.realName}`)
    const redirect = route.query.redirect
    if (redirect) router.push(String(redirect))
    else router.push(data.roleCode === 'STUDENT' ? '/portal/home' : '/dashboard/index')
  } catch (e) {
    err.value = e?.message || '账号或密码不正确'
  } finally {
    loading.value = false
  }
}
</script>
