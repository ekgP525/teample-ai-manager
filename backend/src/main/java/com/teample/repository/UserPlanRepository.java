package com.teample.repository;

import com.teample.entity.UserPlan;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserPlanRepository extends JpaRepository<UserPlan, String> {
}
