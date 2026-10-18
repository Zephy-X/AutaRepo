package cz.vsb.autarepo.autorepo.common.exception;

public final class DomainErrorMessages {
    private DomainErrorMessages() {
    }

    public static String resolve(String errorCode) {
        return switch (errorCode) {
            case "CONFLICT_OVERLAP", "Vehicle conflict: another reservation is active for this time slot.", "Vehicle conflict: another reservation was confirmed for this time slot." ->
                    "Vehicle conflict: another reservation was confirmed or is awaiting approval for this time slot.";
            case "APPROVAL_WINDOW_EXPIRED" -> "Approval window expired: reservation start time has already passed.";
            case "CANCELLATION_WINDOW_CLOSED" -> "Cancellation window closed: reservation has already started.";
            case "ALREADY_CANCELLED" -> "Reservation is already cancelled.";
            case "RESOURCE_INACTIVE" -> "Vehicle is inactive and cannot be reserved.";
            case "INVALID_STATE_TRANSITION" -> "Invalid state transition for this operation.";
            case "INVALID_INTERVAL" -> "Invalid interval. Start time must be before end time.";
            case "INSUFFICIENT_LEAD_TIME" -> "Start time must be at least two hours from now.";
            case "MAX_DURATION_EXCEEDED" -> "Reservation duration cannot exceed 14 days.";
            case "START_MUST_BE_FUTURE" -> "Reservation start time must be in the future.";
            default -> errorCode;
        };
    }
}
