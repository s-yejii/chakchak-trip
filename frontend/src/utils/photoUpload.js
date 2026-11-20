// ⑤ 사진·영상 올리기 공통 (앨범·회고에서 같이 씀). POST /api/photos
import { upload } from '@/api/http'

// 두 자리로: 7 → "07"
function pad(n) {
  return String(n).padStart(2, '0')
}

// 파일 수정 시각 → "2026-11-01T10:20". 서버가 파일 안의 찍은 시각(EXIF)을 못 읽을 때만 써요
function toTakenAt(file) {
  const d = new Date(file.lastModified)
  return d.getFullYear() + '-' + pad(d.getMonth() + 1) + '-' + pad(d.getDate()) + 'T' + pad(d.getHours()) + ':' + pad(d.getMinutes())
}

// 영상 길이(초). 브라우저에 영상을 잠깐 불러서 길이만 읽어요. 사진이면 0
// Promise: "다 읽으면 값을 줄게"라는 약속. 부르는 쪽에서 await로 기다려요
function readDuration(file) {
  if (!file.type.startsWith('video/')) return Promise.resolve(0)
  return new Promise((resolve) => {
    const video = document.createElement('video')
    video.preload = 'metadata'
    video.onloadedmetadata = () => {
      URL.revokeObjectURL(video.src) // 잠깐 만든 주소 정리
      resolve(Number.isFinite(video.duration) ? Math.round(video.duration) : 0)
    }
    video.onerror = () => resolve(0)
    video.src = URL.createObjectURL(file) // 내 PC 파일을 브라우저에서 잠깐 쓰는 주소
  })
}

const REENCODE_QUALITY = 0.95 // 찍은 시각을 지우려고 다시 저장할 때 화질

// 사진을 한 번 다시 그려서 저장: 파일 안의 찍은 시각(EXIF)이 지워져서 서버가 우리가 보낸 시각을 써요
// 브라우저가 못 여는 사진(HEIC 등)이나 영상은 그대로 (11/19 RetroPhotoCard에서 옮김, 장소 블럭도 같이 씀)
export async function withoutTakenInfo(file) {
  if (!file.type.startsWith('image/') || file.type === 'image/heic') return file
  try {
    const bitmap = await createImageBitmap(file) // 파일을 그림으로 읽기
    const canvas = document.createElement('canvas')
    canvas.width = bitmap.width
    canvas.height = bitmap.height
    canvas.getContext('2d').drawImage(bitmap, 0, 0)
    const blob = await new Promise((resolve) => canvas.toBlob(resolve, 'image/jpeg', REENCODE_QUALITY))
    return new File([blob], file.name.replace(/\.\w+$/, '') + '.jpg', { type: 'image/jpeg' })
  } catch (e) {
    return file
  }
}

// 고른 파일들을 한 번에 올려요. tripId가 없으면 서버가 찍은 날짜로 여행을 찾아요
// placeTimes(11/19, 웹 회고 장소 블럭): 파일마다 "2026-11-14T17:15" 같은 그 장소 시각.
//   주면 사진 속 찍은 시각을 지우고(withoutTakenInfo) 이 시각을 보냄 → 언제 찍은 사진이든 그 장소에 붙어요
// 돌려줌: { savedCount, unmatchedCount }
export async function uploadPhotos(files, tripId, placeTimes) {
  const form = new FormData() // 파일을 담는 상자
  for (let i = 0; i < files.length; i++) {
    const file = files[i]
    form.append('files', placeTimes ? await withoutTakenInfo(file) : file)
    form.append('takenAts', placeTimes ? placeTimes[i] : toTakenAt(file))
    form.append('durations', await readDuration(file))
  }
  if (tripId) form.append('tripId', tripId)
  return upload('/photos', form)
}

// 영상 길이 글자: 12 → "0:12", 75 → "1:15"
export function toDurationText(sec) {
  if (!sec) return ''
  return Math.floor(sec / 60) + ':' + pad(sec % 60)
}
