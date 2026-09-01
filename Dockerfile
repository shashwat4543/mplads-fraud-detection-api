# Stage 1: Build JAR with Maven
FROM eclipse-temurin:21-jdk-alpine AS build
WORKDIR /app

# Copy Maven wrapper & project configuration
COPY pom.xml .
COPY .mvn .mvn
COPY mvnw .

# Copy source code and files
COPY src src

# Build package
RUN chmod +x ./mvnw && ./mvnw clean package -DskipTests

# Stage 2: Production Runtime
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app

COPY --from=build /app/target/*.jar app.jar

EXPOSE 8080

ENTRYPOINT ["java", "-Xms128m", "-Xmx256m", "-XX:+UseG1GC", "-jar", "app.jar"]