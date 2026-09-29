package com.bingomap.bingo_map.report;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface BinReportRepository extends JpaRepository<BinReport, Long> {

    List<BinReport> findByUserIdOrderByCreatedAtDesc(Long userId);

    // 관리자 목록: 최신순 (보류/승인/반려 우선순위 정렬은 서비스 계층에서 처리)
    List<BinReport> findAllByOrderByCreatedAtDesc();

    long countByStatus(String status);

    // 관리자 페이지 나눔: 보류 -> 승인 -> 반려 순, 같은 상태 안에서는 최신순
    @Query(value = "select r from BinReport r "
            + "order by case r.status when 'PENDING' then 0 when 'APPROVED' then 1 else 2 end, "
            + "r.createdAt desc, r.reportId desc",
            countQuery = "select count(r) from BinReport r")
    Page<BinReport> findAllForAdmin(Pageable pageable);

    // 관리자 페이지 나눔: 특정 상태만 최신순
    @Query(value = "select r from BinReport r where r.status = :status "
            + "order by r.createdAt desc, r.reportId desc",
            countQuery = "select count(r) from BinReport r where r.status = :status")
    Page<BinReport> findByStatusForAdmin(@Param("status") String status, Pageable pageable);
}