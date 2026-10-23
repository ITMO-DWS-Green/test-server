package ru.itmo.testserver.shop;

/** Вызов зависимого сервиса не удался. */
public class DownstreamException extends Exception {

    private final String peer;
    private final int status;
    private final boolean timeout;

    public DownstreamException(String peer, int status, boolean timeout, String message) {
        super(message);
        this.peer = peer;
        this.status = status;
        this.timeout = timeout;
    }

    public String peer() {
        return peer;
    }

    public int status() {
        return status;
    }

    public boolean timeout() {
        return timeout;
    }

    public String describe() {
        if (timeout) {
            return "timed out";
        }
        return status > 0 ? "responded " + status : "unreachable (" + getMessage() + ")";
    }
}
