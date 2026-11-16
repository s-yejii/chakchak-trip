// 이름 뒤 조사: 끝 글자에 받침이 있으면 "을/으로", 없으면 "를/로" (11/15)
//   "을(를)", "(으)로"를 그대로 보여 주던 알림 글을 진짜 문장처럼
//   끝에 괄호(예: "스타벅스 명동점(2층)")가 있으면 괄호 앞 글자로 봐요. 한글이 아니면(CU 등) 받침 없음으로 봐요
const HANGUL_FIRST = 0xac00
const HANGUL_COUNT = 11172
const JONG_COUNT = 28
const JONG_RIEUL = 8 // ㄹ 받침: "으로"가 아니라 "로" (예: 지하철로)

function lastJong(word) {
  const text = String(word).trim().replace(/\s*\([^)]*\)$/, '')
  const code = text.charCodeAt(text.length - 1) - HANGUL_FIRST
  if (Number.isNaN(code) || code < 0 || code >= HANGUL_COUNT) return 0
  return code % JONG_COUNT
}

// "칠린" → "칠린을", "나지트" → "나지트를"
export function withEulReul(word) {
  return word + (lastJong(word) ? '을' : '를')
}

// "경복궁" → "경복궁으로", "지하철" → "지하철로", "버스" → "버스로"
export function withEuroRo(word) {
  const jong = lastJong(word)
  return word + (jong && jong !== JONG_RIEUL ? '으로' : '로')
}
