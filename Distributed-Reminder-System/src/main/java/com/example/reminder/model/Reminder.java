package com.example.reminder.model;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Objects;
import java.util.UUID;

/**
 * Domain entity representing a scheduled reminder.
 */
public class Reminder {

    private static final DateTimeFormatter BUCKET_FORMATTER =
            DateTimeFormatter.ofPattern("yyyy-MM-dd-HH").withZone(ZoneId.of("UTC"));

    private final String id;
    private final String userId;
    private final String title;
    private final String message;
    private final long triggerTimeMs;
    private final String timeBucket;
    private volatile ReminderStatus status;

    public Reminder(String userId, String title, String message, long triggerTimeMs) {
        this(UUID.randomUUID().toString(), userId, title, message, triggerTimeMs, ReminderStatus.PENDING);
    }

    public Reminder(String id, String userId, String title, String message, long triggerTimeMs, ReminderStatus status) {
        this.id = Objects.requireNonNull(id, "id cannot be null");
        this.userId = Objects.requireNonNull(userId, "userId cannot be null");
        this.title = title;
        this.message = message;
        this.triggerTimeMs = triggerTimeMs;
        this.timeBucket = calculateTimeBucket(triggerTimeMs);
        this.status = status;
    }

    /**
     * Calculates the time-partition bucket (e.g. "2026-10-29-18" for 18:00 UTC).
     */
    public static String calculateTimeBucket(long timestampMs) {
        return BUCKET_FORMATTER.format(Instant.ofEpochMilli(timestampMs));
    }

    /**
     * Calculates all unique time buckets spanned by a time range [startMs, endMs].
     */
    public static java.util.Set<String> calculateTimeBuckets(long startMs, long endMs) {
        java.util.Set<String> buckets = new java.util.LinkedHashSet<>();
        long current = startMs;
        while (current <= endMs) {
            buckets.add(calculateTimeBucket(current));
            current += 3600_000L;
        }
        buckets.add(calculateTimeBucket(endMs));
        return buckets;
    }

    /**
     * Computes unique idempotency key for notification deduplication.
     */
    public String getIdempotencyKey() {
        return "reminder:" + id + ":" + triggerTimeMs;
    }

    public String getId() {
        return id;
    }

    public String getUserId() {
        return userId;
    }

    public String getTitle() {
        return title;
    }

    public String getMessage() {
        return message;
    }

    public long getTriggerTimeMs() {
        return triggerTimeMs;
    }

    public String getTimeBucket() {
        return timeBucket;
    }

    public ReminderStatus getStatus() {
        return status;
    }

    public void setStatus(ReminderStatus status) {
        this.status = status;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Reminder reminder = (Reminder) o;
        return Objects.equals(id, reminder.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }

    @Override
    public String toString() {
        return "Reminder(id='" + id + "', user='" + userId + "', trigger=" + triggerTimeMs + ", bucket='" + timeBucket + "', status=" + status + ")";
    }
}
