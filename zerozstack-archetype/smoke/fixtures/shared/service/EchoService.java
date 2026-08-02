package com.smoke.service;

import com.smoke.model.Message;
import com.zeroz4j.api.RmiService;

/** Exercises defect 3: the server implementation must be discoverable through the bean manager. */
@RmiService
public interface EchoService {

    Message echo(String text);
}
