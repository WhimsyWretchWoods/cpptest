#!/usr/bin/env bash
#
# Copyright 2012 the original author or authors.
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#      http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.
#

##############################################################################
##
##  Gradle start up script for UN*X
##
##############################################################################

# Add default JVM options here. You may also use JAVA_OPTS and GRADLE_OPTS.
# The two latter have the advantage of being used by all tools/applications
# that start the JVM.
DEFAULT_JVM_OPTS=""

APP_NAME="Gradle"
APP_BASE_NAME=$(basename "$0")

# Use Java 8 for Gradle 7.x, Java 11 for Gradle 7.x, Java 17 for Gradle 8.x
# Java home environment variable for toolchains is available as JAVA_HOME.
if [ -z "$JAVA_HOME" ]; then
    if [ -x "/usr/libexec/java_home" ]; then
        JAVA_HOME=$(/usr/libexec/java_home -v 17)
    elif [ -d "/usr/lib/jvm/java-17-openjdk" ]; then
        JAVA_HOME="/usr/lib/jvm/java-17-openjdk"
    elif [ -d "/usr/lib/jvm/default-java" ]; then
        JAVA_HOME="/usr/lib/jvm/default-java"
    elif [ -d "/usr/lib/jvm/java-11-openjdk" ]; then
        JAVA_HOME="/usr/lib/jvm/java-11-openjdk"
    elif [ -d "/usr/lib/jvm/java-8-openjdk" ]; then
        JAVA_HOME="/usr/lib/jvm/java-8-openjdk"
    fi
fi

# OS specific support (must be 'true' or 'false').
cygwin=false
darwin=false
mingw=false
case "`uname`" in
  CYGWIN*)
    cygwin=true
    ;;
  Darwin*)
    darwin=true
    ;;
  MINGW*)
    mingw=true
    ;;
esac

# For Cygwin, ensure paths are in UNIX format before anything else.
if $cygwin ; then
  [ -n "$JAVA_HOME" ] && JAVA_HOME=`cygpath --unix "$JAVA_HOME"`
fi

# Determine the Java command to run.
if [ -n "$JAVA_HOME" ] ; then
  if [ -x "$JAVA_HOME/jre/sh/java" ] ; then
    JAVACMD="$JAVA_HOME/jre/sh/java"
  elif [ -x "$JAVA_HOME/bin/java" ] ; then
    JAVACMD="$JAVA_HOME/bin/java"
  else
    echo "ERROR: JAVA_HOME is set to an invalid directory: $JAVA_HOME"
    echo "Please set the JAVA_HOME environment variable in your shell to the correct Java installation."
    exit 1
  fi
else
  JAVACMD="java"
fi

if [ ! -x "$JAVACMD" ] ; then
  echo "ERROR: JAVA_HOME is not set and no 'java' command can be found in your PATH."
  echo "Please set the JAVA_HOME environment variable in your shell to the correct Java installation, or add 'java' to your PATH."
  exit 1
fi

# Determine the script directory.
SCRIPT_DIR=$(dirname $(readlink -f "$0"))
GRADLE_HOME=$(dirname "$SCRIPT_DIR")

# Determine GRADLE_HOME
if [ -z "$GRADLE_HOME" ]; then
  # If GRADLE_HOME is not set, try to determine it from the script's location
  # This implies that gradlew is in the root of the project
  GRADLE_HOME=$(cd "$(dirname "$0")" && pwd)
fi

# Add default JVM options and other options from various sources
# to GRADLE_OPTS.
GRADLE_OPTS="$DEFAULT_JVM_OPTS $JAVA_OPTS $GRADLE_OPTS"

# For Cygwin, switch paths to Windows format before running java.
if $cygwin ; then
  JAVA_HOME=`cygpath --windows "$JAVA_HOME"`
  GRADLE_HOME=`cygpath --windows "$GRADLE_HOME"`
fi

# Execute Gradle.
exec "$JAVACMD" $GRADLE_OPTS -classpath "`dirname "$0"`/gradle/wrapper/gradle-wrapper.jar" org.gradle.wrapper.GradleWrapperMain "$@"
