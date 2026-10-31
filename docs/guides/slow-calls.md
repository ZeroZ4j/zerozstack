# Slow calls

This page is for someone whose remote call waits on an AI model, a report or an import, and can pass
the 30 second limit.

By the end you will have one service method that returns at once, a screen that follows the work with
no timers, and work that survives a reconnect and a page reload.

## When to use this

Use a slow call when the work can pass 30 seconds, or when losing the answer to a dropped connection
would cost money or time. Every plain RMI call fails with `RequestTimeoutException` at 30 seconds. The
server keeps working and only the answer is lost. See
[An RMI call never returns](troubleshooting.md#an-rmi-call-never-returns).

Do not use one for a call that always answers in a second or two. Use a plain
[RMI method](../decide/rmi-vs-state-sync.md).

Do not write start-and-poll code, a pending state, a store for results, or a timer. This does all of
that.

## Write one

The method looks like any other, with `SlowCall<T>` as its return type. `T` is anything that may cross
the wire. This is the report in the `job-monitor` example.

```java
// shared module
import com.zeroz4j.api.RmiService;
import com.zeroz4j.api.SlowCall;

@RmiService
public interface JobService {
    String REPORT_KEY = "job-report";

    SlowCall<JobReport> writeReport();
}
```

```java
// server module
import com.zeroz4j.api.SlowCall;
import com.zeroz4j.server.SlowCalls;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

@ApplicationScoped
public class JobServiceImpl implements JobService {

    @Inject
    SlowCalls slowCalls;

    @Override
    public SlowCall<JobReport> writeReport() {
        return slowCalls.start(REPORT_KEY, job -> {
            job.progress(0, "Reading the build log");
            Thread.sleep(13_000L);
            return new JobReport("Pipeline finished with 2 warnings.", 2);
        });
    }
}
```

```java
// client module
import com.zeroz4j.api.SlowCall;
import com.zeroz4j.api.SlowCallState;
import com.zeroz4j.signals.Effect;

SlowCall<JobReport> report = new JobService_Stub().writeReport();

Effect.create(() -> {
    SlowCallState<JobReport> state = report.state();
    if (state.isFinished()) {
        label.setText(state.value().summary());
    }
});
```

`writeReport()` on the stub is an ordinary RMI call and returns in one round trip. Call it where you
call any service method, such as a click handler. The lambda runs on its own thread, so it may block.
Dispose the `Effect` the way you dispose any other. The handle has nothing to dispose.

The key names the work. Each caller has one run per key:

- Starting a key that is **running** hands back the running work and ignores the lambda. A second
  click or a second tab starts nothing.
- Starting a key that has **ended** starts it again and drops the old result.

Put the argument in the key when each one is separate work: `"review-" + callId`. Use one key for one
kind of value. Keep the key in the shared module, as `REPORT_KEY` is, so both tiers build the same
string.

## Draw every state

`state()` reads a signal, so an `Effect` runs again on every change.

| Method | True when | Also read |
|---|---|---|
| `isChecking()` | The browser has not heard from the server yet | |
| `isIdle()` | The server has nothing under this key | |
| `isRunning()` | The work runs, or the value is still on its way | `percent()`, `progressText()` |
| `isFinished()` | The value is here | `value()` |
| `isFailed()` | The work failed, or the server no longer has it | `message()`, a sentence to show |
| `isCanceled()` | Somebody stopped the work | |

`percent()` is 0 to 100, or -1 when the work has not said.

## Find the work after a reload

A reload loses the handle, so ask for it by key. This starts nothing.

```java
SlowCall<JobReport> report = SlowCall.find(JobService.REPORT_KEY);
```

A handle from `find` follows every run under that key, so one field can hold it for the life of a
screen, and the button only has to call the service. `JobMonitorView` in the `job-monitor` example is
written this way. Its effect reads `isChecking()` so the button is not enabled before the screen knows.

## Report progress and stop

```java
return slowCalls.start(key, job -> {
    for (int i = 0; i < files.size(); i++) {
        if (job.isCanceled()) {
            return null;
        }
        job.progress(i * 100 / files.size(), "Reading " + files.get(i));
        read(files.get(i));
    }
    return summary();
});
```

Calling `progress` often is safe. The browser hears it at most four times a second, and it always
gets the last value.

On the client, `report.cancel()` asks the server to stop. It is an ordinary remote call, so it throws
`DisconnectedException` while the connection is down. The work gets an interrupt, which ends any
blocking call, and `job.isCanceled()` turns true. The state turns to canceled at once, even if the
work ignores both, and whatever the work returns after that is dropped. Work that ignores both keeps
its running slot until it ends, or until it passes the time limit below.

## Limit how long it runs

Set `zeroz.slowcall.maxMinutes` to give every slow call a time limit. Unset or 0 means no limit.

Work past the limit is stopped the way a cancel stops it: an interrupt, and `job.isCanceled()` turns
true. The state turns to failed with the message "The work took too long." The running slot is free
at once, even if the work ignores the interrupt, and a late return changes nothing. The check runs
every five seconds, so work can run a few seconds past the limit.

## What the work can read

The work runs on a thread of its own, with the caller's context restored.

| Available in the work | Value |
|---|---|
| `RmiRequestContext.getPrincipal()`, `getRoles()` | The caller's, as they were at the start |
| `RmiRequestContext.getTenantId()` | The caller's, so a tenant resolver finds the right store |
| `RmiRequestContext.getClientId()`, `getLocale()` | The caller's |
| `RmiRequestContext.getSessionId()` | **Null.** The connection may be gone long before the work ends |
| `@RequestScoped` beans | Active, with one request scope for the whole run |

Code that needs a session id fails in the work. A `LiveMutex` lock is one such case. To reach the
person while the work runs, use `publishToUser`, or a scoped signal with `Scope.USER` or `Scope.CLIENT`.

In a WAR the thread comes from your `SessionThreadFactoryProvider`, as it does for an RMI call, so it
carries what that factory carries. See [limitations](../reference/limitations.md#slow-calls).

## What happens when

| Event | Result |
|---|---|
| The connection drops and comes back | Nothing to write. The state catches up, and a result that finished meanwhile is fetched |
| The page reloads | `SlowCall.find(key)` finds the running work or the finished result |
| The tab closes | The work runs on and the result waits for the time below |
| The same person opens a second browser | It sees the same work, if they signed in |
| The server restarts | Running work and results are lost. A page that was following the work shows "The server no longer has this work. Start it again." |
| The work passes `zeroz.slowcall.maxMinutes` | It is stopped, and the page shows "The work took too long." |
| The result expires | The same sentence, for a page that had not received it. A page holding the value keeps it |

A page reloaded during a restart has nothing to compare with, so it shows `isIdle()`.

## Who owns the work

The connection decides, and no argument can change it.

- A signed-in caller owns the work as that user in that tenant. All their browsers see it.
- An anonymous caller owns it as that browser, through the signed id in its cookie.

Somebody else's run looks exactly like one that does not exist. `@Secured`, `@RolesAllowed` and
argument validation apply to the service method as always, so they are checked before the work
starts. They are not checked again when the result is fetched.

Signing in or out changes the owner, so anonymous work is not visible after signing in.

## Failures

Throw from the lambda, the way you throw from a service method.

```java
throw new ClientVisibleException("The model is not available.");
```

`ClientVisibleException` keeps its sentence, and a `Message` is said in the caller's language. Anything
else reaches `message()` as "The server could not complete this request. Reference: &lt;code&gt;", and
the server log has the real message under that code.

Two refusals come from the start call itself, as an ordinary RMI error, and nothing runs:

- "Too many slow calls are running. Wait for one to finish."
- "The server is busy. Try again in a moment."

## Settings

| Property | What it does | Unset |
|---|---|---|
| `zeroz.slowcall.keepMinutes` | How long a result is kept after the work ends | **60** |
| `zeroz.slowcall.maxKeptPerClient` | Ended slow calls kept per browser or user, oldest dropped first | **16** |
| `zeroz.slowcall.maxRunningPerClient` | Slow calls one browser or user may run at once | **8** |
| `zeroz.slowcall.maxRunning` | Slow calls this server runs at once, across everybody | **1,000** |
| `zeroz.slowcall.maxMinutes` | How long one slow call may run; 0 means no limit | **0** |

The server-wide limit exists because an anonymous visitor can drop its cookie and become a new
browser.

## Test it

Use [the test harness](testing.md). `TestConnection.call` goes through the real engine, and
`awaitSlowCall` waits for the end. A slow call has to start inside a service call, so a direct
`server.bean(...)` call fails with a sentence.

```java
import com.zeroz4j.api.SlowCall;
import com.zeroz4j.api.SlowCallState;
import com.zeroz4j.server.test.TestConnection;
import com.zeroz4j.server.test.TestServer;

try (TestServer server = TestServer.start(JobServiceImpl.class);
     TestConnection browser = server.connect("demo", "user")) {

    SlowCall<JobReport> call = browser.call(JobService.class, "writeReport");
    SlowCallState<JobReport> end = browser.awaitSlowCall(call, 5_000L);

    assertEquals(2, end.value().findings());
}
```

`server.reconnect(browser)` opens the next connection for the same browser and person, which is what
a reconnect and a reload are to the server. `browser.slowCallState(SlowCall.find(key))` reads the
state there, and `browser.cancelSlowCall(call)` asks the work to stop.
`JobReportTest` in `zerozstack-examples/job-monitor` is the one to copy.

## Limits

- **Everything is in memory.** A server restart loses running work and results, and nothing resumes.
- **One server.** Work and results live in the process that started them, so a sticky session is
  needed behind a load balancer.
- **No time limit unless you set one.** With `zeroz.slowcall.maxMinutes` unset, work that hangs and
  ignores its interrupt holds a running slot until the server stops.
- **A stopped thread may run on.** The limit frees the slot, but Java cannot end a thread that ignores
  its interrupt, so that thread keeps its memory and whatever it holds until it returns.
- **A kept result holds its objects.** Return a plain value or a copy, never a live graph object from
  [the store](persistence.md).
- **Result size is not limited.** A result is held whole in memory and sent in one message, and none
  of these settings bound its size.
- **Roles are not checked again at fetch.** They are checked when the work starts. A user who loses a
  role can still fetch a result of theirs until it expires.
- **`T` is not checked at compile time.** A value that cannot cross the wire fails when the browser
  fetches it, with the reference sentence, and the log names the type.
- **`find` knows only the key,** so the compiler does not check `T` against what the server started.
- **The failure sentence is in the language of the caller who started the work,** and a lost-work
  sentence is fixed when it is shown. Neither changes when somebody switches language.
