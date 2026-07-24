
const statusEl = document.getElementById('obd-status');

// Target backend socket for live data, but we use REST for
// occasional stuff
const socket = new WebSocket(`ws://${window.location.host}/obd`);

socket.addEventListener('open', (event) => {
    console.log("Socket connection opened.");

    // Update status indicator
    statusEl.textContent = 'Connected';
    statusEl.className = 'status-online';
});

socket.addEventListener('close', (event) => {
    // Handle duplicate instances
    if (event.code === 4001 || event.reason.includes("already connected")) {
        console.log("Socket connection closed due to existing instance.");
        socket.close(4001, "Already connected elsewhere.")
        alert("OBD Dashboard is already active in another tab!");
    } else {
        console.log("Socket connection closed.")
    }

    // Update status indicator
    statusEl.textContent = 'Disconnected';
    statusEl.className = 'status-offline';
});

socket.addEventListener('message', (event) => {
    console.log('OBD Data received:', event.data)
    document.getElementById('output').textContent = event.data;
});

