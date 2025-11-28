console.log("Job Board Script Loaded");

setTimeout(() => {
    document.getElementById('loading').style.display = 'none';
    const list = document.getElementById('job-list');

    // In a real app, this would fetch from an API
    const jobs = [
        { id: 1, title: 'Junior Kotlin Developer', company: 'JetBrains' },
        { id: 2, title: 'Senior AI Engineer', company: 'OpenAI' }
    ];

    jobs.forEach(job => {
        const li = document.createElement('li');
        li.className = 'job-item';
        // We create standard links
        li.innerHTML = `<a href="/job/${job.id}">${job.title} - ${job.company}</a>`;
        list.appendChild(li);
    });
}, 500); // 500ms Simulated Latency