FROM eclipse-temurin:21-alpine

ARG JAR_FILE=target/*.jar

EXPOSE 80/tcp

COPY ${JAR_FILE} app.jar

ENTRYPOINT ["java","-jar","/app.jar"]