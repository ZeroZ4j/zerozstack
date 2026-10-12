package com.zeroz4j.server.vidocqspike;

import io.vidocq.vauban.core.container.VaubanContainer;
import io.vidocq.vauban.core.context.RequestContext;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.context.control.RequestContextController;

/**
 * SPIKE. Vauban 0.3.0 has a request scope but offers no RequestContextController bean, which the
 * engine looks up for every frame. This puts the standard interface over Vauban's own context.
 */
@ApplicationScoped
public class SpikeRequestContextController implements RequestContextController {

    @Override
    public boolean activate() {
        RequestContext context = VaubanContainer.current().requestContext();
        if (context.isActive()) {
            return false;
        }
        context.activate();
        return true;
    }

    @Override
    public void deactivate() {
        VaubanContainer.current().requestContext().deactivate();
    }
}
