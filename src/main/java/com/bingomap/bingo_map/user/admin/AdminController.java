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
        if (!isAdmin(request)) {//어드민인지 아닌지 검사
            return redirectDenied(request);
        }
        return "forward:/admin/admin.html";
    }

    // 관리자 대시보드 통계 API
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
        }//관리자가 아닐경우 본 출력문을 출력
        if (bindingResult.hasErrors()) {
            return ResponseEntity.badRequest().body(Map.of("message", bindingResult.getFieldErrors().get(0).getDefaultMessage()));
        }

        // 현재 세션에서 로그인한 사용자의 고유 ID(PK)를 가져옵니다. (세션이 없으면 null 반환)
        Long myUserId = (Long) request.getSession(false).getAttribute(LoginController.SESSION_USER_ID);
        // 대상 사용자 ID(userId)가 현재 로그인한 본인의 ID(myUserId)와 같은지 비교합니다.
        if (userId.equals(myUserId)) {
            // 본인의 ID와 일치할 경우, 400 Bad Request 에러 상태코드와 함께 안내 메시지를 반환합니다.
            return ResponseEntity.badRequest().body(Map.of("message", "본인의 권한은 스스로 변경할 수 없습니다."));
        }

        User target = userRepository.findById(userId).orElse(null);
        // 전달받은 userId로 DB에서 회원 정보를 조회합니다. (존재하지 않으면 null 반환)
        if (target == null) {
            // 조회 결과가 존재하지 않는 회원인 경우 처리
            return ResponseEntity.status(404).body(Map.of("message", "존재하지 않는 회원입니다."));
        }// 404 Not Found 상태코드와 함께 안내 메시지를 출력.

        // 회원 엔티티의 권한(Role) 정보를 요청받은 DTO의 권한으로 변경합니다.
        target.setRole(dto.getRole());

        // 변경된 회원 정보를 DB에 저장(업데이트)합니다.
        userRepository.save(target);

        // 변경 성공 시 200 OK 상태코드와 함께 완료 메시지를 출력.
        return ResponseEntity.ok(Map.of("message", "변경되었습니다."));
    }

    // 회원 삭제
    @DeleteMapping("/api/admin/users/{userId}")
    @ResponseBody
    public ResponseEntity deleteUser(@PathVariable Long userId, HttpServletRequest request) {
        // 1. 관리자 권한 체크: 요청한 사용자가 관리자가 아닌 경우 403 Forbidden 에러 반환
        if (!isAdmin(request)) {
            return ResponseEntity.status(403).body(Map.of("message", "관리자만 접근할 수 있습니다."));
        }

        // 2. 본인 계정 삭제 방지: 세션에서 로그인한 본인의 ID를 가져옵니다.
        Long myUserId = (Long) request.getSession(false).getAttribute(LoginController.SESSION_USER_ID);
        // 삭제하려는 대상(userId)이 로그인한 본인(myUserId)인 경우 400 Bad Request 에러 반환
        if (userId.equals(myUserId)) {
            return ResponseEntity.badRequest().body(Map.of("message", "본인 계정은 스스로 삭제할 수 없습니다."));
        }

        // 3. 존재 유무 확인: 삭제하려는 회원이 DB에 존재하는지 확인 (없으면 404 Not Found 에러 반환)
        if (!userRepository.existsById(userId)) {
            return ResponseEntity.status(404).body(Map.of("message", "존재하지 않는 회원입니다."));
        }

        // 4. 회원 삭제 수행: DB에서 해당 ID의 회원을 삭제합니다.
        userRepository.deleteById(userId);

        // 5. 성공 응답 반환: 200 OK 상태코드와 함께 메시지 반환
        return ResponseEntity.ok(Map.of("message", "삭제되었습니다."));
    }

    // [헬퍼 메서드 1] 현재 요청의 세션을 확인하여 관리자(ADMIN) 권한인지 여부를 판별합니다.
    private boolean isAdmin(HttpServletRequest request) {
        HttpSession session = request.getSession(false); // 세션이 존재하지 않으면 새로 생성하지 않고 null 반환
        // 세션이 존재하고, 세션에 저장된 권한 값이 "ADMIN"인지 비교하여 true/false 반환
        return session != null && "ADMIN".equals(session.getAttribute(LoginController.SESSION_USER_ROLE));
    }

    // [헬퍼 메서드 2] 권한이 없거나 미인증 사용자를 로그인 페이지로 리다이렉트시킬 때 상태별 에러 메시지를 생성합니다.
    private String redirectDenied(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        String message;

        // 세션이 없거나 로그인 정보가 없는 경우
        if (session == null || session.getAttribute(LoginController.SESSION_USER_ID) == null) {
            message = "로그인이 필요한 페이지입니다.";
        } else { // 로그인은 되어있으나 관리자 권한이 아닌 경우
            message = "관리자만 접근할 수 있는 페이지입니다.";
        }

        // 에러 메시지를 URL 인코딩하여 로그인 페이지 리다이렉트 경로를 반환합니다.
        return "redirect:/login?error=" + URLEncoder.encode(message, StandardCharsets.UTF_8);
    }
}