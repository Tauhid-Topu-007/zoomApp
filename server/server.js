// server/server.js - Enhanced multi-laptop connection solution with meeting sync
const { WebSocketServer } = require('ws');
const { networkInterfaces } = require('os');
const { createServer } = require('http');
const express = require('express');
const os = require('os');

const PORT = 8887;
const STUN_PORT = 3478;
const TURN_PORT = 5349;

// Add better error handling
process.on('uncaughtException', (err) => {
    console.error('\n❌ Uncaught Exception:', err);
});

process.on('unhandledRejection', (reason, promise) => {
    console.error('\n❌ Unhandled Rejection at:', promise, 'reason:', reason);
});

// Get all available network interfaces with detailed information
function getNetworkInfo() {
    const interfaces = networkInterfaces();
    const networkInfo = {
        all: [],
        ipv4: [],
        ipv6: [],
        details: {},
        hostname: os.hostname()
    };

    for (const [name, nets] of Object.entries(interfaces)) {
        networkInfo.details[name] = [];
        for (const net of nets) {
            if (!net.internal) { // Skip internal/loopback
                const info = {
                    address: net.address,
                    family: net.family,
                    mac: net.mac,
                    netmask: net.netmask,
                    internal: net.internal
                };
                networkInfo.details[name].push(info);
                networkInfo.all.push(net.address);

                if (net.family === 'IPv4' || net.family === 4) {
                    networkInfo.ipv4.push(net.address);
                } else {
                    networkInfo.ipv6.push(net.address);
                }
            }
        }
    }
    return networkInfo;
}

// Get public IP address
function getPublicIP() {
    try {
        const interfaces = networkInterfaces();
        for (const name of Object.keys(interfaces)) {
            for (const net of interfaces[name]) {
                if (net.family === 'IPv4' && !net.internal) {
                    return net.address;
                }
            }
        }
        return 'localhost';
    } catch (error) {
        console.error('❌ Error getting public IP:', error.message);
        return 'localhost';
    }
}

const networkInfo = getNetworkInfo();
const publicIP = getPublicIP();
const localIPs = networkInfo.ipv4;

// STUN/TURN server configuration with multiple fallbacks
const stunConfig = {
    iceServers: [
        {
            urls: [
                'stun:stun.l.google.com:19302',
                'stun:stun1.l.google.com:19302',
                'stun:stun2.l.google.com:19302',
                'stun:stun3.l.google.com:19302',
                'stun:stun4.l.google.com:19302',
                'stun:stun.services.mozilla.com',
                'stun:stun.stunprotocol.org:3478'
            ]
        },
        // Self-hosted STUN server
        {
            urls: `stun:${publicIP}:${STUN_PORT}`
        },
        // Self-hosted TURN server (optional, requires setup)
        {
            urls: `turn:${publicIP}:${TURN_PORT}`,
            username: 'zoomuser',
            credential: 'zoompass123'
        }
    ],
    iceCandidatePoolSize: 10,
    iceTransportPolicy: 'all',
    bundlePolicy: 'max-bundle'
};

// Express app for WebRTC signaling API
const app = express();
app.use(express.json({ limit: '50mb' }));
app.use(express.urlencoded({ extended: true, limit: '50mb' }));

// CORS and headers
app.use((req, res, next) => {
    res.setHeader('Access-Control-Allow-Origin', '*');
    res.setHeader('Access-Control-Allow-Methods', 'GET, POST, PUT, DELETE, OPTIONS');
    res.setHeader('Access-Control-Allow-Headers', 'Content-Type, Authorization, X-Requested-With');
    res.setHeader('Access-Control-Allow-Credentials', 'true');
    if (req.method === 'OPTIONS') {
        return res.sendStatus(200);
    }
    next();
});

// HTTP server
const httpServer = createServer(app);

// WebSocket server for signaling - listen on all interfaces
const wss = new WebSocketServer({
    server: httpServer,
    perMessageDeflate: false,
    clientTracking: true,
    maxPayload: 50 * 1024 * 1024 // 50MB limit for video
});

const clients = new Map();
const meetings = new Map();
const peerConnections = new Map(); // Store peer connections
const meetingStore = new Map(); // Store meeting information for validation

// Debug endpoint to test connectivity
app.get('/ping', (req, res) => {
    const clientIp = req.headers['x-forwarded-for'] || req.socket.remoteAddress;
    console.log(`🏓 Ping from: ${clientIp}`);
    res.json({
        pong: true,
        time: Date.now(),
        yourIp: clientIp,
        serverIps: localIPs
    });
});

// Enhanced health check endpoint with network info
app.get('/health', (req, res) => {
    const clientIp = req.headers['x-forwarded-for'] || req.socket.remoteAddress;
    const clientPort = req.socket.remotePort;

    console.log(`📊 Health check from: ${clientIp}:${clientPort}`);

    res.json({
        status: 'healthy',
        serverTime: new Date().toISOString(),
        clients: clients.size,
        meetings: meetings.size,
        meetingStore: meetingStore.size,
        peerConnections: Array.from(peerConnections.values())
            .reduce((total, meeting) => total + meeting.size, 0),
        uptime: Math.floor(process.uptime()),
        stunEnabled: true,
        turnEnabled: true,
        publicIP: publicIP,
        availableIPs: localIPs,
        port: PORT,
        yourIP: clientIp,
        yourPort: clientPort,
        hostname: networkInfo.hostname
    });
});

// API endpoint for WebRTC ICE servers configuration
app.get('/api/ice-servers', (req, res) => {
    const clientIp = req.headers['x-forwarded-for'] || req.socket.remoteAddress;
    console.log(`📡 Serving ICE servers configuration to: ${clientIp}`);
    res.json(stunConfig);
});

// Network info endpoint for clients
app.get('/api/network-info', (req, res) => {
    const clientIp = req.headers['x-forwarded-for'] || req.socket.remoteAddress;
    console.log(`🌐 Network info requested from: ${clientIp}`);
    res.json({
        serverIPs: localIPs,
        publicIP: publicIP,
        port: PORT,
        yourIP: clientIp,
        hostname: networkInfo.hostname,
        connectionURLs: localIPs.map(ip => `ws://${ip}:${PORT}`),
        interfaces: networkInfo.details
    });
});

// API endpoint to create a meeting
app.post('/api/meeting', (req, res) => {
    const { meetingId, host, deviceName } = req.body;
    const clientIp = req.headers['x-forwarded-for'] || req.socket.remoteAddress;

    console.log(`📋 Meeting creation API called: ${meetingId} by ${host} from ${clientIp}`);

    if (!meetingStore.has(meetingId)) {
        meetingStore.set(meetingId, {
            id: meetingId,
            host: host,
            hostDevice: deviceName || 'Unknown',
            participants: new Set([host]),
            created: new Date(),
            title: `Meeting ${meetingId}`,
            source: 'api'
        });
    }

    res.json({
        success: true,
        meetingId: meetingId,
        message: 'Meeting created successfully'
    });
});

// API endpoint to get all active meetings
app.get('/api/meetings', (req, res) => {
    const clientIp = req.headers['x-forwarded-for'] || req.socket.remoteAddress;
    console.log(`📋 Meeting list requested from: ${clientIp}`);

    const meetingList = [];
    for (const [meetingId, meeting] of meetingStore) {
        meetingList.push({
            id: meetingId,
            host: meeting.host,
            hostDevice: meeting.hostDevice,
            participants: meeting.participants.size,
            created: meeting.created,
            title: meeting.title || `Meeting ${meetingId}`
        });
    }

    // Also add from meetings map
    for (const [meetingId, meeting] of meetings) {
        if (!meetingStore.has(meetingId)) {
            meetingList.push({
                id: meetingId,
                host: meeting.host,
                hostDevice: meeting.hostDevice || 'Unknown',
                participants: meeting.participants.size,
                created: meeting.created,
                title: meeting.title || `Meeting ${meetingId}`
            });
        }
    }

    res.json({
        meetings: meetingList,
        count: meetingList.length,
        timestamp: Date.now()
    });
});

