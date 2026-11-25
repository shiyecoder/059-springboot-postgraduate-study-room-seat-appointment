<template>
  <div class="view" v-loading="loading">
    <PageHead :title="`违约风险评分 · ${risk.studentName || ''}`" :desc="`规则 ${risk.ruleVersion || '--'} · 计算于 ${dt(risk.calcTime)} · 快照 ${risk.snapshotNo || '--'}`">
      <el-button size="small" @click="$router.back()">返回</el-button>
      <el-button size="small" @click="recalc">重新计算</el-button>
    </PageHead>

    <div class="grid g21">
      <div class="card">
        <div class="hd"><h3>评分构成</h3><div class="sp"></div><span class="sm tx3">得分越高风险越高</span></div>
        <div class="bd">
          <div class="fx" style="gap: 22px; margin-bottom: 14px">
            <div class="score-ring" :style="ringStyle">
              <div class="inner">
                <b>{{ risk.totalScore ?? '--' }}</b>
                <span>风险分</span>
              </div>
            </div>
            <div>
              <div class="fx" style="gap: 8px">
                <span class="tag" :class="tone(risk.riskLevel)">{{ risk.riskLevel }}</span>
                <span class="sm tx3">阈值：低 &lt;40 · 中 40-70 · 高 ≥70</span>
              </div>
              <p class="sm tx2" style="margin-top: 8px; max-width: 380px; line-height: 1.8">{{ risk.conclusion }}</p>
              <div class="sm tx3" v-if="risk.suggestSlot">建议时段：<b class="mono" style="color: var(--pri)">{{ risk.suggestSlot }}</b></div>
            </div>
          </div>

          <el-table :data="risk.factors || []" style="width: 100%">
            <el-table-column prop="name" label="评分因子" min-width="150" />
            <el-table-column prop="value" label="取值 / 阈值" min-width="150" />
            <el-table-column label="权重" width="76" align="right"><template #default="{ row }">{{ row.weight }}%</template></el-table-column>
            <el-table-column label="因子得分" width="92" align="right"><template #default="{ row }">{{ row.score }}</template></el-table-column>
            <el-table-column label="加权得分" width="92" align="right">
              <template #default="{ row }"><b>{{ row.weighted }}</b></template>
            </el-table-column>
            <el-table-column prop="basis" label="计算依据" min-width="200" />
          </el-table>
        </div>
      </div>

      <div class="card">
        <div class="hd"><h3>结论与建议</h3></div>
        <div class="bd">
          <div class="sm tx2 mb8">命中的规则</div>
          <div v-if="(risk.unmet || []).length">
            <div v-for="(u, i) in risk.unmet" :key="i" class="alert r mb8" style="font-size: 12px">{{ u }}</div>
          </div>
          <div v-else class="alert mb8">未命中任何风险规则</div>
          <div class="divider"></div>
          <div class="sm tx2 mb8">改进建议</div>
          <div v-if="(risk.improve || []).length">
            <div v-for="(u, i) in risk.improve" :key="i" class="alert a mb8" style="font-size: 12px">{{ u }}</div>
          </div>
          <div v-else class="sm tx3">暂无建议，保持当前预约习惯即可</div>
          <div class="divider"></div>
          <dl class="dl">
            <dt>学生编号</dt><dd>#{{ risk.studentId }}</dd>
            <dt>规则版本</dt><dd>{{ risk.ruleVersion }}</dd>
            <dt>快照编号</dt><dd class="mono">{{ risk.snapshotNo }}</dd>
          </dl>
        </div>
      </div>
    </div>

    <div class="card mt12">
      <div class="hd"><h3>历史评分快照</h3><div class="sp"></div><span class="sm tx3">最近 10 次</span></div>
      <el-table :data="history" style="width: 100%">
        <el-table-column prop="snapshotNo" label="快照号" width="130"><template #default="{ row }"><span class="mono">{{ row.snapshotNo }}</span></template></el-table-column>
        <el-table-column prop="ruleVersion" label="规则版本" width="100" />
        <el-table-column prop="totalScore" label="总分" width="90" />
        <el-table-column label="等级" width="100"><template #default="{ row }"><span class="tag" :class="tone(row.riskLevel)">{{ row.riskLevel }}</span></template></el-table-column>
        <el-table-column label="计算时间" width="170"><template #default="{ row }">{{ dt(row.calcTime) }}</template></el-table-column>
        <el-table-column prop="conclusion" label="结论" min-width="240" />
      </el-table>
    </div>
  </div>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import { useRoute } from 'vue-router'
import { ElMessage } from 'element-plus'
import PageHead from '../components/PageHead.vue'
import { riskApi } from '../api'
import { dt, tone } from '../utils/format'

const route = useRoute()
const loading = ref(false)
const risk = ref({})
const history = ref([])

const ringStyle = computed(() => {
  const score = Math.min(100, Math.max(0, Number(risk.value.totalScore) || 0))
  const color = score >= 70 ? '#B02A37' : score >= 40 ? '#C97B2E' : '#2B7955'
  return { background: `conic-gradient(${color} 0 ${score * 3.6}deg, #EDF0F1 ${score * 3.6}deg 360deg)` }
})

async function load() {
  loading.value = true
  try {
    risk.value = await riskApi.student(route.params.id)
    const list = await riskApi.snapshot(route.params.id)
    history.value = (list || []).slice(0, 10)
  } finally {
    loading.value = false
  }
}

async function recalc() {
  await load()
  ElMessage.success('已按当前生效规则重新计算')
}

onMounted(load)
</script>

<style scoped>
.score-ring {
  width: 132px;
  height: 132px;
  border-radius: 50%;
  display: grid;
  place-items: center;
  flex: 0 0 132px;
}
.score-ring .inner {
  width: 104px;
  height: 104px;
  border-radius: 50%;
  background: #fff;
  display: grid;
  place-items: center;
  align-content: center;
  box-shadow: inset 0 0 0 1px var(--line);
}
.score-ring b {
  font-size: 26px;
  font-weight: 700;
  color: var(--tx);
  font-variant-numeric: tabular-nums;
  line-height: 1.1;
}
.score-ring span {
  font-size: 11px;
  color: var(--tx3);
}
</style>
