package com.smoke.server;

import com.smoke.model.Message;
import com.smoke.service.EchoService;
import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class EchoServiceImpl implements EchoService {

    @Override
    public Message echo(String text) {
        return new Message("echo:" + text);
    }
}
