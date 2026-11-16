<script setup>
// S01 온보딩 (①): 핵심 3장을 옆으로 넘겨 봐요. 작은 폰 안에는 화면 캡처 사진을 보여 줘요
// 11/16 ①: 진짜 화면을 끼우면 진행 중인 여행이 없을 때 "계산할 수 없어요"가 떠서 사진으로 바꿈 (public/img/onboarding)
import { ref } from 'vue'

const slides = [
  { line1: '예약 문자 하나로', line2: '하루 일정이 나와요', image: '/img/onboarding/onboarding-1.png', alt: '예약 캡처로 고정 일정 넣는 화면' },
  { line1: '예약에 늦을 것 같으면', line2: '먼저 알려 드려요', image: '/img/onboarding/onboarding-2.png', alt: '예약에 14분 늦을 것 같다는 알림 화면' },
  { line1: '추천 하나만 누르면', line2: '남은 일정이 바뀌어요', image: '/img/onboarding/onboarding-3.png', alt: '대안 고르기 화면' },
]
const index = ref(0)
let startX = null

// 옆으로 30px 넘게 밀면 다음·이전 장
function onDown(e) {
  startX = e.clientX
}
function onUp(e) {
  if (startX === null) return
  const moved = e.clientX - startX
  startX = null
  if (Math.abs(moved) < 30) return
  index.value = (index.value + (moved < 0 ? 1 : 2)) % 3
}
</script>

<template>
  <div class="scr scr-Onboarding">
    <div class="app app--white">
      <!-- 11/06: 언어 버튼은 마이 맨 아래(Language)로 옮김 -->

      <h1 class="h-display ob-title" aria-live="polite"><span class="c-navy">{{ slides[index].line1 }}</span><br />{{ slides[index].line2 }}</h1>

      <div class="ob-phone" @pointerdown="onDown" @pointerup="onUp">
        <div class="ob-phone__inner">
          <img class="ob-shot" :src="slides[index].image" :alt="slides[index].alt" draggable="false" />
        </div>
      </div>

      <div class="ob-dots" role="tablist" :aria-label="`소개 ${index + 1}/3`">
        <button v-for="(slide, i) in slides" :key="i" type="button" class="ob-dot" :class="{ on: i === index }" :aria-label="i + 1 + '번째 소개 보기'" :aria-current="i === index ? 'step' : 'false'" @click="index = i"><i></i></button>
      </div>

      <div class="cta cta--white" style="box-shadow: none">
        <RouterLink to="/login" class="btn btn--primary btn--lg btn--block">시작하기</RouterLink>
      </div>
    </div>
  </div>
</template>

<style>
.scr-Onboarding .ob-title{position:absolute;left:50%;top:112px;margin:0;transform:translateX(-50%);width:max-content;max-width:calc(100% - 48px);text-align:center}
.scr-Onboarding .ob-phone{position:absolute;left:50%;top:196px;width:215px;height:450px;margin-left:-107px;border-radius:30px;background:var(--ink);padding:7px;box-shadow:0 18px 40px -16px rgba(24,33,58,.45);touch-action:pan-y;cursor:grab;user-select:none}
.scr-Onboarding .ob-phone__inner{position:relative;width:201px;height:436px;border-radius:23px;overflow:hidden;background:var(--surface)}
.scr-Onboarding .ob-shot{display:block;width:100%;height:100%;object-fit:cover;pointer-events:none}
.scr-Onboarding .ob-dots{position:absolute;left:0;right:0;top:666px;display:flex;justify-content:center;gap:2px}
.scr-Onboarding .ob-dot{width:28px;height:28px;border:0;background:none;padding:0;display:flex;align-items:center;justify-content:center;cursor:pointer}
.scr-Onboarding .ob-dot i{display:block;width:8px;height:8px;border-radius:999px;background:var(--line);transition:background .2s,width .2s}
.scr-Onboarding .ob-dot:hover i{background:var(--line-strong)}
.scr-Onboarding .ob-dot.on i{background:var(--navy);width:20px}
.scr-Onboarding .lang-chip{position:absolute;top:54px;right:16px;z-index:5;display:inline-flex;align-items:center;gap:4px;height:32px;padding:0 12px;border-radius:999px;border:1px solid var(--line);background:var(--surface-raised,#fff);color:var(--ink);font:inherit;font-size:13px;font-weight:600;cursor:pointer}
.scr-Onboarding .lang-chip:hover{border-color:var(--navy);color:var(--navy)}
</style>
