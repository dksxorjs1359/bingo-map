document.addEventListener("DOMContentLoaded", function () {

    let myEmail = null;

    // 1) 대시보드 통계 로드 (회원 수는 실제 값, 나머지는 기능 연동 전이라 0)
    fetch("/api/admin/dashboard")
        .then((res) => {
            if (res.status === 401 || res.status === 403) {
                window.location.href = "/login";
                return "";
            }
            if (!res.ok) throw new Error("failed");
            return res.text();
        })
        .then((text) => {
            if (!text) {
                window.location.href = "/login";
                return;
            }
            const data = JSON.parse(text);
            document.getElementById("admin-member-count").textContent = data.memberCount.toLocaleString();
            document.getElementById("admin-bin-count").textContent = data.binCount.toLocaleString();
            document.getElementById("admin-restaurant-count").textContent = data.restaurantCount.toLocaleString();
            document.getElementById("admin-pending-count").textContent = data.pendingReportCount.toLocaleString();
        })
        .catch(() => {
            // 통계 조회만 실패한 경우엔 관리자 화면을 그대로 열어 둔다 (예전엔 여기서 /login 으로 튕겼음)
            ["admin-member-count", "admin-bin-count", "admin-restaurant-count", "admin-pending-count"]
                .forEach((id) => { const el = document.getElementById(id); if (el) el.textContent = "-"; });
        });

    // 1-1) 대시보드 '제보 검수 대기 목록' 미리보기 (보류 중인 것 상위 5개)
    function loadDashboardPendingReports() {
        fetch("/api/admin/reports")
            .then((res) => (res.ok ? res.json() : []))
            .then((list) => {
                const pending = list.filter((r) => r.status === "PENDING").slice(0, 5);
                const el = document.getElementById("dashboard-pending-reports");
                if (!pending.length) {
                    el.innerHTML = '<p class="admin-empty">보류 중인 제보가 없습니다.</p>';
                    return;
                }
                el.innerHTML = pending.map((r) => (
                    '<p class="admin-message">' +
                    "[" + (categoryLabel[r.category] || r.category) + "] " +
                    escapeHtml(r.name || r.address || (r.latitude + ", " + r.longitude)) +
                    " · " + escapeHtml(r.reporterName) + " · " + r.createdAt +
                    "</p>"
                )).join("");
            })
            .catch(() => {});
    }
    loadDashboardPendingReports();

    // 내 이메일 확인 (본인 계정은 권한변경/삭제 버튼 비활성화하기 위함)
    fetch("/api/mypage/me")
        .then((res) => res.text())
        .then((text) => {
            if (text) myEmail = JSON.parse(text).email;
        })
        .catch(() => {});

    // 2) 사이드바 메뉴 클릭 -> 페이지 이동 없이 해당 탭만 보여주기
    const navLinks = document.querySelectorAll(".admin-nav a[data-tab]");
    const tabSections = document.querySelectorAll(".admin-tab-content");

    function activateTab(tabName) {
        navLinks.forEach((link) => {
            link.classList.toggle("active", link.dataset.tab === tabName);
        });
        tabSections.forEach((section) => {
            section.hidden = section.id !== "tab-" + tabName;
        });
        if (tabName === "users") {
            loadUsers();
        }
        if (tabName === "notices") {
            loadNotices();
        }
        if (tabName === "settings") {
            loadSettings();
        }
        if (tabName === "reports") {
            loadReports();
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

    // 3) 유저 관리: 목록 로드 / 렌더 / 권한변경 / 삭제
    const tbody = document.getElementById("admin-user-table-body");
    const emptyEl = document.getElementById("admin-user-empty");
    const messageEl = document.getElementById("admin-user-message");

    function showMessage(text, isError) {
        messageEl.textContent = text;
        messageEl.className = "admin-message " + (isError ? "error" : "success");
    }

    function loadUsers() {
        fetch("/api/admin/users")
            .then((res) => res.json())
            .then((list) => {
                renderUsers(list);
            })
            .catch(() => {
                showMessage("회원 목록을 불러오지 못했습니다.", true);
            });
    }

    function renderUsers(list) {
        tbody.innerHTML = "";
        emptyEl.hidden = list.length > 0;

        list.forEach((u) => {
            const isMe = u.email === myEmail;
            const isAdminUser = u.role === "ADMIN";

            const tr = document.createElement("tr");
            tr.innerHTML = `
                <td>${escapeHtml(u.name)}${isMe ? " (나)" : ""}</td>
                <td>${escapeHtml(u.nickname)}</td>
                <td>${escapeHtml(u.email)}</td>
                <td><span class="admin-role-badge ${isAdminUser ? "admin" : "user"}">${isAdminUser ? "관리자" : "일반회원"}</span></td>
                <td>${u.joinedAt}</td>
                <td>
                    <div class="admin-row-actions">
                        <button class="${isAdminUser ? "demote" : "promote"}" data-action="role" data-id="${u.userId}" data-role="${isAdminUser ? "USER" : "ADMIN"}" ${isMe ? "disabled" : ""}>
                            ${isAdminUser ? "일반회원으로" : "관리자로"}
                        </button>
                        <button class="delete" data-action="delete" data-id="${u.userId}" ${isMe ? "disabled" : ""}>삭제</button>
                    </div>
                </td>
            `;
            tbody.appendChild(tr);
        });
    }

    function escapeHtml(str) {
        const div = document.createElement("div");
        div.textContent = str ?? "";
        return div.innerHTML;
    }

    tbody.addEventListener("click", function (e) {
        const btn = e.target.closest("button[data-action]");
        if (!btn) return;

        const userId = btn.dataset.id;

        if (btn.dataset.action === "role") {
            const newRole = btn.dataset.role;
            fetch(`/api/admin/users/${userId}/role`, {
                method: "PUT",
                headers: { "Content-Type": "application/json" },
                body: JSON.stringify({ role: newRole }),
            })
                .then(async (res) => {
                    const data = await res.json();
                    if (!res.ok) {
                        showMessage(data.message || "변경에 실패했습니다.", true);
                        return;
                    }
                    showMessage("권한이 변경되었습니다.", false);
                    loadUsers();
                })
                .catch(() => showMessage("변경 중 오류가 발생했습니다.", true));
        }

        if (btn.dataset.action === "delete") {
            if (!confirm("정말 이 회원을 삭제하시겠습니까? 되돌릴 수 없습니다.")) return;

            fetch(`/api/admin/users/${userId}`, { method: "DELETE" })
                .then(async (res) => {
                    const data = await res.json();
                    if (!res.ok) {
                        showMessage(data.message || "삭제에 실패했습니다.", true);
                        return;
                    }
                    showMessage("삭제되었습니다.", false);
                    loadUsers();
                })
                .catch(() => showMessage("삭제 중 오류가 발생했습니다.", true));
        }
    });

    // 3-1) 쓰레기통 제보 관리: 목록 로드 / 렌더 / 개별·일괄 승인·반려·보류
    const reportTbody = document.getElementById("report-table-body");
    const reportEmptyEl = document.getElementById("report-empty");
    const reportMessageEl = document.getElementById("report-message");
    const reportSelectAll = document.getElementById("report-select-all");

    const statusLabel = { PENDING: "보류중", APPROVED: "승인됨", REJECTED: "반려됨" };
    const statusClass = { PENDING: "pending", APPROVED: "approved", REJECTED: "rejected" };
    const categoryLabel = { general: "일반", recycle: "재활용", can: "캔/병" };

    function showReportMessage(text, isError) {
        reportMessageEl.textContent = text;
        reportMessageEl.className = "admin-message " + (isError ? "error" : "success");
    }

    function loadReports() {
        fetch("/api/admin/reports")
            .then((res) => res.json())
            .then((list) => renderReports(list))
            .catch(() => showReportMessage("제보 목록을 불러오지 못했습니다.", true));
    }

    function renderReports(list) {
        reportTbody.innerHTML = "";
        reportEmptyEl.hidden = list.length > 0;
        reportSelectAll.checked = false;

        list.forEach((r) => {
            const location = r.address || (r.latitude.toFixed(5) + ", " + r.longitude.toFixed(5));
            const content = r.name || r.description || "-";
            const isPending = r.status === "PENDING";

            const tr = document.createElement("tr");
            tr.innerHTML = `
                <td><input type="checkbox" class="report-checkbox" value="${r.reportId}"></td>
                <td><span class="admin-role-badge ${statusClass[r.status]}">${statusLabel[r.status]}</span></td>
                <td>${categoryLabel[r.category] || r.category}</td>
                <td title="${escapeHtml(r.description || "")}">${escapeHtml(content)}</td>
                <td>${escapeHtml(location)}</td>
                <td>${escapeHtml(r.reporterName)} (${escapeHtml(r.reporterNickname)})</td>
                <td>${r.createdAt}</td>
                <td>
                    <div class="admin-row-actions">
                        <button class="promote" data-action="approve" data-id="${r.reportId}" ${!isPending ? "disabled" : ""}>승인</button>
                        <button class="delete" data-action="reject" data-id="${r.reportId}" ${!isPending ? "disabled" : ""}>반려</button>
                        <button data-action="hold" data-id="${r.reportId}" ${isPending ? "disabled" : ""}>보류로</button>
                    </div>
                </td>
            `;
            reportTbody.appendChild(tr);
        });
    }

    function updateReportStatus(id, status, rejectReason) {
        return fetch(`/api/admin/reports/${id}/status`, {
            method: "PUT",
            headers: { "Content-Type": "application/json" },
            body: JSON.stringify({ status: status, rejectReason: rejectReason || null }),
        }).then(async (res) => {
            const data = await res.json();
            if (!res.ok) throw new Error(data.message || "처리에 실패했습니다.");
            return data;
        });
    }

    reportSelectAll.addEventListener("change", function () {
        document.querySelectorAll(".report-checkbox").forEach((cb) => (cb.checked = reportSelectAll.checked));
    });

    reportTbody.addEventListener("click", function (e) {
        const btn = e.target.closest("button[data-action]");
        if (!btn) return;
        const id = btn.dataset.id;
        const action = btn.dataset.action;

        if (action === "approve") {
            updateReportStatus(id, "APPROVED")
                .then(() => { showReportMessage("승인되었습니다.", false); loadReports(); })
                .catch((err) => showReportMessage(err.message, true));
        }
        if (action === "reject") {
            const reason = prompt("반려 사유를 입력해주세요.");
            if (reason === null) return;
            if (!reason.trim()) { showReportMessage("반려 사유를 입력해주세요.", true); return; }
            updateReportStatus(id, "REJECTED", reason.trim())
                .then(() => { showReportMessage("반려되었습니다.", false); loadReports(); })
                .catch((err) => showReportMessage(err.message, true));
        }
        if (action === "hold") {
            updateReportStatus(id, "PENDING")
                .then(() => { showReportMessage("보류 상태로 되돌렸습니다.", false); loadReports(); })
                .catch((err) => showReportMessage(err.message, true));
        }
    });

    function selectedReportIds() {
        return [...document.querySelectorAll(".report-checkbox:checked")].map((cb) => cb.value);
    }

    document.getElementById("report-bulk-approve").addEventListener("click", function () {
        const ids = selectedReportIds();
        if (!ids.length) { showReportMessage("승인할 제보를 선택해주세요.", true); return; }
        Promise.all(ids.map((id) => updateReportStatus(id, "APPROVED")))
            .then(() => { showReportMessage(ids.length + "건 승인되었습니다.", false); loadReports(); })
            .catch((err) => showReportMessage(err.message, true));
    });

    document.getElementById("report-bulk-reject").addEventListener("click", function () {
        const ids = selectedReportIds();
        if (!ids.length) { showReportMessage("반려할 제보를 선택해주세요.", true); return; }
        const reason = prompt("반려 사유를 입력해주세요. (선택한 " + ids.length + "건에 동일하게 적용됩니다)");
        if (reason === null) return;
        if (!reason.trim()) { showReportMessage("반려 사유를 입력해주세요.", true); return; }
        Promise.all(ids.map((id) => updateReportStatus(id, "REJECTED", reason.trim())))
            .then(() => { showReportMessage(ids.length + "건 반려되었습니다.", false); loadReports(); })
            .catch((err) => showReportMessage(err.message, true));
    });

    document.getElementById("report-refresh-btn").addEventListener("click", loadReports);

    // 4) 공지사항 관리: 목록 로드 / 작성 / 수정 / 삭제
    const noticeTbody = document.getElementById("notice-table-body");
    const noticeEmptyEl = document.getElementById("notice-empty");
    const noticeMessageEl = document.getElementById("notice-message");
    const noticeFormPanel = document.getElementById("notice-form-panel");
    const noticeFormHeading = document.getElementById("notice-form-heading");
    const noticeFormError = document.getElementById("notice-form-error");
    const noticeIdInput = document.getElementById("notice-form-id");
    const noticeTitleInput = document.getElementById("notice-title-input");
    const noticeContentInput = document.getElementById("notice-content-input");

    function showNoticeMessage(text, isError) {
        noticeMessageEl.textContent = text;
        noticeMessageEl.className = "admin-message " + (isError ? "error" : "success");
    }

    function loadNotices() {
        fetch("/api/notices")
            .then((res) => res.json())
            .then((list) => renderNotices(list))
            .catch(() => showNoticeMessage("공지사항을 불러오지 못했습니다.", true));
    }

    function renderNotices(list) {
        noticeTbody.innerHTML = "";
        noticeEmptyEl.hidden = list.length > 0;

        list.forEach((n) => {
            const tr = document.createElement("tr");
            tr.innerHTML = `
                <td>${escapeHtml(n.title)}</td>
                <td>${n.createdAt}</td>
                <td>${n.updatedAt ? n.updatedAt : "-"}</td>
                <td>
                    <div class="admin-row-actions">
                        <button class="edit" data-action="notice-edit" data-id="${n.noticeId}">수정</button>
                        <button class="delete" data-action="notice-delete" data-id="${n.noticeId}">삭제</button>
                    </div>
                </td>
            `;
            noticeTbody.appendChild(tr);
        });
    }

    function openNoticeForm(notice) {
        noticeFormError.textContent = "";
        if (notice) {
            noticeFormHeading.textContent = "공지 수정";
            noticeIdInput.value = notice.noticeId;
            noticeTitleInput.value = notice.title;
            noticeContentInput.value = notice.content;
        } else {
            noticeFormHeading.textContent = "새 공지 작성";
            noticeIdInput.value = "";
            noticeTitleInput.value = "";
            noticeContentInput.value = "";
        }
        noticeFormPanel.hidden = false;
    }

    function closeNoticeForm() {
        noticeFormPanel.hidden = true;
    }

    document.getElementById("notice-new-btn").addEventListener("click", function () {
        openNoticeForm(null);
    });

    document.getElementById("notice-cancel-btn").addEventListener("click", closeNoticeForm);

    document.getElementById("notice-save-btn").addEventListener("click", function () {
        const payload = {
            title: noticeTitleInput.value.trim(),
            content: noticeContentInput.value.trim(),
        };
        const id = noticeIdInput.value;
        const url = id ? `/api/admin/notices/${id}` : "/api/admin/notices";
        const method = id ? "PUT" : "POST";

        noticeFormError.textContent = "";

        fetch(url, {
            method: method,
            headers: { "Content-Type": "application/json" },
            body: JSON.stringify(payload),
        })
            .then(async (res) => {
                const data = await res.json();
                if (!res.ok) {
                    noticeFormError.textContent = data.message || "저장에 실패했습니다.";
                    return;
                }
                showNoticeMessage(id ? "공지사항이 수정되었습니다." : "공지사항이 등록되었습니다.", false);
                closeNoticeForm();
                loadNotices();
            })
            .catch(() => {
                noticeFormError.textContent = "저장 중 오류가 발생했습니다.";
            });
    });

    noticeTbody.addEventListener("click", function (e) {
        const btn = e.target.closest("button[data-action]");
        if (!btn) return;
        const id = btn.dataset.id;

        if (btn.dataset.action === "notice-edit") {
            fetch(`/api/notices/${id}`)
                .then((res) => res.json())
                .then((notice) => openNoticeForm(notice))
                .catch(() => showNoticeMessage("공지사항을 불러오지 못했습니다.", true));
        }

        if (btn.dataset.action === "notice-delete") {
            if (!confirm("정말 이 공지사항을 삭제하시겠습니까?")) return;

            fetch(`/api/admin/notices/${id}`, { method: "DELETE" })
                .then(async (res) => {
                    const data = await res.json();
                    if (!res.ok) {
                        showNoticeMessage(data.message || "삭제에 실패했습니다.", true);
                        return;
                    }
                    showNoticeMessage("삭제되었습니다.", false);
                    loadNotices();
                })
                .catch(() => showNoticeMessage("삭제 중 오류가 발생했습니다.", true));
        }
    });

    // 5) 시스템 설정: 로드 / 저장
    const settingsMessageEl = document.getElementById("settings-message");
    const signupEnabledSelect = document.getElementById("settings-signup-enabled");
    const maintenanceModeSelect = document.getElementById("settings-maintenance-mode");
    const maintenanceMessageInput = document.getElementById("settings-maintenance-message");

    function showSettingsMessage(text, isError) {
        settingsMessageEl.textContent = text;
        settingsMessageEl.className = "admin-message " + (isError ? "error" : "success");
    }

    function loadSettings() {
        fetch("/api/admin/settings")
            .then((res) => res.json())
            .then((data) => {
                signupEnabledSelect.value = String(data.signupEnabled);
                maintenanceModeSelect.value = String(data.maintenanceMode);
                maintenanceMessageInput.value = data.maintenanceMessage || "";
            })
            .catch(() => showSettingsMessage("설정을 불러오지 못했습니다.", true));
    }

    document.getElementById("settings-save-btn").addEventListener("click", function () {
        const payload = {
            signupEnabled: signupEnabledSelect.value === "true",
            maintenanceMode: maintenanceModeSelect.value === "true",
            maintenanceMessage: maintenanceMessageInput.value.trim(),
        };

        fetch("/api/admin/settings", {
            method: "PUT",
            headers: { "Content-Type": "application/json" },
            body: JSON.stringify(payload),
        })
            .then(async (res) => {
                const data = await res.json();
                if (!res.ok) {
                    showSettingsMessage(data.message || "저장에 실패했습니다.", true);
                    return;
                }
                showSettingsMessage("설정이 저장되었습니다.", false);
            })
            .catch(() => showSettingsMessage("저장 중 오류가 발생했습니다.", true));
    });
});