FROM eclipse-temurin:17-jre-alpine

WORKDIR /app

COPY target/upi-service-*.jar app.jar

EXPOSE 9007

ENTRYPOINT ["java","-XX:+UseContainerSupport","-jar","app.jar"]
