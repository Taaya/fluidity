#!/usr/bin/env bash
set -euo pipefail

# Resolve the project directory even when launched from another folder.
cd -- "$(dirname -- "${BASH_SOURCE[0]}")"

# This local JDK may not be registered with macOS's java_home utility.
if [[ -z "${JAVA_HOME:-}" && -x "$HOME/.jdks/temurin-17/Contents/Home/bin/java" ]]; then
  export JAVA_HOME="$HOME/.jdks/temurin-17/Contents/Home"
fi

printf 'Запускаю Fluidity. После запуска открой адрес из журнала (обычно http://localhost:8080).\nОстановить сервер: Ctrl+C.\n'
exec ./gradlew --console=plain run
