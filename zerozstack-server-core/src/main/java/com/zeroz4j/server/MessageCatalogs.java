/*
 * Copyright 2026 Franz Schöning
 * Project: https://www.zeroz4j.com
 * Author: Franz Schöning - Principal Enterprise Architect (https://www.franzschoning.com)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.zeroz4j.server;

import com.zeroz4j.api.i18n.Messages;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.net.JarURLConnection;
import java.net.URL;
import java.net.URLConnection;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.Enumeration;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * The server's translated text: {@code .properties} files, read from the classpath once and kept.
 *
 * <p>The browser cannot read a file, so it is sent its words over the connection. The server can,
 * and does — which is why adding a language to a deployment is dropping
 * {@code i18n/app_fr.properties} into the shared module and restarting, with nothing regenerated
 * and nothing recompiled.</p>
 *
 * <h2>Files are read as UTF-8</h2>
 *
 * <p>Java's own {@code Properties.load(InputStream)} reads Latin-1 and expects everything else as
 * {@code \\u} escapes, which is a rule from 1998 that nobody remembers and that turns every accented
 * character into rubbish. These files are read as UTF-8, so a translator types
 * {@code Zugriff verweigert} and it works.</p>
 *
 * <h2>What counts as an offered language</h2>
 *
 * <p>Everything under {@code i18n/} on the classpath whose name ends in a language suffix,
 * <b>except the framework's own catalog</b>. That is what a person's browser preference is narrowed
 * against: asking for a language nobody translated has to give the deployment's own language, not a
 * half-translated screen.</p>
 *
 * <p>The exception is not tidiness. This project ships its own words in English and German, so
 * {@code i18n/zeroz4j_de.properties} is on every application's classpath whether that application
 * has been translated or not - and if it counted, an English-only deployment would answer a German
 * browser with German refusals over an English screen, which is the same half-translated screen
 * arrived at from the other direction. See {@link #languageOf}.</p>
 */
final class MessageCatalogs implements Messages.Source {

    private static final Logger LOG = Logger.getLogger(MessageCatalogs.class.getName());

    /** Where catalogs live on the classpath. One folder, so offered languages can be counted. */
    static final String CATALOG_FOLDER = "i18n/";

    /**
     * The file name of the framework's own catalog, which never decides what a deployment offers.
     * See {@link #languageOf}.
     */
    private static final String FRAMEWORK_CATALOG_FILE =
            com.zeroz4j.api.i18n.FrameworkText.CATALOG.substring(CATALOG_FOLDER.length());

    /** A file with no entries, cached so an absent language is looked for only once. */
    private static final Properties NONE = new Properties();

    private static final Map<String, Properties> LOADED = new ConcurrentHashMap<>();

    private static volatile Set<String> offered;

    private static volatile Set<String> catalogs;

    @Override
    public String pattern(String catalog, String key, String language) {
        if (catalog == null || key == null) {
            return null;
        }
        return read(catalog, language).getProperty(key);
    }

    /**
     * One catalog in one language.
     *
     * @param catalog  the base name, for example {@code "i18n/app"}
     * @param language an IETF language tag, or null for the file with no suffix
     * @return the entries; empty when there is no such file
     */
    static Properties read(String catalog, String language) {
        String resource = resourceName(catalog, language);
        Properties cached = LOADED.get(resource);
        if (cached != null) {
            return cached;
        }
        Properties loaded = load(resource);
        LOADED.put(resource, loaded);
        return loaded;
    }

    /**
     * @param catalog  the base name
     * @param language an IETF language tag, or null for the file with no suffix
     * @return the classpath resource that holds it
     */
    static String resourceName(String catalog, String language) {
        if (language == null || language.isEmpty()) {
            return catalog + ".properties";
        }
        return catalog + "_" + language.replace('-', '_') + ".properties";
    }

    private static Properties load(String resource) {
        ClassLoader loader = classLoader();
        try (InputStream stream = loader.getResourceAsStream(resource)) {
            if (stream == null) {
                return NONE;
            }
            Properties entries = new Properties();
            try (Reader reader = new InputStreamReader(stream, StandardCharsets.UTF_8)) {
                entries.load(reader);
            }
            return entries;
        } catch (IOException | IllegalArgumentException unreadable) {
            // A broken file must not take the server down: English is compiled in and still works.
            LOG.log(Level.WARNING, "[zeroz4j] Could not read the message catalog " + resource
                    + "; falling back to the language with no suffix.", unreadable);
            return NONE;
        }
    }

