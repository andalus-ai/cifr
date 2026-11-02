package tech.kayys.cifr.api.repository;

import io.quarkus.hibernate.reactive.panache.PanacheRepositoryBase;
import jakarta.enterprise.context.ApplicationScoped;
import tech.kayys.cifr.api.entity.SessionEntity;

@ApplicationScoped
public class SessionRepository implements PanacheRepositoryBase<SessionEntity, String> {
}
