package com.example.reminder;

import com.example.reminder.model.Reminder;
import com.example.reminder.model.ReminderStatus;
import com.example.reminder.storage.ReminderDatabase;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Real SQL Database Storage Tests")
class ReminderDatabaseTest {

    private ReminderDatabase database;

    @BeforeEach
    void setUp() {
        database = ReminderDatabase.createInMemory("testdb-" + System.nanoTime());
    }

    @AfterEach
    void tearDown() {
        if (database != null) {
            database.close();
        }
    }

    @Test
    @DisplayName("Should insert, query by ID, and update status in real SQL database")
    void testBasicCrud() {
        long trigger = System.currentTimeMillis() + 5000;
        Reminder reminder = new Reminder("user-1", "Doctor Appointment", "Visit clinic at 5 PM", trigger);

        database.save(reminder);
        assertEquals(1, database.count());

        Optional<Reminder> fetched = database.findById(reminder.getId());
        assertTrue(fetched.isPresent());
        assertEquals("user-1", fetched.get().getUserId());
        assertEquals("Doctor Appointment", fetched.get().getTitle());
        assertEquals(ReminderStatus.PENDING, fetched.get().getStatus());

        database.updateStatus(reminder.getId(), ReminderStatus.DELIVERED);
        Optional<Reminder> updated = database.findById(reminder.getId());
        assertTrue(updated.isPresent());
        assertEquals(ReminderStatus.DELIVERED, updated.get().getStatus());
    }

    @Test
    @DisplayName("Should execute compound-indexed time window range query")
    void testTimeWindowRangeQuery() {
        long now = System.currentTimeMillis();
        String bucket = Reminder.calculateTimeBucket(now);

        List<Reminder> batch = new ArrayList<>();
        // 5 reminders within next 10 mins (now + 1m, 2m, 3m, 4m, 5m)
        for (int i = 1; i <= 5; i++) {
            batch.add(new Reminder("user-" + i, "Reminder " + i, "Msg", now + (i * 60_000)));
        }
        // 2 reminders far in future (now + 2 hours)
        for (int i = 6; i <= 7; i++) {
            batch.add(new Reminder("user-" + i, "Far Future " + i, "Msg", now + (2 * 3600_000)));
        }

        database.saveBatch(batch);
        assertEquals(7, database.count());

        // Lookahead window: [now, now + 10 mins]
        List<Reminder> window = database.findRemindersForWindow(now, now + 600_000, ReminderStatus.PENDING);
        assertEquals(5, window.size());
    }
}
