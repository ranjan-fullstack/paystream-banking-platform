FROM eclipse-temurin:17-jre-alpine

WORKDIR /app

COPY target/audit-service-*.jar app.jar

EXPOSE 9011

ENTRYPOINT ["java","-XX:+UseContainerSupport","-jar","app.jar"]
