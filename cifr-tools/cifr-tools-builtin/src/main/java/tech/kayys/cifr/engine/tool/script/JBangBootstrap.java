package tech.kayys.cifr.engine.tool.script;

import java.io.*;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.*;
import java.time.Duration;
import java.util.*;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * Manages JBang discovery, downloading, and bootstrapping for Cifr.
 */
public final class JBangBootstrap {

    public static final String DEFAULT_DOWNLOAD_URL =
            "https://github.com/jbangdev/jbang/releases/latest/download/jbang.zip";

    private static final Path CIFR_TOOLS_DIR =
            Paths.get(System.getProperty("user.home"), ".cifr", "tools", "jbang");

    private JBangBootstrap() {}

    /**
     * Resolves an executable path for JBang.
     */
    public static Optional<Path> findExecutable() {
        // 1. Check JBANG_HOME environment variable
        String jbangHome = System.getenv("JBANG_HOME");
        if (jbangHome != null && !jbangHome.isBlank()) {
            Path bin = Paths.get(jbangHome, "bin", isWindows() ? "jbang.cmd" : "jbang");
            if (Files.isExecutable(bin)) return Optional.of(bin);
        }

        // 2. Check ~/.cifr/tools/jbang
        Path cifrBin = CIFR_TOOLS_DIR.resolve("bin").resolve(isWindows() ? "jbang.cmd" : "jbang");
        if (Files.isExecutable(cifrBin)) return Optional.of(cifrBin);

        Path cifrJar = CIFR_TOOLS_DIR.resolve("bin").resolve("jbang.jar");
        if (Files.isRegularFile(cifrJar)) return Optional.of(cifrJar);

        // 3. Check ~/.andalus/tools/jbang
        Path andalusBin = Paths.get(System.getProperty("user.home"), ".andalus", "tools", "jbang", "bin", isWindows() ? "jbang.cmd" : "jbang");
        if (Files.isExecutable(andalusBin)) return Optional.of(andalusBin);

        // 4. Check ~/.jbang/bin/jbang
        Path userJbangBin = Paths.get(System.getProperty("user.home"), ".jbang", "bin", isWindows() ? "jbang.cmd" : "jbang");
        if (Files.isExecutable(userJbangBin)) return Optional.of(userJbangBin);

        // 5. Check Homebrew and common Unix paths
        List<Path> commonPaths = List.of(
                Paths.get("/opt/homebrew/bin/jbang"),
                Paths.get("/usr/local/bin/jbang"),
                Paths.get("/usr/bin/jbang")
        );
        for (Path p : commonPaths) {
            if (Files.isExecutable(p)) return Optional.of(p);
        }

        // 6. Check system PATH
        String pathEnv = System.getenv("PATH");
        if (pathEnv != null) {
            String binaryName = isWindows() ? "jbang.cmd" : "jbang";
            for (String dir : pathEnv.split(File.pathSeparator)) {
                try {
                    Path candidate = Paths.get(dir, binaryName);
                    if (Files.isExecutable(candidate)) return Optional.of(candidate);
                } catch (Exception ignore) {}
            }
        }

        return Optional.empty();
    }

    /**
     * Ensures JBang is available. If not found, downloads and installs it.
     */
    public static synchronized Path ensureInstalled() throws IOException, InterruptedException {
        Optional<Path> found = findExecutable();
        if (found.isPresent()) {
            return found.get();
        }
        return downloadAndInstall(DEFAULT_DOWNLOAD_URL, CIFR_TOOLS_DIR);
    }

    /**
     * Downloads JBang zip and unpacks it into targetDir.
     */
    public static Path downloadAndInstall(String downloadUrl, Path targetDir)
            throws IOException, InterruptedException {
        Files.createDirectories(targetDir);

        HttpClient client = HttpClient.newBuilder()
                .followRedirects(HttpClient.Redirect.ALWAYS)
                .connectTimeout(Duration.ofSeconds(30))
                .build();

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(downloadUrl))
                .timeout(Duration.ofMinutes(3))
                .header("User-Agent", "Cifr-Platform/1.0 (JBangBootstrap)")
                .GET()
                .build();

        Path tempZip = Files.createTempFile("jbang-download", ".zip");
        try {
            HttpResponse<Path> response = client.send(request, HttpResponse.BodyHandlers.ofFile(tempZip));
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new IOException("Failed to download JBang from " + downloadUrl +
                        ", HTTP status: " + response.statusCode());
            }

            unpackZip(tempZip, targetDir);

            Path bin = targetDir.resolve("bin").resolve(isWindows() ? "jbang.cmd" : "jbang");
            if (!Files.exists(bin)) {
                Path nestedBin = targetDir.resolve("jbang").resolve("bin").resolve(isWindows() ? "jbang.cmd" : "jbang");
                if (Files.exists(nestedBin)) {
                    bin = nestedBin;
                }
            }

            File binFile = bin.toFile();
            if (binFile.exists()) {
                binFile.setExecutable(true, false);
                binFile.setReadable(true, false);
            }

            return bin;
        } finally {
            try { Files.deleteIfExists(tempZip); } catch (Exception ignore) {}
        }
    }

    public static List<String> buildCommandPrefix(Path executableOrJar) {
        if (executableOrJar.toString().endsWith(".jar")) {
            return List.of("java", "-jar", executableOrJar.toAbsolutePath().toString());
        }
        return List.of(executableOrJar.toAbsolutePath().toString());
    }

    private static void unpackZip(Path zipFile, Path destDir) throws IOException {
        try (ZipInputStream zis = new ZipInputStream(Files.newInputStream(zipFile))) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                String entryName = entry.getName();
                if (entryName.startsWith("jbang/")) {
                    entryName = entryName.substring("jbang/".length());
                }
                if (entryName.isEmpty()) continue;

                Path targetPath = destDir.resolve(entryName).normalize();
                if (!targetPath.startsWith(destDir)) {
                    throw new IOException("Zip slip detected in entry: " + entry.getName());
                }

                if (entry.isDirectory()) {
                    Files.createDirectories(targetPath);
                } else {
                    if (targetPath.getParent() != null) {
                        Files.createDirectories(targetPath.getParent());
                    }
                    Files.copy(zis, targetPath, StandardCopyOption.REPLACE_EXISTING);
                    if (entryName.startsWith("bin/") && !entryName.endsWith(".jar") && !isWindows()) {
                        targetPath.toFile().setExecutable(true, false);
                    }
                }
                zis.closeEntry();
            }
        }
    }

    public static boolean isWindows() {
        return System.getProperty("os.name", "").toLowerCase().contains("win");
    }
}
