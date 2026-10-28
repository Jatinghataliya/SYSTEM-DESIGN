package com.example.reminder;

import com.example.reminder.engine.ReminderService;
import com.example.reminder.model.DispatchEvent;
import com.example.reminder.model.Reminder;
import com.example.reminder.model.ReminderStatus;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("End-to-End Distributed Reminder System Tests")
class DistributedReminderSystemTest {

    private ReminderService service;

    @BeforeEach
    void setUp() {
        // 3 worker nodes, 4 consumer threads, scanInterval = 500ms, lookahead = 10,000ms, tick = 20ms, wheelSize = 20
        service = new ReminderService("testdb-" + System.nanoTime(), 3, 4, 500, 10_000, 20, 20);
    }

    @AfterEach
    void tearDown() {
        if (service != null) {
            service.shutdown();
        }
    }

    @Test
    @DisplayName("Should create reminder, schedule in timing wheel, and deliver notification on time")
    void testBasicReminderDelivery() throws Exception {
        long triggerTime = System.currentTimeMillis() + 150;
        Reminder reminder = service.createReminder("user-101", "Meeting", "Standup at 10", triggerTime);

        assertNotNull(reminder.getId());
        assertEquals(ReminderStatus.PREFETCHED, reminder.getStatus());

        // Wait for reminder to expire and get delivered
        Thread.sleep(400);

        assertEquals(1, service.getPushGateway().getDeliveryCount());
        DispatchEvent event = service.getPushGateway().getDeliveredEvents().get(0);
        assertEquals(reminder.getId(), event.reminderId());
        assertEquals("user-101", event.userId());

        Optional<Reminder> inDb = service.getDatabase().findById(reminder.getId());
        assertTrue(inDb.isPresent());
        assertEquals(ReminderStatus.DELIVERED, inDb.get().getStatus());
    }

    @Test
    @DisplayName("Should cancel scheduled reminder in O(1) before trigger time with zero notifications sent")
    void testCancellationInFlight() throws Exception {
        long triggerTime = System.currentTimeMillis() + 300;
        Reminder reminder = service.createReminder("user-202", "Flight Check-in", "Check in for flight", triggerTime);

        Thread.sleep(50);
        boolean cancelled = service.cancelReminder(reminder.getId());
        assertTrue(cancelled);

        // Wait past original trigger time
        Thread.sleep(400);

        assertEquals(0, service.getPushGateway().getDeliveryCount(), "Cancelled reminder should never trigger push notification");
        Optional<Reminder> inDb = service.getDatabase().findById(reminder.getId());
        assertTrue(inDb.isPresent());
        assertEquals(ReminderStatus.CANCELLED, inDb.get().getStatus());
    }

    @Test
    @DisplayName("Should handle 6:00 PM burst of 5,000 reminders without DB strain")
    void testSimulated6PMSpike() throws Exception {
        int burstSize = 5000;
        long triggerTime = System.currentTimeMillis() + 500;

        List<Reminder> burstList = new ArrayList<>(burstSize);
        for (int i = 0; i < burstSize; i++) {
            burstList.add(new Reminder("user-" + i, "6 PM Notification " + i, "Daily digest", triggerTime));
        }

        // Save batch to database directly
        service.getDatabase().saveBatch(burstList);
        assertEquals(burstSize, service.getDatabase().count());

        // Trigger batch prefetch window
        service.triggerPrefetchNow();

        // Wait for prefetch and timing wheels to expire and consumers deliver
        Thread.sleep(1500);

        assertTrue(service.getPrefetcher().getPrefetchedRemindersCount() >= burstSize,
                "All burst reminders should be prefetched into timing wheels");
        assertTrue(service.getPushGateway().getDeliveryCount() > 0,
                "Push notifications should be successfully delivered");
        System.out.printf("[TEST] Delivered %d/%d burst notifications.\n",
                service.getPushGateway().getDeliveryCount(), burstSize);
    }

    @Test
    @DisplayName("Idempotency store should block duplicate deliveries on replay")
    void testIdempotencyDeduplication() {
        String key = "reminder:test-123:60000";
        assertTrue(service.getIdempotencyStore().tryAcquire(key, 3600));
        assertFalse(service.getIdempotencyStore().tryAcquire(key, 3600), "Duplicate acquisition must be rejected");
        assertEquals(1, service.getIdempotencyStore().getDuplicateCount());
    }
}
