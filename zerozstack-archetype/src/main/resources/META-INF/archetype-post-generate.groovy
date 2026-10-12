/*
 * Runs after a project is generated. The archetype plugin checks the `container` answer against
 * its pattern only when it asks the question; with -B (batch mode) any text gets through, and the
 * generated server would then ask for a module that does not exist. This refuses it instead.
 */
def container = request.properties.getProperty('container')
if (!(container in ['helidon', 'vidocq'])) {
    throw new IllegalArgumentException("container must be 'helidon' or 'vidocq', not '" + container
            + "'. The project folder '" + request.artifactId
            + "' was written before this check ran; delete it and generate again.")
}
