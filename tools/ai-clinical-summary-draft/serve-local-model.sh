#!/bin/sh
# Copyright (c) 2026 CARLOS Contributors. Licensed under GPL-2.0-or-later.
set -eu
export OLLAMA_HOST=127.0.0.1:11434
export OLLAMA_NO_CLOUD=1
export OLLAMA_NUM_PARALLEL=1
export OLLAMA_MAX_LOADED_MODELS=1
export OLLAMA_MAX_QUEUE=1
exec ollama serve
