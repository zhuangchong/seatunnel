/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.github.interestinglab.waterdrop.config.impl;

import io.github.interestinglab.waterdrop.config.Config;
import io.github.interestinglab.waterdrop.config.ConfigException;
import io.github.interestinglab.waterdrop.config.ConfigIncluder;
import io.github.interestinglab.waterdrop.config.ConfigMemorySize;
import io.github.interestinglab.waterdrop.config.ConfigObject;
import io.github.interestinglab.waterdrop.config.ConfigOrigin;
import io.github.interestinglab.waterdrop.config.ConfigParseOptions;
import io.github.interestinglab.waterdrop.config.ConfigParseable;
import io.github.interestinglab.waterdrop.config.ConfigValue;
import io.github.interestinglab.waterdrop.config.impl.SimpleIncluder.NameSource;

import java.io.File;
import java.lang.ref.WeakReference;
import java.net.URL;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.Callable;

/**
 * Internal implementation detail, not ABI stable, do not touch.
 * For use only by the {@link io.github.interestinglab.waterdrop.config} package.
 */
public class ConfigImpl {

    private static class LoaderCache {
        private Config currentSystemProperties;
        private WeakReference<ClassLoader> currentLoader;
        private Map<String, Config> cache;

        LoaderCache() {
            this.currentSystemProperties = null;
            this.currentLoader = new WeakReference<ClassLoader>(null);
            this.cache = new HashMap<String, Config>();
        }

        // for now, caching as long as the loader remains the same,
        // drop entire cache if it changes.
        synchronized Config getOrElseUpdate(ClassLoader loader, String key, Callable<Config> updater) {
            if (loader != currentLoader.get()) {
                // reset the cache if we start using a different loader
                cache.clear();
                currentLoader = new WeakReference<ClassLoader>(loader);
            }

            Config systemProperties = systemPropertiesAsConfig();
            if (systemProperties != currentSystemProperties) {
                cache.clear();
                currentSystemProperties = systemProperties;
            }

            Config config = cache.get(key);
            if (config == null) {
                try {
                    config = updater.call();
                } catch (RuntimeException e) {
                    throw e; // this will include ConfigException
                } catch (Exception e) {
                    throw new ConfigException.Generic(e.getMessage(), e);
                }
                if (config == null)
                    throw new ConfigException.BugOrBroken("null config from cache updater");
                cache.put(key, config);
            }

            return config;
        }
    }

    private static class LOADER_CACHE_HOLDER {
        static final LoaderCache CACHE = new LoaderCache();
    }

    public static Config computeCachedConfig(ClassLoader loader, String key,
                                             Callable<Config> updater) {
        LoaderCache cache;
        try {
            cache = LOADER_CACHE_HOLDER.CACHE;
        } catch (ExceptionInInitializerError e) {
            throw ConfigImplUtil.extractInitializerError(e);
        }
        return cache.getOrElseUpdate(loader, key, updater);
    }

    static class FileNameSource implements SimpleIncluder.NameSource {
        @Override
        public ConfigParseable nameToParseable(String name, ConfigParseOptions parseOptions) {
            return Parseable.newFile(new File(name), parseOptions);
        }
    }

    static class ClasspathNameSource implements SimpleIncluder.NameSource {
        @Override
        public ConfigParseable nameToParseable(String name, ConfigParseOptions parseOptions) {
            return Parseable.newResources(name, parseOptions);
        }
    }

    static class ClasspathNameSourceWithClass implements SimpleIncluder.NameSource {
        final private Class<?> klass;

        public ClasspathNameSourceWithClass(Class<?> klass) {
            this.klass = klass;
        }

        @Override
        public ConfigParseable nameToParseable(String name, ConfigParseOptions parseOptions) {
            return Parseable.newResources(klass, name, parseOptions);
        }
    }

    public static ConfigObject parseResourcesAnySyntax(Class<?> klass, String resourceBasename,
                                                       ConfigParseOptions baseOptions) {
        NameSource source = new ClasspathNameSourceWithClass(klass);
        return SimpleIncluder.fromBasename(source, resourceBasename, baseOptions);
    }

    public static ConfigObject parseResourcesAnySyntax(String resourceBasename,
                                                       ConfigParseOptions baseOptions) {
        NameSource source = new ClasspathNameSource();
        return SimpleIncluder.fromBasename(source, resourceBasename, baseOptions);
    }

