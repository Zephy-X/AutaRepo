package cz.vsb.autarepo.autorepo.common.exception;

public class CancellationWindowClosedException extends RuntimeException {
    public CancellationWindowClosedException(String message) {
        super(message);
    }
}
