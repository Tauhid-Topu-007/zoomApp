// server/server.js
import { WebSocketServer } from 'ws';
import { networkInterfaces } from 'os';
import { createServer } from 'http';

const PORT = 8887;

// Get local IP addresses for better connection info
function getLocalIPs() {
    const interfaces = networkInterfaces();
    const ips = [];

    for (const name of Object.keys(interfaces)) {
        for (const net of interfaces[name]) {
            // Include both IPv4 and IPv6, exclude internal addresses
            if ((net.family === 'IPv4' || net.family === 4) && !net.internal) {
                ips.push(net.address);
            }
        }
    }
    return ips;
}

// Create HTTP server for health checks
const httpServer = createServer((req, res) => {
    if (req.url === '/health') {
        res.writeHead(200, { 'Content-Type': 'application/json' });
        res.end(JSON.stringify({
            status: 'healthy',
            clients: clients.size,
            meetings: meetings.size,
            uptime: process.uptime()
        }));
    } else {
        res.writeHead(200, { 'Content-Type': 'text/plain' });
        res.end('Zoom WebSocket Server is running\n');
    }
});

// WebSocket server
const wss = new WebSocketServer({
    server: httpServer,
    host: '0.0.0.0'  // This allows connections from any IP
});

const clients = new Map();
const meetings = new Map();

// Start both HTTP and WebSocket servers
httpServer.listen(PORT, '0.0.0.0', () => {
    const localIPs = getLocalIPs();

    console.log(`\n🚀 ZOOM WEB SOCKET SERVER STARTED`);
    console.log(`=========================================`);
    console.log(`✅ Server Status: RUNNING`);
    console.log(`📍 Port: ${PORT}`);
    console.log(`🌐 Bind Address: 0.0.0.0 (All interfaces)`);
    console.log(`\n🔗 CONNECTION URLs:`);
    console.log(`   Local Access:`);
    console.log(`   - ws://localhost:${PORT}`);
    console.log(`   - http://localhost:${PORT}/health`);

    console.log(`\n   Network Access:`);
    localIPs.forEach(ip => {
        console.log(`   - ws://${ip}:${PORT}`);
        console.log(`   - http://${ip}:${PORT}/health`);
    });

    console.log(`\n📊 Server Info:`);
    console.log(`   - Max Connections: Unlimited`);
    console.log(`   - Ping Interval: 15 seconds`);
    console.log(`   - Timeout: 30 seconds`);
    console.log(`   - Protocol: WebSocket`);

    console.log(`\n🎯 Multi-Device Setup Instructions:`);
    console.log(`   1. Note your IP address: ${localIPs[0] || 'Check network settings'}`);
    console.log(`   2. On client devices, connect to: ws://YOUR_IP:${PORT}`);
    console.log(`   3. Ensure firewall allows port ${PORT}`);
    console.log(`   4. For internet access, forward port ${PORT} on router`);

    console.log(`\n🔧 Testing:`);
    console.log(`   - Health check: http://${localIPs[0] || 'localhost'}:${PORT}/health`);
    console.log(`   - Test connection: Open browser console and run:`);
    console.log(`     var ws = new WebSocket("ws://${localIPs[0] || 'localhost'}:${PORT}")`);

    console.log(`\n⏳ Waiting for client connections...\n`);
});

// ... (rest of your existing server code remains the same)
// Connection timeout handling
const CONNECTION_TIMEOUT = 30000; // 30 seconds

