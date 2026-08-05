# Container image for the ${rootArtifactId} server.
#
# Build the project first, then the image from the project root:
#
#   mvn package
#   docker build -t ${rootArtifactId} .
#   docker run -p 8080:8080 ${rootArtifactId}
#
# Deliberately NOT a shaded jar: Weld treats each jar as a separate bean archive, and merging
# them into one jar breaks CDI discovery in ways that surface far from the cause. Here every
# jar stays intact on a plain classpath — and the dependency layer is separate from the app
# layer, so a routine rebuild pushes kilobytes, not the framework.
FROM eclipse-temurin:21-jre
WORKDIR /app

# Dependencies first: this layer only changes when a dependency changes.
COPY ${rootArtifactId}-server/target/libs/ lib/

# The application jar, which also carries the compiled client under META-INF/resources.
COPY ${rootArtifactId}-server/target/${rootArtifactId}-server-*.jar app.jar

EXPOSE 8080

# The classpath is built SORTED, deliberately. A `lib/*` wildcard expands in filesystem
# directory order, which is alphabetical on Windows and arbitrary on Linux — and one of the
# arbitrary orders loads Helidon's CDI extensions in a sequence where the WebSocket routing
# is registered after the server has already been built. The symptom is brutal: HTTP works,
# the page loads, and every WebSocket handshake answers 404 — on Linux only. A sorted
# explicit classpath makes the order deterministic. `exec` keeps signals reaching the JVM.
ENTRYPOINT ["sh", "-c", "exec java -cp \"app.jar:$(ls lib/*.jar | sort | tr '\\n' ':')\" ${package}.server.ServerApp"]
