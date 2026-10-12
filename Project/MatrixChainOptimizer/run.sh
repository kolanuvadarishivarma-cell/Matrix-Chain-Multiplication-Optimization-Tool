#!/bin/bash
# Matrix Chain Multiplication Optimizer - launcher (Mac/Linux)
cd "$(dirname "$0")"
mkdir -p out
if [ ! -f out/Main.class ]; then
  echo "Compiling..."
  javac -d out src/*.java || exit 1
fi
java -cp out Main "$@"
