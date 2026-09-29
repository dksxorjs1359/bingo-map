package com.bingomap.bingo_map.user;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 헤더 등 프론트엔드에서 "지금 로그인 상태인지"를 물어볼 때 쓰는 API.
 * 화면이 전부 정적 HTML이라 서버가 직접 HTML을 못 바꿔주는 대신,
 * 이 API 응답을 보고 JS(header-auth.js)가 화면을 바꾼다.
 */
@RestController
public class SessionController {

    // [GET /api/session] 현재 클라이언트의 세션 상태 및 사용자 정보 조회 API
    @GetMapping("/api/session")
    public SessionResponseDto getSession(HttpServletRequest request) {
        // 기존 세션이 존재하는지 확인 (없으면 새로 생성하지 않고 null 반환)
        HttpSession session = request.getSession(false);

        // 1. 세션이 없거나, 세션에 저장된 사용자 ID가 없는 경우 (비로그인 상태)
        if (session == null || session.getAttribute(LoginController.SESSION_USER_ID) == null) {
            // 로그인되어 있지 않음을 알리는 Response DTO 반환 (isLoggedIn: false, name: null, role: null)
            return new SessionResponseDto(false, null, null);
        }

        // 2. 로그인 상태인 경우, 세션에 저장해둔 이름과 권한 정보를 가져옵니다.
        String name = (String) session.getAttribute(LoginController.SESSION_USER_NAME);
        String role = (String) session.getAttribute(LoginController.SESSION_USER_ROLE);

        // 3. 로그인 정보와 함께 성공 응답 DTO 반환 (isLoggedIn: true, name, role)
        return new SessionResponseDto(true, name, role);
    }
}

