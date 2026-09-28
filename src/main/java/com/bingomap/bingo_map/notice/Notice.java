package com.bingomap.bingo_map.notice;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * notices 테이블과 매핑되는 Entity
 * - 실제 DDL: BinGoMap_ORACLE_query_태건.txt (테이블명 notices, 시퀀스 notices_seq)
 */
@Entity
@Table(name = "notices")
@Getter
@Setter
@NoArgsConstructor
public class Notice {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "seq_notice")
    @SequenceGenerator(name = "seq_notice", sequenceName = "notices_seq", allocationSize = 1)
    @Column(name = "id")
    private Long noticeId;

    // DDL의 author_id NOT NULL 컬럼 - 공지 작성 시 반드시 작성자(관리자 회원 ID)를 넣어야 함
    @Column(name = "author_id", nullable = false)
    private Long authorId;

    @Column(name = "TITLE", nullable = false, length = 200)
    private String title;

    @Lob
    @Column(name = "CONTENT", nullable = false)
    private String content;

    @Column(name = "CREATED_AT", updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "UPDATED_AT")
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now(); // updated_at 도 NOT NULL
    }

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = LocalDateTime.now();
    }

    public Notice(String title, String content) {
        this.title = title;
        this.content = content;
    }
}