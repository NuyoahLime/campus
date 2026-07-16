#!/bin/sh
# Maven Wrapper startup script
# ----------------------------------------------------------------------------

# Resolve links
PRG="$0"
while [ -h "$PRG" ]; do
  ls=`ls -ld "$PRG"`
  link=`expr "$ls" : '.*-> \(.*\)$'`
  if expr "$link" : '/.*' > /dev/null; then
    PRG="$link"
  else
    PRG="`dirname "$PRG"`/$link"
  fi
done

SAVED="`pwd`"
cd "`dirname "$PRG"`/" >/dev/null
APP_HOME="`pwd -P`"
cd "$SAVED" >/dev/null

DEFAULT_JVM_OPTS="-Xmx512m -Xms256m"

# Use the Maven wrapper properties
MAVEN_PROJECTBASEDIR="${MAVEN_BASEDIR:-`pwd`}"
export MAVEN_PROJECTBASEDIR

MAVEN_OPTS="$(tr '\n' ' ' < "$MAVEN_PROJECTBASEDIR/.mvn/jvm.config" 2>/dev/null) $MAVEN_OPTS"

WRAPPER_LAUNCHER=org.apache.maven.wrapper.MavenWrapperMain

exec "$JAVACMD" \
  $MAVEN_OPTS \
  $DEFAULT_JVM_OPTS \
  -classpath "$MAVEN_PROJECTBASEDIR/.mvn/wrapper/maven-wrapper.jar" \
  "-Dmaven.home=${M2_HOME}" \
  "-Dmaven.multiModuleProjectDirectory=${MAVEN_PROJECTBASEDIR}" \
  ${WRAPPER_LAUNCHER} "$@"
