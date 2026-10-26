package io.github.jichuo1.meguviz.ref.runtime.resolve;

import java.util.*;
import java.util.concurrent.*;
import java.util.function.Supplier;

/** Bounded synchronous single-flight. User work runs outside this monitor; waiters never cancel owners. */
public final class QueryCoordinator<K, V> {
    private final Map<K, Flight<V>> flights = new HashMap<>();
    private final Map<Thread, Thread> waits = new HashMap<>();
    private final int capacity;
    private boolean closed;
    public QueryCoordinator(int capacity) { if (capacity < 1) throw new IllegalArgumentException("capacity"); this.capacity = capacity; }
    public V execute(K key, Supplier<V> work, long timeoutMillis) throws InterruptedException, TimeoutException {
        Flight<V> flight; boolean owner;
        Thread current = Thread.currentThread();
        synchronized (this) {
            if (closed) throw new CancellationException("scope closed");
            flight = flights.get(key); owner = flight == null;
            if (owner) {
                if (flights.size() >= capacity) throw new RejectedExecutionException("single-flight capacity");
                flight = new Flight<>(current); flights.put(key, flight);
            } else {
                Thread dependency = flight.owner;
                Set<Thread> seen = new HashSet<>();
                while (dependency != null && seen.add(dependency)) {
                    if (dependency == current) throw new IllegalStateException("recursive or cyclic query");
                    dependency = waits.get(dependency);
                }
                waits.put(current, flight.owner);
            }
        }
        if (owner) {
            try { V result = work.get(); flight.future.complete(result); return result; }
            catch (Throwable failure) { flight.future.completeExceptionally(failure); throw failure; }
            finally { synchronized (this) { if (flights.get(key) == flight) flights.remove(key); } }
        }
        try { return flight.future.get(timeoutMillis, TimeUnit.MILLISECONDS); }
        catch (ExecutionException failure) {
            Throwable cause = failure.getCause();
            if (cause instanceof RuntimeException) throw (RuntimeException) cause;
            if (cause instanceof Error) throw (Error) cause;
            throw new IllegalStateException(cause);
        } finally { synchronized (this) { waits.remove(current); } }
    }
    public synchronized void close() {
        closed = true;
        for (Flight<V> flight : flights.values()) flight.future.completeExceptionally(new CancellationException("scope closed"));
        flights.clear(); waits.clear();
    }
    private static final class Flight<V> {
        final Thread owner;
        final CompletableFuture<V> future = new CompletableFuture<>();
        Flight(Thread owner) { this.owner = owner; }
    }
}
