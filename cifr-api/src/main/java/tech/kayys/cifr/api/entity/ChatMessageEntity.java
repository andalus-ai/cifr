package tech.kayys.cifr.api.entity;

import io.quarkus.hibernate.reactive.panache.PanacheEntityBase;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Column;

@Entity
@Table(name = "chat_messages")
public class ChatMessageEntity extends PanacheEntityBase {
    @Id
    public String id;

    @Column(name = "session_id")
    public String sessionId;

    @Column(name = "tenant_id")
    public String tenantId;

    public String role;
    
    // Large objects like content/tool_calls should map appropriately.
    @Column(columnDefinition = "TEXT")
    public String content;

    @Column(name = "tool_calls", columnDefinition = "TEXT")
    public String toolCalls;

    public Integer tokens;
    
    public String model;

    @Column(name = "created_at")
    public String createdAt;
}
