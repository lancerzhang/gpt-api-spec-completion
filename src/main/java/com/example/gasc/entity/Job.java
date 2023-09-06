package com.example.gasc.entity;

import lombok.Getter;
import lombok.Setter;

import javax.persistence.*;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.Size;
import java.time.Instant;
import java.util.Date;

@Getter
@Setter
@Entity
@Table(name = "job",
        indexes = {
                @Index(name = "idx_github_repo_branch_status", columnList = "githubRepo,branch,status"),
                @Index(name = "idx_status_id", columnList = "status, id")
        })
@NamedEntityGraph(name = "graph.Job.user",
        attributeNodes = @NamedAttributeNode("user"))
public class Job {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotBlank
    @Size(max = 500)
    private String githubRepo;
    @NotBlank
    @Size(max = 255)
    private String branch;
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    private User user;
    private Instant startTime;
    @Enumerated(EnumType.STRING)
    private JobStatus status;
    private Instant endTime;
    private Long duration;
    private double cost;
    @Size(max = 8000)
    private String remark;

    private Date createdAt;

    @Column
    private Date lastModified;

    @PrePersist
    public void prePersist() {
        this.createdAt = new Date();
        this.lastModified = new Date();
    }
}
