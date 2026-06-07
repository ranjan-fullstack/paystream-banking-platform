# --------- 1️⃣ Base Image (Java 17 recommended) ---------
FROM eclipse-temurin:17-jre-alpine

# --------- 2️⃣ Set working directory ---------
WORKDIR /app

# --------- 3️⃣ Copy jar file ---------
# Make sure jar name matches your build output
COPY target/auth-service-*.jar app.jar

# --------- 4️⃣ Expose service port ---------
EXPOSE 9005

# --------- 5️⃣ Run the application ---------
ENTRYPOINT ["java","-XX:+UseContainerSupport","-jar","app.jar"]
