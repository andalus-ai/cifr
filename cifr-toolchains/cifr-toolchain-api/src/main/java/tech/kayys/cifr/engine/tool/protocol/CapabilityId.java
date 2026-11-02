package tech.kayys.cifr.engine.tool.protocol;
public record CapabilityId(String value) {
    public static CapabilityId of(String value) { return new CapabilityId(value); }
    // Common capabilities
    public static final CapabilityId WORKSPACE_READ = of("workspace.read");
    public static final CapabilityId WORKSPACE_LIST = of("workspace.list");
    public static final CapabilityId SEARCH_TEXT = of("search.text");
    public static final CapabilityId SEARCH_SYMBOL = of("search.symbol");
    public static final CapabilityId CODE_SYMBOL = of("code.symbol");
    public static final CapabilityId CODE_REFERENCES = of("code.references");
    public static final CapabilityId PATCH_APPLY = of("patch.apply");
    public static final CapabilityId BUILD_COMPILE = of("build.compile");
    public static final CapabilityId TEST_RUN = of("test.run");
    public static final CapabilityId GIT_STATUS = of("git.status");
    public static final CapabilityId GIT_DIFF = of("git.diff");
}
