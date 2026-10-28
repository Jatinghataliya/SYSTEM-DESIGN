package com.example.reminder.engine;

import com.example.reminder.model.DispatchEvent;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Simulator for external notification gateways (Apple APNs / Google FCM / SMS / Email).
 */
public class PushGateway {

    private final List<DispatchEvent> deliveredEvents = Collections.synchronizedList(new ArrayList<>());
    private final AtomicLong deliveryCount = new AtomicLong(0);

    public void sendPushNotification(DispatchEvent event) {
        deliveryCount.incrementAndGet();
        deliveredEvents.add(event);
    }

    public long getDeliveryCount() {
        return deliveryCount.get();
    }

    public List<DispatchEvent> getDeliveredEvents() {
        return Collections.unmodifiableList(deliveredEvents);
    }

    public void clear() {
        deliveredEvents.clear();
        deliveryCount.set(0);
    }
}
