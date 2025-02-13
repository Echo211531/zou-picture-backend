package com.zr.yunbackend.common;

import io.jsonwebtoken.Claims;

public class UserContext {
    private static final ThreadLocal<Claims> currentUser = new ThreadLocal<>();

    public static void setCurrentUser(Claims user) {
        currentUser.set(user);
    }
    public static Claims getCurrentUser() {
        return currentUser.get();
    }
    public static void removeCurrentUser() {
        currentUser.remove();
    }
}