// API endpoint to validate a specific meeting
app.get('/api/meeting/:meetingId', (req, res) => {
    const { meetingId } = req.params;
    const clientIp = req.headers['x-forwarded-for'] || req.socket.remoteAddress;

    console.log(`🔍 Meeting validation requested for ${meetingId} from: ${clientIp}`);

    const meeting = meetingStore.get(meetingId) || meetings.get(meetingId);
    if (meeting) {
        res.json({
            exists: true,
            meetingId: meetingId,
            host: meeting.host,
            hostDevice: meeting.hostDevice,
            participants: meeting.participants.size,
            created: meeting.created,
            title: meeting.title || `Meeting ${meetingId}`
        });
    } else {
        res.json({
            exists: false,
            meetingId: meetingId,
            message: 'Meeting not found'
        });
    }
});

// Simple root endpoint with enhanced connection info and diagnostics
app.get('/', (req, res) => {
    const clientIp = req.headers['x-forwarded-for'] || req.socket.remoteAddress;
    const clientPort = req.socket.remotePort;
    const serverStats = {
        clients: clients.size,
        meetings: meetings.size,
        meetingStore: meetingStore.size,
        webRTCConnections: Array.from(peerConnections.values())
            .reduce((total, meeting) => total + meeting.size, 0),
        uptime: Math.floor(process.uptime())
    };

    // Generate firewall command based on OS
    const firewallCmd = process.platform === 'win32'
        ? 'netsh advfirewall firewall add rule name="Zoom WebSocket" dir=in action=allow protocol=TCP localport=8887'
        : 'sudo ufw allow 8887/tcp';

    const meetingList = Array.from(meetingStore.entries()).map(([id, meeting]) => ({
        id,
        host: meeting.host,
        hostDevice: meeting.hostDevice,
        participants: meeting.participants.size,
        created: meeting.created
    }));

    res.send(`
        <!DOCTYPE html>
        <html lang="en">
        <head>
            <meta charset="UTF-8">
            <meta name="viewport" content="width=device-width, initial-scale=1.0">
            <title>Zoom WebSocket Server - Multi-Laptop Diagnostic</title>
            <style>
                * {
                    margin: 0;
                    padding: 0;
                    box-sizing: border-box;
                }

                body {
                    font-family: 'Segoe UI', Tahoma, Geneva, Verdana, sans-serif;
                    line-height: 1.6;
                    color: #333;
                    background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
                    min-height: 100vh;
                    padding: 20px;
                }

                .container {
                    max-width: 1400px;
                    margin: 0 auto;
                    background: white;
                    border-radius: 15px;
                    box-shadow: 0 10px 30px rgba(0, 0, 0, 0.2);
                    overflow: hidden;
                }

                header {
                    background: linear-gradient(to right, #3498db, #2c3e50);
                    color: white;
                    padding: 30px;
                    text-align: center;
                }

                h1 {
                    font-size: 2.5rem;
                    margin-bottom: 10px;
                    display: flex;
                    align-items: center;
                    justify-content: center;
                    gap: 15px;
                    flex-wrap: wrap;
                }

                .status-badge {
                    background: #27ae60;
                    color: white;
                    padding: 5px 15px;
                    border-radius: 20px;
                    font-size: 0.9rem;
                    font-weight: bold;
                }

                .warning-badge {
                    background: #f39c12;
                    color: white;
                    padding: 5px 15px;
                    border-radius: 20px;
                    font-size: 0.9rem;
                    font-weight: bold;
                }

                .content {
                    display: grid;
                    grid-template-columns: repeat(auto-fit, minmax(400px, 1fr));
                    gap: 20px;
                    padding: 30px;
                }

                .card {
                    background: #f8f9fa;
                    border-radius: 10px;
                    padding: 20px;
                    border-left: 5px solid;
                    transition: transform 0.3s, box-shadow 0.3s;
                }

                .card:hover {
                    transform: translateY(-5px);
                    box-shadow: 0 5px 15px rgba(0, 0, 0, 0.1);
                }

                .card-success {
                    border-left-color: #27ae60;
                }

                .card-info {
                    border-left-color: #3498db;
                }

                .card-warning {
                    border-left-color: #f39c12;
                }

                .card-primary {
                    border-left-color: #9b59b6;
                }

                .card-danger {
                    border-left-color: #e74c3c;
                }

                h2 {
                    color: #2c3e50;
                    margin-bottom: 15px;
                    font-size: 1.5rem;
                }

                h3 {
                    color: #34495e;
                    margin: 15px 0 10px;
                    font-size: 1.2rem;
                }

                ul {
                    list-style: none;
                    padding-left: 0;
                }

                li {
                    margin: 8px 0;
                    padding: 8px 12px;
                    background: white;
                    border-radius: 5px;
                    border: 1px solid #e0e0e0;
                    display: flex;
                    justify-content: space-between;
                    align-items: center;
                    flex-wrap: wrap;
                }

                code {
                    background: #2c3e50;
                    color: #ecf0f1;
                    padding: 4px 8px;
                    border-radius: 4px;
                    font-family: 'Courier New', monospace;
                    font-size: 0.9rem;
                }

                .ip-list {
                    display: grid;
                    grid-template-columns: repeat(auto-fill, minmax(280px, 1fr));
                    gap: 10px;
                }

                .ip-item {
                    background: white;
                    padding: 15px;
                    border-radius: 8px;
                    text-align: center;
                    border: 2px solid #3498db;
                    cursor: pointer;
                    transition: all 0.3s;
                }

                .ip-item:hover {
                    background: #3498db;
                    color: white;
                    transform: translateY(-2px);
                }

                .ip-item code {
                    background: transparent;
                    color: inherit;
                    font-size: 1rem;
                }

                .stats-grid {
                    display: grid;
                    grid-template-columns: repeat(auto-fit, minmax(150px, 1fr));
                    gap: 15px;
                    margin-top: 20px;
                }

                .stat-item {
                    background: white;
                    padding: 15px;
                    border-radius: 8px;
                    text-align: center;
                    border: 2px solid #3498db;
                }

                .stat-value {
                    font-size: 2rem;
                    font-weight: bold;
                    color: #2c3e50;
                }

                .stat-label {
                    font-size: 0.9rem;
                    color: #7f8c8d;
                    margin-top: 5px;
                }

                .copy-btn {
                    background: #3498db;
                    color: white;
                    border: none;
                    padding: 8px 15px;
                    border-radius: 4px;
                    cursor: pointer;
                    font-size: 0.9rem;
                    transition: background 0.3s;
                    margin: 5px;
                }

                .copy-btn:hover {
                    background: #2980b9;
                }

                .test-btn {
                    background: #27ae60;
                    color: white;
                    border: none;
                    padding: 10px 20px;
                    border-radius: 4px;
                    cursor: pointer;
                    font-size: 1rem;
                    transition: background 0.3s;
                    margin: 5px;
                }

                .test-btn:hover {
                    background: #219a52;
                }

                .client-info {
                    background: #f1c40f;
                    color: #2c3e50;
                    padding: 15px;
                    border-radius: 5px;
                    margin-top: 10px;
                    font-weight: bold;
                }

                footer {
                    text-align: center;
                    padding: 20px;
                    background: #ecf0f1;
                    color: #7f8c8d;
                    margin-top: 30px;
                }

                .troubleshooting {
                    background: #fff3cd;
                    border: 1px solid #ffeeba;
                    padding: 15px;
                    border-radius: 5px;
                    margin-top: 20px;
                }

                .network-test {
                    margin-top: 20px;
                    padding: 15px;
                    background: #d4edda;
                    border: 1px solid #c3e6cb;
                    border-radius: 5px;
                }

                .diagnostic-panel {
                    background: #1e1e1e;
                    color: #00ff00;
                    padding: 15px;
                    border-radius: 5px;
                    font-family: monospace;
                    margin-top: 10px;
                    max-height: 300px;
                    overflow-y: auto;
                }

                .step {
                    margin: 10px 0;
                    padding: 10px;
                    border-left: 3px solid #3498db;
                    background: #f8f9fa;
                }

                .step-number {
                    display: inline-block;
                    width: 25px;
                    height: 25px;
                    background: #3498db;
                    color: white;
                    border-radius: 50%;
                    text-align: center;
                    line-height: 25px;
                    margin-right: 10px;
                }

                .meeting-list {
                    max-height: 300px;
                    overflow-y: auto;
                }

                .meeting-item {
                    background: #e8f4f8;
                    margin: 5px 0;
                    padding: 10px;
                    border-radius: 5px;
                    border-left: 3px solid #3498db;
                }

                .meeting-id {
                    font-weight: bold;
                    color: #2980b9;
                }

                .meeting-host {
                    color: #27ae60;
                }

                .meeting-participants {
                    color: #e67e22;
                }
            </style>
        </head>
        <body>
            <div class="container">
                <header>
                    <h1>
                        🚀 Zoom WebSocket Server
                        <span class="status-badge">MULTI-LAPTOP SUPPORT</span>
                        <span class="warning-badge">v2.0 Diagnostic</span>
                    </h1>
                    <p>Connect multiple laptops for video conferencing with WebRTC</p>
                    <p><strong>Hostname:</strong> ${networkInfo.hostname} | <strong>Your IP:</strong> ${clientIp}</p>
                </header>

                <div class="content">
                    <div class="card card-success">
                        <h2>✅ Server Status</h2>
                        <p><strong>Status:</strong> <span style="color: green; font-weight: bold;">RUNNING</span></p>
                        <p><strong>Port:</strong> ${PORT}</p>
                        <p><strong>Public IP:</strong> ${publicIP}</p>
                        <p><strong>Your IP (from request):</strong> ${clientIp}:${clientPort}</p>
                        <p><strong>Uptime:</strong> ${serverStats.uptime}s</p>
                        <p><strong>Connected Clients:</strong> ${serverStats.clients}</p>
                        <p><strong>Active Meetings:</strong> ${serverStats.meetings}</p>
                        <p><strong>Meeting Store:</strong> ${serverStats.meetingStore}</p>
                    </div>

                    <div class="card card-primary">
                        <h2>🔗 Connection URLs for Other Laptops</h2>
                        <p>Use ONE of these addresses to connect from other laptops:</p>
                        <div class="ip-list">
                            ${localIPs.map(ip => `
                                <div class="ip-item" onclick="copyToClipboard('ws://${ip}:${PORT}')">
                                    <code>ws://${ip}:${PORT}</code>
                                    <br>
                                    <small>Click to copy</small>
                                </div>
                            `).join('')}
                        </div>
                        <div class="client-info">
                            <strong>Your laptop's connection info:</strong><br>
                            IP: ${clientIp}<br>
                            Port: ${clientPort}
                        </div>
                    </div>

                    <div class="card card-info">
                        <h2>📊 Live Statistics</h2>
                        <div class="stats-grid">
                            <div class="stat-item">
                                <div class="stat-value" id="clientCount">${serverStats.clients}</div>
                                <div class="stat-label">Connected Laptops</div>
                            </div>
                            <div class="stat-item">
                                <div class="stat-value" id="meetingCount">${serverStats.meetings}</div>
                                <div class="stat-label">Active Meetings</div>
                            </div>
                            <div class="stat-item">
                                <div class="stat-value" id="webrtcCount">${serverStats.webRTCConnections}</div>
                                <div class="stat-label">WebRTC Pairs</div>
                            </div>
                            <div class="stat-item">
                                <div class="stat-value" id="storeCount">${serverStats.meetingStore}</div>
                                <div class="stat-label">Stored Meetings</div>
                            </div>
                        </div>
                    </div>

                    <div class="card card-info">
                        <h2>📋 Active Meetings</h2>
                        <div class="meeting-list">
                            ${meetingList.length > 0 ? meetingList.map(meeting => `
                                <div class="meeting-item">
                                    <div><span class="meeting-id">${meeting.id}</span></div>
                                    <div><span class="meeting-host">Host: ${meeting.host}</span> <span class="meeting-host">(${meeting.hostDevice || 'Unknown'})</span></div>
                                    <div><span class="meeting-participants">Participants: ${meeting.participants}</span></div>
                                    <div><small>Created: ${new Date(meeting.created).toLocaleString()}</small></div>
                                </div>
                            `).join('') : '<p>No active meetings</p>'}
                        </div>
                        <button class="test-btn" onclick="refreshMeetings()">🔄 Refresh Meetings</button>
                        <button class="test-btn" onclick="createTestMeeting()">➕ Create Test Meeting</button>
                    </div>

                    <div class="card card-info">
                        <h2>📡 Network Interfaces</h2>
                        <h3>Available on this laptop:</h3>
                        <ul>
                            ${Object.entries(networkInfo.details).map(([name, addrs]) => `
                                <li>
                                    <strong>${name}:</strong>
                                    ${addrs.map(addr => `<code>${addr.address}</code> (${addr.family})`).join(', ')}
                                    <br><small>MAC: ${addrs[0]?.mac || 'N/A'}</small>
                                </li>
                            `).join('')}
                        </ul>
                    </div>

                    <div class="card card-warning">
                        <h2>🔧 Diagnostic Tools</h2>
                        <button class="test-btn" onclick="testLocalConnection()">📊 Test Local Connection</button>
                        <button class="test-btn" onclick="testAllInterfaces()">🌐 Test All Interfaces</button>
                        <button class="test-btn" onclick="checkFirewall()">🔥 Check Firewall</button>
                        <button class="test-btn" onclick="showNetworkInfo()">📋 Network Info</button>
                        <button class="test-btn" onclick="listMeetings()">📋 List Meetings</button>

                        <div id="diagnosticOutput" class="diagnostic-panel">
                            Ready to run diagnostics...
                        </div>
                    </div>

                    <div class="card card-danger">
                        <h2>⚠️ Quick Troubleshooting</h2>
                        <div class="step">
                            <span class="step-number">1</span>
                            <strong>Check if server is listening:</strong>
                            <code>netstat -an | find "8887"</code>
                            <button class="copy-btn" onclick="copyToClipboard('netstat -an | find \"8887\"')">Copy</button>
                        </div>
                        <div class="step">
                            <span class="step-number">2</span>
                            <strong>Add Windows Firewall rule (Run as Admin):</strong>
                            <code>${firewallCmd}</code>
                            <button class="copy-btn" onclick="copyToClipboard('${firewallCmd}')">Copy</button>
                        </div>
                        <div class="step">
                            <span class="step-number">3</span>
                            <strong>Test from client laptop:</strong>
                            <code>curl http://${localIPs[0] || 'SERVER_IP'}:${PORT}/health</code>
                            <button class="copy-btn" onclick="copyToClipboard('curl http://${localIPs[0] || 'SERVER_IP'}:${PORT}/health')">Copy</button>
                        </div>
                        <div class="step">
                            <span class="step-number">4</span>
                            <strong>Check available meetings:</strong>
                            <code>curl http://${localIPs[0] || 'SERVER_IP'}:${PORT}/api/meetings</code>
                            <button class="copy-btn" onclick="copyToClipboard('curl http://${localIPs[0] || 'SERVER_IP'}:${PORT}/api/meetings')">Copy</button>
                        </div>
                    </div>

                    <div class="card card-success">
                        <h2>🎯 Multi-Laptop Setup Guide</h2>
                        <div class="step">
                            <span class="step-number">1</span>
                            <strong>On THIS laptop (HOST):</strong>
                            <ul>
                                <li>Server is already running</li>
                                <li>Note your IP from above: <code>${localIPs[0] || '192.168.x.x'}</code></li>
                                <li>Keep this terminal open</li>
                                <li>Run firewall command if needed</li>
                            </ul>
                        </div>
                        <div class="step">
                            <span class="step-number">2</span>
                            <strong>On OTHER laptops (CLIENTS):</strong>
                            <ul>
                                <li>Open Command Prompt and ping the host: <code>ping ${localIPs[0] || 'SERVER_IP'}</code></li>
                                <li>If ping works, test port: <code>telnet ${localIPs[0] || 'SERVER_IP'} ${PORT}</code></li>
                                <li>Run the JavaFX application</li>
                                <li>When prompted, choose "Enter Server IP"</li>
                                <li>Enter this laptop's IP: <code>${localIPs[0] || '192.168.x.x'}</code></li>
                                <li>Port: <code>8887</code></li>
                            </ul>
                        </div>
                    </div>
                </div>

                <footer>
                    <p>Zoom WebSocket Server v2.0 | Multi-Laptop Support | WebRTC with STUN/TURN</p>
                    <p>⏳ Connected Laptops: <span id="footerClientCount">${serverStats.clients}</span> |
                       Active Meetings: <span id="footerMeetingCount">${serverStats.meetings}</span></p>
                    <p>🔧 For support, run diagnostics above or check firewall settings</p>
                </footer>
            </div>

            <script>
                function copyToClipboard(text) {
                    navigator.clipboard.writeText(text).then(() => {
                        alert('✅ Copied to clipboard: ' + text);
                    }).catch(err => {
                        const textarea = document.createElement('textarea');
                        textarea.value = text;
                        document.body.appendChild(textarea);
                        textarea.select();
                        document.execCommand('copy');
                        document.body.removeChild(textarea);
                        alert('✅ Copied to clipboard: ' + text);
                    });
                }

                function appendToDiagnostic(message) {
                    const output = document.getElementById('diagnosticOutput');
                    output.innerHTML += message + '\\n';
                    output.scrollTop = output.scrollHeight;
                }

                async function testLocalConnection() {
                    const output = document.getElementById('diagnosticOutput');
                    output.innerHTML = 'Running local connection test...\\n';

                    try {
                        const response = await fetch('/ping');
                        const data = await response.json();
                        output.innerHTML += '✅ Local connection successful\\n';
                        output.innerHTML += '📊 Server time: ' + new Date(data.time).toLocaleTimeString() + '\\n';
                        output.innerHTML += '🌐 Your IP: ' + data.yourIp + '\\n';
                        output.innerHTML += '🔌 Server IPs: ' + data.serverIps.join(', ') + '\\n';
                    } catch (error) {
                        output.innerHTML += '❌ Local connection failed: ' + error + '\\n';
                    }
                }

                async function testAllInterfaces() {
                    const output = document.getElementById('diagnosticOutput');
                    output.innerHTML = 'Testing all network interfaces...\\n';

                    const ips = ${JSON.stringify(localIPs)};

                    for (const ip of ips) {
                        try {
                            const response = await fetch('http://' + ip + ':${PORT}/ping', {
                                timeout: 2000
                            });
                            const data = await response.json();
                            output.innerHTML += '✅ ' + ip + ':${PORT} - reachable\\n';
                        } catch (error) {
                            output.innerHTML += '❌ ' + ip + ':${PORT} - not reachable\\n';
                        }
                    }
                }

                function checkFirewall() {
                    const output = document.getElementById('diagnosticOutput');
                    output.innerHTML = 'Firewall Check Instructions:\\n';
                    output.innerHTML += '1. Open Command Prompt as Administrator\\n';
                    output.innerHTML += '2. Run: netsh advfirewall firewall show rule name="Zoom WebSocket"\\n';
                    output.innerHTML += '3. If no rule exists, add it:\\n';
                    output.innerHTML += '   netsh advfirewall firewall add rule name="Zoom WebSocket" dir=in action=allow protocol=TCP localport=8887\\n';
                }

                function showNetworkInfo() {
                    const output = document.getElementById('diagnosticOutput');
                    output.innerHTML = 'Network Information:\\n';
                    output.innerHTML += 'Hostname: ${networkInfo.hostname}\\n';
                    output.innerHTML += 'Your IP: ${clientIp}\\n';
                    output.innerHTML += 'Available IPs for clients:\\n';
                    ${JSON.stringify(localIPs)}.forEach(ip => {
                        output.innerHTML += '  - ws://' + ip + ':${PORT}\\n';
                    });
                }

                async function listMeetings() {
                    const output = document.getElementById('diagnosticOutput');
                    output.innerHTML = 'Fetching meetings...\\n';

                    try {
                        const response = await fetch('/api/meetings');
                        const data = await response.json();
                        output.innerHTML += '📋 Found ' + data.count + ' meetings:\\n';
                        data.meetings.forEach(meeting => {
                            output.innerHTML += '  • ' + meeting.id + ' (Host: ' + meeting.host + ', Participants: ' + meeting.participants + ')\\n';
                        });
                    } catch (error) {
                        output.innerHTML += '❌ Failed to fetch meetings: ' + error + '\\n';
                    }
                }

                async function createTestMeeting() {
                    const output = document.getElementById('diagnosticOutput');
                    output.innerHTML = 'Creating test meeting...\\n';

                    const meetingId = Math.floor(Math.random() * 900000 + 100000).toString();
                    const host = 'ServerHost';

                    try {
                        const response = await fetch('/api/meeting', {
                            method: 'POST',
                            headers: {
                                'Content-Type': 'application/json'
                            },
                            body: JSON.stringify({
                                meetingId: meetingId,
                                host: host,
                                deviceName: 'Server'
                            })
                        });

                        const data = await response.json();
                        if (data.success) {
                            output.innerHTML += '✅ Test meeting created: ' + meetingId + '\\n';
                            output.innerHTML += '📋 You can now join with this ID\\n';
                            setTimeout(() => listMeetings(), 1000);
                        } else {
                            output.innerHTML += '❌ Failed to create meeting\\n';
                        }
                    } catch (error) {
                        output.innerHTML += '❌ Error: ' + error + '\\n';
                    }
                }

                async function testPing() {
                    const result = document.getElementById('testResult');
                    result.innerHTML = 'Testing ping...';
                    try {
                        const response = await fetch('/ping');
                        const data = await response.json();
                        result.innerHTML = '✅ Ping successful! Response time: ' + (Date.now() - data.time) + 'ms';
                    } catch (error) {
                        result.innerHTML = '❌ Ping failed: ' + error;
                    }
                }

                async function testHealth() {
                    const result = document.getElementById('testResult');
                    result.innerHTML = 'Testing health...';
                    try {
                        const response = await fetch('/health');
                        const data = await response.json();
                        result.innerHTML = '✅ Server healthy! Connected clients: ' + data.clients + ', Meetings: ' + data.meetings;
                    } catch (error) {
                        result.innerHTML = '❌ Health check failed: ' + error;
                    }
                }

                async function testMeetings() {
                    const result = document.getElementById('testResult');
                    result.innerHTML = 'Fetching meetings...';
                    try {
                        const response = await fetch('/api/meetings');
                        const data = await response.json();
                        result.innerHTML = '✅ Found ' + data.count + ' meetings';
                        if (data.count > 0) {
                            result.innerHTML += '\\n' + data.meetings.map(m => m.id).join(', ');
                        }
                    } catch (error) {
                        result.innerHTML = '❌ Failed to fetch meetings: ' + error;
                    }
                }

                function testWebSocket() {
                    const result = document.getElementById('testResult');
                    result.innerHTML = 'Testing WebSocket connection...';

                    try {
                        const ws = new WebSocket('ws://' + window.location.host);

                        ws.onopen = function() {
                            result.innerHTML = '✅ WebSocket connected successfully!';
                            ws.close();
                        };

                        ws.onerror = function(error) {
                            result.innerHTML = '❌ WebSocket connection failed';
                        };

                        ws.onclose = function() {
                            if (result.innerHTML.includes('Testing')) {
                                result.innerHTML = '❌ WebSocket connection failed - check if server is running';
                            }
                        };
                    } catch (error) {
                        result.innerHTML = '❌ WebSocket test failed: ' + error;
                    }
                }

                function refreshMeetings() {
                    location.reload();
                }

                // Auto-refresh stats
                setInterval(() => {
                    fetch('/health')
                        .then(response => response.json())
                        .then(data => {
                            document.getElementById('clientCount').textContent = data.clients;
                            document.getElementById('meetingCount').textContent = data.meetings;
                            document.getElementById('webrtcCount').textContent = data.peerConnections;
                            document.getElementById('storeCount').textContent = data.meetingStore;
                            document.getElementById('footerClientCount').textContent = data.clients;
                            document.getElementById('footerMeetingCount').textContent = data.meetings;
                        })
                        .catch(error => console.error('Error fetching stats:', error));
                }, 5000);
            </script>
        </body>
        </html>
    `);
});

