package tech.kayys.aljabr.api.entity;

import io.quarkus.hibernate.reactive.panache.PanacheEntityBase;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Column;

@Entity
@Table(name = "sessions")
public class SessionEntity extends PanacheEntityBase {
    @Id
    public String id;

    @Column(name = "project_id")
    public String projectId;

    @Column(name = "tenant_id")
    public String tenantId;

    public String name;
    
    public String status;

    public String metadata;
}
