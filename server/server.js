// server/server.js
import { WebSocketServer } from 'ws';

const PORT = 8887;
// Allow connections from any IP address
const wss = new WebSocketServer({
  port: PORT,
  host: '0.0.0.0'  // This allows connections from any IP
});

const clients = new Map();
const meetings = new Map();

console.log(`✅ WebSocket Server started on port ${PORT}`);
console.log(`🌐 Server is accessible via:`);
console.log(`   - Local: ws://localhost:${PORT}`);
console.log(`   - Network: ws://YOUR_IP_ADDRESS:${PORT}`);
console.log(`   - All interfaces: ws://0.0.0.0:${PORT}`);

wss.on('connection', (ws, req) => {
  const clientIp = req.socket.remoteAddress;
  console.log(`🔗 New client connected from: ${clientIp}`);

  // Assign a random ID
  const userId = `User-${Math.floor(Math.random() * 1000)}`;
  clients.set(ws, {
    id: userId,
    ip: clientIp,
    audioMuted: false,
    videoOn: false,
    isRecording: false,
    currentMeeting: null
  });

  // Send welcome message
  ws.send(`SYSTEM|global|Server|Welcome ${userId}! You are connected from ${clientIp}`);

  // Notify others
  broadcast(`SYSTEM|global|Server|${userId} joined the chat from ${clientIp}`, ws);

  ws.on('message', (message) => {
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
        default:
          // Default broadcast for unknown message types
          broadcast(msg, ws);
      }
    } else {
      // Fallback: broadcast raw message
      broadcast(msg, ws);
    }
  });

  ws.on('close', () => {
    console.log(`❌ ${userId} (${clientIp}) disconnected`);
    const userInfo = clients.get(ws);
    if (userInfo && userInfo.currentMeeting) {
      // Notify meeting participants that user left
      broadcastToMeeting(`USER_LEFT|${userInfo.currentMeeting}|${userId}|${userId} disconnected`, userInfo.currentMeeting, ws);
      removeUserFromMeeting(userInfo.currentMeeting, userId);
    }
    clients.delete(ws);
    broadcast(`SYSTEM|global|Server|${userId} left the chat`);
  });

  ws.on('error', (error) => {
    console.error(`❌ WebSocket error for ${userId}:`, error);
  });
});

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

  broadcast(`SYSTEM|${meetingId}|Server|${userId} created meeting ${meetingId}`);
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
  broadcastToMeeting(`SYSTEM|${meetingId}|Server|${userId} joined the meeting`, meetingId);

  // Send current participants list to the new user
  if (meeting) {
    const participants = Array.from(meeting.participants).join(', ');
    ws.send(`SYSTEM|${meetingId}|Server|Current participants: ${participants}`);
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
  broadcastToMeeting(`SYSTEM|${meetingId}|Server|${userId} left the meeting`, meetingId);
}

function handleMeetingEnded(ws, userId, meetingId, content) {
  console.log(`🔚 ${userId} ended meeting: ${meetingId}`);

  const meeting = meetings.get(meetingId);
  if (meeting && meeting.host === userId) {
    // Notify all participants
    broadcastToMeeting(`MEETING_ENDED|${meetingId}|${userId}|${content}`, meetingId);
    broadcastToMeeting(`SYSTEM|${meetingId}|Server|Meeting ended by host ${userId}`, meetingId);

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
    broadcastToMeeting(`SYSTEM|${meetingId}|Server|${userId} ${content}`, meetingId);
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
    broadcastToMeeting(`SYSTEM|${meetingId}|Server|${userId} ${content}`, meetingId);
  }
}

function handleAudioControl(ws, userId, meetingId, content) {
  console.log(`🔊 ${userId} audio control: ${content}`);

  const meeting = meetings.get(meetingId);
  if (meeting && meeting.host === userId) {
    // Only host can send audio controls
    if (content === 'MUTE_ALL') {
      broadcastToMeeting(`AUDIO_CONTROL|${meetingId}|${userId}|MUTE_ALL`, meetingId, ws);
      broadcastToMeeting(`SYSTEM|${meetingId}|Server|Host muted all participants`, meetingId);

      // Update all participants' audio state
      for (const [client, userInfo] of clients) {
        if (userInfo.currentMeeting === meetingId && userInfo.id !== userId) {
          userInfo.audioMuted = true;
        }
      }
    } else if (content === 'UNMUTE_ALL') {
      broadcastToMeeting(`AUDIO_CONTROL|${meetingId}|${userId}|UNMUTE_ALL`, meetingId, ws);
      broadcastToMeeting(`SYSTEM|${meetingId}|Server|Host unmuted all participants`, meetingId);

      // Update all participants' audio state
      for (const [client, userInfo] of clients) {
        if (userInfo.currentMeeting === meetingId && userInfo.id !== userId) {
          userInfo.audioMuted = false;
        }
      }
    }
  } else if (!meeting || meeting.host !== userId) {
    // Notify non-host that they can't perform this action
    ws.send(`SYSTEM|${meetingId}|Server|Only the host can mute/unmute all participants`);
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
    ws.send(`SYSTEM|${meetingId}|Server|Only the host can perform video controls`);
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
      client.send(message);
      sentCount++;
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
      broadcastToMeeting(`SYSTEM|${meetingId}|Server|${newHost} is now the host`, meetingId);
      console.log(`👑 New host for meeting ${meetingId}: ${newHost}`);
    }
  }
}

function broadcast(message, sender) {
  let sentCount = 0;
  for (const [client, userInfo] of clients) {
    if (client.readyState === 1 && client !== sender) {
      client.send(message);
      sentCount++;
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
}, 60000); // Log status every 60 seconds

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
      client.send('SYSTEM|global|Server|Server is shutting down');
    }
  }

  wss.close(() => {
    console.log('✅ WebSocket server closed gracefully');
    process.exit(0);
  });
});

console.log('🔧 To allow external connections, make sure:');
console.log('   1. Your firewall allows port ' + PORT);
console.log('   2. You forward port ' + PORT + ' on your router (if behind NAT)');
console.log('   3. Clients use your public IP address');
console.log('\n🎯 Supported message types:');
console.log('   • CHAT|meetingId|username|message');
console.log('   • MEETING_CREATED|meetingId|username|description');
console.log('   • USER_JOINED|meetingId|username|status');
console.log('   • USER_LEFT|meetingId|username|status');
console.log('   • MEETING_ENDED|meetingId|username|reason');
console.log('   • AUDIO_STATUS|meetingId|username|status');
console.log('   • VIDEO_STATUS|meetingId|username|status');
console.log('   • AUDIO_CONTROL|meetingId|username|command');
console.log('   • VIDEO_CONTROL|meetingId|username|command');
console.log('\n🚀 Server is ready for audio/video controls integration!');