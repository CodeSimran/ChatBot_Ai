#!/bin/bash
# Filter script to remove hardcoded API keys
FILE_PATH="chatbot-backend/src/main/resources/application.yml"
if [ -f "$FILE_PATH" ]; then
  # Replace any hardcoded key with environment variable reference
  sed -i.bak 's/key: [^$].*/key: ${GROQ_API_KEY}/' "$FILE_PATH"
  rm -f "$FILE_PATH.bak"
fi
