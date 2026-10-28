package com.example.reminder.model;

/**
 * Event message dispatched to the distributed notification message queue.
 */
public record DispatchEvent(
        String reminderId,
        String userId,
        String title,
        String message,
        long scheduledTriggerMs,
        long actualDispatchMs,
        String idempotencyKey
) {
    public static DispatchEvent fromReminder(Reminder reminder) {
        return new DispatchEvent(
                reminder.getId(),
                reminder.getUserId(),
                reminder.getTitle(),
                reminder.getMessage(),
                reminder.getTriggerTimeMs(),
                System.currentTimeMillis(),
                reminder.getIdempotencyKey()
        );
    }
}
