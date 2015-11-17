package udp;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * @author Paweł Sikora
 */
public class Statistics {
    private volatile long testStartPeriod = System.currentTimeMillis();
    private AtomicInteger handledRequests = new AtomicInteger(0);
    private AtomicInteger allReceived = new AtomicInteger(0);


    public Statistics onSent() {
        long curTime;
        handledRequests.incrementAndGet();
        allReceived.incrementAndGet();
        curTime = System.currentTimeMillis();
        if (curTime - testStartPeriod >= 1000) {
            System.out.println(String.valueOf(curTime - testStartPeriod) + " " + handledRequests.get() + " all received: " + allReceived.get());
            handledRequests.set(0);
            testStartPeriod = curTime;
        }
        return this;
    }
}