package com.bingomap.bingo_map.notice;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface NoticeRepository extends JpaRepository<Notice, Long> {
    List<Notice> findAllByOrderByCreatedAtDesc();

    // 페이지 나눔용: 최신순 (작성 시각이 같아도 순서가 흔들리지 않도록 id를 함께 정렬)
    Page<Notice> findAllByOrderByCreatedAtDescNoticeIdDesc(Pageable pageable);
}