import java.util.concurrent.atomic.AtomicInteger;

/**
 * class represents a Lamport clock
 */
class LamportClock {
    private final AtomicInteger time = new AtomicInteger(0);

    /**
     * Increase the local clock value when send an event
     */
    public void tick() {
        time.incrementAndGet();
    }

    /**
     * When receiving an event, update the local clock value to a larger value
     *
     * @param sentTime int Lamport clock from other node
     */
    public void updateTime(int sentTime) {
        int localTime = time.get();
        int newTime = Math.max(localTime, sentTime);
        time.set(newTime);
    }

    /**
     * get current time
     *
     * @return int
     */
    public int getTime() {
        return time.get();
    }
}
