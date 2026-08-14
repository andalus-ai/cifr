package tech.kayys.aljabr.api.repository;

import io.quarkus.hibernate.reactive.panache.PanacheRepositoryBase;
import jakarta.enterprise.context.ApplicationScoped;
import tech.kayys.aljabr.api.entity.SessionEntity;

@ApplicationScoped
public class SessionRepository implements PanacheRepositoryBase<SessionEntity, String> {
}
