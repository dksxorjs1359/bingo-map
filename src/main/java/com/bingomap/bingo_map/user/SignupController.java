package com.bingomap.bingo_map.user;

import jakarta.validation.Valid;
import org.springframework.stereotype.Controller;
import org.springframework.validation.BindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

@Controller
public class SignupController
{
    private final SignupService signupService; // 회원가입 비즈니스 로직을 처리하는 서비스 의존성 주입

    // 생성자 주입(Constructor Injection)을 통한 SignupService 객체 할당
    public SignupController(SignupService signupService) {
        this.signupService = signupService;
    }

    // [GET /signup] 회원가입 페이지 요청 처리
    @GetMapping("/signup")
    public String signup() {
        // static 디렉토리 내의 /signup/signup.html 정적 파일로 요청을 포워딩합니다.
        return "forward:/signup/signup.html";
    }

    // [POST /signup] 회원가입 요청 처리
    @PostMapping("/signup")
    public String signup(@Valid @ModelAttribute SignupRequestDto requestDto, BindingResult bindingResult) {

        // 1. DTO 유효성 검증(@Valid) 실패 시 (이메일 형식, 입력 필수 값 누락 등)
        if (bindingResult.hasErrors()) {
            // 발생한 검증 에러 중 첫 번째 에러 메시지를 추출합니다.
            FieldError firstError = bindingResult.getFieldErrors().get(0);
            // 에러 메시지를 쿼리 파라미터로 전달하며 회원가입 페이지로 리다이렉트
            return redirectWithError(firstError.getDefaultMessage());
        }

        try {
            // 2. 회원가입 비즈니스 로직(DB 저장, 비밀번호 암호화 등) 실행
            signupService.signup(requestDto);

            // 3. 회원가입 성공 시 -> 로그인 페이지로 이동하며 성공 쿼리 파라미터(?signup=success) 전달
            return "redirect:/login?signup=success";
        } catch (SignupException e) {
            // 4. 회원가입 비즈니스 예외 발생 시 (예: 중복된 이메일/닉네임, 비밀번호 확인 불일치 등)
            // 서비스층에서 던진 에러 메시지를 URL에 담아 회원가입 페이지로 리다이렉트
            return redirectWithError(e.getMessage());
        }
    }

        //에러 메시지를 URL 인코딩하여 회원가입 페이지 리다이렉트 URL을 생성합니다.
        private String redirectWithError(String message) {
        // 한글 및 특수문자 깨짐 방지를 위한 UTF-8 URL 인코딩
        String encoded = URLEncoder.encode(message, StandardCharsets.UTF_8);
        return "redirect:/signup?error=" + encoded;
    }
}