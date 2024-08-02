FROM gcr.io/distroless/java21-debian12:nonroot

COPY build/libs/*.jar /app/

CMD ["-jar", "/app/app.jar"]