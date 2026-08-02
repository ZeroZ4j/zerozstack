package com.smoke.signals;

import com.smoke.model.Message;
import com.zeroz4j.signals.Signals;
import com.zeroz4j.signals.ValueSignal;

/** Exercises defect 1: broadcasting this needs a generated Message_Serializer. */
public final class SmokeSignals {

    public static final ValueSignal<Message> TICK = Signals.shared("smoke.tick", new Message("initial"));

    private SmokeSignals() {
    }
}
