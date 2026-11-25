<template>
  <div class="view">
    <PageHead title="站内消息" desc="预约提醒、违约通知与递补消息，未读消息在顶栏红点提示">
      <el-button size="small" @click="readAll" :disabled="!unread">全部标记已读</el-button>
    </PageHead>

    <div class="card">
      <div class="bd np" v-loading="loading">
        <div v-for="m in rows" :key="m.id" class="msg" :class="{ unread: !m.readFlag }" @click="markRead(m)">
          <div class="fx">
            <span class="dot" v-if="!m.readFlag"></span>
            <span class="tag" :class="m.msgType === '违约通知' ? 'r' : m.msgType === '预约提醒' ? 'b' : 'a'">{{ m.msgType }}</span>
            <b style="font-size: 13px">{{ m.title }}</b>
            <div class="fx1"></div>
            <span class="sm tx3">{{ dt(m.createTime) }}</span>
          </div>
          <p class="sm tx2" style="margin-top: 5px; line-height: 1.75; padding-left: 2px">{{ m.content }}</p>
        </div>
        <div class="empty" v-if="!loading && !rows.length"><b>暂无消息</b>预约提醒与违约通知会出现在这里</div>
      </div>
      <el-pagination
        v-model:current-page="page"
        :page-size="size"
        :total="total"
        layout="total, prev, pager, next"
        background
        @current-change="load"
      />
    </div>
  </div>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import PageHead from '../components/PageHead.vue'
import { systemApi } from '../api'
import { dt } from '../utils/format'

const rows = ref([])
const total = ref(0)
const page = ref(1)
const size = 10
const loading = ref(false)
const unread = computed(() => rows.value.filter((m) => !m.readFlag).length)

async function load() {
  loading.value = true
  try {
    const d = await systemApi.messages({ page: page.value, size })
    rows.value = d.records || []
    total.value = d.total || 0
  } finally {
    loading.value = false
  }
}

async function markRead(m) {
  if (m.readFlag) return
  await systemApi.read(m.id)
  m.readFlag = 1
  ElMessage.success('已标记已读')
}

async function readAll() {
  await systemApi.readAll()
  rows.value.forEach((m) => (m.readFlag = 1))
  ElMessage.success('已全部标记已读')
}

onMounted(load)
</script>

<style scoped>
.msg {
  padding: 12px 16px;
  border-bottom: 1px solid #f0f2f3;
  cursor: pointer;
}
.msg:last-child {
  border-bottom: 0;
}
.msg:hover {
  background: #fbfcfc;
}
.msg.unread {
  background: #fbfcfc;
}
.msg .dot {
  width: 6px;
  height: 6px;
  border-radius: 50%;
  background: var(--dngr);
  flex: 0 0 6px;
}
</style>
