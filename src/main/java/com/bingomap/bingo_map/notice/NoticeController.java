package com.bingomap.bingo_map.notice;

import com.bingomap.bingo_map.user.LoginController;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;

import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;

@Controller
public class NoticeController
{
    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("yyyy.MM.dd HH:mm");

    private final NoticeRepository noticeRepository;

    public NoticeController(NoticeRepository noticeRepository) {
        this.noticeRepository = noticeRepository;
    }

    @GetMapping("/notices")
    public String notices() {
        return "forward:/notices/notices.html";
    }

    // ===== 공개 API (로그인 여부 상관없이 누구나 조회 가능) =====

    @GetMapping("/api/notices")
    @ResponseBody
    public List<NoticeResponseDto> list() {
        return noticeRepository.findAllByOrderByCreatedAtDesc().stream()
                .map(this::toDto)
                .toList();
    }

    @GetMapping("/api/notices/{id}")
    @ResponseBody
    public ResponseEntity<?> detail(@PathVariable Long id) {
        return noticeRepository.findById(id)
                .<ResponseEntity<?>>map(n -> ResponseEntity.ok(toDto(n)))
                .orElse(ResponseEntity.status(404).body(Map.of("message", "존재하지 않는 공지사항입니다.")));
    }

    // ===== 관리자 전용 API =====

    @PostMapping("/api/admin/notices")
    @ResponseBody
    public ResponseEntity<?> create(@Valid @RequestBody NoticeRequestDto dto,
                                    BindingResult bindingResult,
                                    HttpServletRequest request) {
        if (!isAdmin(request)) {
            return ResponseEntity.status(403).body(Map.of("message", "관리자만 접근할 수 있습니다."));
        }
        if (bindingResult.hasErrors()) {
            return ResponseEntity.badRequest().body(Map.of("message", bindingResult.getFieldErrors().get(0).getDefaultMessage()));
        }

        Notice notice = new Notice(dto.getTitle(), dto.getContent());
        // notices.author_id 는 NOT NULL → 로그인한 관리자의 회원 ID를 작성자로 저장
        notice.setAuthorId((Long) request.getSession(false).getAttribute(LoginController.SESSION_USER_ID));
        noticeRepository.save(notice);
        return ResponseEntity.ok(toDto(notice));
    }

    @PutMapping("/api/admin/notices/{id}")
    @ResponseBody
    public ResponseEntity<?> update(@PathVariable Long id,
                                    @Valid @RequestBody NoticeRequestDto dto,
                                    BindingResult bindingResult,
                                    HttpServletRequest request) {
        if (!isAdmin(request)) {
            return ResponseEntity.status(403).body(Map.of("message", "관리자만 접근할 수 있습니다."));
        }
        if (bindingResult.hasErrors()) {
            return ResponseEntity.badRequest().body(Map.of("message", bindingResult.getFieldErrors().get(0).getDefaultMessage()));
        }

        Notice notice = noticeRepository.findById(id).orElse(null);
        if (notice == null) {
            return ResponseEntity.status(404).body(Map.of("message", "존재하지 않는 공지사항입니다."));
        }

        notice.setTitle(dto.getTitle());
        notice.setContent(dto.getContent());
        noticeRepository.save(notice);
        return ResponseEntity.ok(toDto(notice));
    }

    @DeleteMapping("/api/admin/notices/{id}")
    @ResponseBody
    public ResponseEntity<?> delete(@PathVariable Long id, HttpServletRequest request) {
        if (!isAdmin(request)) {
            return ResponseEntity.status(403).body(Map.of("message", "관리자만 접근할 수 있습니다."));
        }
        if (!noticeRepository.existsById(id)) {
            return ResponseEntity.status(404).body(Map.of("message", "존재하지 않는 공지사항입니다."));
        }
        noticeRepository.deleteById(id);
        return ResponseEntity.ok(Map.of("message", "삭제되었습니다."));
    }

    private boolean isAdmin(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        return session != null && "ADMIN".equals(session.getAttribute(LoginController.SESSION_USER_ROLE));
    }

    private NoticeResponseDto toDto(Notice n) {
        String updatedAt = n.getUpdatedAt() != null ? n.getUpdatedAt().format(DATE_FORMAT) : null;
        return new NoticeResponseDto(
                n.getNoticeId(),
                n.getTitle(),
                n.getContent(),
                n.getCreatedAt() != null ? n.getCreatedAt().format(DATE_FORMAT) : "-",
                updatedAt
        );
    }
}