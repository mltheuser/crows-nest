console.log("Detail Script Loaded");

// Extract job ID from URL path (e.g., /job/1 -> 1)
const pathParts = window.location.pathname.split('/');
const jobId = pathParts[pathParts.length - 1];

setTimeout(() => {
    fetch(`/api/job/${jobId}`)
        .then(response => response.json())
        .then(job => {
            document.getElementById('loading').style.display = 'none';
            document.getElementById('job-details').style.display = 'block';
            
            document.querySelector('.job-title').textContent = job.title;
            document.querySelector('.company').textContent = job.company;
            document.querySelector('.location').textContent = 'Remote';  // Static location
            document.querySelector('.description').textContent = 
                `This is a great opportunity at ${job.company}.`;
        })
        .catch(error => {
            console.error('Failed to load job details:', error);
            document.getElementById('loading').textContent = 'Failed to load job details.';
        });
}, 100); // 100ms simulated latency
