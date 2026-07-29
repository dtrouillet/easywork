package fr.easywork.document.domain;

import jakarta.persistence.*;
import org.hibernate.envers.Audited;

import java.util.UUID;

@Entity
@Audited
@Table(name = "correspondent")
public class Correspondent {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, unique = true)
    private String name;

    public Correspondent() {}

    public Correspondent(String name) {
        this.name = name;
    }

    public UUID getId() { return id; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
}
