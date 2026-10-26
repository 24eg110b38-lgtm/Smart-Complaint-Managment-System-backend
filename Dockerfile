FROM maven:3.9.9-eclipse-temurin-21 AS build
WORKDIR /workspace

ENV MAVEN_OPTS="-Xmx384m"

COPY pom.xml ./
COPY src ./src
RUN mvn -B -DskipTests clean package

FROM eclipse-temurin:21-jre
WORKDIR /app

COPY --from=build /workspace/target/*.jar /app/app.jar

EXPOSE 8080

ENTRYPOINT ["java","-jar","/app/app.jar"]
