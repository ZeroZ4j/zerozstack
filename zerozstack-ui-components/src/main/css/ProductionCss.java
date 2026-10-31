/*
 * Copyright 2026 Franz Schöning (https://www.franzschoning.com)
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

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.PosixFilePermission;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

/**
 * Builds the finished, minified stylesheet of a generated application. Run by the client module's
 * {@code production} profile as a plain JDK source-file launch; it needs nothing but the JDK.
 *
 * <p>Usage: {@code java ProductionCss.java WORK_DIR MODULE_DIR "THEMES"}</p>
 *
 * <ol>
 * <li>Picks the Tailwind standalone executable for this operating system and processor, downloads it
 *     once into {@code ~/.zeroz4j/cache}, and checks its SHA-256 against the value pinned below,
 *     which is the one in the release's {@code sha256sums.txt}. A mismatch deletes the file and
 *     fails the build. The cached copy is checked again on every run.</li>
 * <li>Does the same for daisyUI's {@code daisyui.mjs} and {@code daisyui-theme.mjs}.</li>
 * <li>Writes an {@code input.css} that lists the library's classes, the application's Java sources and
 *     its {@code index.html} as sources, and runs Tailwind with {@code --minify}.</li>
 * <li>Writes {@code MODULE_DIR/target/webapp-production}: a copy of {@code src/main/webapp} whose
 *     {@code index.html} links the finished file in place of the two CDN tags between the markers
 *     {@code zeroz4j:styles:begin} and {@code zeroz4j:styles:end}.</li>
 * </ol>
 *
 * <p>To change a version, change the pins here together with the hashes, and nothing else.</p>
 */
public final class ProductionCss {

    static final String TAILWIND_VERSION = "4.1.16";
    static final String DAISY_VERSION = "5.6.14";

    /** From https://github.com/tailwindlabs/tailwindcss/releases/download/v4.1.16/sha256sums.txt */
    static final String[][] TAILWIND = {
        {"linux-x64", "tailwindcss-linux-x64", "09e6876a63ceb09ccd7e5867e3dbb2b2dc65c3a2f2e2fe210d68ea3bc0432050"},
        {"linux-x64-musl", "tailwindcss-linux-x64-musl", "02ffe87b7e37da4c661156862743fd65e71ec379f32fccf9588faf33d8cb12cb"},
        {"linux-arm64", "tailwindcss-linux-arm64", "967eb434f4d6a1c0dfda106decc646cb742e04d745aa484726023cdd46ba8eda"},
        {"linux-arm64-musl", "tailwindcss-linux-arm64-musl", "bd1947c4ee8126333db2ae87fd19232aaca9286f930e4d50fa5e0c6d56c16036"},
        {"macos-x64", "tailwindcss-macos-x64", "fde2aed09bf249cab9f986fd6f2089e3ce9a9ce1c7862fddbfa807c417e0f5d3"},
        {"macos-arm64", "tailwindcss-macos-arm64", "e6cd44b8167f5746ca32e54f6a1411dd1a6c0dd15d26a9c273b3b2ed9d87df7d"},
        // Tailwind publishes no Windows arm64 build; Windows on ARM runs the x64 one.
        {"windows-x64", "tailwindcss-windows-x64.exe", "07d351195199d9b3334cfdf7bbce39dd91abc2fb319041c78b7405bc3f850daf"},
    };

    /** From data.jsdelivr.com/v1/packages/gh/saadeghi/daisyui@5.6.14 (base64 SHA-256 converted to hex). */
    static final String DAISY_URL = "https://cdn.jsdelivr.net/gh/saadeghi/daisyui@" + DAISY_VERSION + "/packages/bundle/";
    static final String[][] DAISY = {
        {"daisyui.mjs", "1ac6b4ffcc2ec5055494faf89a811640926daaab6bea1cbc8e7183e75e2ccb65"},
        {"daisyui-theme.mjs", "e7aa784b03744eaac62f042f69195790ed120dd5b0d016f279da762c9c18fc8d"},
    };

    static final String BEGIN = "zeroz4j:styles:begin";
    static final String END = "zeroz4j:styles:end";

    private ProductionCss() { }

