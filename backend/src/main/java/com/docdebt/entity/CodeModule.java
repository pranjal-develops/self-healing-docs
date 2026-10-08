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

    private String repositoryFullName; // e.g. "owner/repo" where webhook was triggered
    private String targetBranch;       // e.g. "main" or "master"

    /**
     * Where healed docs are published.
     * "github" (default) = commit into docs/ folder of the repo.
     * "onedrive"         = push to OneDrive via Microsoft Graph.
     * "sharepoint"       = push to SharePoint/OneDrive for Business.
     */
    @Column(name = "doc_storage_target")
    private String docStorageTarget = "github";

    @Column(name = "pr_heal_threshold")
    private Integer prHealThreshold = 1;

    public String getDocStorageTarget() {
        return (docStorageTarget != null && !docStorageTarget.isBlank()) ? docStorageTarget : "github";
    }

    public int getPrHealThreshold() {
        return (prHealThreshold != null && prHealThreshold > 0) ? prHealThreshold : 1;
    }

    // --- Technical doc (HLD/LLD - architecture, endpoints, data model) ---
    private String technicalDocPath;   // e.g. "docs/Technical/PaymentService-HLD.md"
    @Column(columnDefinition = "TEXT")
    private String technicalEmbedding; // comma-separated floats, for semantic discovery
    private boolean technicalScaffolded = false;

    // --- Business doc (features, use cases, user-facing impact) ---
    private String businessDocPath;    // e.g. "docs/Business/PaymentService-Business.md"
    @Column(columnDefinition = "TEXT")
    private String businessEmbedding;
    private boolean businessScaffolded = false;

    /**
     * Set to now() when a healed doc is *published* (committed / uploaded),
     * not when the draft is generated. Volatility should not reset until a
     * human or the system actually publishes the result.
     */
    private LocalDateTime lastDocUpdate = LocalDateTime.now();

    private int volatilityScore = 0;

    @OneToMany(mappedBy = "module", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<PrSummary> prSummaries = new ArrayList<>();

    public CodeModule(String name, String technicalDocPath, String businessDocPath) {
        this.name = name;
        this.technicalDocPath = technicalDocPath;
        this.businessDocPath = businessDocPath;
    }

    public CodeModule(String name, String technicalDocPath, String businessDocPath,
                      String docStorageTarget, int prHealThreshold) {
        this.name = name;
        this.technicalDocPath = technicalDocPath;
        this.businessDocPath = businessDocPath;
        this.docStorageTarget = docStorageTarget != null ? docStorageTarget : "github";
        this.prHealThreshold = prHealThreshold > 0 ? prHealThreshold : 1;
    }
}
