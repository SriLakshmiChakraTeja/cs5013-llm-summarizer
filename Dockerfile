FROM ghcr.io/ggml-org/llama.cpp:server
RUN apt-get update && apt-get install -y openjdk-17-jdk maven curl && rm -rf /var/lib/apt/lists/*
WORKDIR /app
COPY pom.xml .
COPY src ./src
RUN mvn -q clean compile
COPY g9v3-3b-q8_0.gguf /models/g9v3.gguf
COPY start.sh .
ENTRYPOINT ["bash", "/app/start.sh"]