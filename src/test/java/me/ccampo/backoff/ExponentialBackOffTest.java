package me.ccampo.backoff;

import org.junit.Test;

import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

public class ExponentialBackOffTest {

    @Test
    public void testThatMaxAttemptsAreExceeded() {
        final BackOffResult<Long> result = ExponentialBackOff.<Long>builder()
                .withMaxAttempts(3)
                .withBase(1)
                .withTask(() -> {
                    throw new RuntimeException("Fake exception");
                })
                .withExceptionHandler(Throwable::printStackTrace)
                .execute();

        assertThat(result.status).isEqualTo(BackOffResultStatus.EXCEEDED_MAX_ATTEMPTS);
        assertThat(result.data).isEmpty();
    }

    @Test
    public void testSuccessfulExecution() {
        final BackOffResult<String> result = ExponentialBackOff.<String>builder()
                .withBase(100)
                .withCap(5000)
                .withMaxAttempts(5)
                .withJitter()
                .withTask(() -> "Do something")
                .withExceptionHandler(Throwable::printStackTrace)
                .execute();

        assertThat(result.status).isEqualTo(BackOffResultStatus.SUCCESSFUL);
        assertThat(result.data).hasValue("Do something");
    }

    @Test
    public void successfulAfterThreeAttempts() {
        final AtomicInteger attempts = new AtomicInteger(0);
        final AtomicInteger exceptions = new AtomicInteger(0);
        final BackOffResult<String> result = ExponentialBackOff.<String>builder()
                .withBase(1)
                .withCap(5000)
                .withMaxAttempts(5)
                .withTask(() -> {
                    attempts.incrementAndGet();
                    final boolean shouldThrow = attempts.get() < 3;
                    if (shouldThrow) {
                        System.out.println("Throwing exception");
                        throw new RuntimeException("Fake exception");
                    }
                    return "Fake result";
                })
                .withExceptionHandler(e -> exceptions.incrementAndGet())
                .execute();

        assertThat(attempts.get()).isEqualTo(3);
        assertThat(exceptions.get()).isEqualTo(2);
        assertThat(result.status).isEqualTo(BackOffResultStatus.SUCCESSFUL);
        assertThat(result.data).hasValue("Fake result");
    }

    @Test
    public void testInfiniteAttempts() {
        final AtomicInteger attempts = new AtomicInteger(0);
        final AtomicInteger exceptions = new AtomicInteger(0);
        final BackOffResult<String> result = ExponentialBackOff.<String>builder()
                .withBase(1)
                .withCap(10)
                .withInfiniteAttemps()
                .withTask(() -> {
                    attempts.incrementAndGet();
                    // Just to ensure that we exceed the default max attempts
                    final boolean shouldThrow = attempts.get() < ExponentialBackOff.DEFAULT_MAX_ATTEMPTS + 1;
                    if (shouldThrow) {
                        System.out.println("Throwing exception");
                        throw new RuntimeException("Fake exception");
                    }
                    return "Fake result";
                })
                .withExceptionHandler(e -> exceptions.incrementAndGet())
                .execute();
        assertThat(attempts.get()).isEqualTo(ExponentialBackOff.DEFAULT_MAX_ATTEMPTS + 1);
        assertThat(exceptions.get()).isEqualTo(ExponentialBackOff.DEFAULT_MAX_ATTEMPTS);
        assertThat(result.status).isEqualTo(BackOffResultStatus.SUCCESSFUL);
        assertThat(result.data).hasValue("Fake result");
    }
}
