#!/bin/sh
# Licensed to the Apache Software Foundation (ASF) under one
# or more contributor license agreements.  See the NOTICE file
# distributed with this work for additional information
# regarding copyright ownership.  The ASF licenses this file
# to you under the Apache License, Version 2.0 (the
# "License"); you may not use this file except in compliance
# with the License.  You may obtain a copy of the License at
#
#    https://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing,
# software distributed under the License is distributed on an
# "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
# KIND, either express or implied.  See the License for the
# specific language governing permissions and limitations
# under the License.

set -e

BASEDIR=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
MAVEN_PROJECTBASEDIR="$BASEDIR"
WRAPPER_DIR="$BASEDIR/.mvn/wrapper"
PROPS="$WRAPPER_DIR/maven-wrapper.properties"
WRAPPER_JAR="$WRAPPER_DIR/maven-wrapper.jar"

if [ ! -f "$PROPS" ]; then
    echo "Error: $PROPS not found"
    exit 1
fi

if [ ! -f "$WRAPPER_JAR" ]; then
    WRAPPER_URL=$(grep -E '^wrapperUrl=' "$PROPS" | cut -d= -f2-)
    if [ -z "$WRAPPER_URL" ]; then
        WRAPPER_URL="https://repo.maven.apache.org/maven2/org/apache/maven/wrapper/maven-wrapper/3.2.0/maven-wrapper-3.2.0.jar"
    fi

    if command -v curl >/dev/null 2>&1; then
        curl -fsSL "$WRAPPER_URL" -o "$WRAPPER_JAR"
    elif command -v wget >/dev/null 2>&1; then
        wget -q -O "$WRAPPER_JAR" "$WRAPPER_URL"
    else
        echo "Error: curl or wget is required to download the Maven wrapper jar"
        exit 1
    fi
fi

exec java -classpath "$WRAPPER_JAR" -Dmaven.multiModuleProjectDirectory="$MAVEN_PROJECTBASEDIR" org.apache.maven.wrapper.MavenWrapperMain "$@"