// WebSocket connection handling
const CONNECTION_TIMEOUT = 30000;

wss.on('listening', () => {
    console.log('\n✅ WebSocket server is listening on all interfaces:');
    console.log('   ' + '-'.repeat(40));
    localIPs.forEach(ip => {
        console.log(`   🔗 ws://${ip}:${PORT}`);
        console.log(`   🌐 http://${ip}:${PORT}`);
    });
});

wss.on('connection', (ws, req) => {
    const clientIp = req.headers['x-forwarded-for'] || req.socket.remoteAddress;
    const clientPort = req.socket.remotePort;
    const deviceId = req.headers['device-id'] || `Device-${Math.floor(Math.random() * 10000)}`;
    const deviceName = req.headers['device-name'] || `Laptop-${Math.floor(Math.random() * 100)}`;

    console.log('\n' + '='.repeat(50));
    console.log(`🔌 NEW CONNECTION ATTEMPT`);
    console.log(`   IP: ${clientIp}`);
    console.log(`   Port: ${clientPort}`);
    console.log(`   Device ID: ${deviceId}`);
    console.log(`   Device Name: ${deviceName}`);
    console.log('='.repeat(50));

    const userId = `User-${Math.floor(Math.random() * 10000)}`;
    const connectionId = `${userId}-${Date.now()}`;

    clients.set(ws, {
        id: userId,
        deviceId: deviceId,
        deviceName: deviceName,
        connectionId: connectionId,
        ip: clientIp,
        port: clientPort,
        audioMuted: false,
        videoOn: false,
        isRecording: false,
        currentMeeting: null,
        connectedAt: new Date(),
        lastActivity: new Date(),
        webrtcReady: false,
        iceCandidates: [],
        userAgent: req.headers['user-agent'] || 'Unknown'
    });

    console.log(`✅ Connection established - UserID: ${userId}, Device: ${deviceName}`);
    console.log(`📊 Total connected clients: ${clients.size}`);

    // Send connection success message
    ws.send(JSON.stringify({
        type: 'CONNECTION_SUCCESS',
        data: {
            userId: userId,
            deviceId: deviceId,
            message: 'Connected to server successfully',
            serverIPs: localIPs,
            yourIP: clientIp,
            timestamp: Date.now()
        }
    }));

    // Send network info to new client
    ws.send(JSON.stringify({
        type: 'NETWORK_INFO',
        data: {
            serverIPs: localIPs,
            yourIP: clientIp,
            port: PORT,
            hostname: networkInfo.hostname
        }
    }));

    // Send ICE server configuration
    ws.send(JSON.stringify({
        type: 'ICE_SERVERS',
        data: stunConfig
    }));

    // Send current meeting list
    sendMeetingListToClient(ws);

    const welcomeMessage = `SYSTEM|global|Server|CONNECTED|${userId}|Welcome! Your IP: ${clientIp}, Device: ${deviceName}`;
    ws.send(welcomeMessage);
    console.log(`📤 Sent welcome to ${userId} (${deviceName})`);

    // Notify others
    broadcast(`SYSTEM|global|Server|USER_JOINED|${userId}|${deviceName} (${deviceId}) joined from ${clientIp}`, ws);

    // Heartbeat
    const heartbeatInterval = setInterval(() => {
        if (ws.readyState === 1) {
            try {
                ws.ping();
                const info = clients.get(ws);
                if (info) info.lastActivity = new Date();
            } catch (error) {
                console.log(`❌ Ping failed for ${userId}:`, error.message);
            }
        }
    }, 15000);

    // Timeout check
    const timeoutCheck = setInterval(() => {
        const clientInfo = clients.get(ws);
        if (clientInfo) {
            const timeSinceLastActivity = Date.now() - clientInfo.lastActivity;
            if (timeSinceLastActivity > CONNECTION_TIMEOUT) {
                console.log(`⏰ Connection timeout for ${userId}, closing...`);
                ws.close(1000, 'Connection timeout');
                clearInterval(timeoutCheck);
            }
        }
    }, 5000);

    ws.on('message', (message) => {
        const clientInfo = clients.get(ws);
        if (clientInfo) {
            clientInfo.lastActivity = new Date();
        }

        const msg = message.toString();

        // Log first 100 chars of message
        console.log(`📨 From ${userId}: ${msg.substring(0, 100)}${msg.length > 100 ? '...' : ''}`);

        // Check if it's a JSON WebRTC message
        try {
            const jsonMsg = JSON.parse(msg);
            if (jsonMsg.type) {
                handleJsonMessage(ws, userId, jsonMsg);
                return;
            }
        } catch (e) {
            // Not JSON, process as regular message
        }

        // Handle pipe-delimited messages
        const parts = msg.split('|');
        if (parts.length >= 4) {
            const type = parts[0];
            const meetingId = parts[1];
            const username = parts[2];
            const content = parts[3];

            switch (type) {
                case 'CHAT':
                    handleChatMessage(ws, userId, meetingId, content);
                    break;
                case 'MEETING_CREATED':
                    handleMeetingCreated(ws, userId, meetingId, content);
                    break;
                case 'MEETING_AVAILABLE':
                    handleMeetingAvailable(ws, userId, meetingId, content);
                    break;
                case 'USER_JOINED':
                    handleUserJoined(ws, userId, meetingId, content);
                    break;
                case 'USER_LEFT':
                    handleUserLeft(ws, userId, meetingId, content);
                    break;
                case 'MEETING_ENDED':
                    handleMeetingEnded(ws, userId, meetingId, content);
                    break;
                case 'AUDIO_STATUS':
                    handleAudioStatus(ws, userId, meetingId, content);
                    break;
                case 'VIDEO_STATUS':
                    handleVideoStatus(ws, userId, meetingId, content);
                    break;
                case 'VIDEO_FRAME':
                    console.log(`🎥 ${userId} sending video frame (${content.length} chars)`);
                    broadcastToMeeting(msg, meetingId, ws);
                    break;
                case 'WEBRTC_SIGNAL':
                    handleWebRTCSignal(ws, userId, meetingId, content);
                    break;
                case 'GET_ALL_MEETINGS':
                case 'GET_MEETINGS':
                    handleGetAllMeetings(ws, userId, meetingId, content);
                    break;
                case 'VALIDATE_MEETING':
                    handleValidateMeeting(ws, userId, meetingId, content);
                    break;
                case 'PING':
                    ws.send(`PONG|${meetingId}|Server|${Date.now()}`);
                    break;
                default:
                    broadcast(msg, ws);
            }
        } else {
            broadcast(msg, ws);
        }
    });

    ws.on('pong', () => {
        const clientInfo = clients.get(ws);
        if (clientInfo) {
            clientInfo.lastActivity = new Date();
        }
    });

    ws.on('close', (code, reason) => {
        console.log(`❌ ${userId} (${deviceName}) disconnected (Code: ${code}, Reason: ${reason})`);
        clearInterval(heartbeatInterval);
        clearInterval(timeoutCheck);

        const userInfo = clients.get(ws);
        if (userInfo && userInfo.currentMeeting) {
            broadcastToMeeting(`USER_LEFT|${userInfo.currentMeeting}|${userId}|${deviceName} disconnected`, userInfo.currentMeeting, ws);
            removeUserFromMeeting(userInfo.currentMeeting, userId);
            cleanupWebRTCConnections(userInfo.currentMeeting, userId);
        }

        clients.delete(ws);
        console.log(`📊 Remaining clients: ${clients.size}`);
        broadcast(`SYSTEM|global|Server|USER_LEFT|${userId}|${deviceName} left`);
    });

    ws.on('error', (error) => {
        console.error(`❌ WebSocket error for ${userId}:`, error.message);
        clearInterval(heartbeatInterval);
        clearInterval(timeoutCheck);
    });
});

