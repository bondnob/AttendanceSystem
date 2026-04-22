package com.attendance.auth.security;

public final class UserContext {

    private static final ThreadLocal<CurrentUser> HOLDER = new ThreadLocal<>();

    private UserContext() {
    }

    public static void set(CurrentUser currentUser) {
        HOLDER.set(currentUser);
    }

    public static CurrentUser get() {
        return HOLDER.get();
    }

    public static Long getUserId() {
        CurrentUser currentUser = HOLDER.get();
        return currentUser == null ? null : currentUser.getUserId();
    }

    public static void clear() {
        HOLDER.remove();
    }
}