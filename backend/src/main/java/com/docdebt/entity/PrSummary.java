package com.docdebt.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Entity
@Table(name = "pr_summaries")
@Getter
@Setter
@NoArgsConstructor
public class PrSummary {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "module_id")
    private CodeModule module;

    private String prNumber;
    private String prUrl;
    private String author;

    // What changed, in engineering terms (endpoints, data model, dependencies...)
    @Column(columnDefinition = "TEXT")
    private String technicalSummary;

    // What changed, in feature/use-case terms - or a note that there's no
    // user-facing impact, for purely internal/technical PRs.
    @Column(columnDefinition = "TEXT")
    private String businessSummary;

    private LocalDateTime createdAt = LocalDateTime.now();

    // Flipped to true once folded into both docs during the Reduce phase
    private boolean processed = false;

    public PrSummary(CodeModule module, String prNumber, String prUrl, String author,
                      String technicalSummary, String businessSummary) {
        this.module = module;
        this.prNumber = prNumber;
        this.prUrl = prUrl;
        this.author = author;
        this.technicalSummary = technicalSummary;
        this.businessSummary = businessSummary;
    }
}
