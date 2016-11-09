package me.ccampo.backoff;

import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;

import java.util.Objects;
import java.util.concurrent.Callable;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Consumer;
import java.util.function.Predicate;

/**
 * Used to execute a task with an exponential backoff retry policy. Upon
 * failure (an exception is thrown from the task), the task will retry up to a
 * specified max number of attempts. The task will wait (back off) a period
 * of time before the next attempt, with this time period exponentially
 * increasing for each successive retry.
 * <p>
 * If the task is ever successful, it will return immediately and break the
 * back off loop. The output of the task is captured in the "data" field in the
 * result object {@link BackOffResult}. The type of this field is determined by
 * the return type of the task executed.
 * <p>
 * Randomness (a.k.a jitter) can also be introduced into the wait time, to help
 * with optimization of competing clients and improve overall efficiency
 * (see: <a href=https://www.awsarchitectureblog.com/2015/03/backoff.html>
 * https://www.awsarchitectureblog.com/2015/03/backoff.html</a>).
 * <p>
 * The recommended approach is to use the builder class to build an
 * {@link ExponentialBackOff} object, configuring all of the available fields,
 * and supplying your task. The back off can then be executed with the
 * {@link ExponentialBackOff#execute()} method, and the result can be
 * collected.
 * <p>
 * Note that all times and durations are assumed to be in milliseconds.
 *
 * @param <T> The return type of the task executed with exponential backoff.
 */
public class ExponentialBackOff<T> {

    protected static final int DEFAULT_MAX_ATTEMPTS = 10;
    protected static final long DEFAULT_WAIT_CAP_MILLIS = 60000;
    protected static final long DEFAULT_WAIT_BASE_MILLIS = 100;

    private final long cap;
    private final long base;
    private final int maxAttempts;
    private final boolean infinite;
    private final boolean jitter;
    private final Callable<T> task;
    private final Consumer<Exception> exceptionHandler;
    private final Predicate<T> retryIf;

    public ExponentialBackOff(final long cap,
            final long base,
            final int maxAttempts,
            final boolean jitter,
            final boolean infinite,
            @NotNull final Callable<T> task,
            @NotNull final Consumer<Exception> exceptionHandler,
            @NotNull final Predicate<T> retryIf) {
        this.cap = cap;
        this.base = base;
        this.maxAttempts = maxAttempts;
        this.jitter = jitter;
        this.infinite = infinite;
        this.task = Objects.requireNonNull(task);
        this.exceptionHandler = Objects.requireNonNull(exceptionHandler);
        this.retryIf = Objects.requireNonNull(retryIf);
    }

    @NotNull
    public static <T> Builder<T> builder() {
        return new Builder<>();
    }

    /**
     * Executes the given task, with exponential backoff (configured via the builder).
     */
    @NotNull
    public BackOffResult<T> execute() {
        if (infinite) {
            return execute(attempt -> true, 0);
        }
        return execute(attempt -> attempt < maxAttempts, 0);
    }

    // Protected so this can be used in testing
    @NotNull
    protected BackOffResult<T> execute(@NotNull final Predicate<Long> predicate, final long attempt) {
        long curAttempt = attempt;
        do {
            try {
                final T result = task.call();
                if (retryIf.test(result)) {
                    throw new Exception("Forced retry");
                }
                return new BackOffResult<>(result, BackOffResultStatus.SUCCESSFUL);
            } catch (final Exception e) {
                exceptionHandler.accept(e);
                doWait(attempt);
            }
        } while (predicate.test(curAttempt++));
        return new BackOffResult<>(BackOffResultStatus.EXCEEDED_MAX_ATTEMPTS);
    }

    private void doWait(final long attempt) {
        try {
            final long waitTime = jitter ? getWaitTimeWithJitter(cap, base, attempt) : getWaitTime(cap, base, attempt);
            Thread.sleep(waitTime);
        } catch (final InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    @Contract(pure = true)
    protected static long getWaitTime(final long cap, final long base, final long n) {
        // Simple check for overflows
        final long expWait = ((long) Math.pow(2, n)) * base;
        return expWait <= 0 ? cap : Math.min(cap, expWait);
    }

    @Contract(pure = true)
    protected static long getWaitTimeWithJitter(final long cap, final long base, final long n) {
        return ThreadLocalRandom.current().nextLong(0, getWaitTime(cap, base, n));
    }

    public static final class Builder<T> {
        private long cap = DEFAULT_WAIT_CAP_MILLIS;
        private long base = DEFAULT_WAIT_BASE_MILLIS;
        private int maxAttempts = DEFAULT_MAX_ATTEMPTS;
        private boolean infinite = false;
        private boolean jitter = false;
        private Callable<T> task = () -> null;
        private Consumer<Exception> exceptionHandler = e -> {
        };
        private Predicate<T> retryIf = t -> false;


        private Builder() {
        }

        @NotNull
        public ExponentialBackOff<T> build() {
            return new ExponentialBackOff<>(cap, base, maxAttempts, jitter, infinite, task, exceptionHandler, retryIf);
        }

        /**
         * Executes the {@link ExponentialBackOff} produced by this builder.
         *
         * @return A {@link BackOffResult} containing the return data of
         * the task and the status.
         */
        @NotNull
        public BackOffResult<T> execute() {
            return build().execute();
        }

        /**
         * The max wait time, in milliseconds, of the back off.
         */
        @NotNull
        public Builder<T> withCap(final long cap) {
            this.cap = cap;
            return this;
        }

        /**
         * The base wait time, in milliseconds, of the back off.
         */
        @NotNull
        public Builder<T> withBase(final long base) {
            this.base = base;
            return this;
        }

        /**
         * The maximum number of attempts performed in the back off.
         */
        @NotNull
        public Builder<T> withMaxAttempts(final int maxAttempts) {
            this.maxAttempts = maxAttempts;
            return this;
        }

        /**
         * Call to enable infinite retry attempts until there is a successful
         * response.
         */
        @NotNull
        public Builder<T> withInfiniteAttempts() {
            this.infinite = true;
            return this;
        }

        /**
         * Enable to introduce randomness (jitter) to the back off wait time.
         */
        @NotNull
        public Builder<T> withJitter() {
            this.jitter = true;
            return this;
        }

        /**
         * The task to perform, which will be retried with backoff if it
         * encounters any unhandled exceptions.
         */
        @NotNull
        public Builder<T> withTask(@NotNull final Callable<T> task) {
            this.task = Objects.requireNonNull(task);
            return this;
        }

        /**
         * A function that can be used to introduce custom exception handling
         * logic. The task will still be retried after the exception is handled,
         * however this gives you an place to do various things like logging the
         * exception, updating your application state, etc.
         */
        @NotNull
        public Builder<T> withExceptionHandler(@NotNull final Consumer<Exception> exceptionHandler) {
            this.exceptionHandler = Objects.requireNonNull(exceptionHandler);
            return this;
        }

        /**
         * A function that can be used to determine whether or not the main task
         * should be retried, even if it did not throw an exception. The input
         * argument to this function is the return value (of type {@link T}) of
         * the previous attempt.
         */
        @NotNull
        public Builder<T> retryIf(@NotNull final Predicate<T> retryIf) {
            this.retryIf = Objects.requireNonNull(retryIf);
            return this;
        }
    }
}
