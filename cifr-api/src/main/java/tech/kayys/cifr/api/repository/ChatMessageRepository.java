package tech.kayys.cifr.api.repository;

import io.quarkus.hibernate.reactive.panache.PanacheRepositoryBase;
import jakarta.enterprise.context.ApplicationScoped;
import tech.kayys.cifr.api.entity.ChatMessageEntity;

@ApplicationScoped
public class ChatMessageRepository implements PanacheRepositoryBase<ChatMessageEntity, String> {
}
