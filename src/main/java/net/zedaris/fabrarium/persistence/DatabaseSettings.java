package net.zedaris.fabrarium.persistence;

public record DatabaseSettings(String url, String username, String password, String driverClass) {
    public DatabaseSettings {
        if (url == null || url.isBlank()) {
            throw new IllegalArgumentException("database url is required");
        }
        username = username == null ? "" : username;
        password = password == null ? "" : password;
        driverClass = driverClass == null ? "" : driverClass;
    }
}
