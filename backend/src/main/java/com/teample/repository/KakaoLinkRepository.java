package com.teample.repository;

import com.teample.entity.KakaoLink;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface KakaoLinkRepository extends JpaRepository<KakaoLink, String> {

    List<KakaoLink> findByDeadlineRemindersTrue();
}