wss.on('connection', (ws, req) => {
    const clientIp = req.socket.remoteAddress.replace(/^.*:/, ''); // Remove IPv6 prefix
    const clientPort = req.socket.remotePort;
    console.log(`🔗 New client connected from: ${clientIp}:${clientPort}`);

    // Assign a random ID
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
        lastActivity: new Date()
    });

    // Send immediate welcome message with connection info
    const welcomeMessage = `SYSTEM|global|Server|CONNECTED|${userId}|Welcome! Connected from ${clientIp}`;
    ws.send(welcomeMessage);
    console.log(`📤 Sent welcome to ${userId}: ${welcomeMessage}`);

    // Notify others about new connection
    broadcast(`SYSTEM|global|Server|USER_JOINED|${userId}|${userId} joined from ${clientIp}`, ws);

    // Set up heartbeat/ping mechanism
    const heartbeatInterval = setInterval(() => {
        if (ws.readyState === 1) { // OPEN
            try {
                ws.ping();
                clients.get(ws).lastActivity = new Date();
            } catch (error) {
                console.log(`❌ Ping failed for ${userId}:`, error.message);
            }
        }
    }, 15000); // Ping every 15 seconds

    // Handle connection timeout
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
        console.log(`📨 ${userId} (${clientIp}): ${msg}`);

        // Parse message format: "TYPE|MEETING_ID|USERNAME|CONTENT"
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
                case 'AUDIO_CONTROL':
                    handleAudioControl(ws, userId, meetingId, content);
                    break;
                case 'VIDEO_CONTROL':
                    handleVideoControl(ws, userId, meetingId, content);
                    break;
                case 'PING':
                    // Handle ping from client
                    ws.send(`PONG|${meetingId}|Server|${Date.now()}`);
                    break;
                   case 'VIDEO_STATUS':
                       console.log(`🎥 ${userId} video status: ${content}`);

                       // Broadcast video status to all meeting participants
                       broadcastToMeeting(`VIDEO_STATUS|${meetingId}|${userId}|${content}`, meetingId, ws);

                       // Broadcast system message for important video events
                       if (content.includes('HOST_VIDEO_STARTED') || content.includes('HOST_VIDEO_STOPPED')) {
                           broadcastToMeeting(`SYSTEM|${meetingId}|Server|VIDEO_STATUS|${userId}|${content}`, meetingId);
                       }
                       break;

                     case 'VIDEO_FRAME':
                         console.log(`🎥 ${userId} sending video frame (${content.length} bytes)`);

                         // Broadcast video frame to all other participants in the meeting
                         broadcastToMeeting(`VIDEO_FRAME|${meetingId}|${userId}|${content}`, meetingId, ws);
                         break;
                default:
                    // Default broadcast for unknown message types
                    broadcast(msg, ws);
            }
        } else {
            // Fallback: broadcast raw message
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
        console.log(`❌ ${userId} (${clientIp}) disconnected - Code: ${code}, Reason: ${reason}`);

        clearInterval(heartbeatInterval);
        clearInterval(timeoutCheck);

        const userInfo = clients.get(ws);
        if (userInfo && userInfo.currentMeeting) {
            // Notify meeting participants that user left
            broadcastToMeeting(`USER_LEFT|${userInfo.currentMeeting}|${userId}|${userId} disconnected`, userInfo.currentMeeting, ws);
            removeUserFromMeeting(userInfo.currentMeeting, userId);
        }
        clients.delete(ws);
        broadcast(`SYSTEM|global|Server|USER_LEFT|${userId}|${userId} left the chat`);
    });

    ws.on('error', (error) => {
        console.error(`❌ WebSocket error for ${userId}:`, error.message);
        clearInterval(heartbeatInterval);
        clearInterval(timeoutCheck);
    });
});

// ... (keep all your existing handler functions the same)
// Message Handlers
function handleChatMessage(ws, userId, meetingId, content) {
    console.log(`💬 ${userId} in meeting ${meetingId}: ${content}`);
    broadcastToMeeting(`CHAT|${meetingId}|${userId}|${content}`, meetingId, ws);
}