    /**
     * Every language this deployment has a catalog for.
     *
     * <p>Read once. A deployment does not grow a language while it is running — adding one is
     * dropping in a file and restarting.</p>
     *
     * @return the language tags, lower-cased, with {@code -} between language and region
     */
    static Set<String> offeredLanguages() {
        Set<String> known = offered;
        if (known != null) {
            return known;
        }
        Set<String> found = new LinkedHashSet<>();
        try {
            Enumeration<URL> folders = classLoader().getResources(CATALOG_FOLDER);
            while (folders.hasMoreElements()) {
                collectFrom(folders.nextElement(), found);
            }
        } catch (IOException unreadable) {
            LOG.log(Level.WARNING, "[zeroz4j] Could not list the message catalogs on the classpath;"
                    + " only the deployment's own language will be offered.", unreadable);
        }
        known = Collections.unmodifiableSet(found);
        offered = known;
        return known;
    }

    /**
     * Every catalog on this deployment's classpath, by base name.
     *
     * <p>Read from the file names in {@code i18n/}, with the language suffix taken off, so an
     * application declaring {@code @MessageCatalog(baseName = "i18n/app")} is found without saying
     * so twice. Read once: a deployment does not grow a catalog while it is running.</p>
     *
     * @return the base names, for example {@code i18n/app} and {@code i18n/zeroz4j}
     */
    static Set<String> catalogNames() {
        Set<String> known = catalogs;
        if (known != null) {
            return known;
        }
        Set<String> found = new LinkedHashSet<>();
        try {
            Enumeration<URL> folders = classLoader().getResources(CATALOG_FOLDER);
            while (folders.hasMoreElements()) {
                collectNamesFrom(folders.nextElement(), found);
            }
        } catch (IOException unreadable) {
            LOG.log(Level.WARNING, "[zeroz4j] Could not list the message catalogs on the classpath;"
                    + " the browser will be sent no words and will show the ones it compiled in.",
                    unreadable);
        }
        known = Collections.unmodifiableSet(found);
        catalogs = known;
        return known;
    }

    /**
     * Every catalog this deployment has, in one language, ready to put on the wire.
     *
     * <p>Each catalog is the language asked for laid over the file with no suffix, so a translation
     * that is missing a key still shows the fallback sentence rather than the key. That is the same
     * order {@link Messages#lookup} uses on the server, which is what makes a message read the same
     * in a browser hint and in the server's refusal of the same value.</p>
     *
     * @param language an IETF language tag, or null for the fallback language
     * @return catalog base name to key/value pairs; never null
     */
    static Map<String, Map<String, String>> allEntries(String language) {
        Map<String, Map<String, String>> everything = new LinkedHashMap<>();
        for (String catalog : catalogNames()) {
            Map<String, String> words = new LinkedHashMap<>();
            copyInto(read(catalog, null), words);
            if (language != null && !language.isEmpty()) {
                copyInto(read(catalog, language), words);
            }
            if (!words.isEmpty()) {
                everything.put(catalog, words);
            }
        }
        return everything;
    }

    private static void copyInto(Properties from, Map<String, String> into) {
        for (String key : from.stringPropertyNames()) {
            into.put(key, from.getProperty(key));
        }
    }

    /**
     * What one file name contributes to the offered-language list.
     *
     * <p>Package-private for {@code LocaleResolutionTest}, which pins the rule that the framework's
     * own languages are not the deployment's. Reaching that rule through the classpath would mean
     * building a jar in a test; reaching it through the one method that decides is enough.</p>
     *
     * @param fileName a file name such as {@code app_de.properties}
     * @param found    the set to add to
     */
    static void languageOfForTesting(String fileName, Set<String> found) {
        languageOf(fileName, found);
    }

    /** Forgets what was read. Test support only: a test may put a new file on the classpath. */
    static void forgetForTesting() {
        LOADED.clear();
        offered = null;
        catalogs = null;
    }

    private static void collectNamesFrom(URL folder, Set<String> found) {
        String protocol = folder.getProtocol();
        if ("file".equals(protocol)) {
            try {
                Path directory = Paths.get(folder.toURI());
                if (!Files.isDirectory(directory)) {
                    return;
                }
                try (DirectoryStream<Path> entries = Files.newDirectoryStream(directory)) {
                    for (Path entry : entries) {
                        baseNameOf(entry.getFileName().toString(), found);
                    }
                }
            } catch (Exception unreadable) {
                LOG.log(Level.FINE, "[zeroz4j] Could not list " + folder, unreadable);
            }
            return;
        }
        if (!"jar".equals(protocol)) {
            return;
        }
        try {
            URLConnection connection = folder.openConnection();
            if (!(connection instanceof JarURLConnection)) {
                return;
            }
            // Not closed, for the reason collectFromJar gives: the container shares this JarFile.
            JarFile jar = ((JarURLConnection) connection).getJarFile();
            Enumeration<JarEntry> entries = jar.entries();
            while (entries.hasMoreElements()) {
                String name = entries.nextElement().getName();
                if (name.startsWith(CATALOG_FOLDER) && name.indexOf('/', CATALOG_FOLDER.length()) < 0) {
                    baseNameOf(name.substring(CATALOG_FOLDER.length()), found);
                }
            }
        } catch (Exception unreadable) {
            LOG.log(Level.FINE, "[zeroz4j] Could not list " + folder, unreadable);
        }
    }

