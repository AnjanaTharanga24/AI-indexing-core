FROM eclipse-temurin:17-jre-alpine
WORKDIR /app
COPY target/auradocs-ai-service-0.0.1-SNAPSHOT.jar app.jar
EXPOSE 8585
ENTRYPOINT ["java", "-jar", "app.jar"]