    public static void main(String[] args) throws Exception {
        if (args.length != 3) {
            throw new IllegalArgumentException("usage: ProductionCss WORK_DIR MODULE_DIR THEMES");
        }
        Path work = Path.of(args[0]).toAbsolutePath().normalize();
        Path module = Path.of(args[1]).toAbsolutePath().normalize();
        String themes = args[2].trim();
        Path cache = Path.of(System.getProperty("user.home"), ".zeroz4j", "cache");
        Path webapp = module.resolve("src/main/webapp");
        Path outDir = module.resolve("target/webapp-production");

        Path classList = work.resolve("META-INF/zeroz4j/css/library-classes.txt");
        if (!Files.isRegularFile(classList)) {
            throw new IllegalStateException(classList + " is missing: the zerozstack-ui-components jar "
                + "did not unpack. Run the build from the project root.");
        }
        Files.copy(classList, work.resolve("library-classes.txt"), StandardCopyOption.REPLACE_EXISTING);

        Path tailwind = fetchTailwind(cache);
        Files.createDirectories(work);
        for (String[] daisy : DAISY) {
            Path cached = fetch(DAISY_URL + daisy[0], cache.resolve("daisyui-" + DAISY_VERSION), daisy[0], daisy[1]);
            Files.copy(cached, work.resolve(daisy[0]), StandardCopyOption.REPLACE_EXISTING);
        }

        StringBuilder css = new StringBuilder();
        css.append("@import \"tailwindcss\";\n");
        Path appCss = module.resolve("src/main/tailwind/app.css");
        if (Files.isRegularFile(appCss)) {
            css.append("@import \"").append(relative(work, appCss)).append("\";\n");
        }
        css.append("@source not \"./tailwindcss*\";\n");
        css.append("@source not \"./daisyui{,*}.mjs\";\n");
        css.append("@plugin \"./daisyui.mjs\" {\n  themes: ").append(themes).append(";\n}\n");
        css.append("@source \"./library-classes.txt\";\n");
        Path java = module.resolve("src/main/java");
        if (Files.isDirectory(java)) {
            css.append("@source \"").append(relative(work, java)).append("\";\n");
        }
        if (Files.isDirectory(webapp)) {
            css.append("@source \"").append(relative(work, webapp)).append("\";\n");
        }
        Files.writeString(work.resolve("input.css"), css.toString(), StandardCharsets.UTF_8);

        Path built = work.resolve("output.css");
        Files.deleteIfExists(built);
        System.out.println("ProductionCss: Tailwind " + TAILWIND_VERSION + ", daisyUI " + DAISY_VERSION
            + ", themes: " + themes);
        Process process = new ProcessBuilder(tailwind.toString(), "-i", "input.css", "-o", "output.css", "--minify")
            .directory(work.toFile()).inheritIO().start();
        if (!process.waitFor(300, TimeUnit.SECONDS)) {
            process.destroyForcibly();
            throw new IllegalStateException("Tailwind did not finish within 300 seconds");
        }
        if (process.exitValue() != 0) {
            throw new IllegalStateException("Tailwind failed with exit code " + process.exitValue());
        }
        String result = Files.readString(built, StandardCharsets.UTF_8);
        if (result.length() < 5000 || !result.contains(".btn") || !result.contains("data-theme")) {
            throw new IllegalStateException("The stylesheet Tailwind wrote looks wrong (" + result.length()
                + " characters, daisyUI button or theme rules missing): " + built);
        }

        String name = "app-" + HexFormat.of().formatHex(
            MessageDigest.getInstance("SHA-256").digest(result.getBytes(StandardCharsets.UTF_8))).substring(0, 8) + ".css";
        deleteTree(outDir);
        if (Files.isDirectory(webapp)) {
            copyTree(webapp, outDir);
        }
        Files.createDirectories(outDir.resolve("css"));
        Files.writeString(outDir.resolve("css").resolve(name), result, StandardCharsets.UTF_8);

        Path index = outDir.resolve("index.html");
        if (!Files.isRegularFile(index)) {
            throw new IllegalStateException(webapp.resolve("index.html") + " does not exist");
        }
        String html = Files.readString(index, StandardCharsets.UTF_8);
        int begin = html.indexOf(BEGIN);
        int end = html.indexOf(END);
        if (begin < 0 || end < begin) {
            throw new IllegalStateException("index.html has no " + BEGIN + " / " + END + " comment markers "
                + "around its Tailwind and daisyUI tags, so there is nothing to replace. See "
                + "docs/guides/packaging.md, \"A finished stylesheet for production\".");
        }
        int start = html.lastIndexOf("<!--", begin);
        int stop = html.indexOf("-->", end) + 3;
        String link = "<link rel=\"stylesheet\" href=\"css/" + name + "\">";
        Files.writeString(index, html.substring(0, start) + link + html.substring(stop), StandardCharsets.UTF_8);
        System.out.println("ProductionCss: wrote " + outDir.resolve("css").resolve(name) + " ("
            + result.length() + " characters)");
    }

