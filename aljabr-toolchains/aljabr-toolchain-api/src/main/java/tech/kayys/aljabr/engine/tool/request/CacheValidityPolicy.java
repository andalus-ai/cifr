package tech.kayys.aljabr.engine.tool.request;
public interface CacheValidityPolicy {
    boolean valid(String cacheKey, String workspaceFingerprint, long cachedAtRevision, long currentRevision);
}
