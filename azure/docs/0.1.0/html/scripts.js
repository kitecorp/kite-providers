// Theme toggle (dark/light mode)
function toggleTheme() {
    const html = document.documentElement;
    const currentTheme = html.getAttribute('data-theme');
    const newTheme = currentTheme === 'dark' ? 'light' : 'dark';
    html.setAttribute('data-theme', newTheme);
    localStorage.setItem('kite-theme', newTheme);
}

// Initialize theme from localStorage or system preference
(function initTheme() {
    const saved = localStorage.getItem('kite-theme');
    if (saved) {
        document.documentElement.setAttribute('data-theme', saved);
    } else if (window.matchMedia('(prefers-color-scheme: dark)').matches) {
        document.documentElement.setAttribute('data-theme', 'dark');
    }
})();

// Back to top button
function scrollToTop() {
    window.scrollTo({ top: 0, behavior: 'smooth' });
}

// Show/hide back to top button based on scroll position
window.addEventListener('scroll', function() {
    const btn = document.querySelector('.back-to-top');
    if (btn) {
        btn.classList.toggle('visible', window.scrollY > 300);
    }
});

// Toast notification
function showToast(message, duration = 2000) {
    const toast = document.getElementById('toast');
    if (toast) {
        toast.textContent = message;
        toast.classList.add('show');
        setTimeout(() => toast.classList.remove('show'), duration);
    }
}

// Recently viewed resources (localStorage) - scoped per provider
function getProviderName() {
    // Extract provider from path: /aws/Vpc.html -> aws
    const match = window.location.pathname.match(/\/([^/]+)\/[^/]+\.html$/);
    return match ? match[1] : 'default';
}

function getRecentKey() {
    return 'kite-recent-' + getProviderName();
}

function addToRecentlyViewed(name) {
    if (!name || name === 'index') return;
    const key = getRecentKey();
    let recent = JSON.parse(localStorage.getItem(key) || '[]');
    recent = recent.filter(r => r !== name);
    recent.unshift(name);
    recent = recent.slice(0, 5);
    localStorage.setItem(key, JSON.stringify(recent));
}

function renderRecentlyViewed() {
    const container = document.getElementById('recently-viewed');
    const list = container?.querySelector('.recently-list');
    if (!container || !list) return;

    const key = getRecentKey();
    const recent = JSON.parse(localStorage.getItem(key) || '[]');
    if (recent.length === 0) {
        container.style.display = 'none';
        return;
    }

    container.style.display = 'block';
    list.innerHTML = recent.map(name =>
        `<li><a href="${name}.html">${name}</a></li>`
    ).join('');
}

// Track current page view
(function trackPageView() {
    const path = window.location.pathname;
    const match = path.match(/\/([^/]+)\.html$/);
    if (match && match[1] !== 'index') {
        addToRecentlyViewed(match[1]);
    }
    renderRecentlyViewed();
})();

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
        showToast('Copied to clipboard!');
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

    // j/k navigation between resources (like GitHub)
    if ((e.key === 'j' || e.key === 'k') && document.activeElement.tagName !== 'INPUT') {
        const prevLink = document.querySelector('.nav-prev');
        const nextLink = document.querySelector('.nav-next');

        if (e.key === 'j' && nextLink) {
            window.location.href = nextLink.href;
        } else if (e.key === 'k' && prevLink) {
            window.location.href = prevLink.href;
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

// Example tabs switching
function showExample(btn, type) {
    // Hide all example content
    document.querySelectorAll('.example-content').forEach(c => c.classList.remove('active'));
    document.querySelectorAll('.example-tab').forEach(t => t.classList.remove('active'));
    // Show selected
    document.getElementById('example-' + type)?.classList.add('active');
    btn.classList.add('active');
}

// Copy property deep link
function copyPropLink(propName) {
    const url = window.location.origin + window.location.pathname + '#prop-' + propName;
    navigator.clipboard.writeText(url).then(() => {
        showToast('Link copied: #prop-' + propName);
        // Update URL without reload
        history.pushState(null, '', '#prop-' + propName);
    });
}

// Version switcher
function loadVersions() {
    // Fetch versions.json from parent directory (docs root)
    fetch('../versions.json')
        .then(res => res.ok ? res.json() : null)
        .catch(() => null)
        .then(data => {
            if (!data || !data.versions) return;

            const select = document.getElementById('version-select');
            if (!select) return;

            // Get current version from path
            const pathMatch = window.location.pathname.match(/\/([^/]+)\/html\//);
            const currentVersion = pathMatch ? pathMatch[1] : null;

            // Clear and rebuild options
            select.innerHTML = '';
            data.versions
                .sort((a, b) => b.version.localeCompare(a.version, undefined, {numeric: true}))
                .forEach(v => {
                    const opt = document.createElement('option');
                    opt.value = v.path;
                    opt.textContent = 'v' + v.version + (v.version === data.latest ? ' (latest)' : '');
                    opt.selected = (v.path === currentVersion || v.version === currentVersion);
                    select.appendChild(opt);
                });
        });
}

function switchVersion(versionPath) {
    // Replace current version in path with new version
    const currentPath = window.location.pathname;
    const newPath = currentPath.replace(/\/[^/]+\/html\//, '/' + versionPath + '/html/');
    window.location.href = newPath;
}

// Load versions on page load
loadVersions();
