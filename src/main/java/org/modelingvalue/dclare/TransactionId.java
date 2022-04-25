package org.modelingvalue.dclare;

public class TransactionId {

    public static final TransactionId of(long number) {
        return new TransactionId(null, number);
    }

    public static final TransactionId of(TransactionId superTransactionId, long number) {
        return new TransactionId(superTransactionId, number);
    }

    private final TransactionId superTransactionId;
    private final long          number;

    private TransactionId(TransactionId superTransactionId, long number) {
        this.superTransactionId = superTransactionId;
        this.number = number;
    }

    public TransactionId superTransactionId() {
        return superTransactionId;
    }

    public boolean isSuper() {
        return superTransactionId == null;
    }

    public boolean isSub() {
        return superTransactionId != null;
    }

    public long number() {
        return number;
    }

    @Override
    public String toString() {
        return (isSub() ? Long.toString(superTransactionId.number) + "." : "") + Long.toString(number);
    }

}