    /**
     * Reads the catalog name out of a file name: {@code app_pt_BR.properties} and
     * {@code app.properties} are both the catalog {@code i18n/app}.
     */
    private static void baseNameOf(String fileName, Set<String> found) {
        if (!fileName.endsWith(".properties")) {
            return;
        }
        String stem = fileName.substring(0, fileName.length() - ".properties".length());
        int underscore = stem.indexOf('_');
        if (underscore > 0) {
            stem = stem.substring(0, underscore);
        }
        if (!stem.isEmpty()) {
            found.add(CATALOG_FOLDER + stem);
        }
    }

    private static void collectFrom(URL folder, Set<String> found) {
        String protocol = folder.getProtocol();
        if ("file".equals(protocol)) {
            collectFromDirectory(folder, found);
        } else if ("jar".equals(protocol)) {
            collectFromJar(folder, found);
        }
    }

    private static void collectFromDirectory(URL folder, Set<String> found) {
        try {
            Path directory = Paths.get(folder.toURI());
            if (!Files.isDirectory(directory)) {
                return;
            }
            try (DirectoryStream<Path> entries = Files.newDirectoryStream(directory)) {
                for (Path entry : entries) {
                    languageOf(entry.getFileName().toString(), found);
                }
            }
        } catch (Exception unreadable) {
            LOG.log(Level.FINE, "[zeroz4j] Could not list " + folder, unreadable);
        }
    }

    private static void collectFromJar(URL folder, Set<String> found) {
        try {
            URLConnection connection = folder.openConnection();
            if (!(connection instanceof JarURLConnection)) {
                return;
            }
            // Do not close the JarFile: containers hand out a shared, cached one, and closing it
            // takes every other resource in that jar away from everybody else.
            JarFile jar = ((JarURLConnection) connection).getJarFile();
            Enumeration<JarEntry> entries = jar.entries();
            while (entries.hasMoreElements()) {
                String name = entries.nextElement().getName();
                if (name.startsWith(CATALOG_FOLDER) && name.indexOf('/', CATALOG_FOLDER.length()) < 0) {
                    languageOf(name.substring(CATALOG_FOLDER.length()), found);
                }
            }
        } catch (Exception unreadable) {
            LOG.log(Level.FINE, "[zeroz4j] Could not list " + folder, unreadable);
        }
    }

    /**
     * Reads the language suffix out of a file name such as {@code app_pt_BR.properties}. A file with
     * no suffix is the fallback language and is not a language of its own.
     *
     * <p><b>The framework's own catalog is skipped, and that is not tidiness.</b> This project ships
     * its own forty-odd words in English and German, so {@code i18n/zeroz4j_de.properties} is on
     * every application's classpath whether or not that application has been translated at all. If
     * it counted, every deployment would offer German, and a visitor whose browser asks for German
     * would be answered with German refusals over an English screen - the half-translated screen
     * this whole design exists to prevent.</p>
     *
     * <p>So what a deployment can answer in is decided by the deployment's own catalogs, and the
     * framework's languages ride along with whichever of them it has. A server with no interface at
     * all that wants German refusals says so with {@code zeroz.i18n.defaultLocale}, which is the
     * setting for exactly that.</p>
     */
    private static void languageOf(String fileName, Set<String> found) {
        if (!fileName.endsWith(".properties")) {
            return;
        }
        String stem = fileName.substring(0, fileName.length() - ".properties".length());
        int underscore = stem.indexOf('_');
        if (underscore < 0 || underscore == stem.length() - 1) {
            return;
        }
        if (FRAMEWORK_CATALOG_FILE.equals(stem.substring(0, underscore))) {
            return;
        }
        String tag = stem.substring(underscore + 1).replace('_', '-');
        if (!tag.isEmpty()) {
            found.add(tag.toLowerCase(Locale.ROOT));
        }
    }

    private static ClassLoader classLoader() {
        ClassLoader context = Thread.currentThread().getContextClassLoader();
        return context != null ? context : MessageCatalogs.class.getClassLoader();
    }
}
