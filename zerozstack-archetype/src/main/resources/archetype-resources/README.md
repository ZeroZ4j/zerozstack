#*
  Filtered through Velocity when a project is generated, exactly like AGENTS.md next to it, and
  Velocity reads ## as the start of a comment. Every second-level heading below is therefore
  wrapped in #[[ ... ]]#, Velocity's literal block, which emits its contents untouched. An
  unescaped heading disappears from the generated file with no error and no warning.

  ${rootArtifactId} and ${package} are substituted; see archetype-metadata.xml.
*###
# ${rootArtifactId}

A ZeroZ Stack application. The user interface is written in Java and compiled to run in the
browser, the two halves talk over a WebSocket, and there is no JavaScript, JSON or SQL to write.

#[[## The three modules]]#

| Module | Holds |
|---|---|
| `${rootArtifactId}-shared` | The types that cross the wire, and the service interfaces. Both halves compile against it. |
| `${rootArtifactId}-client` | The user interface. Java, compiled for the browser. |
| `${rootArtifactId}-server` | The implementations of those services. |

#[[## Building it]]#

There are three commands, and picking the right one is worth real time. The browser compiler is
the slowest part of this build by a wide margin, so the commands differ mainly in whether they run
it.

| Command | What it does | Use it for |
|---|---|---|
| `mvn compile` or `mvn test-compile` | Java only. **Does not build the browser bundle.** | Checking that what you just typed compiles. This is the loop you run all day. |
| `mvn install` | Everything, including a readable browser bundle you can run. | Before running the application, and before you call a piece of work finished. |
| `mvn install -Pproduction` | The same, with the browser bundle optimized and minified — about a quarter the size. | Before you ship, and before you hand the application to anyone. |

On a freshly generated project with a warm Maven cache those take roughly 5, 16 and 17 seconds. On
a slower build machine the full build is 60 to 90. The quick check is the one that pays off: it is
the only one that does not compile the user interface, and that is nearly all of the difference.

Then run it:

```bash
cd ${rootArtifactId}-server
java -cp "target/classes;target/libs/*" ${package}.server.ServerApp
```

Use `:` instead of `;` in that classpath on Linux and macOS. There is no `mvn exec:java` here.
Then open <http://localhost:8080>.

#[[## Two things that will catch you out]]#

**The quick check does not compile the user interface.** `mvn compile` and `mvn test-compile` run
`javac` and stop. The browser compiler accepts a smaller language than `javac` does — it emulates
part of the JDK, not all of it — so client code can pass the quick check and still fail to build.
Run a full `mvn install` before you believe a piece of client work is done, and give any automated
pipeline `mvn verify` rather than `mvn test`, or it is not compiling your user interface at all.

**Build and run `-Pproduction` before you ship.** It is the only command that produces the shape
your users get. Minification renames things, and renaming can break code that a readable build
runs perfectly — that has happened in this framework before, and it reached two releases because
nobody ran the minified shape until users did. Start the application, click through it, and watch
the browser console for errors.

#[[## Shipping it]]#

`mvn verify -Ppackage` produces a folder containing a launcher, every jar, and a Java runtime, so
the machine you hand it to needs no Java installed. The `Dockerfile` in this directory builds a
container image instead. Combine either with `-Pproduction`:

```bash
mvn verify -Pproduction,package
```

Never add `maven-shade-plugin`. Merging the jars breaks the bean discovery this application
depends on, and the beans simply stop being found.

#[[## Where to read more]]#

`AGENTS.md` next to this file is written for an AI coding assistant and is the shortest correct
summary of the rules this framework enforces. The published documentation is at
<https://stack.zeroz4j.com/>.
