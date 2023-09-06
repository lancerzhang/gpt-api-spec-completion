package com.example.gasc.service;

import com.example.gasc.DTO.JobCreationDTO;
import com.example.gasc.entity.Job;
import com.example.gasc.entity.JobStatus;
import com.example.gasc.entity.User;
import com.example.gasc.exception.ExistingJobException;
import com.example.gasc.exception.InsufficientBudgetException;
import com.example.gasc.repository.JobRepository;
import com.example.gasc.repository.UserRepository;
import lombok.Getter;
import lombok.Setter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;

@Getter
@Setter
@Service
public class JobService {
    private final Logger logger = LoggerFactory.getLogger(this.getClass());

    @Autowired
    private JobRepository jobRepository;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private ApplicationContext applicationContext;

    public Optional<Job> getJobById(Long id) {
        return jobRepository.findById(id);
    }

    public Job createJob(JobCreationDTO dto, Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new UsernameNotFoundException("User not found with userId: " + userId));

        if (user.getBudget() <= 0) {
            throw new InsufficientBudgetException("User has insufficient budget.");
        }

        // Check for existing job
        List<JobStatus> statuses = Arrays.asList(JobStatus.NOT_STARTED, JobStatus.IN_PROGRESS);

        Optional<Job> existingJob = jobRepository.findByGithubRepoAndBranchAndStatusIn(dto.getGithubRepo(), dto.getBranch(),  statuses);

        if (existingJob.isPresent()) {
            throw new ExistingJobException("A job with the given criteria is already in progress or not started.");
        }

        Job job = new Job();
        job.setGithubRepo(dto.getGithubRepo());
        job.setBranch(dto.getBranch());
        job.setUser(user);
        job.setStatus(JobStatus.NOT_STARTED);

        return jobRepository.save(job);
    }

    public Page<Job> getAllJobs(Pageable pageable) {
        return jobRepository.findAll(pageable);
    }

    public List<Job> findNotStartedJobs() {
        return jobRepository.findByStatusOrderByIdAsc(JobStatus.NOT_STARTED);
    }

    public void runJob(Job job) {
        // Update the job's status to IN_PROGRESS and persist it
        job.setStatus(JobStatus.IN_PROGRESS);
        jobRepository.save(job);

        logger.info("Starting the job " + job.getId());

        jobRepository.save(job);
    }
}
