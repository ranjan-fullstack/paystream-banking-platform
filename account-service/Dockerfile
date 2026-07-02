FROM eclipse-temurin:17-jre-alpine

WORKDIR /app

COPY target/account-service-*.jar app.jar

EXPOSE 9003

ENTRYPOINT ["java","-XX:+UseContainerSupport","-jar","app.jar"]
