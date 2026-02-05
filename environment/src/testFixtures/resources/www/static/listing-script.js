console.log("Listing Script Loaded");

// Get page from URL query params
const urlParams = new URLSearchParams(window.location.search);
const page = parseInt(urlParams.get('page')) || 1;

// Detect page type from URL
const pathname = window.location.pathname;
const isButtonPagination = pathname.includes('jobs-button');
const isUrlPagination = pathname.includes('/jobs') && !isButtonPagination;
const isIndexPage = pathname === '/' || pathname === '';

setTimeout(() => {
    fetch(`/api/jobs?page=${page}`)
        .then(response => response.json())
        .then(jobs => {
            document.getElementById('loading').style.display = 'none';
            const list = document.getElementById('job-list');
            
            jobs.forEach(job => {
                const div = document.createElement('div');
                div.className = 'job-item';
                div.innerHTML = `
                    <a class="job-title" href="/job/${job.id}">${job.title}</a>
                    <span class="company">${job.company}</span>
                `;
                list.appendChild(div);
            });
            
            // Setup pagination based on page type (index has no pagination)
            if (isButtonPagination) {
                const nextBtn = document.getElementById('next-btn');
                if (nextBtn) {
                    nextBtn.style.display = 'inline-block';
                    nextBtn.addEventListener('click', () => {
                        // Page 1 -> 2, Page 2+ -> 2 (simulate stuck last page)
                        const nextPage = page === 1 ? 2 : 2;
                        window.location.href = `/jobs-button?page=${nextPage}`;
                    });
                }
            } else if (isUrlPagination) {
                const nextLink = document.getElementById('next-page-link');
                if (nextLink && page < 2) {  // Only show next on page 1
                    nextLink.style.display = 'inline-block';
                    nextLink.href = `/jobs?page=${page + 1}`;
                }
            }
            // Index page has no pagination controls
        })
        .catch(error => {
            console.error('Failed to load jobs:', error);
            document.getElementById('loading').textContent = 'Failed to load jobs.';
        });
}, 100); // 100ms simulated latency
