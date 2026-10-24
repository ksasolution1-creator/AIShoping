# API 서버 이미지. 빌드: docker build apps/api
# jOOQ 생성 코드는 저장소에 있으므로 빌드에 DB가 필요 없다.
FROM eclipse-temurin:21-jdk AS build
WORKDIR /src
COPY gradlew settings.gradle build.gradle ./
COPY gradle gradle
RUN chmod +x gradlew && ./gradlew --no-daemon --quiet help
COPY src src
RUN ./gradlew --no-daemon --quiet bootJar -x test \
	&& cp "$(ls build/libs/*.jar | grep -v plain)" app.jar

FROM eclipse-temurin:21-jre
RUN groupadd --system app && useradd --system --gid app --home /app app \
	&& mkdir -p /app/uploads && chown -R app:app /app
WORKDIR /app
COPY --from=build --chown=app:app /src/app.jar app.jar
USER app
ENV STORAGE_LOCAL_DIR=/app/uploads \
	JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=60 -XX:+ExitOnOutOfMemoryError"
EXPOSE 18080
ENTRYPOINT ["java", "-jar", "app.jar"]
