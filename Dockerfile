FROM gcr.io/distroless/java25:nonroot@sha256:02ba64d87d0371be011404245a1ad4aeaa94d8264732a795b64dcd5bf036b116
ENV TZ="Europe/Oslo"
ENV JAVA_TOOL_OPTIONS=-XX:+ExitOnOutOfMemoryError
WORKDIR /app
COPY build/libs/*.jar ./app.jar
EXPOSE 8080
CMD ["app.jar"]
