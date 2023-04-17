FROM cgr.dev/chainguard/jre:openjdk-jre-17

COPY build/libs/*.jar /app/

CMD ["-jar", "/app/app.jar"]