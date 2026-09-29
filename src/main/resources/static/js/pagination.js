/**
 * 페이지 번호 UI를 그려주는 공용 함수.
 * renderPagination(container, page, totalPages, onChange)
 *  - page: 현재 페이지 (0부터)
 *  - totalPages: 전체 페이지 수
 *  - onChange(newPage): 사용자가 다른 페이지를 눌렀을 때 호출됨
 * 페이지가 많아져도 현재 페이지 주변 5개 번호만 보여주고, « ‹ › » 버튼으로 이동한다.
 */
function renderPagination(container, page, totalPages, onChange) {
    container.innerHTML = "";
    if (!totalPages || totalPages <= 1) return;

    const WINDOW = 5;
    let start = Math.max(0, page - Math.floor(WINDOW / 2));
    const end = Math.min(totalPages, start + WINDOW);
    start = Math.max(0, end - WINDOW);

    function addButton(label, target, options) {
        const opts = options || {};
        const btn = document.createElement("button");
        btn.type = "button";
        btn.className = "pagination-btn" + (opts.active ? " active" : "");
        btn.textContent = label;
        if (opts.title) btn.title = opts.title;
        if (opts.disabled) {
            btn.disabled = true;
        } else {
            btn.addEventListener("click", function () { onChange(target); });
        }
        container.appendChild(btn);
    }

    const atFirst = page <= 0;
    const atLast = page >= totalPages - 1;

    addButton("«", 0, { disabled: atFirst, title: "첫 페이지" });
    addButton("‹", page - 1, { disabled: atFirst, title: "이전 페이지" });
    for (let i = start; i < end; i++) {
        addButton(String(i + 1), i, { active: i === page });
    }
    addButton("›", page + 1, { disabled: atLast, title: "다음 페이지" });
    addButton("»", totalPages - 1, { disabled: atLast, title: "마지막 페이지" });
}
