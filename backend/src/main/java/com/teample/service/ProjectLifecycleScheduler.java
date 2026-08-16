package com.teample.service;

import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class ProjectLifecycleScheduler {

    private final ProjectService projectService;

    @Scheduled(fixedDelayString = "${teample.projects.lifecycle-sync-delay-ms:3600000}")
    public void synchronizeExpiredProjects() {
        projectService.synchronizeExpiredProjects();
    }
}
