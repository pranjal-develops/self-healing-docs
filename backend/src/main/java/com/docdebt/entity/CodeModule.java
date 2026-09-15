package com.docdebt.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "modules")
@Getter
@Setter
@NoArgsConstructor
public class CodeModule {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String name; // e.g. "PaymentService"

    // --- Technical doc (HLD/LLD - architecture, endpoints, data model) ---
    private String technicalDocPath;   // e.g. "PaymentService-HLD.md"
    @Column(columnDefinition = "TEXT")
    private String technicalEmbedding; // comma-separated floats, for semantic discovery
    private boolean technicalScaffolded = false;

    // --- Business doc (features, use cases, user-facing impact) ---
    private String businessDocPath;    // e.g. "PaymentService-Business.md"
    @Column(columnDefinition = "TEXT")
    private String businessEmbedding;
    private boolean businessScaffolded = false;

    private LocalDateTime lastDocUpdate = LocalDateTime.now();

    private int volatilityScore = 0;

    @OneToMany(mappedBy = "module", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<PrSummary> prSummaries = new ArrayList<>();

    public CodeModule(String name, String technicalDocPath, String businessDocPath) {
        this.name = name;
        this.technicalDocPath = technicalDocPath;
        this.businessDocPath = businessDocPath;
    }
}
