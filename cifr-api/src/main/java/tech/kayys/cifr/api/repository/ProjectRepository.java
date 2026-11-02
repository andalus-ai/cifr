package tech.kayys.cifr.api.repository;

import io.quarkus.hibernate.reactive.panache.PanacheRepositoryBase;
import jakarta.enterprise.context.ApplicationScoped;
import tech.kayys.cifr.api.entity.ProjectEntity;

@ApplicationScoped
public class ProjectRepository implements PanacheRepositoryBase<ProjectEntity, String> {
}
