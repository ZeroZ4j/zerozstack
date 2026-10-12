package com.zeroz4j.server.vidocqspike;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.context.Dependent;
import jakarta.enterprise.context.RequestScoped;
import jakarta.enterprise.event.Event;
import jakarta.enterprise.event.Observes;
import jakarta.enterprise.inject.Any;
import jakarta.enterprise.inject.Instance;
import jakarta.enterprise.inject.Produces;
import jakarta.enterprise.inject.spi.BeanManager;
import jakarta.enterprise.inject.spi.InjectionPoint;
import jakarta.inject.Inject;

import java.util.concurrent.atomic.AtomicInteger;

/** SPIKE. Beans that exist only so CdiProbe can exercise each CDI feature server-core relies on. */
public final class ProbeBeans {

    public static class Ping {
        public final String text;
        public Ping(String text) { this.text = text; }
    }

    /** What an InjectionPoint-aware @Dependent producer hands out. */
    public static class Tagged {
        public final String injectedInto;
        public Tagged(String injectedInto) { this.injectedInto = injectedInto; }
    }

    @RequestScoped
    public static class PerRequest {
        private static final AtomicInteger COUNTER = new AtomicInteger();
        private final int number = COUNTER.incrementAndGet();
        public int number() { return number; }
    }

    @ApplicationScoped
    public static class Producers {
        @Produces
        @Dependent
        public Tagged tagged(InjectionPoint ip) {
            return new Tagged(ip == null || ip.getMember() == null
                    ? "null" : ip.getMember().getDeclaringClass().getSimpleName());
        }
    }

    @ApplicationScoped
    public static class Probe {
        @Inject BeanManager beanManager;
        @Inject @Any Instance<Object> beans;
        @Inject Event<Ping> pings;
        @Inject Tagged tagged;
        @Inject PerRequest perRequest;
        volatile String observed;

        void onPing(@Observes Ping ping) { observed = ping.text; }

        public BeanManager beanManager() { return beanManager; }
        public Instance<Object> beans() { return beans; }
        public Event<Ping> pings() { return pings; }
        public Tagged tagged() { return tagged; }
        public PerRequest perRequest() { return perRequest; }
        public String observed() { return observed; }
    }

    private ProbeBeans() { }
}
