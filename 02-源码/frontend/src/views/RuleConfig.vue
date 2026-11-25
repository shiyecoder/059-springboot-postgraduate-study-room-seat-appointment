<template>
  <div class="view">
    <PageHead title="规则配置" desc="违约风险评分五因子权重可配置，保存即生成新版本并归档旧版本">
      <el-tag v-if="roles.includes(roleCode)" type="warning" effect="plain">运营主管可编辑</el-tag>
    </PageHead>

    <div class="grid g21">
      <div class="card">
        <div class="hd">
          <h3>当前生效规则 {{ active.ruleVersion }}</h3>
          <div class="sp"></div>
          <span class="tag b">权重合计 {{ totalWeight }}%</span>
          <span class="sm tx3">生效于 {{ dt(active.effectTime) }}</span>
        </div>
        <div class="bd">
          <div v-for="f in factors" :key="f.key" class="fx" style="margin-bottom: 14px; gap: 12px">
            <span style="width: 120px; font-size: 12.5px; color: var(--tx2)">{{ f.label }}</span>
            <el-slider v-model="form[f.key]" :max="60" :step="5" style="flex: 1" :disabled="!canEdit" />
            <span class="mono" style="width: 46px; text-align: right">{{ form[f.key] }}%</span>
          </div>
          <div class="alert" :class="totalWeight === 100 ? '' : 'r'" style="margin-top: 4px">
            合计 {{ totalWeight }}% {{ totalWeight === 100 ? '，符合要求' : '，必须等于 100% 才能保存' }}
          </div>
          <el-form label-width="76px" style="margin-top: 12px">
            <el-form-item label="变更说明">
              <el-input v-model="remark" maxlength="80" placeholder="如：考季临近，违约次数权重 30%→35%" />
            </el-form-item>
          </el-form>
          <div class="fx">
            <el-button type="primary" size="small" :disabled="!canEdit || totalWeight !== 100" :loading="saving" @click="save">保存为新版本</el-button>
            <el-button size="small" @click="resetForm">恢复当前生效值</el-button>
            <div class="fx1"></div>
            <span class="sm tx3">评分 = Σ(因子得分 × 权重)，得分越高风险越高</span>
          </div>
        </div>
      </div>

      <div class="card">
        <div class="hd"><h3>规则效果</h3></div>
        <div class="bd">
          <div class="grid g2" style="gap: 10px">
            <div class="kpi"><div class="lb">高风险学生</div><div class="vl">{{ effect.highRisk ?? 0 }}<small>人</small></div><div class="df">分值 ≥ 70</div></div>
            <div class="kpi g"><div class="lb">建议采纳率</div><div class="vl">{{ effect.adoptRate ?? 0 }}<small>%</small></div><div class="df">未被人工修正占比</div></div>
            <div class="kpi d"><div class="lb">人工修正率</div><div class="vl">{{ effect.correctRate ?? 0 }}<small>%</small></div><div class="df">{{ effect.corrected }} / {{ effect.total }} 条快照</div></div>
            <div class="kpi a"><div class="lb">违约下降</div><div class="vl">{{ effect.violationDrop ?? 0 }}<small>%</small></div><div class="df">规则上线前后对比</div></div>
          </div>
          <div class="divider"></div>
          <div class="sm tx2" style="line-height: 1.9">
            规则上线后，近 30 天违约率由 7.2% 降至 {{ (7.2 * (1 - (effect.violationDrop || 0) / 100)).toFixed(1) }}%；
            高风险学生预约热门座位时自动提示改约冷门时段，人工修正主要集中在「考季系数」因子。
          </div>
        </div>
      </div>
    </div>

    <div class="grid g2 mt12">
      <div class="card">
        <div class="hd"><h3>版本历史</h3><div class="sp"></div><span class="sm tx3">{{ versions.length }} 个版本</span></div>
        <el-table :data="versions" style="width: 100%">
          <el-table-column prop="ruleVersion" label="版本" width="76"><template #default="{ row }"><span class="mono">{{ row.ruleVersion }}</span></template></el-table-column>
          <el-table-column label="权重（违约/到座/提前/占用/考季）" min-width="200">
            <template #default="{ row }">
              <span class="mono sm">{{ row.wviolation }}/{{ row.warrive }}/{{ row.wadvance }}/{{ row.woccupy }}/{{ row.wseason }}</span>
            </template>
          </el-table-column>
          <el-table-column label="生效时间" width="150"><template #default="{ row }">{{ dt(row.effectTime) }}</template></el-table-column>
          <el-table-column label="状态" width="90">
            <template #default="{ row }"><span class="tag" :class="row.status === '生效中' ? 'g' : ''">{{ row.status }}</span></template>
          </el-table-column>
          <el-table-column prop="remark" label="说明" min-width="160" />
        </el-table>
      </div>

      <div class="card">
        <div class="hd"><h3>最近计算快照</h3><div class="sp"></div><span class="sm tx3">可人工修正并留痕</span></div>
        <el-table :data="snapshots" style="width: 100%" height="286">
          <el-table-column prop="snapshotNo" label="快照号" width="112"><template #default="{ row }"><span class="mono sm">{{ row.snapshotNo }}</span></template></el-table-column>
          <el-table-column label="学生" width="70"><template #default="{ row }">#{{ row.studentId }}</template></el-table-column>
          <el-table-column label="分值" width="72"><template #default="{ row }">{{ row.totalScore }}</template></el-table-column>
          <el-table-column label="等级" width="80">
            <template #default="{ row }"><span class="tag" :class="tone(row.riskLevel)">{{ row.riskLevel }}</span></template>
          </el-table-column>
          <el-table-column label="时间" min-width="130"><template #default="{ row }">{{ dt(row.calcTime) }}</template></el-table-column>
          <el-table-column label="操作" width="70" fixed="right">
            <template #default="{ row }">
              <el-button link type="primary" size="small" @click="openCorrect(row)">修正</el-button>
            </template>
          </el-table-column>
        </el-table>
      </div>
    </div>

    <el-dialog v-model="dlg" title="人工修正风险分" width="460px">
      <div class="alert a mb12" v-if="cur">快照 {{ cur.snapshotNo }} · 原分 {{ cur.totalScore }} · {{ cur.riskLevel }}</div>
      <el-form label-width="84px">
        <el-form-item label="新等级">
          <el-select v-model="corr.newLevel" style="width: 160px">
            <el-option v-for="l in ['低风险', '中风险', '高风险']" :key="l" :label="l" :value="l" />
          </el-select>
        </el-form-item>
        <el-form-item label="新分值">
          <el-input-number v-model="corr.newScore" :min="0" :max="100" :precision="1" controls-position="right" />
        </el-form-item>
        <el-form-item label="修正原因">
          <el-input v-model="corr.reason" type="textarea" :rows="3" maxlength="120" show-word-limit placeholder="不少于 5 个字，将写入修正台账" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dlg = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="doCorrect">提交修正</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import PageHead from '../components/PageHead.vue'
