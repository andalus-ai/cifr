package tech.kayys.cifr.api.entity;

import io.quarkus.hibernate.reactive.panache.PanacheEntityBase;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Column;

@Entity
@Table(name = "projects")
public class ProjectEntity extends PanacheEntityBase {
    @Id
    public String id;

    @Column(name = "tenant_id")
    public String tenantId;

    public String name;
    
    public String description;

    @Column(name = "base_path")
    public String basePath;

    public String metadata;

    public boolean archived;
}