function handleMeetingCreated(ws, userId, meetingId, content) {
    console.log(`🎯 ${userId} created meeting: ${meetingId}`);

    // Create meeting if it doesn't exist
    if (!meetings.has(meetingId)) {
        meetings.set(meetingId, {
            id: meetingId,
            host: userId,
            participants: new Set([userId]),
            created: new Date()
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
        // Create meeting if it doesn't exist (for quick join)
        meetings.set(meetingId, {
            id: meetingId,
            host: userId, // First joiner becomes host
            participants: new Set([userId]),
            created: new Date()
        });
    }

    const userInfo = clients.get(ws);
    if (userInfo) {
        userInfo.currentMeeting = meetingId;
    }

    broadcastToMeeting(`USER_JOINED|${meetingId}|${userId}|${content}`, meetingId, ws);
    broadcastToMeeting(`SYSTEM|${meetingId}|Server|USER_JOINED|${userId}|joined the meeting`, meetingId);

    // Send current participants list to the new user
    if (meeting) {
        const participants = Array.from(meeting.participants).join(', ');
        ws.send(`SYSTEM|${meetingId}|Server|PARTICIPANTS|${participants}`);
    }
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
}

function handleMeetingEnded(ws, userId, meetingId, content) {
    console.log(`🔚 ${userId} ended meeting: ${meetingId}`);

    const meeting = meetings.get(meetingId);
    if (meeting && meeting.host === userId) {
        // Notify all participants
        broadcastToMeeting(`MEETING_ENDED|${meetingId}|${userId}|${content}`, meetingId);
        broadcastToMeeting(`SYSTEM|${meetingId}|Server|MEETING_ENDED|${userId}|ended the meeting`, meetingId);

        // Clear all participants' current meeting
        for (const participant of meeting.participants) {
            for (const [client, userInfo] of clients) {
                if (userInfo.id === participant) {
                    userInfo.currentMeeting = null;
                    break;
                }
            }
        }

        // Remove meeting
        meetings.delete(meetingId);
    }
}

function handleAudioStatus(ws, userId, meetingId, content) {
    console.log(`🔊 ${userId} audio status: ${content}`);

    const userInfo = clients.get(ws);
    if (userInfo) {
        userInfo.audioMuted = content.includes('muted');
    }

    broadcastToMeeting(`AUDIO_STATUS|${meetingId}|${userId}|${content}`, meetingId, ws);

    // Broadcast system message for important audio events
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

    // Broadcast system message for important video events
    if (content.includes('started') || content.includes('stopped') || content.includes('recording')) {
        broadcastToMeeting(`SYSTEM|${meetingId}|Server|VIDEO_STATUS|${userId}|${content}`, meetingId);
    }
}

function handleAudioControl(ws, userId, meetingId, content) {
    console.log(`🔊 ${userId} audio control: ${content}`);

    const meeting = meetings.get(meetingId);
    if (meeting && meeting.host === userId) {
        // Only host can send audio controls
        if (content === 'MUTE_ALL') {
            broadcastToMeeting(`AUDIO_CONTROL|${meetingId}|${userId}|MUTE_ALL`, meetingId, ws);
            broadcastToMeeting(`SYSTEM|${meetingId}|Server|AUDIO_CONTROL|${userId}|muted all participants`, meetingId);

            // Update all participants' audio state
            for (const [client, userInfo] of clients) {
                if (userInfo.currentMeeting === meetingId && userInfo.id !== userId) {
                    userInfo.audioMuted = true;
                }
            }
        } else if (content === 'UNMUTE_ALL') {
            broadcastToMeeting(`AUDIO_CONTROL|${meetingId}|${userId}|UNMUTE_ALL`, meetingId, ws);
            broadcastToMeeting(`SYSTEM|${meetingId}|Server|AUDIO_CONTROL|${userId}|unmuted all participants`, meetingId);

            // Update all participants' audio state
            for (const [client, userInfo] of clients) {
                if (userInfo.currentMeeting === meetingId && userInfo.id !== userId) {
                    userInfo.audioMuted = false;
                }
            }
        }
    } else if (!meeting || meeting.host !== userId) {
        // Notify non-host that they can't perform this action
        ws.send(`SYSTEM|${meetingId}|Server|ERROR|Only the host can mute/unmute all participants`);
    }
}

function handleVideoControl(ws, userId, meetingId, content) {
    console.log(`🎥 ${userId} video control: ${content}`);

    const meeting = meetings.get(meetingId);
    if (meeting && meeting.host === userId) {
        // Only host can send video controls
        broadcastToMeeting(`VIDEO_CONTROL|${meetingId}|${userId}|${content}`, meetingId, ws);
    } else if (!meeting || meeting.host !== userId) {
        // Notify non-host that they can't perform this action
        ws.send(`SYSTEM|${meetingId}|Server|ERROR|Only the host can perform video controls`);
    }
}

// Utility Functions
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
    console.log(`📤 Broadcast to meeting ${meetingId}: ${message} (sent to ${sentCount} participants)`);
}

function removeUserFromMeeting(meetingId, userId) {
    const meeting = meetings.get(meetingId);
    if (meeting) {
        meeting.participants.delete(userId);

        // If no participants left, remove the meeting
        if (meeting.participants.size === 0) {
            meetings.delete(meetingId);
            console.log(`🗑️  Meeting ${meetingId} removed (no participants)`);
        }
        // If host left, assign new host
        else if (meeting.host === userId) {
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
    console.log(`📤 Global broadcast: ${message} (sent to ${sentCount} clients)`);
}

// Server status monitoring
setInterval(() => {
    console.log(`\n📊 Server Status - Clients: ${clients.size}, Meetings: ${meetings.size}`);

    for (const [meetingId, meeting] of meetings) {
        console.log(`   Meeting ${meetingId}:`);
        console.log(`     - Host: ${meeting.host}`);
        console.log(`     - Participants: ${meeting.participants.size}`);
        console.log(`     - Created: ${meeting.created.toLocaleTimeString()}`);

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
}, 30000); // Log status every 30 seconds

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