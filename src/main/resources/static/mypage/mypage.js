document.addEventListener("DOMContentLoaded", function () {

    let currentProfile = null; // 마지막으로 불러온 프로필 (취소 시 복원용)

    function renderProfile(data) {
        currentProfile = data;
        document.getElementById("mypage-name").textContent = data.name + "님";
        document.getElementById("mypage-nickname").textContent = data.nickname;
        document.getElementById("mypage-badge").textContent = data.role === "ADMIN" ? "관리자" : "일반회원";
        document.getElementById("mypage-email").textContent = data.email;
        document.getElementById("mypage-avatar").textContent = data.name.charAt(0);
        document.getElementById("mypage-nationality").textContent = data.nationality;
        document.getElementById("mypage-joined").textContent = data.joinedAt;
    }

    // 1) 프로필 정보 로드
    fetch("/api/mypage/me")
        .then((res) => {
            if (!res.ok) throw new Error("failed");
            return res.text();
        })
        .then((text) => {
            if (!text) {
                window.location.href = "/login";
                return;
            }
            renderProfile(JSON.parse(text));
        })
        .catch(() => {
            window.location.href = "/login";
        });

    // 1-1) 통계 카드 (작성한 리뷰 / 받은 좋아요 등)
    fetch("/api/mypage/stats")
        .then((res) => (res.ok ? res.json() : null))
        .then((data) => {
            if (!data) return;
            document.getElementById("stat-review-count").textContent = data.reviewCount;
            document.getElementById("stat-report-count").textContent = data.reportCount;
            document.getElementById("stat-favorite-count").textContent = data.favoriteCount;
            document.getElementById("stat-help-count").textContent = data.helpCountTotal;
        })
        .catch(() => {});

    // 1-2) 내가 쓴 리뷰 (프로필 탭 '최근 작성한 리뷰' + 리뷰 탭 전체 목록에서 공용으로 사용)
    let myReviewsCache = null;

    function renderStars(rating) {
        if (rating === null || rating === undefined) return "-";
        return "★ " + rating.toFixed(1);
    }

    function reviewCardHtml(review) {
        const thumbStyle = review.thumbnailUrl
            ? ' style="background-image:url(\'' + review.thumbnailUrl + '\')"'
            : "";
        return (
            '<div class="my-review-card">' +
            '<div class="my-review-thumb"' + thumbStyle + '></div>' +
            '<div class="my-review-body">' +
            '<div class="my-review-top">' +
            '<span class="my-review-restaurant">' + review.restaurantName + "</span>" +
            '<span class="my-review-rating">' + renderStars(review.rating) + "</span>" +
            "</div>" +
            '<p class="my-review-content">' + review.content + "</p>" +
            '<span class="my-review-meta">' + review.createdAt + " · 도움이 돼요 " + review.helpCount + "</span>" +
            "</div>" +
            "</div>"
        );
    }

    function renderRecentReviews(reviews) {
        const el = document.getElementById("recent-reviews-list");
        if (!reviews.length) {
            el.innerHTML = '<p class="mypage-empty">아직 작성한 리뷰가 없습니다.<br>맛집 페이지에서 첫 리뷰를 남겨보세요.</p>';
            return;
        }
        el.innerHTML = reviews.slice(0, 3).map(reviewCardHtml).join("");
    }

    function renderAllReviews(reviews) {
        const el = document.getElementById("reviews-tab-panel");
        if (!reviews.length) {
            el.innerHTML = '<p class="mypage-empty">아직 작성한 리뷰가 없습니다.<br>맛집 페이지에서 첫 리뷰를 남겨보세요.</p>';
            return;
        }
        el.innerHTML = reviews.map(reviewCardHtml).join("");
    }

    function loadMyReviews() {
        if (myReviewsCache) {
            return Promise.resolve(myReviewsCache);
        }
        return fetch("/api/mypage/reviews")
            .then((res) => (res.ok ? res.json() : []))
            .then((data) => {
                myReviewsCache = data || [];
                return myReviewsCache;
            })
            .catch(() => []);
    }

    loadMyReviews().then(renderRecentReviews);

    // 1-3) 작성한 제보 (프로필 탭 '최근 제보 내역' + 제보 탭 전체 목록에서 공용으로 사용)
    let myReportsCache = null;

    const reportStatusLabel = { PENDING: "보류중", APPROVED: "승인됨", REJECTED: "반려됨" };
    const reportStatusClass = { PENDING: "pending", APPROVED: "approved", REJECTED: "rejected" };
    const reportCategoryLabel = { general: "일반", recycle: "재활용", can: "캔/병" };

    function reportCardHtml(r) {
        const rejectHtml = r.status === "REJECTED" && r.rejectReason
            ? '<p class="my-report-reject-reason">반려 사유: ' + r.rejectReason + "</p>"
            : "";
        return (
            '<div class="my-report-card">' +
            '<div class="my-report-body">' +
            "<b>" + (r.name || (reportCategoryLabel[r.category] || r.category) + " 쓰레기통") + "</b>" +
            '<p>' + (r.address || (r.latitude.toFixed(5) + ", " + r.longitude.toFixed(5))) + "</p>" +
            '<span class="my-report-meta">' + r.createdAt + "</span>" +
            rejectHtml +
            "</div>" +
            '<span class="my-report-status ' + reportStatusClass[r.status] + '">' + reportStatusLabel[r.status] + "</span>" +
            "</div>"
        );
    }

    function renderRecentReports(reports) {
        const el = document.getElementById("recent-reports-list");
        if (!reports.length) {
            el.innerHTML = '<p class="mypage-empty">아직 제보한 내역이 없습니다.<br><a href="/report">쓰레기통 위치 제보하러 가기</a></p>';
            return;
        }
        el.innerHTML = reports.slice(0, 3).map(reportCardHtml).join("");
    }

    function renderReports(reports) {
        const el = document.getElementById("reports-tab-panel");
        if (!reports.length) {
            el.innerHTML = '<p class="mypage-empty">아직 제보한 내역이 없습니다.<br><a href="/report">쓰레기통 위치 제보하러 가기</a></p>';
            return;
        }
        el.innerHTML = reports.map(reportCardHtml).join("");
    }

    function loadMyReports() {
        if (myReportsCache) {
            return Promise.resolve(myReportsCache);
        }
        return fetch("/api/reports/mine")
            .then((res) => (res.ok ? res.json() : []))
            .then((data) => {
                myReportsCache = data || [];
                return myReportsCache;
            })
            .catch(() => []);
    }

    loadMyReports().then(renderRecentReports);

    // 2) 사이드바 메뉴 클릭 -> 페이지 이동 없이 해당 탭만 보여주기
    const navLinks = document.querySelectorAll(".mypage-nav a[data-tab]");
    const tabSections = document.querySelectorAll(".mypage-tab-content");

    function activateTab(tabName) {
        navLinks.forEach((link) => {
            link.classList.toggle("active", link.dataset.tab === tabName);
        });
        tabSections.forEach((section) => {
            section.hidden = section.id !== "tab-" + tabName;
        });
        if (tabName === "settings") {
            loadSettings();
        }
        if (tabName === "reviews") {
            loadMyReviews().then(renderAllReviews);
        }
        if (tabName === "reports") {
            loadMyReports().then(renderReports);
        }
    }

    navLinks.forEach((link) => {
        link.addEventListener("click", function (e) {
            e.preventDefault();
            activateTab(this.dataset.tab);
        });
    });

    document.querySelectorAll("[data-tab-link]").forEach((link) => {
        link.addEventListener("click", function (e) {
            e.preventDefault();
            activateTab(this.dataset.tabLink);
        });
    });

    // 3) "정보 수정" -> 수정 패널 열기/닫기/저장
    const editPanel = document.getElementById("mypage-edit-panel");
    const editBtn = document.getElementById("mypage-edit-btn");
    const saveBtn = document.getElementById("mypage-save-btn");
    const cancelBtn = document.getElementById("mypage-cancel-btn");
    const errorEl = document.getElementById("mypage-edit-error");

    function openEditPanel() {
        if (!currentProfile) return;
        document.getElementById("edit-name").value = currentProfile.name;
        document.getElementById("edit-nickname").value = currentProfile.nickname;

        const nationalitySelect = document.getElementById("edit-nationality");
        const currentNationality = currentProfile.nationality === "미입력" ? "대한민국" : currentProfile.nationality;
        if ([...nationalitySelect.options].some((opt) => opt.value === currentNationality)) {
            nationalitySelect.value = currentNationality;
        }

        errorEl.textContent = "";
        editPanel.hidden = false;
    }

    function closeEditPanel() {
        editPanel.hidden = true;
    }

    editBtn.addEventListener("click", function () {
        if (editPanel.hidden) {
            openEditPanel();
        } else {
            closeEditPanel();
        }
    });
    cancelBtn.addEventListener("click", closeEditPanel);

    saveBtn.addEventListener("click", function () {
        const payload = {
            name: document.getElementById("edit-name").value.trim(),
            nickname: document.getElementById("edit-nickname").value.trim(),
            nationality: document.getElementById("edit-nationality").value,
        };

        errorEl.textContent = "";

        fetch("/api/mypage/me", {
            method: "PUT",
            headers: { "Content-Type": "application/json" },
            body: JSON.stringify(payload),
        })
            .then(async (res) => {
                const data = await res.json();
                if (!res.ok) {
                    errorEl.textContent = data.message || "수정에 실패했습니다.";
                    return;
                }
                renderProfile(data);
                closeEditPanel();
            })
            .catch(() => {
                errorEl.textContent = "수정 중 오류가 발생했습니다. 다시 시도해주세요.";
            });
    });

    // 4) 설정 탭: 다크모드 / 알림 / 위치정보
    const darkModeToggle = document.getElementById("setting-dark-mode");
    const notifyToggle = document.getElementById("setting-notify-email");
    const locationToggle = document.getElementById("setting-location");
    const locationCheckPanel = document.getElementById("location-check-panel");
    const locationResult = document.getElementById("location-result");
    const saveMessageEl = document.getElementById("settings-save-message");

    let settingsLoaded = false;

    function showSaveMessage(text, isError) {
        saveMessageEl.textContent = text;
        saveMessageEl.className = "settings-save-message " + (isError ? "error" : "success");
    }

    // 다크모드는 서버 저장 없이 즉시 적용 + localStorage에 기억
    darkModeToggle.addEventListener("change", function () {
        const isDark = darkModeToggle.checked;
        document.documentElement.setAttribute("data-theme", isDark ? "dark" : "light");
        localStorage.setItem("bingomap-theme", isDark ? "dark" : "light");
    });

    // 알림/위치 설정은 서버에 저장
    function saveServerSettings() {
        fetch("/api/mypage/settings", {
            method: "PUT",
            headers: { "Content-Type": "application/json" },
            body: JSON.stringify({
                notifyEmail: notifyToggle.checked,
                locationEnabled: locationToggle.checked,
            }),
        })
            .then(async (res) => {
                const data = await res.json();
                if (!res.ok) {
                    showSaveMessage(data.message || "저장에 실패했습니다.", true);
                    return;
                }
                showSaveMessage("설정이 저장되었습니다.", false);
            })
            .catch(() => showSaveMessage("저장 중 오류가 발생했습니다.", true));
    }

    notifyToggle.addEventListener("change", saveServerSettings);
    locationToggle.addEventListener("change", function () {
        locationCheckPanel.hidden = !locationToggle.checked;
        locationResult.textContent = "";
        saveServerSettings();
    });

    document.getElementById("location-check-btn").addEventListener("click", function () {
        if (!navigator.geolocation) {
            locationResult.textContent = "이 브라우저는 위치 정보 기능을 지원하지 않습니다.";
            return;
        }
        locationResult.textContent = "위치 확인 중...";
        navigator.geolocation.getCurrentPosition(
            function (pos) {
                locationResult.textContent =
                    "현재 위치: 위도 " + pos.coords.latitude.toFixed(5) +
                    ", 경도 " + pos.coords.longitude.toFixed(5);
            },
            function () {
                locationResult.textContent = "위치 권한이 거부되었거나 확인할 수 없습니다.";
            }
        );
    });

    function loadSettings() {
        // 다크모드는 localStorage 기준으로 스위치 상태만 맞춰줌
        darkModeToggle.checked = localStorage.getItem("bingomap-theme") === "dark";

        if (settingsLoaded) return; // 알림/위치는 서버에서 한 번만 불러오면 충분
        fetch("/api/mypage/settings")
            .then((res) => res.json())
            .then((data) => {
                notifyToggle.checked = data.notifyEmail;
                locationToggle.checked = data.locationEnabled;
                locationCheckPanel.hidden = !data.locationEnabled;
                settingsLoaded = true;
            })
            .catch(() => showSaveMessage("설정을 불러오지 못했습니다.", true));
    }
});