// Send meeting list to a specific client
function sendMeetingListToClient(ws) {
    const meetingList = [];
    for (const [meetingId, meeting] of meetingStore) {
        meetingList.push({
            id: meetingId,
            host: meeting.host,
            hostDevice: meeting.hostDevice,
            participants: meeting.participants.size,
            title: meeting.title || `Meeting ${meetingId}`
        });
    }

    // Also add from meetings map
    for (const [meetingId, meeting] of meetings) {
        if (!meetingStore.has(meetingId)) {
            meetingList.push({
                id: meetingId,
                host: meeting.host,
                hostDevice: meeting.hostDevice || 'Unknown',
                participants: meeting.participants.size,
                title: meeting.title || `Meeting ${meetingId}`
            });
        }
    }

    ws.send(JSON.stringify({
        type: 'MEETING_LIST',
        data: meetingList
    }));

    // Also send as pipe-delimited for backward compatibility
    const pipeList = meetingList.map(m => `${m.id}|${m.host}|${m.participants}`).join(';');
    ws.send(`MEETING_LIST|global|Server|${pipeList}`);
}

// JSON Message Handler
function handleJsonMessage(ws, userId, message) {
    const { type, data } = message;
    console.log(`📡 JSON message from ${userId}: ${type}`);

    switch (type) {
        case 'WEBRTC_OFFER':
            if (data.targetUserId) {
                forwardToUser(data.targetUserId, JSON.stringify({
                    type: 'WEBRTC_OFFER',
                    fromUserId: userId,
                    sdp: data.sdp
                }));
            }
            break;

        case 'WEBRTC_ANSWER':
            if (data.targetUserId) {
                forwardToUser(data.targetUserId, JSON.stringify({
                    type: 'WEBRTC_ANSWER',
                    fromUserId: userId,
                    sdp: data.sdp
                }));
            }
            break;

        case 'WEBRTC_ICE_CANDIDATE':
            if (data.targetUserId) {
                forwardToUser(data.targetUserId, JSON.stringify({
                    type: 'WEBRTC_ICE_CANDIDATE',
                    fromUserId: userId,
                    candidate: data.candidate
                }));
            }
            break;

        case 'WEBRTC_READY':
            const clientInfo = clients.get(ws);
            if (clientInfo) {
                clientInfo.webrtcReady = true;
                console.log(`✅ ${userId} WebRTC ready`);
            }
            break;

        case 'PING':
            ws.send(JSON.stringify({
                type: 'PONG',
                timestamp: Date.now(),
                userId: userId
            }));
            break;
    }
}