    public static ConfigObject parseFileAnySyntax(File basename, ConfigParseOptions baseOptions) {
        NameSource source = new FileNameSource();
        return SimpleIncluder.fromBasename(source, basename.getPath(), baseOptions);
    }

    static AbstractConfigObject emptyObject(String originDescription) {
        ConfigOrigin origin = originDescription != null ? SimpleConfigOrigin
                .newSimple(originDescription) : null;
        return emptyObject(origin);
    }

    public static Config emptyConfig(String originDescription) {
        return emptyObject(originDescription).toConfig();
    }

    static AbstractConfigObject empty(ConfigOrigin origin) {
        return emptyObject(origin);
    }

    // default origin for values created with fromAnyRef and no origin specified
    final private static ConfigOrigin DEFAULT_VALUE_ORIGIN = SimpleConfigOrigin
            .newSimple("hardcoded value");
    final private static ConfigBoolean DEFAULT_TRUE_VALUE = new ConfigBoolean(
            DEFAULT_VALUE_ORIGIN, true);
    final private static ConfigBoolean DEFAULT_FALSE_VALUE = new ConfigBoolean(
            DEFAULT_VALUE_ORIGIN, false);
    final private static ConfigNull DEFAULT_NULL_VALUE = new ConfigNull(
            DEFAULT_VALUE_ORIGIN);
    final private static SimpleConfigList DEFAULT_EMPTY_LIST = new SimpleConfigList(
            DEFAULT_VALUE_ORIGIN, Collections.<AbstractConfigValue>emptyList());
    final private static SimpleConfigObject DEFAULT_EMPTY_OBJECT = SimpleConfigObject
            .empty(DEFAULT_VALUE_ORIGIN);

    private static SimpleConfigList emptyList(ConfigOrigin origin) {
        if (origin == null || origin == DEFAULT_VALUE_ORIGIN)
            return DEFAULT_EMPTY_LIST;
        else
            return new SimpleConfigList(origin,
                    Collections.<AbstractConfigValue>emptyList());
    }

    private static AbstractConfigObject emptyObject(ConfigOrigin origin) {
        // we want null origin to go to SimpleConfigObject.empty() to get the
        // origin "empty config" rather than "hardcoded value"
        if (origin == DEFAULT_VALUE_ORIGIN)
            return DEFAULT_EMPTY_OBJECT;
        else
            return SimpleConfigObject.empty(origin);
    }

    private static ConfigOrigin valueOrigin(String originDescription) {
        if (originDescription == null)
            return DEFAULT_VALUE_ORIGIN;
        else
            return SimpleConfigOrigin.newSimple(originDescription);
    }

    public static ConfigValue fromAnyRef(Object object, String originDescription) {
        ConfigOrigin origin = valueOrigin(originDescription);
        return fromAnyRef(object, origin, FromMapMode.KEYS_ARE_KEYS);
    }

    public static ConfigObject fromPathMap(
            Map<String, ? extends Object> pathMap, String originDescription) {
        ConfigOrigin origin = valueOrigin(originDescription);
        return (ConfigObject) fromAnyRef(pathMap, origin,
                FromMapMode.KEYS_ARE_PATHS);
    }

