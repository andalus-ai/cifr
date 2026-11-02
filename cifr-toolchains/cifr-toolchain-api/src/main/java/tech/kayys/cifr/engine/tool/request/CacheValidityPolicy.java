package tech.kayys.cifr.engine.tool.request;
public interface CacheValidityPolicy {
    boolean valid(String cacheKey, String workspaceFingerprint, long cachedAtRevision, long currentRevision);
}
