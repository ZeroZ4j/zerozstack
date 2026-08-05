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

# Exec form on purpose: no shell, signals reach the JVM, and Java itself expands the
# classpath wildcard.
ENTRYPOINT ["java", "-cp", "app.jar:lib/*", "${package}.server.ServerApp"]
