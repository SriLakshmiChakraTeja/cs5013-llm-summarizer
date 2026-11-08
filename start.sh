#!/bin/bash
llama-server -m /models/g9v3.gguf --port 8080 -c 16384 &
until curl -s http://127.0.0.1:8080/health >/dev/null; do sleep 2; done
exec mvn -q exec:java -Dexec.mainClass=DemoRunner