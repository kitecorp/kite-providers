// Mobile menu toggle
function toggleMobileMenu() {
    const sidebar = document.querySelector('.sidebar-left');
    const overlay = document.querySelector('.mobile-overlay');
    const btn = document.querySelector('.mobile-menu-btn');
    sidebar.classList.toggle('open');
    overlay.classList.toggle('open');
    btn.classList.toggle('open');
}

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

// Fuzzy search with descriptions
document.getElementById('search')?.addEventListener('input', function(e) {
    const query = e.target.value.toLowerCase().trim();

    document.querySelectorAll('.nav-category').forEach(category => {
        let hasVisible = false;

        category.querySelectorAll('.nav-item').forEach(item => {
            const name = item.dataset.name || '';
            const desc = item.dataset.desc || '';
            // Fuzzy match: check if all characters appear in order
            const fuzzyMatch = (text, pattern) => {
                if (!pattern) return true;
                let pi = 0;
                for (let i = 0; i < text.length && pi < pattern.length; i++) {
                    if (text[i] === pattern[pi]) pi++;
                }
                return pi === pattern.length;
            };
            const visible = fuzzyMatch(name, query) || desc.includes(query);
            item.style.display = visible ? '' : 'none';
            if (visible) hasVisible = true;
        });

        category.style.display = hasVisible ? '' : 'none';

        if (query && hasVisible) {
            category.classList.remove('collapsed');
        }
    });
});

// Keyboard shortcuts
document.addEventListener('keydown', function(e) {
    const searchInput = document.getElementById('search');
    const sidebar = document.querySelector('.sidebar-left');

    // "/" to focus search (when not in input)
    if (e.key === '/' && document.activeElement.tagName !== 'INPUT') {
        e.preventDefault();
        searchInput?.focus();
        searchInput?.select();
    }

    // Escape to close mobile menu or blur search
    if (e.key === 'Escape') {
        if (sidebar?.classList.contains('open')) {
            toggleMobileMenu();
        } else if (document.activeElement === searchInput) {
            searchInput.blur();
            searchInput.value = '';
            searchInput.dispatchEvent(new Event('input'));
        }
    }

    // Arrow key navigation in search results
    if (searchInput && document.activeElement === searchInput) {
        const visibleItems = Array.from(document.querySelectorAll('.nav-item'))
            .filter(item => item.style.display !== 'none');

        if (e.key === 'ArrowDown' && visibleItems.length > 0) {
            e.preventDefault();
            visibleItems[0].querySelector('a')?.focus();
        }
    }

    // Navigate between visible items with arrow keys
    if (document.activeElement.closest('.nav-item')) {
        const visibleItems = Array.from(document.querySelectorAll('.nav-item'))
            .filter(item => item.style.display !== 'none');
        const currentItem = document.activeElement.closest('.nav-item');
        const currentIndex = visibleItems.indexOf(currentItem);

        if (e.key === 'ArrowDown' && currentIndex < visibleItems.length - 1) {
            e.preventDefault();
            visibleItems[currentIndex + 1].querySelector('a')?.focus();
        } else if (e.key === 'ArrowUp') {
            e.preventDefault();
            if (currentIndex > 0) {
                visibleItems[currentIndex - 1].querySelector('a')?.focus();
            } else {
                searchInput?.focus();
            }
        } else if (e.key === 'Enter') {
            // Already handled by link click
        }
    }
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

// Add anchor links to headings with IDs
document.querySelectorAll('h2[id], section[id] > h2').forEach(heading => {
    const id = heading.id || heading.parentElement.id;
    if (id) {
        const anchor = document.createElement('a');
        anchor.className = 'anchor-link';
        anchor.href = '#' + id;
        anchor.textContent = '#';
        anchor.setAttribute('aria-label', 'Link to this section');
        heading.insertBefore(anchor, heading.firstChild);
    }
});

// Smooth scroll for anchor links
document.querySelectorAll('a[href^="#"]').forEach(anchor => {
    anchor.addEventListener('click', function(e) {
        const target = document.querySelector(this.getAttribute('href'));
        if (target) {
            e.preventDefault();
            target.scrollIntoView({ behavior: 'smooth', block: 'start' });
            history.pushState(null, '', this.getAttribute('href'));
        }
    });
});