    static AbstractConfigValue fromAnyRef(Object object, ConfigOrigin origin,
                                          FromMapMode mapMode) {
        if (origin == null)
            throw new ConfigException.BugOrBroken(
                    "origin not supposed to be null");

        if (object == null) {
            if (origin != DEFAULT_VALUE_ORIGIN)
                return new ConfigNull(origin);
            else
                return DEFAULT_NULL_VALUE;
        } else if (object instanceof AbstractConfigValue) {
            return (AbstractConfigValue) object;
        } else if (object instanceof Boolean) {
            if (origin != DEFAULT_VALUE_ORIGIN) {
                return new ConfigBoolean(origin, (Boolean) object);
            } else if ((Boolean) object) {
                return DEFAULT_TRUE_VALUE;
            } else {
                return DEFAULT_FALSE_VALUE;
            }
        } else if (object instanceof String) {
            return new ConfigString.Quoted(origin, (String) object);
        } else if (object instanceof Number) {
            // here we always keep the same type that was passed to us,
            // rather than figuring out if a Long would fit in an Int
            // or a Double has no fractional part. i.e. deliberately
            // not using ConfigNumber.newNumber() when we have a
            // Double, Integer, or Long.
            if (object instanceof Double) {
                return new ConfigDouble(origin, (Double) object, null);
            } else if (object instanceof Integer) {
                return new ConfigInt(origin, (Integer) object, null);
            } else if (object instanceof Long) {
                return new ConfigLong(origin, (Long) object, null);
            } else {
                return ConfigNumber.newNumber(origin,
                        ((Number) object).doubleValue(), null);
            }
        } else if (object instanceof Duration) {
            return new ConfigLong(origin, ((Duration) object).toMillis(), null);
        } else if (object instanceof Map) {
            if (((Map<?, ?>) object).isEmpty())
                return emptyObject(origin);

            if (mapMode == FromMapMode.KEYS_ARE_KEYS) {
                Map<String, AbstractConfigValue> values = new LinkedHashMap<String, AbstractConfigValue>();
                for (Map.Entry<?, ?> entry : ((Map<?, ?>) object).entrySet()) {
                    Object key = entry.getKey();
                    if (!(key instanceof String))
                        throw new ConfigException.BugOrBroken(
                                "bug in method caller: not valid to create ConfigObject from map with non-String key: "
                                        + key);
                    AbstractConfigValue value = fromAnyRef(entry.getValue(),
                            origin, mapMode);
                    values.put((String) key, value);
                }

                return new SimpleConfigObject(origin, values);
            } else {
                return PropertiesParser.fromPathMap(origin, (Map<?, ?>) object);
            }
        } else if (object instanceof Iterable) {
            Iterator<?> i = ((Iterable<?>) object).iterator();
            if (!i.hasNext())
                return emptyList(origin);

            List<AbstractConfigValue> values = new ArrayList<AbstractConfigValue>();
            while (i.hasNext()) {
                AbstractConfigValue v = fromAnyRef(i.next(), origin, mapMode);
                values.add(v);
            }

            return new SimpleConfigList(origin, values);
        } else if (object instanceof ConfigMemorySize) {
            return new ConfigLong(origin, ((ConfigMemorySize) object).toBytes(), null);
        } else {
            throw new ConfigException.BugOrBroken(
                    "bug in method caller: not valid to create ConfigValue from: "
                            + object);
        }
    }

    private static class DEFAULT_INCLUDER_HOLDER {
        static final ConfigIncluder DEFAULT_INCLUDER = new SimpleIncluder(null);
    }

    static ConfigIncluder DEFAULT_INCLUDER() {
        try {
            return DEFAULT_INCLUDER_HOLDER.DEFAULT_INCLUDER;
        } catch (ExceptionInInitializerError e) {
            throw ConfigImplUtil.extractInitializerError(e);
        }
    }

    private static Properties getSystemProperties() {
        // Avoid ConcurrentModificationException due to parallel setting of system properties by copying properties
        final Properties systemProperties = System.getProperties();
        final Properties systemPropertiesCopy = new Properties();
        synchronized (systemProperties) {
            systemPropertiesCopy.putAll(systemProperties);
        }
        return systemPropertiesCopy;
    }

    private static AbstractConfigObject loadSystemProperties() {
        return (AbstractConfigObject) Parseable.newProperties(getSystemProperties(),
                ConfigParseOptions.defaults().setOriginDescription("system properties")).parse();
    }

    private static class SystemPropertiesHolder {
        // this isn't final due to the reloadSystemPropertiesConfig() hack below
        static volatile AbstractConfigObject SYSTEM_PROPERTIES = loadSystemProperties();
    }

    static AbstractConfigObject systemPropertiesAsConfigObject() {
        try {
            return SystemPropertiesHolder.SYSTEM_PROPERTIES;
        } catch (ExceptionInInitializerError e) {
            throw ConfigImplUtil.extractInitializerError(e);
        }
    }

    public static Config systemPropertiesAsConfig() {
        return systemPropertiesAsConfigObject().toConfig();
    }

    public static void reloadSystemPropertiesConfig() {
        // ConfigFactory.invalidateCaches() relies on this having the side
        // effect that it drops all caches
        SystemPropertiesHolder.SYSTEM_PROPERTIES = loadSystemProperties();
    }

    private static AbstractConfigObject loadEnvVariables() {
        return PropertiesParser.fromStringMap(newSimpleOrigin("env variables"), System.getenv());
    }

    private static class EnvVariablesHolder {
        static volatile AbstractConfigObject ENV_VARIABLES = loadEnvVariables();
    }