function forwardToUser(targetUserId, message) {
    for (const [client, userInfo] of clients) {
        if (userInfo.id === targetUserId && client.readyState === 1) {
            client.send(message);
            console.log(`📡 Forwarded message to ${targetUserId}`);
            return true;
        }
    }
    console.log(`❌ Target user ${targetUserId} not found`);
    return false;
}

function handleWebRTCSignal(ws, userId, meetingId, content) {
    broadcastToMeeting(`WEBRTC_SIGNAL|${meetingId}|${userId}|${content}`, meetingId, ws);
}

function cleanupWebRTCConnections(meetingId, userId) {
    const meetingPeers = peerConnections.get(meetingId);
    if (meetingPeers) {
        for (const [key, value] of meetingPeers.entries()) {
            if (key.includes(userId)) {
                meetingPeers.delete(key);
                console.log(`🗑️ Cleaned up WebRTC connection: ${key}`);
            }
        }
    }
}

// Meeting handler functions
function handleChatMessage(ws, userId, meetingId, content) {
    console.log(`💬 ${userId} in meeting ${meetingId}: ${content.substring(0, 50)}...`);
    broadcastToMeeting(`CHAT|${meetingId}|${userId}|${content}`, meetingId, ws);
}

function handleMeetingCreated(ws, userId, meetingId, content) {
    console.log(`🎯 ${userId} created meeting: ${meetingId}`);

    const userInfo = clients.get(ws);
    const deviceName = userInfo ? userInfo.deviceName : 'Unknown';

    // Parse content for additional info
    const contentParts = content.split('|');
    const meetingTitle = contentParts.length > 0 ? contentParts[0] : `Meeting ${meetingId}`;
    const deviceInfo = contentParts.length > 1 ? contentParts[1] : deviceName;

    if (!meetings.has(meetingId)) {
        meetings.set(meetingId, {
            id: meetingId,
            host: userId,
            hostDevice: deviceName,
            participants: new Set([userId]),
            created: new Date(),
            title: meetingTitle,
            deviceInfo: deviceInfo,
            webrtcEnabled: true
        });
    }

    // Store in meetingStore for persistence
    if (!meetingStore.has(meetingId)) {
        meetingStore.set(meetingId, {
            id: meetingId,
            host: userId,
            hostDevice: deviceName,
            participants: new Set([userId]),
            created: new Date(),
            title: meetingTitle,
            deviceInfo: deviceInfo
        });
    }

    if (userInfo) {
        userInfo.currentMeeting = meetingId;
    }

    // Broadcast to ALL clients (including those not in the meeting)
    broadcast(`SYSTEM|${meetingId}|Server|MEETING_CREATED|${userId}|created meeting ${meetingId} on ${deviceName}`, ws);

    // Also send as JSON for clients that prefer JSON
    broadcast(JSON.stringify({
        type: 'MEETING_CREATED',
        data: {
            meetingId: meetingId,
            host: userId,
            hostDevice: deviceName,
            title: meetingTitle,
            timestamp: Date.now()
        }
    }), ws);
}

