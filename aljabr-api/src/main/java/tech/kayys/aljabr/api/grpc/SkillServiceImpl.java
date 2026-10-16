package tech.kayys.aljabr.api.grpc;

import io.quarkus.grpc.GrpcService;
import io.smallrye.mutiny.Uni;
import jakarta.inject.Inject;
import tech.kayys.aljabr.engine.skill.management.SkillManagerService;
import tech.kayys.andalus.pro.api.grpc.*;

import java.util.List;
import java.util.Map;
import java.util.Optional;

@GrpcService
@jakarta.inject.Singleton
public class SkillServiceImpl extends MutinySkillServiceGrpc.SkillServiceImplBase implements SkillService {

    @Inject
    public SkillManagerService skillManager;

    @Override
    public Uni<ListSkillsResponse> listSkills(ListSkillsRequest request) {
        return Uni.createFrom().item(() -> {
            var list = skillManager.listSkills(
                    request.getCategory().isBlank() ? null : request.getCategory(),
                    request.getQuery().isBlank() ? null : request.getQuery(),
                    request.getIncludeTrash()
            );

            List<SkillInfo> infos = list.stream()
                    .map(this::toSkillInfo)
                    .toList();

            return ListSkillsResponse.newBuilder()
                    .addAllSkills(infos)
                    .setTotalCount(infos.size())
                    .build();
        });
    }

    @Override
    public Uni<GetSkillResponse> getSkill(GetSkillRequest request) {
        return Uni.createFrom().item(() -> {
            Optional<SkillManagerService.SkillDetailDto> detailOpt = skillManager.getSkill(request.getSkillId());
            if (detailOpt.isEmpty()) {
                return GetSkillResponse.newBuilder()
                        .setFound(false)
                        .setMessage("Skill not found: " + request.getSkillId())
                        .build();
            }

            SkillManagerService.SkillDetailDto detail = detailOpt.get();
            SkillDetail.Builder b = SkillDetail.newBuilder()
                    .setInfo(toSkillInfo(detail.summary()))
                    .setInstructions(detail.instructions())
                    .putAllReferences(detail.references())
                    .addAllScripts(detail.scripts())
                    .setRawMarkdown(detail.rawMarkdown() != null ? detail.rawMarkdown() : "");

            return GetSkillResponse.newBuilder()
                    .setFound(true)
                    .setSkill(b.build())
                    .setMessage("Found skill")
                    .build();
        });
    }

    @Override
    public Uni<SaveSkillResponse> saveSkill(SaveSkillRequest request) {
        return Uni.createFrom().item(() -> {
            SkillManagerService.SkillInputDto input = new SkillManagerService.SkillInputDto(
                    request.getId(),
                    request.getName(),
                    request.getDescription(),
                    request.getCategory(),
                    request.getReasoningMode(),
                    request.getMutatesWorkspace(),
                    request.getAllowedToolsList(),
                    request.getInstructions(),
                    request.getReferencesMap(),
                    request.getScriptsMap()
            );

            var res = skillManager.saveSkill(input);
            return SaveSkillResponse.newBuilder()
                    .setSuccess(res.success())
                    .setSkillId(res.skillId() != null ? res.skillId() : "")
                    .setMessage(res.message())
                    .setSourcePath(res.path() != null ? res.path() : "")
                    .build();
        });
    }

    @Override
    public Uni<DeleteSkillResponse> deleteSkill(DeleteSkillRequest request) {
        return Uni.createFrom().item(() -> {
            var res = skillManager.deleteSkill(request.getSkillId(), request.getHardDelete());
            return DeleteSkillResponse.newBuilder()
                    .setSuccess(res.success())
                    .setSkillId(res.skillId() != null ? res.skillId() : request.getSkillId())
                    .setMovedToTrash(res.movedToTrash())
                    .setTrashPath(res.path() != null ? res.path() : "")
                    .setMessage(res.message())
                    .build();
        });
    }

    @Override
    public Uni<RestoreSkillResponse> restoreSkill(RestoreSkillRequest request) {
        return Uni.createFrom().item(() -> {
            var res = skillManager.restoreSkill(request.getSkillId());
            return RestoreSkillResponse.newBuilder()
                    .setSuccess(res.success())
                    .setSkillId(res.skillId() != null ? res.skillId() : request.getSkillId())
                    .setRestoredPath(res.path() != null ? res.path() : "")
                    .setMessage(res.message())
                    .build();
        });
    }

    @Override
    public Uni<ListSkillsResponse> listTrash(Empty request) {
        return Uni.createFrom().item(() -> {
            var list = skillManager.listTrash();
            List<SkillInfo> infos = list.stream()
                    .map(this::toSkillInfo)
                    .toList();

            return ListSkillsResponse.newBuilder()
                    .addAllSkills(infos)
                    .setTotalCount(infos.size())
                    .build();
        });
    }

    @Override
    public Uni<ReloadSkillsResponse> reloadSkills(Empty request) {
        return Uni.createFrom().item(() -> {
            int count = skillManager.reloadSkills();
            return ReloadSkillsResponse.newBuilder()
                    .setSuccess(true)
                    .setReloadedCount(count)
                    .setMessage("Successfully reloaded " + count + " skills")
                    .build();
        });
    }

    private SkillInfo toSkillInfo(SkillManagerService.SkillSummary summary) {
        return SkillInfo.newBuilder()
                .setId(summary.id())
                .setName(summary.name())
                .setDescription(summary.description() != null ? summary.description() : "")
                .setCategory(summary.category() != null ? summary.category() : "DISCOVERY")
                .setReasoningMode(summary.reasoningMode() != null ? summary.reasoningMode() : "NONE")
                .setMutatesWorkspace(summary.mutatesWorkspace())
                .addAllAllowedTools(summary.allowedTools() != null ? summary.allowedTools() : List.of())
                .setSourcePath(summary.sourcePath() != null ? summary.sourcePath() : "")
                .setInTrash(summary.inTrash())
                .build();
    }
}
