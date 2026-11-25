<template>
  <div class="view">
    <PageHead title="公告通知" desc="开放时间调整、规则修订与考季安排统一在此发布">
      <el-tag type="info" effect="plain">共 {{ rows.length }} 条</el-tag>
    </PageHead>

    <div class="card">
      <div class="bd np" v-loading="loading">
        <div v-for="n in rows" :key="n.id" class="notice" :class="{ on: cur?.id === n.id }" @click="cur = n">
          <div class="fx">
            <span class="tag" :class="n.noticeType === '系统公告' ? 'b' : n.noticeType === '规则修订' ? 'a' : 'o'">{{ n.noticeType }}</span>
            <b style="font-size: 13px">{{ n.title }}</b>
            <div class="fx1"></div>
            <span class="sm tx3">{{ dt(n.publishTime) }}</span>
          </div>
          <p class="sm tx2" style="margin-top: 6px; line-height: 1.75">{{ n.content }}</p>
        </div>
        <div class="empty" v-if="!loading && !rows.length"><b>暂无公告</b>发布后学生端首页同步展示</div>
      </div>
    </div>

    <div class="grid g2 mt12" v-if="cur">
      <div class="card">
        <div class="hd"><h3>{{ cur.title }}</h3><div class="sp"></div><span class="tag b">{{ cur.noticeType }}</span></div>
        <div class="bd">
          <p style="font-size: 13px; line-height: 1.9">{{ cur.content }}</p>
          <div class="divider"></div>
          <dl class="dl">
            <dt>发布时间</dt><dd>{{ dt(cur.publishTime) }}</dd>
            <dt>状态</dt><dd><span class="tag g">{{ cur.status }}</span></dd>
          </dl>
        </div>
      </div>
      <div class="card">
        <div class="hd"><h3>发布须知</h3></div>
        <div class="bd">
          <div class="alert">公告发布后同步推送站内消息给全部在册学生，学生端首页与「公告通知」页可见。</div>
          <div class="alert a mt8">涉及开放时段、违约扣分与黑名单规则的调整，须在公告中写明生效时间，避免申诉争议。</div>
          <div class="divider"></div>
          <div class="sm tx2" style="line-height: 1.9">本学期已发布 {{ rows.length }} 条公告，其中规则类 {{ ruleCount }} 条。</div>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import PageHead from '../components/PageHead.vue'
import { systemApi } from '../api'
import { dt } from '../utils/format'

const rows = ref([])
const cur = ref(null)
const loading = ref(false)
const ruleCount = computed(() => rows.value.filter((r) => r.noticeType === '规则修订').length)

onMounted(async () => {
  loading.value = true
  try {
    rows.value = await systemApi.notices()
    cur.value = rows.value[0] || null
  } finally {
    loading.value = false
  }
})
</script>

<style scoped>
.notice {
  padding: 12px 16px;
  border-bottom: 1px solid #f0f2f3;
  cursor: pointer;
}
.notice:last-child {
  border-bottom: 0;
}
.notice:hover {
  background: #fbfcfc;
}
.notice.on {
  background: var(--pri-l);
  border-left: 3px solid var(--pri);
  padding-left: 13px;
}
</style>