function handleMeetingAvailable(ws, userId, meetingId, content) {
    console.log(`📢 ${userId} announced meeting available: ${meetingId}`);

    const userInfo = clients.get(ws);
    const deviceName = userInfo ? userInfo.deviceName : 'Unknown';

    // Parse content
    const parts = content.split('|');
    const host = parts.length > 0 ? parts[0] : userId;
    const hostDevice = parts.length > 1 ? parts[1] : deviceName;

    if (!meetingStore.has(meetingId)) {
        meetingStore.set(meetingId, {
            id: meetingId,
            host: host,
            hostDevice: hostDevice,
            participants: new Set([host]),
            created: new Date(),
            title: `Meeting ${meetingId}`
        });
    }

    if (!meetings.has(meetingId)) {
        meetings.set(meetingId, {
            id: meetingId,
            host: host,
            hostDevice: hostDevice,
            participants: new Set([host]),
            created: new Date(),
            title: `Meeting ${meetingId}`
        });
    }

    // Broadcast to all clients
    broadcast(`MEETING_AVAILABLE|${meetingId}|${host}|Meeting available: ${meetingId} hosted by ${host} on ${hostDevice}`, ws);
    broadcast(JSON.stringify({
        type: 'MEETING_AVAILABLE',
        data: {
            meetingId: meetingId,
            host: host,
            hostDevice: hostDevice
        }
    }), ws);
}