    static Path fetchTailwind(Path cache) throws Exception {
        String key = platform();
        for (String[] row : TAILWIND) {
            if (row[0].equals(key)) {
                String url = "https://github.com/tailwindlabs/tailwindcss/releases/download/v"
                    + TAILWIND_VERSION + "/" + row[1];
                Path file = fetch(url, cache.resolve("tailwindcss-" + TAILWIND_VERSION), row[1], row[2]);
                try {
                    Files.setPosixFilePermissions(file, Set.of(PosixFilePermission.OWNER_READ,
                        PosixFilePermission.OWNER_WRITE, PosixFilePermission.OWNER_EXECUTE,
                        PosixFilePermission.GROUP_READ, PosixFilePermission.GROUP_EXECUTE,
                        PosixFilePermission.OTHERS_READ, PosixFilePermission.OTHERS_EXECUTE));
                } catch (UnsupportedOperationException windows) {
                    // no POSIX permissions: nothing to set
                }
                return file;
            }
        }
        throw new IllegalStateException("No pinned Tailwind " + TAILWIND_VERSION + " executable for " + key
            + ". Supported: windows-x64, linux-x64, linux-arm64, macos-x64, macos-arm64.");
    }

    static String platform() {
        String os = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
        String arch = System.getProperty("os.arch", "").toLowerCase(Locale.ROOT);
        boolean arm = arch.contains("aarch64") || arch.contains("arm64");
        boolean x64 = arch.equals("amd64") || arch.equals("x86_64");
        if (!arm && !x64) {
            throw new IllegalStateException("Unsupported processor: " + arch);
        }
        if (os.contains("win")) {
            return "windows-x64";
        }
        if (os.contains("mac")) {
            return arm ? "macos-arm64" : "macos-x64";
        }
        if (os.contains("linux")) {
            String base = arm ? "linux-arm64" : "linux-x64";
            String musl = arm ? "/lib/ld-musl-aarch64.so.1" : "/lib/ld-musl-x86_64.so.1";
            return Files.exists(Path.of(musl)) ? base + "-musl" : base;
        }
        throw new IllegalStateException("Unsupported operating system: " + os);
    }

    /** Returns the cached file, downloading it first if needed. Fails if the SHA-256 is not the pinned one. */
    static Path fetch(String url, Path dir, String fileName, String expected) throws Exception {
        Files.createDirectories(dir);
        Path target = dir.resolve(fileName);
        if (Files.isRegularFile(target)) {
            if (sha256(target).equals(expected)) {
                return target;
            }
            System.out.println("ProductionCss: cached " + target + " does not match its pinned SHA-256; fetching again");
            Files.delete(target);
        }
        Path part = dir.resolve(fileName + ".part");
        System.out.println("ProductionCss: downloading " + url);
        HttpClient client = HttpClient.newBuilder().followRedirects(HttpClient.Redirect.ALWAYS)
            .connectTimeout(Duration.ofSeconds(30)).build();
        HttpResponse<InputStream> response = client.send(
            HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofMinutes(5)).GET().build(),
            HttpResponse.BodyHandlers.ofInputStream());
        if (response.statusCode() != 200) {
            throw new IOException("GET " + url + " answered " + response.statusCode());
        }
        try (InputStream in = response.body()) {
            Files.copy(in, part, StandardCopyOption.REPLACE_EXISTING);
        }
        String actual = sha256(part);
        if (!actual.equals(expected)) {
            Files.deleteIfExists(part);
            throw new IllegalStateException("SHA-256 mismatch for " + url + "\n  expected " + expected
                + "\n  actual   " + actual + "\nThe file was deleted and the build stops here.");
        }
        Files.move(part, target, StandardCopyOption.REPLACE_EXISTING);
        return target;
    }

    static String sha256(Path file) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        try (InputStream in = new DigestInputStream(Files.newInputStream(file), digest)) {
            in.transferTo(java.io.OutputStream.nullOutputStream());
        }
        return HexFormat.of().formatHex(digest.digest());
    }

    static String relative(Path from, Path to) {
        return from.relativize(to).toString().replace('\\', '/');
    }

    static void deleteTree(Path dir) throws IOException {
        if (!Files.exists(dir)) {
            return;
        }
        try (Stream<Path> walk = Files.walk(dir)) {
            for (Path p : walk.sorted(java.util.Comparator.reverseOrder()).toList()) {
                Files.delete(p);
            }
        }
    }

    static void copyTree(Path from, Path to) throws IOException {
        List<Path> all;
        try (Stream<Path> walk = Files.walk(from)) {
            all = walk.toList();
        }
        for (Path p : all) {
            Path dest = to.resolve(from.relativize(p).toString());
            if (Files.isDirectory(p)) {
                Files.createDirectories(dest);
            } else {
                Files.createDirectories(dest.getParent());
                Files.copy(p, dest, StandardCopyOption.REPLACE_EXISTING);
            }
        }
    }
}
