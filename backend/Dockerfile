# 착착트립 서버(Spring Boot) 이미지 (11/16 ④ 신예지)
#   1단계(build): Maven으로 jar 만들기   2단계: jar만 Java 17 실행 이미지에 담기 (이미지가 작아지고 소스·Maven이 안 들어감)
#   키는 이미지에 넣지 않아요. docker-compose.yml이 .env 값을 환경변수로 넘겨요 (.dockerignore에서 application-local.properties 뺌)
FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /build
# pom.xml만 먼저 복사해서 라이브러리를 받아 둠 → 소스만 바뀌면 다음 빌드 때 이 단계는 건너뜀(빨라짐)
COPY pom.xml .
RUN mvn -q -B dependency:go-offline
COPY src ./src
RUN mvn -q -B package -DskipTests

FROM eclipse-temurin:17-jre
WORKDIR /app
ENV TZ=Asia/Seoul
COPY --from=build /build/target/*.jar app.jar
EXPOSE 8080
# MaxRAMPercentage: 서버 메모리의 60%까지만 Java가 쓰게 (DB·nginx와 같은 서버에서 돌아서)
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=60", "-jar", "app.jar"]
