# Container image for the ${rootArtifactId} server.
#
# Build the project first, then the image from the project root:
#
#   mvn package -Pproduction
#   docker build -t ${rootArtifactId} .
#   docker run -p 8080:8080 ${rootArtifactId}
#
# -Pproduction is not optional here. A plain `mvn package` builds the browser bundle readable and
# unoptimized, which is right while you are developing and wrong in an image you hand to anyone:
# the bundle it copies in is several times larger, and it is not the shape you will have tested by
# the time it matters. Minification also renames things, and renaming has broken this framework
# before, so the image you ship should be built from the shape you ran.
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
