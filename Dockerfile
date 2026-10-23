FROM eclipse-temurin:21-jdk AS build
WORKDIR /src
COPY . .
RUN ./gradlew bootJar --no-daemon -x test

FROM eclipse-temurin:21-jre
WORKDIR /opt/app
COPY --from=build /src/build/libs/*.jar app.jar
COPY playbooks ./playbooks
# Ограниченная куча — чтобы MEMORY_LEAK быстро упирался в предел
ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=60"
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
