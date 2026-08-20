package tech.kayys.aljabr.engine;

import tech.kayys.wayang.sdk.gollek.model.Task;
import tech.kayys.wayang.sdk.gollek.tools.Grep;
import tech.kayys.wayang.sdk.gollek.tools.PlannerIface;
import tech.kayys.wayang.sdk.gollek.tools.Scanner;
import tech.kayys.wayang.sdk.gollek.tools.TaskStoreIface;
import tech.kayys.wayang.sdk.gollek.tools.ToolsFactory;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.PathMatcher;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * Built-in OSS implementation of the minimal coding-agent tools used by
 * {@code wayang code} when no richer tool extension is present.
 */
public final class AljabrFallbackTools implements ToolsFactory {

    private AljabrFallbackTools() {
    }

    public static ToolsFactory create() {
        return new AljabrFallbackTools();
    }

    @Override
    public Scanner createScanner(Path baseDir) {
        return glob -> {
            List<Path> matches = new ArrayList<>();
            Path start = normalizeBaseDir(baseDir);
            PathMatcher matcher = FileSystems.getDefault().getPathMatcher("glob:" + glob);
            try (Stream<Path> paths = Files.walk(start)) {
                paths.filter(path -> matchesGlob(start, matcher, path))
                        .forEach(matches::add);
            }
            return matches;
        };
    }

    @Override
    public Grep createGrep(Path baseDir) {
        return (regex, files) -> {
            List<Grep.Match> matches = new ArrayList<>();
            Pattern pattern = Pattern.compile(regex);
            for (Path file : files == null ? List.<Path>of() : files) {
                collectMatches(pattern, file, matches);
            }
            return matches;
        };
    }

    @Override
    public PlannerIface createPlanner() {
        return goal -> List.of(
                new PlannerIface.PlanStep(
                        "1",
                        1,
                        "Analyze the codebase and locate relevant files for: " + goal),
                new PlannerIface.PlanStep(
                        "2",
                        2,
                        "Outline an implementation plan with 3-5 steps"),
                new PlannerIface.PlanStep(
                        "3",
                        3,
                        "Implement and test changes locally"));
    }

    @Override
    public TaskStoreIface createTaskStore(Path projectDir) {
        return new TaskStoreIface() {
            @Override
            public List<Task> listTasks() {
                return List.of();
            }

            @Override
            public Task addTask(String description) {
                return new Task(UUID.randomUUID().toString(), description);
            }

            @Override
            public void updateTask(Task task) {
            }

            @Override
            public boolean removeTask(String id) {
                return false;
            }
        };
    }

    private static Path normalizeBaseDir(Path baseDir) {
        return baseDir == null ? Path.of(".").toAbsolutePath().normalize() : baseDir.toAbsolutePath().normalize();
    }

    private static boolean matchesGlob(Path start, PathMatcher matcher, Path path) {
        try {
            return matcher.matches(start.relativize(path));
        } catch (RuntimeException exception) {
            return false;
        }
    }

    private static void collectMatches(
            Pattern pattern,
            Path file,
            List<Grep.Match> matches) throws IOException {
        if (file == null || !Files.isRegularFile(file)) {
            return;
        }
        int lineNumber = 0;
        try (BufferedReader reader = Files.newBufferedReader(file)) {
            String line;
            while ((line = reader.readLine()) != null) {
                lineNumber++;
                if (pattern.matcher(line).find()) {
                    matches.add(new Grep.Match(file, lineNumber, line));
                }
            }
        }
    }
}
