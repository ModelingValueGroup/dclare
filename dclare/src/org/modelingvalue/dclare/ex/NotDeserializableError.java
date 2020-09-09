package org.modelingvalue.dclare.ex;

@SuppressWarnings("unused")
public class NotDeserializableError extends RuntimeException {
    public NotDeserializableError() {
    }

    public NotDeserializableError(String message) {
        super(message);
    }

    public NotDeserializableError(Throwable cause) {
        super(cause);
    }

    public NotDeserializableError(String message, Throwable cause) {
        super(message, cause);
    }
}
