async function triggerAI() {
    const prompt = document.getElementById('ai-prompt').value;
    const log = document.getElementById('terminal');
    
    if(!prompt) return alert("প্রম্পট লিখুন!");

    log.innerHTML += `<br><span style="color: #d29922;">[CONNECTING]</span> Dialing Neural Bridge...`;

    try {
        const response = await fetch('https://api.github.com/repos/uniquenetworkbd/NetGuard-Sentinel/actions/workflows/sentinel_architect.yml/dispatches', {
            method: 'POST',
            headers: {
                // নিশ্চিত করুন এখানে আপনার টোকেনটি 'Bearer ' সহ আছে
                'Authorization': 'Bearer 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',
                'Accept': 'application/vnd.github.v3+json',
                'Content-Type': 'application/json'
            },
            body: JSON.stringify({ 
                ref: 'main', 
                inputs: { prompt: prompt } 
            })
        });

        // যদি রেসপন্স ২০৪ হয় তার মানে গিটহাব রিকোয়েস্ট গ্রহণ করেছে
        if (response.ok || response.status === 204) {
            log.innerHTML += `<br><span style="color: #7ee787;">[SUCCESS]</span> Agent is working: ${prompt}`;
        } else {
            const errorText = await response.text();
            console.error("GitHub Error:", errorText);
            log.innerHTML += `<br><span style="color: #f85149;">[FAILED]</span> Server rejected Token. (Status: ${response.status})`;
        }
    } catch (error) {
        log.innerHTML += `<br><span style="color: #f85149;">[FAILED]</span> Network error. Check your Internet or Token.`;
    }
}
