package com.bingomap.bingo_map.report;

import com.bingomap.bingo_map.common.PageResponse;
import com.bingomap.bingo_map.user.LoginController;
import com.bingomap.bingo_map.user.User;
import com.bingomap.bingo_map.user.UserRepository;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Controller
public class AdminReportController {

    private final BinReportRepository binReportRepository;
    private final UserRepository userRepository;

    public AdminReportController(BinReportRepository binReportRepository, UserRepository userRepository) {
        this.binReportRepository = binReportRepository;
        this.userRepository = userRepository;
    }

    // 제보 목록 (페이지 나눔)
    // 예) /api/admin/reports?status=PENDING&page=0&size=10
    //  - status: ALL(기본) / PENDING / APPROVED / REJECTED
    //  - ALL은 보류 -> 승인 -> 반려 순, 나머지는 최신순
    //  - page는 0부터, size는 1~50으로 제한
    //  - 응답: { items, page, size, totalPages, totalElements, counts{ALL,PENDING,APPROVED,REJECTED} }
    @GetMapping("/api/admin/reports")
    @ResponseBody
    public ResponseEntity<?> reports(@RequestParam(defaultValue = "0") int page,
                                     @RequestParam(defaultValue = "10") int size,
                                     @RequestParam(defaultValue = "ALL") String status,
                                     HttpServletRequest request) {
        if (!isAdmin(request)) {
            return ResponseEntity.status(403).body(Map.of("message", "관리자만 접근할 수 있습니다."));
        }

        String filter = status.trim().toUpperCase();
        boolean all = "ALL".equals(filter);
        if (!all && !BinReport.STATUS_PENDING.equals(filter) && !BinReport.STATUS_APPROVED.equals(filter)
                && !BinReport.STATUS_REJECTED.equals(filter)) {
            return ResponseEntity.badRequest().body(Map.of("message", "status 값이 올바르지 않습니다."));
        }

        PageRequest pageRequest = PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), 50));
        Page<BinReport> result = all
                ? binReportRepository.findAllForAdmin(pageRequest)
                : binReportRepository.findByStatusForAdmin(filter, pageRequest);

        // 제보자 정보는 이 페이지에 나온 사용자만 한 번에 조회한다 (건마다 조회하지 않음)
        Set<Long> userIds = result.getContent().stream().map(BinReport::getUserId).collect(Collectors.toSet());
        Map<Long, User> users = userRepository.findAllById(userIds).stream()
                .collect(Collectors.toMap(User::getUserId, Function.identity()));

        List<BinReportResponseDto> items = result.getContent().stream()
                .map(r -> {
                    BinReportResponseDto dto = new BinReportResponseDto(r);
                    User reporter = users.get(r.getUserId());
                    if (reporter != null) {
                        dto.setReporter(reporter.getName(), reporter.getNickname());
                    } else {
                        dto.setReporter("탈퇴한 회원", "-");
                    }
                    return dto;
                })
                .toList();

        long pending = binReportRepository.countByStatus(BinReport.STATUS_PENDING);
        long approved = binReportRepository.countByStatus(BinReport.STATUS_APPROVED);
        long rejected = binReportRepository.countByStatus(BinReport.STATUS_REJECTED);

        PageResponse<BinReportResponseDto> body = PageResponse.of(result, items);
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("items", body.items());
        response.put("page", body.page());
        response.put("size", body.size());
        response.put("totalPages", body.totalPages());
        response.put("totalElements", body.totalElements());
        response.put("counts", Map.of("ALL", pending + approved + rejected,
                "PENDING", pending, "APPROVED", approved, "REJECTED", rejected));
        return ResponseEntity.ok(response);
    }

    // 제보 검수 처리: 보류로 되돌리기 / 승인 / 반려(사유 포함)
    @PutMapping("/api/admin/reports/{reportId}/status")
    @ResponseBody
    public ResponseEntity<?> updateStatus(@PathVariable Long reportId,
                                          @Valid @RequestBody BinReportStatusUpdateDto dto,
                                          BindingResult bindingResult,
                                          HttpServletRequest request) {
        if (!isAdmin(request)) {
            return ResponseEntity.status(403).body(Map.of("message", "관리자만 접근할 수 있습니다."));
        }
        if (bindingResult.hasErrors()) {
            return ResponseEntity.badRequest().body(Map.of("message", bindingResult.getFieldErrors().get(0).getDefaultMessage()));
        }
        if (BinReport.STATUS_REJECTED.equals(dto.getStatus())
                && (dto.getRejectReason() == null || dto.getRejectReason().isBlank())) {
            return ResponseEntity.badRequest().body(Map.of("message", "반려 사유를 입력해주세요."));
        }

        BinReport report = binReportRepository.findById(reportId).orElse(null);
        if (report == null) {
            return ResponseEntity.status(404).body(Map.of("message", "존재하지 않는 제보입니다."));
        }

        Long adminId = (Long) request.getSession(false).getAttribute(LoginController.SESSION_USER_ID);
        report.review(dto.getStatus(), dto.getRejectReason(), adminId);
        binReportRepository.save(report);

        return ResponseEntity.ok(Map.of("message", "처리되었습니다."));
    }

    // 제보 삭제 (관리자 페이지의 삭제 버튼이 호출)
    @DeleteMapping("/api/admin/reports/{reportId}")
    @ResponseBody
    public ResponseEntity<?> deleteReport(@PathVariable Long reportId, HttpServletRequest request) {
        if (!isAdmin(request)) {
            return ResponseEntity.status(403).body(Map.of("message", "관리자만 접근할 수 있습니다."));
        }
        if (!binReportRepository.existsById(reportId)) {
            return ResponseEntity.status(404).body(Map.of("message", "존재하지 않는 제보입니다."));
        }
        binReportRepository.deleteById(reportId);
        return ResponseEntity.ok(Map.of("message", "삭제되었습니다."));
    }

    private boolean isAdmin(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        return session != null && "ADMIN".equals(session.getAttribute(LoginController.SESSION_USER_ROLE));
    }
}