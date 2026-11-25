<template>
  <div class="view">
    <PageHead title="菜单管理" desc="侧栏菜单与按钮权限均由此维护，角色授权后即时生效">
      <el-tag type="info" effect="plain">共 {{ count }} 项</el-tag>
    </PageHead>

    <div class="card">
      <div class="hd"><h3>菜单树</h3><div class="sp"></div><span class="sm tx3">目录 → 菜单 → 按钮</span></div>
      <el-table :data="tree" row-key="id" :tree-props="{ children: 'children' }" default-expand-all style="width: 100%" v-loading="loading">
        <el-table-column prop="name" label="菜单名称" min-width="220" />
        <el-table-column prop="path" label="路由地址" min-width="180"><template #default="{ row }"><span class="mono sm">{{ row.path || '--' }}</span></template></el-table-column>
        <el-table-column prop="component" label="组件" width="150"><template #default="{ row }"><span class="sm tx3">{{ row.component || '--' }}</span></template></el-table-column>
        <el-table-column label="类型" width="86">
          <template #default="{ row }">
            <span class="tag" :class="row.menuType === '目录' ? 'b' : row.menuType === '按钮' ? 'a' : ''">{{ row.menuType }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="perms" label="权限标识" min-width="140"><template #default="{ row }"><span class="mono sm">{{ row.perms || '--' }}</span></template></el-table-column>
        <el-table-column prop="sortNo" label="排序" width="76" align="right" />
      </el-table>
    </div>

    <div class="alert mt12">
      菜单由后端 sys_menu 表驱动，登录后按角色下发，前端路由与 <span class="mono">path</span> 一一对应；新增菜单需同步在前端注册对应视图，否则点击会落到 404 兜底。
    </div>
  </div>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import PageHead from '../components/PageHead.vue'
import { systemApi } from '../api'

const tree = ref([])
const loading = ref(false)
const count = computed(() => {
  let n = 0
  const walk = (list) => list.forEach((m) => {
    n++
    if (m.children) walk(m.children)
  })
  walk(tree.value)
  return n
})

onMounted(async () => {
  loading.value = true
  try {
    tree.value = await systemApi.menuTree()
  } finally {
    loading.value = false
  }
})
</script>
