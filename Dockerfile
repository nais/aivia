FROM gcr.io/distroless/java17-debian11:nonroot

COPY build/libs/*.jar /app/

CMD ["-jar", "/app/app.jar"]