package cz.vsb.autarepo.autorepo.common.exception;

public class ApprovalWindowExpiredException extends RuntimeException {
    public ApprovalWindowExpiredException(String message) {
        super(message);
    }
}
