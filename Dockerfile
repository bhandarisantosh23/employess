
FROM maven:4.0.0 eclipse-temurin-17-alpine AS builder

WORKDIR /app

COPY  pom.xml .
 COPY  src ./src

 RUN mvn clean package -DskipTests
  FROM eclipse-temurin-17-alpine
  WORKDIR /app

#  RUN addgroup  -S spring && adduser -S apring -G spring
EXPOSE 8080
COPY --from =builder /app/target/*.jar app.jar
RUN Chown : spring:spring app.jar
USER spring
HEALTHCHECK --intervsl=30s  --timeout=3s --start-perio=5s --retries=3
CMD wget --no-verbose  --tries --spider http://localhost:8080/api/health



ENTRYPOINT ["java", "-jar", "app.jar"]