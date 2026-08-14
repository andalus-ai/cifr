package tech.kayys.aljabr.api.repository;

import io.quarkus.hibernate.reactive.panache.PanacheRepositoryBase;
import jakarta.enterprise.context.ApplicationScoped;
import tech.kayys.aljabr.api.entity.ProjectEntity;

@ApplicationScoped
public class ProjectRepository implements PanacheRepositoryBase<ProjectEntity, String> {
}
