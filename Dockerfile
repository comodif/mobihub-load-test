FROM openjdk:21-slim
WORKDIR /app
COPY target/*.jar app.jar
ENTRYPOINT ["java", "-Dspring.profiles.active=container", "-jar", "app.jar"]