import { riskApi } from '../api'
import { dt, tone } from '../utils/format'
import { useUserStore } from '../store/user'

const store = useUserStore()
const roleCode = computed(() => store.state.user?.roleCode)
const roles = ['OPERATION', 'ADMIN']
const canEdit = computed(() => roles.includes(roleCode.value))

const factors = [
  { key: 'wViolation', label: '近 30 天违约次数' },
  { key: 'wArrive', label: '历史到座率' },
  { key: 'wAdvance', label: '平均提前预约时长' },
  { key: 'wOccupy', label: '同时段占用率' },
  { key: 'wSeason', label: '考季系数' }
]

const form = reactive({ wViolation: 30, wArrive: 25, wAdvance: 15, wOccupy: 15, wSeason: 15 })
const active = ref({})
const versions = ref([])
const effect = ref({})
const snapshots = ref([])
const remark = ref('')
const saving = ref(false)
const dlg = ref(false)
const cur = ref(null)
const corr = reactive({ newLevel: '中风险', newScore: 60, reason: '' })

const totalWeight = computed(() => factors.reduce((s, f) => s + Number(form[f.key] || 0), 0))

function fillActive() {
  const a = active.value || {}
  form.wViolation = a.wviolation ?? 30
  form.wArrive = a.warrive ?? 25
  form.wAdvance = a.wadvance ?? 15
  form.wOccupy = a.woccupy ?? 15
  form.wSeason = a.wseason ?? 15
}

function resetForm() {
  fillActive()
  remark.value = ''
}

async function load() {
  const [r, e, s] = await Promise.all([riskApi.rule(), riskApi.effect(), riskApi.snapshot()])
  active.value = r.active || {}
  versions.value = r.versions || []
  effect.value = e || {}
  snapshots.value = (s || []).slice(0, 20)
  fillActive()
}

async function save() {
  if (totalWeight.value !== 100) return ElMessage.warning('权重合计必须等于 100%')
  saving.value = true
  try {
    const r = await riskApi.saveRule({ ...form, remark: remark.value || '权重调整' })
    ElMessage.success(`已生成新版本 ${r.ruleVersion}，旧版本已归档`)
    remark.value = ''
    load()
  } finally {
    saving.value = false
  }
}

function openCorrect(row) {
  cur.value = row
  corr.newLevel = row.riskLevel
  corr.newScore = row.totalScore
  corr.reason = ''
  dlg.value = true
}

async function doCorrect() {
  if (corr.reason.trim().length < 5) return ElMessage.warning('修正原因不少于 5 个字')
  saving.value = true
  try {
    await riskApi.correct({ snapshotId: cur.value.id, newLevel: corr.newLevel, newScore: corr.newScore, reason: corr.reason })
    ElMessage.success('已修正，原分保留可追溯')
    dlg.value = false
    load()
  } finally {
    saving.value = false
  }
}

onMounted(load)
</script>
