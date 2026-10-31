# sign-in — a real sign-in form

A password form, a session cookie, and sign-out. The server side is one class, `Accounts`, and the
client side is `new Login()`.

Runs on **port 8093**.

```bash
mvn clean install -DskipTests          # once, from the repository root
cd zerozstack-examples/sign-in
run.bat                                # Windows
```

Open <http://localhost:8093/tasks/7> and sign in as `anna@example.com` / `anna-password`, or
`admin@example.com` / `admin-password`. You land on `/tasks/7`, the page you asked for. Reload, and
you are still signed in. A wrong password brings the form back with your name still in it.

**Sign out** ends this browser's session. **Sign out everywhere** ends every session this person has:
open the page in a second browser first, and it goes back to the form by itself.

To see the connection time cap and renewal without waiting, put this in a file called
`zeroz4j.properties` in `sign-in-server/`, and restart:

```properties
zeroz.signin.secret=any-value-of-at-least-32-characters
zeroz.signin.connectionMinutes=1
zeroz.signin.renewMinutes=1
```

The server log then shows the connection closing after about a minute, and the next one opening with
the same person. With the secret set, a session also survives a restart of the server.

See [Signing in with a cookie](../../docs/guides/cookie-sign-in.md).
