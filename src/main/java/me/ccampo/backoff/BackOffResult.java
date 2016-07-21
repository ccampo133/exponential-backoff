package me.ccampo.backoff;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;
import java.util.Optional;

public class BackOffResult<T> {

    public final Optional<T> data;
    public final BackOffResultStatus status;

    private BackOffResult(@NotNull final Optional<T> data, @NotNull final BackOffResultStatus status) {
        this.data = Objects.requireNonNull(data);
        this.status = Objects.requireNonNull(status);
    }

    public BackOffResult(@Nullable final T data, @NotNull final BackOffResultStatus status) {
        this(Optional.ofNullable(data), status);
    }

    public BackOffResult(@NotNull final BackOffResultStatus status) {
        this(Optional.empty(), status);
    }
}
