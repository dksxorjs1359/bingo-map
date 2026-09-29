document.addEventListener("DOMContentLoaded", function () {
    const PAGE_SIZE = 10;
    const listEl = document.getElementById("notice-list");
    const emptyEl = document.getElementById("notice-empty");
    const pagerEl = document.getElementById("notice-pagination");

    function loadNotices(page) {
        fetch("/api/notices?page=" + page + "&size=" + PAGE_SIZE)
            .then((res) => res.json())
            .then((data) => {
                // 삭제 등으로 현재 페이지가 비었으면 마지막 페이지로 이동
                if (!data.items.length && data.totalPages > 0 && page > data.totalPages - 1) {
                    loadNotices(data.totalPages - 1);
                    return;
                }
                renderNotices(data.items);
                emptyEl.hidden = data.totalElements > 0;
                renderPagination(pagerEl, data.page, data.totalPages, function (p) {
                    loadNotices(p);
                    listEl.scrollIntoView({ behavior: "smooth", block: "start" });
                });
            })
            .catch(() => {
                emptyEl.hidden = false;
                emptyEl.textContent = "공지사항을 불러오지 못했습니다.";
            });
    }

    function renderNotices(list) {
        listEl.innerHTML = "";
        list.forEach((n) => {
            const item = document.createElement("div");
            item.className = "notice-item";
            item.innerHTML = `
                <div class="notice-item-head">
                    <span class="notice-item-title">${escapeHtml(n.title)}</span>
                    <span class="notice-item-date">${n.createdAt}</span>
                </div>
                <div class="notice-item-body">${escapeHtml(n.content)}</div>
            `;
            item.querySelector(".notice-item-head").addEventListener("click", function () {
                item.classList.toggle("open");
            });
            listEl.appendChild(item);
        });
    }

    function escapeHtml(str) {
        const div = document.createElement("div");
        div.textContent = str ?? "";
        return div.innerHTML;
    }

    loadNotices(0);
});
