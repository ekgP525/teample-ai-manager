# Railway/Render 등 컨테이너 배포용. Java 21 + ffmpeg(webm/mov 변환용) 포함.
# 로컬 실행은 계속 ./gradlew bootRun을 쓴다.

FROM eclipse-temurin:21-jdk AS build
WORKDIR /app
COPY gradlew build.gradle settings.gradle ./
COPY gradle ./gradle
RUN chmod +x gradlew && ./gradlew --no-daemon dependencies > /dev/null 2>&1 || true
COPY src ./src
RUN ./gradlew --no-daemon clean bootJar -x test

FROM eclipse-temurin:21-jre
RUN apt-get update \
    && apt-get install -y --no-install-recommends ffmpeg \
    && rm -rf /var/lib/apt/lists/*
WORKDIR /app
COPY --from=build /app/build/libs/*.jar app.jar
# 녹음 파일 저장 위치. Railway에서 볼륨을 /app/data 에 마운트하고 MEDIA_DIR=/app/data/media 로 지정한다.
RUN mkdir -p /app/data/media
ENV MEDIA_DIR=/app/data/media
EXPOSE 8080
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75", "-jar", "/app/app.jar"]