    static AbstractConfigObject envVariablesAsConfigObject() {
        try {
            return EnvVariablesHolder.ENV_VARIABLES;
        } catch (ExceptionInInitializerError e) {
            throw ConfigImplUtil.extractInitializerError(e);
        }
    }

    public static Config envVariablesAsConfig() {
        return envVariablesAsConfigObject().toConfig();
    }

    public static void reloadEnvVariablesConfig() {
        // ConfigFactory.invalidateCaches() relies on this having the side
        // effect that it drops all caches
        EnvVariablesHolder.ENV_VARIABLES = loadEnvVariables();
    }

    public static Config defaultReference(final ClassLoader loader) {
        return computeCachedConfig(loader, "defaultReference", new Callable<Config>() {
            @Override
            public Config call() {
                Config unresolvedResources = Parseable
                        .newResources("reference.conf",
                                ConfigParseOptions.defaults().setClassLoader(loader))
                        .parse().toConfig();
                return systemPropertiesAsConfig().withFallback(unresolvedResources).resolve();
            }
        });
    }

    private static class DebugHolder {
        private static String LOADS = "loads";
        private static String SUBSTITUTIONS = "substitutions";

        private static Map<String, Boolean> loadDiagnostics() {
            Map<String, Boolean> result = new HashMap<String, Boolean>();
            result.put(LOADS, false);
            result.put(SUBSTITUTIONS, false);

            // People do -Dconfig.trace=foo,bar to enable tracing of different things
            String s = System.getProperty("config.trace");
            if (s == null) {
                return result;
            } else {
                String[] keys = s.split(",");
                for (String k : keys) {
                    if (k.equals(LOADS)) {
                        result.put(LOADS, true);
                    } else if (k.equals(SUBSTITUTIONS)) {
                        result.put(SUBSTITUTIONS, true);
                    } else {
                        System.err.println("config.trace property contains unknown trace topic '"
                                + k + "'");
                    }
                }
                return result;
            }
        }

        private static final Map<String, Boolean> DIAGNOSTICS = loadDiagnostics();

        private static final boolean TRACE_LOADS_ENABLE = DIAGNOSTICS.get(LOADS);
        private static final boolean TRACE_SUB_SITUATIONS_ENABLE = DIAGNOSTICS.get(SUBSTITUTIONS);

        static boolean TRACE_LOADS_ENABLE() {
            return TRACE_LOADS_ENABLE;
        }

        static boolean TRACE_SUB_SITUATIONS_ENABLE() {
            return TRACE_SUB_SITUATIONS_ENABLE;
        }
    }

    public static boolean TRACE_LOADS_ENABLE() {
        try {
            return DebugHolder.TRACE_LOADS_ENABLE();
        } catch (ExceptionInInitializerError e) {
            throw ConfigImplUtil.extractInitializerError(e);
        }
    }

    public static boolean TRACE_SUB_SITUATIONS_ENABLE() {
        try {
            return DebugHolder.TRACE_SUB_SITUATIONS_ENABLE();
        } catch (ExceptionInInitializerError e) {
            throw ConfigImplUtil.extractInitializerError(e);
        }
    }

    public static void trace(String message) {
        System.err.println(message);
    }

    public static void trace(int indentLevel, String message) {
        while (indentLevel > 0) {
            System.err.print("  ");
            indentLevel -= 1;
        }
        System.err.println(message);
    }

    // the basic idea here is to add the "what" and have a canonical
    // toplevel error message. the "original" exception may however have extra
    // detail about what happened. call this if you have a better "what" than
    // further down on the stack.
    static ConfigException.NotResolved improveNotResolved(Path what,
                                                          ConfigException.NotResolved original) {
        String newMessage = what.render()
                + " has not been resolved, you need to call Config#resolve(),"
                + " see API docs for Config#resolve()";
        if (newMessage.equals(original.getMessage()))
            return original;
        else
            return new ConfigException.NotResolved(newMessage, original);
    }

    public static ConfigOrigin newSimpleOrigin(String description) {
        if (description == null) {
            return DEFAULT_VALUE_ORIGIN;
        } else {
            return SimpleConfigOrigin.newSimple(description);
        }
    }

    public static ConfigOrigin newFileOrigin(String filename) {
        return SimpleConfigOrigin.newFile(filename);
    }

    public static ConfigOrigin newURLOrigin(URL url) {
        return SimpleConfigOrigin.newURL(url);
    }
}