function handleGetAllMeetings(ws, userId, meetingId, content) {
    console.log(`📋 ${userId} requested all meetings`);

    sendMeetingListToClient(ws);
}

function handleValidateMeeting(ws, userId, meetingId, content) {
    console.log(`🔍 ${userId} validating meeting: ${meetingId}`);

    const meeting = meetingStore.get(meetingId) || meetings.get(meetingId);

    if (meeting) {
        const response = `MEETING_VALIDATION_RESPONSE|${meetingId}|Server|VALID|${meeting.host}`;
        ws.send(response);

        // Also send as JSON
        ws.send(JSON.stringify({
            type: 'MEETING_VALIDATION_RESPONSE',
            data: {
                meetingId: meetingId,
                valid: true,
                host: meeting.host,
                hostDevice: meeting.hostDevice,
                participants: meeting.participants.size
            }
        }));

        console.log(`✅ Meeting ${meetingId} is valid (host: ${meeting.host})`);
    } else {
        const response = `MEETING_VALIDATION_RESPONSE|${meetingId}|Server|INVALID|Meeting not found`;
        ws.send(response);

        ws.send(JSON.stringify({
            type: 'MEETING_VALIDATION_RESPONSE',
            data: {
                meetingId: meetingId,
                valid: false,
                message: 'Meeting not found'
            }
        }));

        console.log(`❌ Meeting ${meetingId} not found`);
    }
}

function handleUserJoined(ws, userId, meetingId, content) {
    console.log(`✅ ${userId} joined meeting: ${meetingId}`);

    const userInfo = clients.get(ws);
    const deviceName = userInfo ? userInfo.deviceName : 'Unknown';

    const meeting = meetings.get(meetingId);
    if (meeting) {
        meeting.participants.add(userId);
    } else {
        meetings.set(meetingId, {
            id: meetingId,
            host: userId,
            participants: new Set([userId]),
            created: new Date(),
            hostDevice: deviceName,
            title: `Meeting ${meetingId}`
        });
    }

    // Also update meetingStore
    const storeMeeting = meetingStore.get(meetingId);
    if (storeMeeting) {
        storeMeeting.participants.add(userId);
    } else {
        meetingStore.set(meetingId, {
            id: meetingId,
            host: userId,
            hostDevice: deviceName,
            participants: new Set([userId]),
            created: new Date(),
            title: `Meeting ${meetingId}`
        });
    }

    if (userInfo) {
        userInfo.currentMeeting = meetingId;
    }

    broadcastToMeeting(`USER_JOINED|${meetingId}|${userId}|${content}`, meetingId, ws);
    broadcastToMeeting(`SYSTEM|${meetingId}|Server|USER_JOINED|${userId}|${deviceName} joined the meeting`, meetingId);

    // Send ICE server config to new user
    ws.send(JSON.stringify({
        type: 'ICE_SERVERS',
        data: stunConfig
    }));

    // Notify other participants about new peer
    broadcastToMeeting(JSON.stringify({
        type: 'WEBRTC_NEW_PEER',
        data: {
            userId: userId,
            deviceName: deviceName,
            meetingId: meetingId
        }
    }), meetingId, ws);

    // Send updated participant list
    sendParticipantList(meetingId);
}

function handleUserLeft(ws, userId, meetingId, content) {
    console.log(`🚪 ${userId} left meeting: ${meetingId}`);

    const userInfo = clients.get(ws);
    const deviceName = userInfo ? userInfo.deviceName : 'Unknown';

    removeUserFromMeeting(meetingId, userId);

    if (userInfo) {
        userInfo.currentMeeting = null;
    }

    broadcastToMeeting(`USER_LEFT|${meetingId}|${userId}|${content}`, meetingId, ws);
    broadcastToMeeting(`SYSTEM|${meetingId}|Server|USER_LEFT|${userId}|${deviceName} left the meeting`, meetingId);

    broadcastToMeeting(JSON.stringify({
        type: 'WEBRTC_PEER_LEFT',
        data: {
            userId: userId,
            deviceName: deviceName
        }
    }), meetingId, ws);

    // Send updated participant list
    sendParticipantList(meetingId);
}

function sendParticipantList(meetingId) {
    const meeting = meetings.get(meetingId);
    if (!meeting) return;

    const participants = [];
    for (const userId of meeting.participants) {
        for (const [client, info] of clients) {
            if (info.id === userId) {
                participants.push({
                    id: userId,
                    deviceName: info.deviceName,
                    deviceId: info.deviceId,
                    audioMuted: info.audioMuted,
                    videoOn: info.videoOn
                });
                break;
            }
        }
    }

    broadcastToMeeting(JSON.stringify({
        type: 'PARTICIPANT_LIST',
        data: participants
    }), meetingId);
}

function handleMeetingEnded(ws, userId, meetingId, content) {
    console.log(`🔚 ${userId} ended meeting: ${meetingId}`);

    const meeting = meetings.get(meetingId);
    if (meeting && (meeting.host === userId || meeting.host === 'User-' + userId)) {
        broadcastToMeeting(`MEETING_ENDED|${meetingId}|${userId}|${content}`, meetingId);
        broadcastToMeeting(`SYSTEM|${meetingId}|Server|MEETING_ENDED|${userId}|ended the meeting`, meetingId);

        for (const participant of meeting.participants) {
            for (const [client, userInfo] of clients) {
                if (userInfo.id === participant) {
                    userInfo.currentMeeting = null;
                    break;
                }
            }
        }

        peerConnections.delete(meetingId);
        meetings.delete(meetingId);
        // Keep in meetingStore for history
        console.log(`🗑️ Cleaned up all data for meeting ${meetingId}`);
    }
}

function handleAudioStatus(ws, userId, meetingId, content) {
    console.log(`🔊 ${userId} audio status: ${content}`);

    const userInfo = clients.get(ws);
    if (userInfo) {
        userInfo.audioMuted = content.includes('muted');
    }

    broadcastToMeeting(`AUDIO_STATUS|${meetingId}|${userId}|${content}`, meetingId, ws);

    if (content.includes('muted') || content.includes('unmuted') || content.includes('deafened')) {
        broadcastToMeeting(`SYSTEM|${meetingId}|Server|AUDIO_STATUS|${userId}|${content}`, meetingId);
    }

    // Update participant list
    sendParticipantList(meetingId);
}

function handleVideoStatus(ws, userId, meetingId, content) {
    console.log(`🎥 ${userId} video status: ${content}`);

    const userInfo = clients.get(ws);
    if (userInfo) {
        userInfo.videoOn = content.includes('started');
        userInfo.isRecording = content.includes('recording');
    }

    broadcastToMeeting(`VIDEO_STATUS|${meetingId}|${userId}|${content}`, meetingId, ws);

    if (content.includes('started') || content.includes('stopped') || content.includes('recording')) {
        broadcastToMeeting(`SYSTEM|${meetingId}|Server|VIDEO_STATUS|${userId}|${content}`, meetingId);
    }

    // Update participant list
    sendParticipantList(meetingId);
}

