package com.example.gasc.controller;

import com.example.gasc.DTO.JobCreationDTO;
import com.example.gasc.entity.Job;
import com.example.gasc.oauth2.PrincipalUser;
import com.example.gasc.service.JobService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.Optional;

@RestController
@RequestMapping("/api/v1/jobs")
public class JobController {

    @Autowired
    private JobService jobService;

    @GetMapping("/{id}")
    public ResponseEntity<Job> getJob(@PathVariable Long id) {
        Optional<Job> job = jobService.getJobById(id);
        return job.map(ResponseEntity::ok).orElseGet(() -> ResponseEntity.notFound().build());
    }

    @PostMapping
    public ResponseEntity<Job> createJob(@RequestBody JobCreationDTO dto, Authentication authentication) {
        PrincipalUser authenticatedUser = (PrincipalUser) authentication.getPrincipal();
        Job createdJob = jobService.createJob(dto, authenticatedUser.getId());
        return ResponseEntity.ok(createdJob);
    }

    @GetMapping
    public ResponseEntity<Page<Job>> getAllJobs(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {

        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Order.desc("id")));
        Page<Job> jobsPage = jobService.getAllJobs(pageable);

        return ResponseEntity.ok(jobsPage);
    }

}
