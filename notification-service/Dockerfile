FROM eclipse-temurin:17-jre-alpine

WORKDIR /app

COPY target/notification-service-*.jar app.jar

EXPOSE 9010

ENTRYPOINT ["java","-XX:+UseContainerSupport","-jar","app.jar"]
