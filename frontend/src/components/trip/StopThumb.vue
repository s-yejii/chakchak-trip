<script setup>
// 일정 칸 사진: 믿을 만한 사진이 있으면 사진, 없으면 종류별 아이콘 (엉뚱한 사진보다 나아요)
// 11/19 ①: 이름 첫 글자 칸("동") 대신 imageFor 아이콘 (터미널이면 버스, 음식점이면 포크·나이프)
import { computed, ref, watch } from 'vue'
import { imageFor } from '@/utils/mapView'

const props = defineProps({
  src: { type: String, default: null },
  name: { type: String, default: '' },
  category: { type: String, default: '' },
})

const isBroken = ref(false) // 사진 주소가 깨졌으면 아이콘으로
watch(() => props.src, () => {
  isBroken.value = false
})
const icon = computed(() => imageFor(props.category))
</script>

<template>
  <img v-if="src && !isBroken" class="thumb" :src="src" alt="" @error="isBroken = true" />
  <img v-else class="thumb" :src="icon" alt="" />
</template>
