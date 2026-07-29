package com.teample.repository;

import com.teample.entity.Minutes;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MinutesRepository extends JpaRepository<Minutes, String> {
}
