#!/bin/sh
# Gradle wrapper bootstrap - download on first use

APP_BASE_NAME=$(basename "$0")
APP_HOME=$(pwd)
CLASSPATH="$APP_HOME/gradle/wrapper/gradle-wrapper.jar"

# Download gradle-wrapper.jar if not present
if [ ! -f "$CLASSPATH" ]; then
    echo "Gradle wrapper JAR not found. Please run:"
    echo "  gradle wrapper"
    echo "Or open project in Android Studio"
    exit 1
fi

exec java     -classpath "$CLASSPATH"     org.gradle.wrapper.GradleWrapperMain     "$@"
