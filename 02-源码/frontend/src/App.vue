<template>
  <router-view />
</template>

<script setup>
import { onMounted } from 'vue'
import { useUserStore } from './store/user'

const store = useUserStore()
onMounted(() => store.restore())
</script>
