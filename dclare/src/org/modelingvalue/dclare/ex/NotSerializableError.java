package org.modelingvalue.dclare.ex;

@SuppressWarnings("unused")
public class NotSerializableError extends RuntimeException {
    public NotSerializableError() {
    }

    public NotSerializableError(String message) {
        super(message);
    }

    public NotSerializableError(String message, Throwable cause) {
        super(message, cause);
    }
}
