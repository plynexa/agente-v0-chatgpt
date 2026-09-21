#!/bin/sh

APP_HOME=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
CLASSPATH=$APP_HOME/gradle/wrapper/gradle-wrapper.jar
JAVA_CMD=${JAVA_HOME:+$JAVA_HOME/bin/}java

if [ ! -f "$CLASSPATH" ]; then
  echo "gradle-wrapper.jar is missing. Install Gradle 8.10.2 and run: gradle wrapper --gradle-version 8.10.2" >&2
  exit 1
fi

exec "$JAVA_CMD" -classpath "$CLASSPATH" org.gradle.wrapper.GradleWrapperMain "$@"
