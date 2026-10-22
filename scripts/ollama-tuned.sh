#!/usr/bin/env bash
# ---------------------------------------------------------------------------
# Start Ollama tuned for a small machine (8 GB of RAM and a running IDE).
#
#   ./scripts/ollama-tuned.sh
#
# The default Ollama settings keep a 5 GB model resident in RAM for five
# minutes after the last request. On top of IntelliJ, Postgres, two JVMs and a
# browser that pushes the machine into swap, and everything starts to lag.
#
# These settings:
#   OLLAMA_KEEP_ALIVE=30s       free the model from RAM shortly after use
#   OLLAMA_NUM_PARALLEL=1       one request at a time (each needs its own context)
#   OLLAMA_MAX_LOADED_MODELS=1  never hold two models at once
#   OLLAMA_CONTEXT_LENGTH=2048  a smaller KV cache, which is pure overhead for us
#   OLLAMA_FLASH_ATTENTION=1    less memory for the attention computation
#
# Pair it with a small model: qwen2.5:3b is 1.9 GB and classifies tickets just
# as well as the 4.9 GB llama3.1.
# ---------------------------------------------------------------------------
set -euo pipefail

export OLLAMA_KEEP_ALIVE="${OLLAMA_KEEP_ALIVE:-30s}"
export OLLAMA_NUM_PARALLEL="${OLLAMA_NUM_PARALLEL:-1}"
export OLLAMA_MAX_LOADED_MODELS="${OLLAMA_MAX_LOADED_MODELS:-1}"
export OLLAMA_CONTEXT_LENGTH="${OLLAMA_CONTEXT_LENGTH:-2048}"
export OLLAMA_FLASH_ATTENTION="${OLLAMA_FLASH_ATTENTION:-1}"

echo "Ollama settings:"
echo "  KEEP_ALIVE       ${OLLAMA_KEEP_ALIVE}"
echo "  NUM_PARALLEL     ${OLLAMA_NUM_PARALLEL}"
echo "  MAX_LOADED_MODELS ${OLLAMA_MAX_LOADED_MODELS}"
echo "  CONTEXT_LENGTH   ${OLLAMA_CONTEXT_LENGTH}"
echo
echo "Installed models:"
ollama list 2>/dev/null || true
echo
exec ollama serve
