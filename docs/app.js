async function triggerAI() {
    const prompt = document.getElementById('ai-prompt').value;
    const log = document.getElementById('terminal');
    
    if(!prompt) {
        alert("দয়া করে একটি প্রম্পট লিখুন!");
        return;
    }

    log.innerHTML += `<br><span style="color: #d29922;">[CONNECTING]</span> Dialing Neural Bridge...`;

    try {
        const response = await fetch('https://api.github.com/repos/uniquenetworkbd/NetGuard-Sentinel/actions/workflows/sentinel_architect.yml/dispatches', {
            method: 'POST',
            headers: {
                // এখানে 'Bearer ' যোগ করা হয়েছে, যা আগে মিসিং ছিল
                'Authorization': 'Bearer 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',
                'Accept': 'application/vnd.github.v3+json',
                'Content-Type': 'application/json'
            },
            body: JSON.stringify({ 
                ref: 'main', 
                inputs: { prompt: prompt } 
            })
        });

        // GitHub API সফল হলে ২৪৪ (No Content) স্ট্যাটাস দেয়
        if (response.status === 204 || response.ok) {
            log.innerHTML += `<br><span style="color: #7ee787;">[SUCCESS]</span> Agent is working: ${prompt}`;
        } else {
            const errorData = await response.json();
            console.log(errorData);
            throw new Error('Bridge Failed');
        }
    } catch (error) {
        log.innerHTML += `<br><span style="color: #f85149;">[FAILED]</span> Connection Lost. Please check Token or Repository Name.`;
    }
}
