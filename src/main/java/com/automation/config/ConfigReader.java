package com.automation.config;

import com.automation.constants.AppConstants;
import com.automation.exceptions.ConfigException;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

/**
 * Singleton configuration reader.
 *
 * Loading strategy (last-write-wins, so env-specific overrides base):
 *   1. Load config.properties (base defaults)
 *   2. Load {env}.properties   (environment-specific overrides)
 *   3. Apply JVM -D system properties (runtime overrides — highest priority)
 *
 * Usage: ConfigReader.get().getProperty("base.url")
 */
public final class ConfigReader {

    private static final Logger log = LogManager.getLogger(ConfigReader.class);

    // Volatile + double-checked locking — safe for concurrent test thread init
    private static volatile ConfigReader instance;
    private final Properties properties = new Properties();

    private ConfigReader() {
        loadBaseConfig();
        loadEnvironmentConfig();
        applySystemPropertyOverrides();
    }

    public static ConfigReader get() {
        if (instance == null) {
            synchronized (ConfigReader.class) {
                if (instance == null) {
                    instance = new ConfigReader();
                }
            }
        }
        return instance;
    }

    // ─── Public accessors ────────────────────────────────────────────────────

    /**
     * Returns the property value or throws ConfigException if key is absent.
     */
    public String getProperty(String key) {
        String value = properties.getProperty(key);
        if (value == null || value.isBlank()) {
            throw new ConfigException("Required property '" + key + "' is not defined in any config file.");
        }
        return value.trim();
    }

    /**
     * Returns the property value or the supplied default when key is absent.
     */
    public String getProperty(String key, String defaultValue) {
        return properties.getProperty(key, defaultValue).trim();
    }

    public boolean getBoolean(String key, boolean defaultValue) {
        String raw = properties.getProperty(key);
        return raw != null ? Boolean.parseBoolean(raw.trim()) : defaultValue;
    }

    public int getInt(String key, int defaultValue) {
        String raw = properties.getProperty(key);
        if (raw == null) return defaultValue;
        try {
            return Integer.parseInt(raw.trim());
        } catch (NumberFormatException e) {
            log.warn("Property '{}' value '{}' is not a valid integer; using default {}", key, raw, defaultValue);
            return defaultValue;
        }
    }

    public String getBaseUrl() {
        return getProperty("base.url");
    }

    public String getBrowser() {
        return getProperty(AppConstants.PROP_BROWSER, AppConstants.BROWSER_CHROMIUM);
    }

    public boolean isHeadless() {
        return getBoolean(AppConstants.PROP_HEADLESS, true);
    }

    public String getEnvironment() {
        return getProperty(AppConstants.PROP_ENV, AppConstants.ENV_QA);
    }

    // ─── Private loading helpers ─────────────────────────────────────────────

    private void loadBaseConfig() {
        loadFromClasspath(AppConstants.CONFIG_FILE_PATH, true);
    }

    private void loadEnvironmentConfig() {
        String env = System.getProperty(AppConstants.PROP_ENV,
                properties.getProperty(AppConstants.PROP_ENV, AppConstants.ENV_QA)).toLowerCase();
        log.info("Active environment: {}", env);

        String envConfigPath = switch (env) {
            case AppConstants.ENV_UAT  -> AppConstants.UAT_CONFIG_PATH;
            case AppConstants.ENV_PROD -> AppConstants.PROD_CONFIG_PATH;
            default                    -> AppConstants.QA_CONFIG_PATH;
        };

        loadFromClasspath(envConfigPath, false);
    }

    /**
     * Merges all JVM -D system properties whose keys exist in our loaded
     * properties — lets CI/CD pipelines override individual values without
     * editing files.
     */
    private void applySystemPropertyOverrides() {
        System.getProperties().forEach((k, v) -> {
            String key = k.toString();
            // Only override known config keys to avoid polluting our config
            if (properties.containsKey(key) || isFrameworkKey(key)) {
                log.debug("System-property override: {} = {}", key, v);
                properties.setProperty(key, v.toString());
            }
        });
    }

    private boolean isFrameworkKey(String key) {
        return key.equals(AppConstants.PROP_ENV)
                || key.equals(AppConstants.PROP_BROWSER)
                || key.equals(AppConstants.PROP_HEADLESS);
    }

    private void loadFromClasspath(String path, boolean required) {
        try (InputStream is = getClass().getClassLoader().getResourceAsStream(path)) {
            if (is == null) {
                if (required) {
                    throw new ConfigException("Required config file not found on classpath: " + path);
                }
                log.warn("Optional config file not found: {}; skipping.", path);
                return;
            }
            properties.load(is);
            log.info("Loaded config from: {}", path);
        } catch (IOException e) {
            throw new ConfigException("Failed to read config file: " + path, e);
        }
    }
}
