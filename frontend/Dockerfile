# 착착트립 화면(Vue) 이미지 (11/16 ④ 신예지)
#   1단계(build): npm으로 화면 파일(dist) 만들기   2단계: nginx가 dist를 주고 /api는 서버로 넘김 (nginx.conf)
FROM node:22-alpine AS build
WORKDIR /build
# package*.json만 먼저 복사해서 라이브러리를 받아 둠 → 화면 코드만 바뀌면 이 단계는 건너뜀
COPY package.json package-lock.json ./
RUN npm ci
COPY . .
RUN npm run build

FROM nginx:1.27-alpine
COPY nginx.conf /etc/nginx/conf.d/default.conf
COPY --from=build /build/dist /usr/share/nginx/html
EXPOSE 80