function broadcastToMeeting(message, meetingId, sender) {
    const meeting = meetings.get(meetingId);
    if (!meeting) {
        console.log(`❌ Meeting ${meetingId} not found for broadcast`);
        return;
    }

    let sentCount = 0;
    for (const [client, userInfo] of clients) {
        if (client.readyState === 1 &&
            client !== sender &&
            userInfo.currentMeeting === meetingId) {
            try {
                client.send(message);
                sentCount++;
            } catch (error) {
                console.log(`❌ Failed to send to ${userInfo.id}:`, error.message);
            }
        }
    }
    if (sentCount > 0) {
        console.log(`📤 Broadcast to meeting ${meetingId}: sent to ${sentCount} participants`);
    }
}

function removeUserFromMeeting(meetingId, userId) {
    const meeting = meetings.get(meetingId);
    if (meeting) {
        meeting.participants.delete(userId);

        if (meeting.participants.size === 0) {
            meetings.delete(meetingId);
            console.log(`🗑️ Meeting ${meetingId} removed (no participants)`);
        } else if (meeting.host === userId) {
            const newHost = Array.from(meeting.participants)[0];
            meeting.host = newHost;
            broadcastToMeeting(`SYSTEM|${meetingId}|Server|HOST_CHANGED|${newHost}|is now the host`, meetingId);
            console.log(`👑 New host for meeting ${meetingId}: ${newHost}`);
        }
    }

    // Also remove from meetingStore if needed
    const storeMeeting = meetingStore.get(meetingId);
    if (storeMeeting) {
        storeMeeting.participants.delete(userId);
    }
}

function broadcast(message, sender) {
    let sentCount = 0;
    for (const [client, userInfo] of clients) {
        if (client.readyState === 1 && client !== sender) {
            try {
                client.send(message);
                sentCount++;
            } catch (error) {
                console.log(`❌ Failed to broadcast to ${userInfo.id}:`, error.message);
            }
        }
    }
    if (sentCount > 0) {
        console.log(`📤 Global broadcast: sent to ${sentCount} clients`);
    }
}

// Start server with explicit binding
httpServer.listen(PORT, '0.0.0.0', () => {
    console.log('\n' + '='.repeat(70));
    console.log('🚀 ZOOM WEB SOCKET SERVER WITH MULTI-LAPTOP SUPPORT');
    console.log('='.repeat(70));
    console.log(`✅ Server Status: RUNNING`);
    console.log(`📍 Port: ${PORT}`);
    console.log(`💻 Hostname: ${networkInfo.hostname}`);
    console.log(`🌐 Public IP: ${publicIP}`);
    console.log(`🎯 STUN Server: stun:${publicIP}:${STUN_PORT}`);
    console.log(`📡 TURN Server: turn:${publicIP}:${TURN_PORT}`);

    console.log('\n📡 CONNECTION URLs FOR OTHER LAPTOPS:');
    console.log('   ' + '-'.repeat(50));
    localIPs.forEach((ip, index) => {
        console.log(`   ${index + 1}. ws://${ip}:${PORT}`);
        console.log(`      http://${ip}:${PORT} (Web Interface)`);
    });

    console.log('\n🔧 NETWORK INTERFACES:');
    Object.entries(networkInfo.details).forEach(([name, addrs]) => {
        console.log(`   📶 ${name}:`);
        addrs.forEach(addr => {
            console.log(`      - ${addr.address} (${addr.family})`);
        });
    });

    console.log('\n🔍 QUICK TESTS:');
    console.log(`   From THIS laptop: curl http://localhost:${PORT}/health`);
    console.log(`   From CLIENT laptop: curl http://${localIPs[0] || 'SERVER_IP'}:${PORT}/health`);
    console.log(`   List meetings: curl http://${localIPs[0] || 'SERVER_IP'}:${PORT}/api/meetings`);

    console.log('\n⚠️  WINDOWS FIREWALL (Run as Administrator):');
    console.log(`   netsh advfirewall firewall add rule name="Zoom WebSocket" dir=in action=allow protocol=TCP localport=${PORT}`);

    console.log('\n🎯 MULTI-LAPTOP SETUP:');
    console.log('   1. On THIS laptop (HOST):');
    console.log(`      - Server is running on IP: ${localIPs[0] || '192.168.x.x'}`);
    console.log('      - Keep this terminal open');
    console.log('      - Run firewall command if needed');
    console.log('\n   2. On OTHER laptops (CLIENTS):');
    console.log('      - Open Command Prompt and ping the host:');
    console.log(`        ping ${localIPs[0] || 'SERVER_IP'}`);
    console.log('      - If ping works, test port:');
    console.log(`        telnet ${localIPs[0] || 'SERVER_IP'} ${PORT}`);
    console.log('      - Run JavaFX application');
    console.log('      - Choose "Enter Server IP"');
    console.log(`      - Enter: ${localIPs[0] || '192.168.x.x'}`);
    console.log('      - Port: 8887');

    console.log('\n📊 Web Interface:');
    localIPs.forEach(ip => {
        console.log(`   http://${ip}:${PORT}/`);
    });

    console.log('\n⏳ Waiting for connections...\n');
    console.log('='.repeat(70));
});

// Server monitoring
setInterval(() => {
    console.log('\n' + '-'.repeat(40));
    console.log(`📊 SERVER STATUS UPDATE`);
    console.log(`   Connected Clients: ${clients.size}`);
    console.log(`   Active Meetings: ${meetings.size}`);
    console.log(`   Meeting Store: ${meetingStore.size}`);
    console.log(`   WebRTC Pairs: ${Array.from(peerConnections.values()).reduce((total, meeting) => total + meeting.size, 0)}`);

    if (clients.size > 0) {
        console.log(`   👤 Connected clients:`);
        for (const [_, info] of clients) {
            console.log(`      - ${info.id} (${info.deviceName || 'Unknown'}) [${info.ip}]`);
            if (info.currentMeeting) {
                console.log(`        📅 In meeting: ${info.currentMeeting}`);
            }
        }
    }

    if (meetings.size > 0 || meetingStore.size > 0) {
        console.log(`   📅 Meetings:`);
        for (const [id, meeting] of meetings) {
            console.log(`      - Active: ${id}: ${meeting.participants.size} participants (Host: ${meeting.host})`);
        }
        for (const [id, meeting] of meetingStore) {
            if (!meetings.has(id)) {
                console.log(`      - Stored: ${id}: created by ${meeting.host} on ${meeting.hostDevice || 'Unknown'}`);
            }
        }
    }
    console.log('-'.repeat(40));
}, 30000);

// Handle server errors
wss.on('error', (error) => {
    console.error('❌ WebSocket Server error:', error);
});

httpServer.on('error', (error) => {
    console.error('❌ HTTP Server error:', error);
    if (error.code === 'EADDRINUSE') {
        console.error(`   Port ${PORT} is already in use!`);
        console.error('   Close other applications using this port or change PORT variable');
    }
});

// Graceful shutdown
process.on('SIGINT', () => {
    console.log('\n🛑 Shutting down WebSocket server...');

    // Notify all clients
    const shutdownMsg = 'SYSTEM|global|Server|SHUTDOWN|Server is shutting down';
    for (const [client, userInfo] of clients) {
        if (client.readyState === 1) {
            try {
                client.send(shutdownMsg);
            } catch (error) {
                // Ignore errors during shutdown
            }
        }
    }

    // Close server
    wss.close(() => {
        console.log('✅ WebSocket server closed');
        httpServer.close(() => {
            console.log('✅ HTTP server closed');
            process.exit(0);
        });
    });

    // Force exit after 5 seconds
    setTimeout(() => {
        console.log('⚠️ Forcing exit...');
        process.exit(1);
    }, 5000);
});

// Export for testing
module.exports = { wss, clients, meetings, meetingStore, peerConnections };