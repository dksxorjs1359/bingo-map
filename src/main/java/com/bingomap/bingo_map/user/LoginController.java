package com.bingomap.bingo_map.user;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

@Controller
public class LoginController {
    // 세션(Session)에 로그인한 사용자 정보를 저장하고 꺼낼 때 사용할 Key 상수 선언
    public static final String SESSION_USER_ID = "LOGIN_USER_ID";     // 사용자 고유 ID Key
    public static final String SESSION_USER_NAME = "LOGIN_USER_NAME"; // 사용자 이름 Key
    public static final String SESSION_USER_ROLE = "LOGIN_USER_ROLE"; // 사용자 권한 Key

    private final LoginService loginService; // 로그인 비즈니스 로직을 처리하는 서비스 의존성 주입

    // 생성자 주입(Constructor Injection)을 통한 LoginService 객체 할당
    public LoginController(LoginService loginService) {
        this.loginService = loginService;
    }

    // [GET /login] 로그인 페이지 요청 처리
    @GetMapping("/login")
    public String login() {
        // static 디렉토리 내의 /login/login.html 정적 파일로 요청을 포워딩합니다.
        return "forward:/login/login.html";
    }

    // [POST /login] 로그인 인증 처리
    @PostMapping("/login")
    public String login(@ModelAttribute LoginRequestDto requestDto, HttpServletRequest request) {
        try {
            // 1. 입력받은 아이디/비밀번호(requestDto)로 사용자 검증 수행
            User user = loginService.login(requestDto);

            // 2. 로그인 성공 시, 기존 세션을 가져오거나 없으면 신규 생성
            HttpSession session = request.getSession();

            // 3. 세션에 로그인한 사용자의 ID, 이름, 권한 정보를 Key-Value 형태로 저장
            session.setAttribute(SESSION_USER_ID, user.getUserId());
            session.setAttribute(SESSION_USER_NAME, user.getName());
            session.setAttribute(SESSION_USER_ROLE, user.getRole());

            // 4. 로그인 성공 후 메인 페이지(/)로 이동(리다이렉트)
            return "redirect:/";
        } catch (LoginException e) {
            // 로그인 실패 시(아이디 불일치, 비밀번호 오류 등) 예외 메시지를 URL 인코딩 처리
            String message = URLEncoder.encode(e.getMessage(), StandardCharsets.UTF_8);

            // 로그인 페이지로 돌아가며 쿼리 파라미터로 에러 메시지 전달
            return "redirect:/login?error=" + message;
        }
    }

    // [GET /logout] 로그아웃 처리
    @GetMapping("/logout")
    public String logout(HttpServletRequest request) {
        // 기존 세션이 존재하는지 확인 (없으면 null 반환하며 새로 생성하지 않음)
        HttpSession session = request.getSession(false);

        // 세션이 존재하면 무효화(삭제) 처리
        if (session != null) {
            session.invalidate();
        }

        // 로그아웃 완료 후 메인 페이지(/)로 이동(리다이렉트)
        return "redirect:/";
    }
}