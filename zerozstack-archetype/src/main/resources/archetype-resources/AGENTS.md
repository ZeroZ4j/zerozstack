#*
  This file is filtered through Velocity when a project is generated, and Velocity reads ## as the
  start of a comment. An unescaped "## Heading" therefore disappears from the generated file with
  no error and no warning - which is exactly what happened the first time this page was written.
  Every second-level heading below is wrapped in #[[ ... ]]#, Velocity's literal block, which emits
  its contents untouched. This comment is itself Velocity syntax and is not generated.

  ${zeroz4jVersion}, ${rootArtifactId} and ${package} are substituted; see archetype-metadata.xml.
*###
# AGENTS.md — this is a ZeroZ Stack application

Instructions for AI coding assistants working in this project. It is short on purpose. It carries
only what you cannot look up, and what you would otherwise get wrong because it contradicts
ordinary Java habits.

#[[## The version this project is built on]]#

**ZeroZ Stack ${zeroz4jVersion}.** It is set once, as `<zeroz4j.version>` in the root `pom.xml`,
and every module follows it.

Read that number before you use anything you find online. The framework's published documentation
and its Context7 index both follow the framework's own main line, which moves ahead of released
versions, so they will happily describe features this project does not have. When something you
read disagrees with the version above, the version above wins.

#[[## Where to look things up]]#

**The rules for exactly this version travel inside the jar this project already depends on.** They
are correct by construction, need no network, and are the first place to look:

```bash
unzip -p ~/.m2/repository/com/zeroz4j/zerozstack-shared-api/${zeroz4jVersion}/zerozstack-shared-api-${zeroz4jVersion}.jar META-INF/zeroz4j/AGENTS.md
```

For anything longer than a rule — guides, worked examples, the protocol, the decision procedure for
how state moves — use the published documentation, and check what it tells you against the version
above:

- <https://stack.zeroz4j.com/>
- Context7, indexed as `/zeroz4j/zerozstack`
- `llms.txt` at <https://github.com/ZeroZ4j/zerozstack/blob/main/llms.txt>

#[[## The shape of this project]]#

Three modules, and which one a class belongs in is not a matter of taste:

| Module | Holds |
|---|---|
| `${rootArtifactId}-shared` | `@DataModel` types and `@RmiService` interfaces. Both tiers compile against it. |
| `${rootArtifactId}-client` | The user interface, written in Java and compiled for the browser by TeaVM. |
| `${rootArtifactId}-server` | `@ApplicationScoped` CDI beans implementing the service interfaces. |

Build and run it:

```bash
mvn clean install
cd ${rootArtifactId}-server
java -cp "target/classes;target/libs/*" ${package}.server.ServerApp
```

Use `:` instead of `;` in that classpath on Linux and macOS. There is no `mvn exec:java` here and
adding one will not work. Then open <http://localhost:8080>.

#[[## Ten things that are not ordinary Java]]#

1. **The client is Java, compiled to JavaScript by TeaVM.** Only JDK APIs TeaVM emulates are
   available in `${rootArtifactId}-client`. Do not add JavaScript, and do not change the compiler's
   `JAVASCRIPT` target to WebAssembly — that is a deliberate choice, not an oversight.
2. **Every type that crosses the wire must be annotated `@DataModel`.** Without it, sending the
   object throws at runtime, which is a long way from where you wrote the class.
3. **Put `@Secured` and `@RolesAllowed` on the `@RmiService` interface, never on the bean that
   implements it.** Only the interface is read. An annotation on the implementation is ignored
   without a word and the method is left open to anyone. They are `com.zeroz4j.api.Secured` and
   `com.zeroz4j.api.RolesAllowed`, not the Jakarta annotations of the same name.
4. **Do not start a thread inside a click handler.** `Component.addDomEventListener` already runs
   the body on a thread that can suspend, which is why a remote call works directly inside one. An
   extra thread is redundant and its screen updates may not appear until something else happens.
5. **Never empty a container with `getElement().setInnerHTML("")`.** That takes the old screen off
   the page without telling it, so its timers and subscriptions keep running forever. Use
   `replaceContents(...)`, `removeAll()` or `remove(...)`.
6. **Never change a signal's value in place.** Setting a list back after adding to it changes
   nothing, because the new value equals the old one. Use `update()` and return a new instance.
7. **The browser may not write to server state unless you say so.** Opt in with `@ClientWritable`
   or `Signals.sharedWritable`, per object, and expect the server to check again on arrival.
8. **Never put file contents in a remote call argument.** Messages over 4 MB close the connection
   rather than raising an error you can catch. Files have their own upload path.
9. **Database commands and queries must be plain classes with a public no-arg constructor, never
   records.** As records they fail on the first call, not at compile time.
10. **Never add `maven-shade-plugin`.** Merging the jars breaks CDI discovery, and the beans simply
    stop being found. The `package` profile already ships this application as one folder.

#[[## Two habits that save the most time]]#

**Decide deliberately how state moves.** There are five ways — a local signal, a remote call, a
server event, a shared signal, and live two-way object sync — and picking the wrong one is the most
common source of trouble in these applications. The decision procedure is in the documentation
under "Choosing how state moves". Do not guess.

**When nothing happens, read the troubleshooting page before reading the code.** A large share of
this framework's failures are silent: no exception, no log line, a screen that simply does not
update. Those symptoms are listed, with what to look at for each.

#[[## Writing for people]]#

Every label, message and error a user reads should be plain, short and concrete. Name a form field
with `withLabel("...")`, not with the constructor argument — the constructor argument is the
placeholder, which vanishes the moment somebody types. Anything a person clicks must be a `Button`,
or a `Link` with a real address; a click listener on a plain `Div` cannot be reached with a keyboard
and is announced as nothing.
