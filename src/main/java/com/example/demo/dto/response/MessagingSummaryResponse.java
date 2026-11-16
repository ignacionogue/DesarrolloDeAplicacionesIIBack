package com.example.demo.dto.response;

public record MessagingSummaryResponse(
        long received,
        long processed,
        long rejected,
        long errors,
        long pendingInbox,
        long published,
        long pendingOutbox,
        long duplicateEventsCurrentRevision,
        long nackedEventsCurrentRevision,
        long publicationFailuresCurrentRevision
) { }
