// server/server.js - CommonJS version
const { WebSocketServer } = require('ws');
const { networkInterfaces } = require('os');
const { createServer } = require('http');
const express = require('express');

const PORT = 8887;
const STUN_PORT = 3478;
const TURN_PORT = 5349;

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

// Get local IP addresses
function getLocalIPs() {
    const interfaces = networkInterfaces();
    const ips = [];

    for (const name of Object.keys(interfaces)) {
        for (const net of interfaces[name]) {
            if ((net.family === 'IPv4' || net.family === 4) && !net.internal) {
                ips.push(net.address);
            }
        }
    }
    return ips;
}

// STUN/TURN server configuration
const publicIP = getPublicIP();
const stunConfig = {
    iceServers: [
        {
            urls: [
                'stun:stun.l.google.com:19302',
                'stun:stun1.l.google.com:19302',
                'stun:stun2.l.google.com:19302',
                'stun:stun3.l.google.com:19302',
                'stun:stun4.l.google.com:19302'
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
    iceCandidatePoolSize: 10
};

// Express app for WebRTC signaling API
const app = express();
app.use(express.json());
app.use((req, res, next) => {
    res.setHeader('Access-Control-Allow-Origin', '*');
    res.setHeader('Access-Control-Allow-Methods', 'GET, POST, OPTIONS');
    res.setHeader('Access-Control-Allow-Headers', 'Content-Type');
    next();
});

// HTTP server
const httpServer = createServer(app);

// WebSocket server for signaling
const wss = new WebSocketServer({
    server: httpServer,
    host: '0.0.0.0'
});

const clients = new Map();
const meetings = new Map();
const peerConnections = new Map(); // Store peer connections

// API endpoint for WebRTC ICE servers configuration
app.get('/api/ice-servers', (req, res) => {
    console.log('📡 Serving ICE servers configuration');
    res.json(stunConfig);
});

// API endpoint to create peer connection offer
app.post('/api/peer-offer', (req, res) => {
    const { meetingId, fromUserId, toUserId, sdp } = req.body;

    console.log(`📡 Peer offer from ${fromUserId} to ${toUserId} in ${meetingId}`);

    // Store the offer
    if (!peerConnections.has(meetingId)) {
        peerConnections.set(meetingId, new Map());
    }

    const meetingPeers = peerConnections.get(meetingId);
    meetingPeers.set(`${fromUserId}_${toUserId}`, {
        from: fromUserId,
        to: toUserId,
        sdp: sdp,
        type: 'offer',
        timestamp: Date.now()
    });

    res.json({ success: true, message: 'Offer stored' });
});

// API endpoint to create peer connection answer
app.post('/api/peer-answer', (req, res) => {
    const { meetingId, fromUserId, toUserId, sdp } = req.body;

    console.log(`📡 Peer answer from ${fromUserId} to ${toUserId} in ${meetingId}`);

    const meetingPeers = peerConnections.get(meetingId);
    if (meetingPeers) {
        meetingPeers.set(`${fromUserId}_${toUserId}`, {
            from: fromUserId,
            to: toUserId,
            sdp: sdp,
            type: 'answer',
            timestamp: Date.now()
        });
    }

    res.json({ success: true, message: 'Answer stored' });
});

// API endpoint to exchange ICE candidates
app.post('/api/ice-candidate', (req, res) => {
    const { meetingId, fromUserId, toUserId, candidate } = req.body;

    console.log(`📡 ICE candidate from ${fromUserId} to ${toUserId} in ${meetingId}`);

    if (!peerConnections.has(meetingId)) {
        peerConnections.set(meetingId, new Map());
    }

    const meetingPeers = peerConnections.get(meetingId);
    const key = `${fromUserId}_${toUserId}_candidates`;
    if (!meetingPeers.has(key)) {
        meetingPeers.set(key, []);
    }

    meetingPeers.get(key).push(candidate);
    res.json({ success: true, message: 'ICE candidate stored' });
});

// API endpoint to get peer connection data
app.get('/api/peer-data/:meetingId/:fromUserId/:toUserId', (req, res) => {
    const { meetingId, fromUserId, toUserId } = req.params;
    console.log(`🔍 Getting peer data for ${fromUserId} -> ${toUserId} in ${meetingId}`);

    const meetingPeers = peerConnections.get(meetingId);
    if (!meetingPeers) {
        return res.json({ data: null });
    }

    const offerKey = `${fromUserId}_${toUserId}`;
    const answerKey = `${toUserId}_${fromUserId}`;
    const candidatesKey = `${toUserId}_${fromUserId}_candidates`;

    const response = {
        offer: meetingPeers.get(offerKey),
        answer: meetingPeers.get(answerKey),
        candidates: meetingPeers.get(candidatesKey) || []
    };

    // Clean up after sending
    if (meetingPeers.has(offerKey)) meetingPeers.delete(offerKey);
    if (meetingPeers.has(answerKey)) meetingPeers.delete(answerKey);
    if (meetingPeers.has(candidatesKey)) meetingPeers.delete(candidatesKey);

    console.log(`📤 Sending peer data`);
    res.json({ data: response });
});

// API endpoint to get all participants in a meeting
app.get('/api/meeting-participants/:meetingId', (req, res) => {
    const { meetingId } = req.params;
    const meeting = meetings.get(meetingId);

    if (!meeting) {
        return res.json({ participants: [] });
    }

    const participants = Array.from(meeting.participants).map(userId => {
        for (const [client, userInfo] of clients) {
            if (userInfo.id === userId) {
                return {
                    id: userId,
                    audioMuted: userInfo.audioMuted,
                    videoOn: userInfo.videoOn,
                    isRecording: userInfo.isRecording
                };
            }
        }
        return { id: userId };
    });

    res.json({ participants });
});

// Health check endpoint
app.get('/health', (req, res) => {
    res.json({
        status: 'healthy',
        clients: clients.size,
        meetings: meetings.size,
        peerConnections: Array.from(peerConnections.values())
            .reduce((total, meeting) => total + meeting.size, 0),
        uptime: Math.floor(process.uptime()),
        stunEnabled: true,
        turnEnabled: true,
        publicIP: publicIP
    });
});

// Simple root endpoint
app.get('/', (req, res) => {
    const localIPs = getLocalIPs();
    const serverStats = {
        clients: clients.size,
        meetings: meetings.size,
        webRTCConnections: Array.from(peerConnections.values())
            .reduce((total, meeting) => total + meeting.size, 0),
        uptime: Math.floor(process.uptime())
    };

    res.send(`
        <!DOCTYPE html>
        <html lang="en">
        <head>
            <meta charset="UTF-8">
            <meta name="viewport" content="width=device-width, initial-scale=1.0">
            <title>Zoom WebSocket Server with STUN/TURN</title>
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
                    max-width: 1200px;
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
                }

                .status-badge {
                    background: #27ae60;
                    color: white;
                    padding: 5px 15px;
                    border-radius: 20px;
                    font-size: 0.9rem;
                    font-weight: bold;
                }

                .content {
                    display: grid;
                    grid-template-columns: repeat(auto-fit, minmax(300px, 1fr));
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
                    grid-template-columns: repeat(auto-fill, minmax(200px, 1fr));
                    gap: 10px;
                }

                .ip-item {
                    background: white;
                    padding: 10px;
                    border-radius: 5px;
                    text-align: center;
                    border: 1px solid #3498db;
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
                    padding: 5px 10px;
                    border-radius: 4px;
                    cursor: pointer;
                    font-size: 0.8rem;
                    transition: background 0.3s;
                }

                .copy-btn:hover {
                    background: #2980b9;
                }

                footer {
                    text-align: center;
                    padding: 20px;
                    background: #ecf0f1;
                    color: #7f8c8d;
                    margin-top: 30px;
                }

                @media (max-width: 768px) {
                    .content {
                        padding: 20px;
                    }

                    h1 {
                        font-size: 2rem;
                        flex-direction: column;
                        gap: 10px;
                    }
                }
            </style>
        </head>
        <body>
            <div class="container">
                <header>
                    <h1>
                        🚀 Zoom WebSocket Server
                        <span class="status-badge">RUNNING</span>
                    </h1>
                    <p>Real-time video conferencing with WebRTC and STUN/TURN support</p>
                </header>

                <div class="content">
                    <div class="card card-success">
                        <h2>✅ Server Information</h2>
                        <p><strong>Port:</strong> ${PORT}</p>
                        <p><strong>Public IP:</strong> ${publicIP}</p>
                        <p><strong>STUN Server:</strong> <code>stun:${publicIP}:${STUN_PORT}</code></p>
                        <p><strong>TURN Server:</strong> <code>turn:${publicIP}:${TURN_PORT}</code></p>
                        <p><strong>TURN Credentials:</strong> zoomuser / zoompass123</p>
                    </div>

                    <div class="card card-info">
                        <h2>📊 Server Statistics</h2>
                        <div class="stats-grid">
                            <div class="stat-item">
                                <div class="stat-value">${serverStats.clients}</div>
                                <div class="stat-label">Connected Clients</div>
                            </div>
                            <div class="stat-item">
                                <div class="stat-value">${serverStats.meetings}</div>
                                <div class="stat-label">Active Meetings</div>
                            </div>
                            <div class="stat-item">
                                <div class="stat-value">${serverStats.webRTCConnections}</div>
                                <div class="stat-label">WebRTC Pairs</div>
                            </div>
                            <div class="stat-item">
                                <div class="stat-value">${serverStats.uptime}s</div>
                                <div class="stat-label">Uptime</div>
                            </div>
                        </div>
                    </div>

                    <div class="card card-info">
                        <h2>🔗 Connection URLs</h2>
                        <h3>Local Access:</h3>
                        <ul>
                            <li>WebSocket: <code>ws://localhost:${PORT}</code>
                                <button class="copy-btn" onclick="copyToClipboard('ws://localhost:${PORT}')">Copy</button></li>
                            <li>Health Check: <code>http://localhost:${PORT}/health</code>
                                <button class="copy-btn" onclick="copyToClipboard('http://localhost:${PORT}/health')">Copy</button></li>
                            <li>ICE Servers: <code>http://localhost:${PORT}/api/ice-servers</code>
                                <button class="copy-btn" onclick="copyToClipboard('http://localhost:${PORT}/api/ice-servers')">Copy</button></li>
                        </ul>

                        <h3>Your Network IPs:</h3>
                        <div class="ip-list">
                            ${localIPs.map(ip => `
                                <div class="ip-item">
                                    <code>${ip}</code>
                                    <button class="copy-btn" onclick="copyToClipboard('${ip}')">Copy IP</button>
                                </div>
                            `).join('')}
                        </div>
                    </div>

                    <div class="card card-warning">
                        <h2>🔧 API Endpoints</h2>
                        <ul>
                            <li><code>GET /health</code> - Server health status</li>
                            <li><code>GET /api/ice-servers</code> - ICE servers configuration</li>
                            <li><code>POST /api/peer-offer</code> - Store peer offer</li>
                            <li><code>POST /api/peer-answer</code> - Store peer answer</li>
                            <li><code>POST /api/ice-candidate</code> - Store ICE candidate</li>
                            <li><code>GET /api/peer-data/:meetingId/:fromUserId/:toUserId</code> - Get peer connection data</li>
                            <li><code>GET /api/meeting-participants/:meetingId</code> - Get meeting participants</li>
                        </ul>
                    </div>

                    <div class="card card-success">
                        <h2>🎯 WebRTC Configuration</h2>
                        <ul>
                            <li><strong>STUN Servers:</strong> Configured for NAT traversal</li>
                            <li><strong>TURN Server:</strong> Optional relay for strict NATs</li>
                            <li><strong>Signaling:</strong> WebSocket + REST API</li>
                            <li><strong>ICE Protocol:</strong> Full support</li>
                            <li><strong>Cross-platform:</strong> Works on all devices</li>
                        </ul>
                    </div>

                    <div class="card card-info">
                        <h2>🚀 Quick Start</h2>
                        <ol style="padding-left: 20px; margin: 15px 0;">
                            <li>Note your IP address: <strong>${publicIP}</strong></li>
                            <li>On client devices, connect to: <code>ws://${publicIP}:${PORT}</code></li>
                            <li>Ensure firewall allows port ${PORT}</li>
                            <li>For internet access, forward port ${PORT} on router</li>
                        </ol>
                    </div>
                </div>

                <footer>
                    <p>Zoom WebSocket Server v1.0.0 | WebRTC with STUN/TURN support</p>
                    <p>⏳ Waiting for client connections...</p>
                </footer>
            </div>

            <script>
                function copyToClipboard(text) {
                    navigator.clipboard.writeText(text).then(() => {
                        alert('Copied to clipboard: ' + text);
                    }).catch(err => {
                        console.error('Failed to copy: ', err);
                    });
                }

                // Auto-refresh stats every 30 seconds
                setInterval(() => {
                    fetch('/health')
                        .then(response => response.json())
                        .then(data => {
                            // Update stats
                            document.querySelectorAll('.stat-item')[0].querySelector('.stat-value').textContent = data.clients;
                            document.querySelectorAll('.stat-item')[1].querySelector('.stat-value').textContent = data.meetings;
                            document.querySelectorAll('.stat-item')[2].querySelector('.stat-value').textContent = data.peerConnections;
                            document.querySelectorAll('.stat-item')[3].querySelector('.stat-value').textContent = data.uptime + 's';
                        })
                        .catch(error => console.error('Error fetching stats:', error));
                }, 30000);
            </script>
        </body>
        </html>
    `);
});

// Start server
const localIPs = getLocalIPs();
httpServer.listen(PORT, '0.0.0.0', () => {
    console.log(`\n🚀 ZOOM WEB SOCKET SERVER WITH STUN/TURN`);
    console.log(`=========================================`);
    console.log(`✅ Server Status: RUNNING`);
    console.log(`📍 Signaling Port: ${PORT}`);
    console.log(`🌐 Public IP: ${publicIP}`);
    console.log(`🎯 STUN Server: stun:${publicIP}:${STUN_PORT}`);
    console.log(`📡 TURN Server: turn:${publicIP}:${TURN_PORT}`);
    console.log(`\n🔗 WebSocket URLs:`);
    console.log(`   - ws://localhost:${PORT}`);
    localIPs.forEach(ip => {
        console.log(`   - ws://${ip}:${PORT}`);
    });

    console.log(`\n🔧 API Endpoints:`);
    console.log(`   - ICE Servers: http://localhost:${PORT}/api/ice-servers`);
    console.log(`   - Health Check: http://localhost:${PORT}/health`);
    console.log(`   - Web Interface: http://localhost:${PORT}/`);

    console.log(`\n🎯 WebRTC Configuration:`);
    console.log(`   1. STUN Servers: Configured for NAT traversal`);
    console.log(`   2. TURN Server: Optional relay for strict NATs`);
    console.log(`   3. Signaling: WebSocket + REST API`);
    console.log(`   4. ICE Protocol: Full support`);
    console.log(`\n⏳ Waiting for connections...\n`);
});

// WebSocket connection handling
const CONNECTION_TIMEOUT = 30000;

wss.on('connection', (ws, req) => {
    const clientIp = req.socket.remoteAddress.replace(/^.*:/, '');
    const clientPort = req.socket.remotePort;
    console.log(`🔗 New client connected from: ${clientIp}:${clientPort}`);

    const userId = `User-${Math.floor(Math.random() * 1000)}`;
    const connectionId = `${userId}-${Date.now()}`;

    clients.set(ws, {
        id: userId,
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
        iceCandidates: []
    });

    // Send ICE server configuration immediately
    ws.send(JSON.stringify({
        type: 'ICE_SERVERS',
        data: stunConfig
    }));

    const welcomeMessage = `SYSTEM|global|Server|CONNECTED|${userId}|Welcome! ICE servers configured`;
    ws.send(welcomeMessage);
    console.log(`📤 Sent welcome with ICE config to ${userId}`);

    // Notify others
    broadcast(`SYSTEM|global|Server|USER_JOINED|${userId}|${userId} joined from ${clientIp}`, ws);

    // Heartbeat
    const heartbeatInterval = setInterval(() => {
        if (ws.readyState === 1) {
            try {
                ws.ping();
                clients.get(ws).lastActivity = new Date();
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

        // Check if it's a JSON WebRTC message
        try {
            const jsonMsg = JSON.parse(msg);
            if (jsonMsg.type && jsonMsg.type.startsWith('WEBRTC_')) {
                handleWebRTCMessage(ws, userId, jsonMsg);
                return;
            }
        } catch (e) {
            // Not JSON, process as regular message
        }

        console.log(`📨 ${userId}: ${msg.substring(0, 100)}${msg.length > 100 ? '...' : ''}`);

        const parts = msg.split('|', 4);
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
                    // For WebRTC, we'll handle video frames differently
                    console.log(`🎥 ${userId} sending video frame via WebRTC`);
                    break;
                case 'WEBRTC_SIGNAL':
                    handleWebRTCSignal(ws, userId, meetingId, content);
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
        console.log(`❌ ${userId} disconnected (Code: ${code}, Reason: ${reason})`);
        clearInterval(heartbeatInterval);
        clearInterval(timeoutCheck);

        const userInfo = clients.get(ws);
        if (userInfo && userInfo.currentMeeting) {
            broadcastToMeeting(`USER_LEFT|${userInfo.currentMeeting}|${userId}|${userId} disconnected`, userInfo.currentMeeting, ws);
            removeUserFromMeeting(userInfo.currentMeeting, userId);

            // Clean up WebRTC connections
            cleanupWebRTCConnections(userInfo.currentMeeting, userId);
        }
        clients.delete(ws);
        broadcast(`SYSTEM|global|Server|USER_LEFT|${userId}|${userId} left`);
    });

    ws.on('error', (error) => {
        console.error(`❌ WebSocket error for ${userId}:`, error.message);
        clearInterval(heartbeatInterval);
        clearInterval(timeoutCheck);
    });
});

// WebRTC Message Handler
function handleWebRTCMessage(ws, userId, message) {
    const { type, data } = message;
    console.log(`📡 WebRTC message from ${userId}: ${type}`);

    switch (type) {
        case 'WEBRTC_OFFER':
            // Forward offer to target user
            forwardWebRTCMessage(data.targetUserId, {
                type: 'WEBRTC_OFFER',
                data: {
                    fromUserId: userId,
                    sdp: data.sdp
                }
            });
            break;

        case 'WEBRTC_ANSWER':
            // Forward answer to target user
            forwardWebRTCMessage(data.targetUserId, {
                type: 'WEBRTC_ANSWER',
                data: {
                    fromUserId: userId,
                    sdp: data.sdp
                }
            });
            break;

        case 'WEBRTC_ICE_CANDIDATE':
            // Forward ICE candidate to target user
            forwardWebRTCMessage(data.targetUserId, {
                type: 'WEBRTC_ICE_CANDIDATE',
                data: {
                    fromUserId: userId,
                    candidate: data.candidate
                }
            });
            break;

        case 'WEBRTC_READY':
            const clientInfo = clients.get(ws);
            if (clientInfo) {
                clientInfo.webrtcReady = true;
                console.log(`✅ ${userId} WebRTC ready`);
            }
            break;
    }
}

function forwardWebRTCMessage(targetUserId, message) {
    for (const [client, userInfo] of clients) {
        if (userInfo.id === targetUserId && client.readyState === 1) {
            client.send(JSON.stringify(message));
            console.log(`📡 Forwarded ${message.type} from ${message.data.fromUserId} to ${targetUserId}`);
            return;
        }
    }
    console.log(`❌ Target user ${targetUserId} not found for WebRTC forwarding`);
}

function handleWebRTCSignal(ws, userId, meetingId, content) {
    // Legacy WebRTC signaling support
    broadcastToMeeting(`WEBRTC_SIGNAL|${meetingId}|${userId}|${content}`, meetingId, ws);
}

function cleanupWebRTCConnections(meetingId, userId) {
    const meetingPeers = peerConnections.get(meetingId);
    if (meetingPeers) {
        // Remove all connections involving this user
        for (const [key, value] of meetingPeers.entries()) {
            if (key.includes(userId)) {
                meetingPeers.delete(key);
                console.log(`🗑️  Cleaned up WebRTC connection: ${key}`);
            }
        }
    }
}

// Existing handler functions
function handleChatMessage(ws, userId, meetingId, content) {
    console.log(`💬 ${userId} in meeting ${meetingId}: ${content}`);
    broadcastToMeeting(`CHAT|${meetingId}|${userId}|${content}`, meetingId, ws);
}

function handleMeetingCreated(ws, userId, meetingId, content) {
    console.log(`🎯 ${userId} created meeting: ${meetingId}`);

    if (!meetings.has(meetingId)) {
        meetings.set(meetingId, {
            id: meetingId,
            host: userId,
            participants: new Set([userId]),
            created: new Date(),
            webrtcEnabled: true
        });
    }

    const userInfo = clients.get(ws);
    if (userInfo) {
        userInfo.currentMeeting = meetingId;
    }

    broadcast(`SYSTEM|${meetingId}|Server|MEETING_CREATED|${userId}|created meeting ${meetingId}`);
}

function handleUserJoined(ws, userId, meetingId, content) {
    console.log(`✅ ${userId} joined meeting: ${meetingId}`);

    const meeting = meetings.get(meetingId);
    if (meeting) {
        meeting.participants.add(userId);
    } else {
        meetings.set(meetingId, {
            id: meetingId,
            host: userId,
            participants: new Set([userId]),
            created: new Date(),
            webrtcEnabled: true
        });
    }

    const userInfo = clients.get(ws);
    if (userInfo) {
        userInfo.currentMeeting = meetingId;
    }

    broadcastToMeeting(`USER_JOINED|${meetingId}|${userId}|${content}`, meetingId, ws);
    broadcastToMeeting(`SYSTEM|${meetingId}|Server|USER_JOINED|${userId}|joined the meeting`, meetingId);

    // Send ICE server config to new user
    ws.send(JSON.stringify({
        type: 'ICE_SERVERS',
        data: stunConfig
    }));

    // Notify other participants about new WebRTC peer
    broadcastToMeeting(JSON.stringify({
        type: 'WEBRTC_NEW_PEER',
        data: { userId: userId }
    }), meetingId, ws);
}

function handleUserLeft(ws, userId, meetingId, content) {
    console.log(`🚪 ${userId} left meeting: ${meetingId}`);
    removeUserFromMeeting(meetingId, userId);

    const userInfo = clients.get(ws);
    if (userInfo) {
        userInfo.currentMeeting = null;
    }

    broadcastToMeeting(`USER_LEFT|${meetingId}|${userId}|${content}`, meetingId, ws);
    broadcastToMeeting(`SYSTEM|${meetingId}|Server|USER_LEFT|${userId}|left the meeting`, meetingId);

    // Notify about WebRTC peer removal
    broadcastToMeeting(JSON.stringify({
        type: 'WEBRTC_PEER_LEFT',
        data: { userId: userId }
    }), meetingId, ws);
}

function handleMeetingEnded(ws, userId, meetingId, content) {
    console.log(`🔚 ${userId} ended meeting: ${meetingId}`);

    const meeting = meetings.get(meetingId);
    if (meeting && meeting.host === userId) {
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

        // Clean up all WebRTC connections for this meeting
        peerConnections.delete(meetingId);
        meetings.delete(meetingId);
        console.log(`🗑️  Cleaned up all WebRTC connections for meeting ${meetingId}`);
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
    console.log(`📤 Broadcast to meeting ${meetingId}: ${message.substring(0, 50)}... (sent to ${sentCount} participants)`);
}

function removeUserFromMeeting(meetingId, userId) {
    const meeting = meetings.get(meetingId);
    if (meeting) {
        meeting.participants.delete(userId);

        if (meeting.participants.size === 0) {
            meetings.delete(meetingId);
            console.log(`🗑️  Meeting ${meetingId} removed (no participants)`);
        } else if (meeting.host === userId) {
            const newHost = Array.from(meeting.participants)[0];
            meeting.host = newHost;
            broadcastToMeeting(`SYSTEM|${meetingId}|Server|HOST_CHANGED|${newHost}|is now the host`, meetingId);
            console.log(`👑 New host for meeting ${meetingId}: ${newHost}`);
        }
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
    console.log(`📤 Global broadcast: ${message.substring(0, 50)}... (sent to ${sentCount} clients)`);
}

// Server monitoring
setInterval(() => {
    console.log(`\n📊 Server Status - Clients: ${clients.size}, Meetings: ${meetings.size}, WebRTC Pairs: ${Array.from(peerConnections.values()).reduce((total, meeting) => total + meeting.size, 0)}`);

    for (const [meetingId, meeting] of meetings) {
        console.log(`   Meeting ${meetingId}:`);
        console.log(`     - Host: ${meeting.host}`);
        console.log(`     - Participants: ${meeting.participants.size}`);
        console.log(`     - WebRTC: ${meeting.webrtcEnabled ? 'Enabled' : 'Disabled'}`);

        // Show participant details
        for (const participant of meeting.participants) {
            for (const [client, userInfo] of clients) {
                if (userInfo.id === participant) {
                    console.log(`       • ${participant} (Audio: ${userInfo.audioMuted ? 'Muted' : 'Unmuted'}, Video: ${userInfo.videoOn ? 'On' : 'Off'}, Recording: ${userInfo.isRecording ? 'Yes' : 'No'})`);
                    break;
                }
            }
        }
    }

    // Show WebRTC connections
    for (const [meetingId, meetingPeers] of peerConnections) {
        if (meetingPeers.size > 0) {
            console.log(`   WebRTC Connections in ${meetingId}:`);
            for (const [key, value] of meetingPeers.entries()) {
                console.log(`       • ${key} - ${value.type} (${new Date(value.timestamp).toLocaleTimeString()})`);
            }
        }
    }
}, 30000);

// Handle server errors
wss.on('error', (error) => {
    console.error('❌ Server error:', error);
});

// Graceful shutdown
process.on('SIGINT', () => {
    console.log('\n🔴 Shutting down WebSocket server...');

    // Notify all clients
    for (const [client, userInfo] of clients) {
        if (client.readyState === 1) {
            client.send('SYSTEM|global|Server|SHUTDOWN|Server is shutting down');
        }
    }

    wss.close(() => {
        console.log('✅ WebSocket server closed gracefully');
        process.exit(0);
    });
});

// Export for testing if needed
module.exports = { wss, clients, meetings, peerConnections };