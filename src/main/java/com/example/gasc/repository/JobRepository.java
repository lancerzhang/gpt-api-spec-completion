package com.example.gasc.repository;

import com.example.gasc.entity.Job;
import com.example.gasc.entity.JobStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface JobRepository extends JpaRepository<Job, Long> {
    @EntityGraph(attributePaths = {"user"})
    Page<Job> findAll(Pageable pageable);

    Optional<Job> findByGithubRepoAndBranchAndStatusIn(String githubRepo, String branch, List<JobStatus> statuses);

    List<Job> findByStatusOrderByIdAsc(JobStatus status);
}
