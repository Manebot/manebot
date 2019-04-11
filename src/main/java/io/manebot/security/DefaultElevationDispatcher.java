package io.manebot.security;

import io.manebot.lambda.ThrowingCallable;
import io.manebot.lambda.ThrowingFunction;
import io.manebot.lambda.ThrowingRunnable;
import io.manebot.user.User;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;

public class DefaultElevationDispatcher implements ElevationDispatcher {
    private final User user;
    private final ExecutorService executorService;

    public DefaultElevationDispatcher(User user, ExecutorService executorService) {
        this.user = user;
        this.executorService = executorService;
    }

    @Override
    public User getElevatedUser() {
        return user;
    }

    @SuppressWarnings("unchecked")
    @Override
    public <R> R elevate(ThrowingCallable<R, Exception> callable) throws Exception {
        final Object[] result = new Object[1];
        elevate(() -> {
            result[0] = callable.call();
        });
        return (R) result[0];
    }

    @SuppressWarnings("unchecked")
    @Override
    public <T, R> R elevate(T object, ThrowingFunction<T, R, Exception> function) throws Exception {
        final Object[] result = new Object[1];
        elevate(() -> {
            result[0] = function.applyChecked(object);
        });
        return (R) result[0];
    }

    @Override
    public void elevate(ThrowingRunnable<Exception> runnable) throws Exception {
        try {
            CompletableFuture<?> completableFuture = new CompletableFuture<>();

            executorService.submit(() -> {
                try {
                    runnable.runChecked();
                    completableFuture.complete(null);
                } catch (Throwable ex) {
                    completableFuture.completeExceptionally(ex);
                }
            });

            completableFuture.get();
        } catch (ExecutionException ex) {
            if (ex.getCause().getClass().isAssignableFrom(Exception.class))
                throw (Exception) ex.getCause();

            throw ex;
        } catch (Throwable ex) {
            throw new RuntimeException(ex);
        }
    }
}
