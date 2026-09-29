package com.bingomap.bingo_map.user;

import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseBody;

import java.util.Map;

/**
 * 비밀번호 찾기/재설정.
 * 메일 발송 서버가 없는 환경이라, 회원가입 때 등록해둔 "본인확인 질문/답변"으로
 * 본인 확인 후 바로 새 비밀번호를 설정하는 방식으로 처리한다.
 */
@Controller
public class PasswordResetController {
    // 비밀번호 정규식: 영문, 숫자, 특수문자 포함 8자 이상 검증
    private static final String PASSWORD_PATTERN =
            "^(?=.*[A-Za-z])(?=.*\\d)(?=.*[$@$!%*#?&])[A-Za-z\\d$@$!%*#?&]{8,}$";

    private final UserRepository userRepository;
    private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    public PasswordResetController(UserRepository userRepository) {
        this.userRepository = userRepository;
    }
    // [GET /password-reset] 비밀번호 재설정 화면 진입
    @GetMapping("/password-reset")
    public String page() {
        return "forward:/password-reset/password-reset.html";
    }

    // 1단계: 이메일 입력 -> 그 계정에 등록된 본인확인 질문을 보여줌
    @PostMapping("/api/password-reset/question")
    @ResponseBody
    public ResponseEntity<?> question(@RequestBody Map<String, String> body) {
        String email = body.get("email");
        User user = email != null ? userRepository.findByEmail(email).orElse(null) : null;
        // 입력받은 이메일로 DB 회원 조회 (없으면 null)
        // 1. 가입되지 않은 이메일인 경우 400 Bad Request
        if (user == null) {
            return ResponseEntity.badRequest().body(Map.of(
                    "message", "가입된 계정을 찾을 수 없습니다. 이메일을 다시 확인해주세요."));
        }
        // 2. 소셜 로그인 계정(비밀번호가 null)인 경우 400 Bad Request
        if (user.getPassword() == null) {
            return ResponseEntity.badRequest().body(Map.of(
                    "message", "간편가입(소셜 로그인) 계정은 비밀번호 재설정이 필요하지 않습니다."));
        }
        // 3. 보안 질문/답변이 미등록된 계정인 경우 400 Bad Request
        if (user.getSecurityQuestion() == null || user.getSecurityAnswer() == null) {
            return ResponseEntity.badRequest().body(Map.of(
                    "message", "이 계정에는 본인확인 질문이 등록되어 있지 않습니다. 관리자에게 문의해주세요."));
        }
        // 검증 통과 시 해당 회원의 보안 질문 문구를 반환 (200 OK)
        return ResponseEntity.ok(Map.of("question", user.getSecurityQuestion()));
    }

    // 2단계: 답변 확인
    @PostMapping("/api/password-reset/verify")
    @ResponseBody
    public ResponseEntity<?> verify(@RequestBody Map<String, String> body) {
        // 이메일과 답변을 이용해 본인 확인 검증 수행
        User user = findUserWithMatchedAnswer(body.get("email"), body.get("answer"));
        if (user == null) {
            return ResponseEntity.badRequest().body(Map.of("message", "답변이 일치하지 않습니다."));
        }
        // 답변 일치 시 인증 성공 응답 반환
        return ResponseEntity.ok(Map.of("verified", true));
    }

    // 3단계: 새 비밀번호로 변경 (변경 직전에도 답변을 다시 검증한다 - 클라이언트 값만 믿지 않음)
    @PostMapping("/api/password-reset/reset")
    @ResponseBody
    public ResponseEntity<?> reset(@RequestBody Map<String, String> body) {
        // 클라이언트 변조 방지를 위해 변경 직전에도 본인확인 정보(이메일, 답변)를 재검증
        User user = findUserWithMatchedAnswer(body.get("email"), body.get("answer"));
        if (user == null) {
            return ResponseEntity.badRequest().body(Map.of("message", "본인 확인 정보가 일치하지 않습니다."));
        }

        String newPassword = body.get("newPassword");
        String newPasswordConfirm = body.get("newPasswordConfirm");

        if (newPassword == null || !newPassword.equals(newPasswordConfirm)) {
            return ResponseEntity.badRequest().body(Map.of(
                    // 1. 비밀번호 및 비밀번호 확인 값 일치 여부 검증
                    "message", "새 비밀번호와 비밀번호 확인이 일치하지 않습니다."));
        }
        if (!newPassword.matches(PASSWORD_PATTERN)) {
            return ResponseEntity.badRequest().body(Map.of(
                    // 2. 비밀번호 복잡도(영문, 숫자, 특수문자 포함 8자 이상) 정규식 검증
                    "message", "비밀번호는 영문, 숫자, 특수문자를 포함해 8자 이상이어야 합니다."));
        }
        // 새 비밀번호를 BCrypt로 암호화하여 DB에 저장
        user.setPassword(passwordEncoder.encode(newPassword));
        userRepository.save(user);

        return ResponseEntity.ok(Map.of("message", "비밀번호가 변경되었습니다."));
    }

    private User findUserWithMatchedAnswer(String email, String answer) {
        if (email == null || answer == null) return null;

        User user = userRepository.findByEmail(email).orElse(null);
        if (user == null || user.getSecurityAnswer() == null) return null;

        String normalized = normalizeAnswer(answer);
        return passwordEncoder.matches(normalized, user.getSecurityAnswer()) ? user : null;
    }

    private String normalizeAnswer(String answer) {
        return answer.trim().toLowerCase().replaceAll("\\s+", "");
    }
}