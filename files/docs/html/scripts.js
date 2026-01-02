function toggleCategory(header) {
    const category = header.parentElement;
    const isCollapsed = category.classList.toggle('collapsed');
    header.setAttribute('aria-expanded', !isCollapsed);
}

function toggleSchema(header) {
    const section = header.parentElement;
    const isExpanded = section.classList.toggle('expanded');
    header.setAttribute('aria-expanded', isExpanded);
}

function copyCode(btn) {
    const code = btn.parentElement.querySelector('code').textContent;
    navigator.clipboard.writeText(code).then(() => {
        btn.textContent = 'Copied!';
        btn.classList.add('copied');
        setTimeout(() => {
            btn.textContent = 'Copy';
            btn.classList.remove('copied');
        }, 2000);
    });
}

// Search functionality
document.getElementById('search')?.addEventListener('input', function(e) {
    const query = e.target.value.toLowerCase();

    document.querySelectorAll('.nav-category').forEach(category => {
        let hasVisible = false;

        category.querySelectorAll('.nav-item').forEach(item => {
            const name = item.dataset.name;
            const visible = name.includes(query);
            item.style.display = visible ? '' : 'none';
            if (visible) hasVisible = true;
        });

        category.style.display = hasVisible ? '' : 'none';

        if (query && hasVisible) {
            category.classList.remove('collapsed');
        }
    });
});

// Highlight current TOC item on scroll
const observerOptions = {
    rootMargin: '-20% 0px -80% 0px'
};

const observer = new IntersectionObserver((entries) => {
    entries.forEach(entry => {
        const id = entry.target.getAttribute('id');
        const tocLink = document.querySelector(`.toc a[href="#${id}"]`);
        if (tocLink) {
            if (entry.isIntersecting) {
                document.querySelectorAll('.toc a').forEach(a => a.classList.remove('active'));
                tocLink.classList.add('active');
            }
        }
    });
}, observerOptions);

document.querySelectorAll('section[id], h2[id]').forEach(section => {
    observer.observe(section);
});
