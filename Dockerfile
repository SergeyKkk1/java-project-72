FROM gradle:9.0.0-jdk21 AS build

WORKDIR /home/gradle/src
COPY app/ ./app/
RUN chmod +x app/gradlew
RUN ./app/gradlew -p app --no-daemon installDist

FROM eclipse-temurin:21-jre

WORKDIR /app
COPY --from=build /home/gradle/src/app/build/install/app/ /app/
ENV PORT=7000
EXPOSE 7000
CMD ["/app/bin/app"]
