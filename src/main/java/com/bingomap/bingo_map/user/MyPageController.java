package com.bingomap.bingo_map.user;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.format.DateTimeFormatter;
import java.util.Map;

@Controller
public class MyPageController {

    // 가입일을 "yyyy.MM.dd" 형식(예: 2026.09.29)으로 변환하기 위한 포맷터 정의
    private static final DateTimeFormatter JOINED_FORMAT = DateTimeFormatter.ofPattern("yyyy.MM.dd");

    private final UserRepository userRepository; // 회원 DB 접근을 위한 Repository 의존성 주입

    // 생성자 주입(Constructor Injection)
    public MyPageController(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    // [1. 마이페이지 화면 접근] GET /mypage
    @GetMapping("/mypage")
    public String mypage(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        // 비로그인 사용자인 경우 에러 메시지와 함께 로그인 페이지로 리다이렉트
        if (session == null || session.getAttribute(LoginController.SESSION_USER_ID) == null) {
            String message = URLEncoder.encode("로그인이 필요한 페이지입니다.", StandardCharsets.UTF_8);
            return "redirect:/login?error=" + message;
        }
        // 로그인된 경우 정적 파일 /mypage/mypage.html 로 포워딩
        return "forward:/mypage/mypage.html";
    }

    // [2. 내 프로필 정보 조회 API] GET /api/mypage/me
    @GetMapping("/api/mypage/me")
    @ResponseBody
    public MyPageResponseDto me(HttpServletRequest request) {
        // 현재 세션의 로그인 사용자 조회
        User user = currentUser(request);
        if (user == null) {
            return null; // 비로그인 시 null 반환
        }
        // 사용자 엔티티를 MyPageResponseDto 객체로 변환하여 반환
        return toDto(user);
    }

    // [3. 내 프로필 정보 수정 API] PUT /api/mypage/me
    @PutMapping("/api/mypage/me")
    @ResponseBody
    public ResponseEntity updateMe(@Valid @RequestBody MyPageUpdateRequestDto dto,
                                   BindingResult bindingResult,
                                   HttpServletRequest request) {
        User user = currentUser(request);
        if (user == null) {
            return ResponseEntity.status(401).body(Map.of("message", "로그인이 필요합니다."));
        }

        // DTO 유효성 검증(@Valid) 실패 시 첫 번째 에러 메시지를 400 Bad Request로 반환
        if (bindingResult.hasErrors()) {
            String message = bindingResult.getFieldErrors().get(0).getDefaultMessage();
            return ResponseEntity.badRequest().body(Map.of("message", message));
        }

        // 닉네임 중복 체크: 기존 닉네임과 다를 때만 DB 중복 여부 검사
        if (!dto.getNickname().equals(user.getNickname())
                && userRepository.existsByNickname(dto.getNickname())) {
            return ResponseEntity.badRequest().body(Map.of("message", "이미 사용 중인 닉네임입니다."));
        }

        // 회원의 이름, 닉네임, 국적 정보 변경
        user.setName(dto.getName());
        user.setNickname(dto.getNickname());
        user.setNationality(dto.getNationality());
        userRepository.save(user); // 변경사항 DB 저장

        // 세션에 저장된 이름도 최신 정보로 갱신 (화면 상단 헤더에 변경된 이름 즉시 반영 목적)
        HttpSession session = request.getSession(false);
        if (session != null) {
            session.setAttribute(LoginController.SESSION_USER_NAME, user.getName());
        }

        // 수정 완료된 최신 사용자 정보를 DTO로 반환 (200 OK)
        return ResponseEntity.ok(toDto(user));
    }

    // [4. 사용자 설정 조회 API] GET /api/mypage/settings
    @GetMapping("/api/mypage/settings")
    @ResponseBody
    public ResponseEntity getSettings(HttpServletRequest request) {
        User user = currentUser(request);
        if (user == null) {
            return ResponseEntity.status(401).body(Map.of("message", "로그인이 필요합니다."));
        }
        // 이메일 수신 동의 여부, 위치정보 활용 동의 여부를 DTO로 반환
        return ResponseEntity.ok(new UserSettingsResponseDto(user.isNotifyEmail(), user.isLocationEnabled()));
    }

    // [5. 사용자 설정 저장 API] PUT /api/mypage/settings
    @PutMapping("/api/mypage/settings")
    @ResponseBody
    public ResponseEntity updateSettings(@RequestBody UserSettingsUpdateDto dto, HttpServletRequest request) {
        User user = currentUser(request);
        if (user == null) {
            return ResponseEntity.status(401).body(Map.of("message", "로그인이 필요합니다."));
        }

        // DTO의 boolean 값(true/false)을 DB 저장용 규격("Y"/"N")으로 변환하여 저장
        user.setNotifyEmail(dto.isNotifyEmail() ? "Y" : "N");
        user.setLocationEnabled(dto.isLocationEnabled() ? "Y" : "N");
        userRepository.save(user); // 변경사항 DB 저장

        // 변경 완료된 최신 설정 상태를 반환
        return ResponseEntity.ok(new UserSettingsResponseDto(user.isNotifyEmail(), user.isLocationEnabled()));
    }

    // [헬퍼 메서드 1] 현재 세션에서 로그인된 사용자 엔티티(User)를 조회
    private User currentUser(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        // 세션이 없거나 세션에 로그인 ID가 없는 경우 null 반환
        if (session == null || session.getAttribute(LoginController.SESSION_USER_ID) == null) {
            return null;
        }
        Long userId = (Long) session.getAttribute(LoginController.SESSION_USER_ID);
        // 세션의 userId로 DB에서 회원을 조회하여 반환 (없으면 null)
        return userRepository.findById(userId).orElse(null);
    }

    // [헬퍼 메서드 2] User 엔티티 데이터를 화면 전달용 MyPageResponseDto 객체로 변환
    private MyPageResponseDto toDto(User user) {
        // 가입일 날짜 포맷팅 (null일 경우 "-"로 처리)
        String joinedAt = user.getCreatedAt() != null ? user.getCreatedAt().format(JOINED_FORMAT) : "-";
        // 국적 정보 처리 (null이거나 공백일 경우 "미입력"으로 처리)
        String nationality = (user.getNationality() != null && !user.getNationality().isBlank())
                ? user.getNationality() : "미입력";

        return new MyPageResponseDto(user.getName(), user.getNickname(), user.getEmail(), nationality, joinedAt, user.getRole());
    }
}