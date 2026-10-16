package tech.kayys.aljabr.engine.skill.telemetry;

import jakarta.enterprise.context.ApplicationScoped;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.stream.Collectors;

@ApplicationScoped
public class SkillTelemetryCollector {

    private final List<SkillExecutionRecord> records = new CopyOnWriteArrayList<>();

    public void record(SkillExecutionRecord record) {
        if (record != null) {
            records.add(record);
        }
    }

    public List<SkillExecutionRecord> getAllRecords() {
        return Collections.unmodifiableList(records);
    }

    public List<SkillExecutionRecord> getRecordsBySkill(String skillId) {
        if (skillId == null) return List.of();
        return records.stream()
            .filter(r -> r.skillId() != null && skillId.equalsIgnoreCase(r.skillId()))
            .collect(Collectors.toList());
    }

    public long getTotalExecutionCount() {
        return records.size();
    }

    public double getSuccessRate() {
        if (records.isEmpty()) return 1.0;
        long successes = records.stream().filter(SkillExecutionRecord::success).count();
        return (double) successes / records.size();
    }

    public void clear() {
        records.clear();
    }
}
