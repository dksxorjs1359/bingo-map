package com.bingomap.bingo_map.user.admin;

import com.bingomap.bingo_map.report.BinReport;
import com.bingomap.bingo_map.report.BinReportRepository;
import com.bingomap.bingo_map.user.LoginController;
import com.bingomap.bingo_map.user.User;
import com.bingomap.bingo_map.user.UserRepository;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import org.springframework.dao.DataAccessException;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;

@Controller
public class AdminController {

    private static final DateTimeFormatter JOINED_FORMAT = DateTimeFormatter.ofPattern("yyyy.MM.dd");

    private final UserRepository userRepository;
    private final BinReportRepository binReportRepository;

    public AdminController(UserRepository userRepository, BinReportRepository binReportRepository) {
        this.userRepository = userRepository;
        this.binReportRepository = binReportRepository;
    }

    // 관리자 페이지 진입: 로그인 안 했거나 관리자가 아니면 튕겨냄
    @GetMapping("/admin")
    public String admin(HttpServletRequest request) {
        if (!isAdmin(request)) {
            return redirectDenied(request);
        }
        return "forward:/admin/admin.html";
    }

    // 관리자 대시보드 통계 API
    // memberCount, pendingReportCount는 실제 값. binCount/restaurantCount는 아직 담당 기능이 없어서 0 고정.
    @GetMapping("/api/admin/dashboard")
    @ResponseBody
    public AdminDashboardResponseDto dashboard(HttpServletRequest request) {
        if (!isAdmin(request)) {
            return null;
        }
        long memberCount = userRepository.count();
        long pendingReportCount;
        try {
            pendingReportCount = binReportRepository.countByStatus(BinReport.STATUS_PENDING);
        } catch (DataAccessException e) {
            // bin_reports 테이블을 아직 안 만들었거나 DB 오류여도 관리자 화면은 열리게 0으로 처리
            pendingReportCount = 0;
        }
        return new AdminDashboardResponseDto(memberCount, 0, 0, pendingReportCount);
    }

    // 전체 회원 목록 조회
    @GetMapping("/api/admin/users")
    @ResponseBody
    public ResponseEntity<?> users(HttpServletRequest request) {
        if (!isAdmin(request)) {
            return ResponseEntity.status(403).body(Map.of("message", "관리자만 접근할 수 있습니다."));
        }

        List<AdminUserDto> result = userRepository.findAll().stream()
                .map(u -> new AdminUserDto(
                        u.getUserId(),
                        u.getName(),
                        u.getNickname(),
                        u.getEmail(),
                        u.getRole(),
                        u.getCreatedAt() != null ? u.getCreatedAt().format(JOINED_FORMAT) : "-"
                ))
                .toList();

        return ResponseEntity.ok(result);
    }

    // 회원 권한 변경 (USER <-> ADMIN)
    @PutMapping("/api/admin/users/{userId}/role")
    @ResponseBody
    public ResponseEntity<?> updateRole(@PathVariable Long userId,
                                        @Valid @RequestBody AdminRoleUpdateDto dto,
                                        BindingResult bindingResult,
                                        HttpServletRequest request) {
        if (!isAdmin(request)) {
            return ResponseEntity.status(403).body(Map.of("message", "관리자만 접근할 수 있습니다."));
        }
        if (bindingResult.hasErrors()) {
            return ResponseEntity.badRequest().body(Map.of("message", bindingResult.getFieldErrors().get(0).getDefaultMessage()));
        }

        Long myUserId = (Long) request.getSession(false).getAttribute(LoginController.SESSION_USER_ID);
        if (userId.equals(myUserId)) {
            return ResponseEntity.badRequest().body(Map.of("message", "본인의 권한은 스스로 변경할 수 없습니다."));
        }

        User target = userRepository.findById(userId).orElse(null);
        if (target == null) {
            return ResponseEntity.status(404).body(Map.of("message", "존재하지 않는 회원입니다."));
        }

        target.setRole(dto.getRole());
        userRepository.save(target);

        return ResponseEntity.ok(Map.of("message", "변경되었습니다."));
    }

    // 회원 삭제
    @DeleteMapping("/api/admin/users/{userId}")
    @ResponseBody
    public ResponseEntity<?> deleteUser(@PathVariable Long userId, HttpServletRequest request) {
        if (!isAdmin(request)) {
            return ResponseEntity.status(403).body(Map.of("message", "관리자만 접근할 수 있습니다."));
        }

        Long myUserId = (Long) request.getSession(false).getAttribute(LoginController.SESSION_USER_ID);
        if (userId.equals(myUserId)) {
            return ResponseEntity.badRequest().body(Map.of("message", "본인 계정은 스스로 삭제할 수 없습니다."));
        }

        if (!userRepository.existsById(userId)) {
            return ResponseEntity.status(404).body(Map.of("message", "존재하지 않는 회원입니다."));
        }

        userRepository.deleteById(userId);
        return ResponseEntity.ok(Map.of("message", "삭제되었습니다."));
    }

    private boolean isAdmin(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        return session != null && "ADMIN".equals(session.getAttribute(LoginController.SESSION_USER_ROLE));
    }

    private String redirectDenied(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        String message;
        if (session == null || session.getAttribute(LoginController.SESSION_USER_ID) == null) {
            message = "로그인이 필요한 페이지입니다.";
        } else {
            message = "관리자만 접근할 수 있는 페이지입니다.";
        }
        return "redirect:/login?error=" + URLEncoder.encode(message, StandardCharsets.UTF_8);
    }
}