package com.zeroz4j.server.vidocqspike;

import com.zeroz4j.api.ObjectMapper;
import com.zeroz4j.example.api.TaskService;
import com.zeroz4j.server.ServerRuntime;
import io.vidocq.vauban.core.container.VaubanContainer;
import jakarta.enterprise.context.control.RequestContextController;
import jakarta.enterprise.inject.spi.Bean;
import jakarta.enterprise.inject.spi.BeanManager;
import jakarta.enterprise.inject.spi.CDI;

import java.util.Set;
import java.util.concurrent.Callable;

/** SPIKE. One line per CDI feature server-core uses: PASS, or FAIL with how it failed. */
final class CdiProbe {

    static void run(VaubanContainer container) {
        ProbeBeans.Probe probe = attempt("container.select(own bean)", () -> container.select(ProbeBeans.Probe.class));
        attempt("CDI.current()", () -> CDI.current().getClass().getName());
        attempt("CDI.current().select(ServerRuntime).get()", () -> {
            var found = CDI.current().select(ServerRuntime.class);
            return "unsatisfied=" + found.isUnsatisfied() + " ambiguous=" + found.isAmbiguous() + " -> " + found.get().getClass().getSimpleName();
        });
        attempt("CDI.current().getBeanManager()", () -> CDI.current().getBeanManager().getClass().getName());
        attempt("@Inject BeanManager", () -> probe.beanManager().getClass().getName());
        attempt("BeanManager.getBeans(Object.class)", () -> {
            Set<Bean<?>> all = probe.beanManager().getBeans(Object.class);
            StringBuilder names = new StringBuilder(all.size() + " beans:");
            for (Bean<?> bean : all) {
                names.append(' ').append(bean.getBeanClass() == null ? "null" : bean.getBeanClass().getSimpleName());
            }
            return names.toString();
        });
        attempt("BeanManager.getBeans(type) + getReference", () -> {
            BeanManager bm = probe.beanManager();
            Bean<?> bean = bm.resolve(bm.getBeans(TaskService.class));
            Object ref = bm.getReference(bean, TaskService.class, bm.createCreationalContext(bean));
            return ref.getClass().getName();
        });
        attempt("@Inject @Any Instance<Object> .select(iface).get()", () -> probe.beans().select(TaskService.class).get().getClass().getName());
        attempt("Instance<Object>.select(ObjectMapper) (produced by @Produces @ApplicationScoped)", () -> probe.beans().select(ObjectMapper.class).get().getClass().getName());
        attempt("Event.fire -> @Observes", () -> {
            probe.pings().fire(new ProbeBeans.Ping("hello"));
            if (!"hello".equals(probe.observed())) throw new IllegalStateException("observer not called, saw " + probe.observed());
            return "observer called";
        });
        attempt("@Produces @Dependent with InjectionPoint", () -> {
            String into = probe.tagged().injectedInto;
            if (!"Probe".equals(into)) throw new IllegalStateException("InjectionPoint member was " + into);
            return "injected into " + into;
        });
        attempt("RequestContextController via Instance<Object>, @RequestScoped per activation", () -> {
            RequestContextController controller = probe.beans().select(RequestContextController.class).get();
            int first, second;
            boolean a = controller.activate();
            try { first = probe.perRequest().number(); } finally { if (a) controller.deactivate(); }
            boolean b = controller.activate();
            try { second = probe.perRequest().number(); } finally { if (b) controller.deactivate(); }
            if (first == second) throw new IllegalStateException("same instance in two requests: " + first);
            return "activated=" + a + "," + b + " instances " + first + " then " + second;
        });
        attempt("@RequestScoped activation on a virtual thread", () -> {
            Object[] out = new Object[1];
            Thread t = Thread.ofVirtual().start(() -> {
                try {
                    RequestContextController controller = probe.beans().select(RequestContextController.class).get();
                    boolean a = controller.activate();
                    try { out[0] = "instance " + probe.perRequest().number(); } finally { if (a) controller.deactivate(); }
                } catch (Throwable e) { out[0] = e; }
            });
            t.join();
            if (out[0] instanceof Throwable e) throw new IllegalStateException(e);
            return out[0];
        });
    }

    private static <T> T attempt(String what, Callable<T> body) {
        try {
            T result = body.call();
            String shown = String.valueOf(result);
            System.out.println("[probe] PASS " + what + " -> " + (shown.length() > 400 ? shown.substring(0, 400) + "..." : shown));
            return result;
        } catch (Throwable e) {
            System.out.println("[probe] FAIL " + what + " -> " + e);
            return null;
        }
    }

    private CdiProbe() { }
}
