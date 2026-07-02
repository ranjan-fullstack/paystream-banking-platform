FROM eclipse-temurin:17-jre-alpine

WORKDIR /app

COPY target/fraud-detection-service-*.jar app.jar

EXPOSE 9009

ENTRYPOINT ["java","-XX:+UseContainerSupport","-jar","app.jar"]
