// server/server.js
import { WebSocketServer } from 'ws';

const PORT = 8887;
// Allow connections from any IP address
const wss = new WebSocketServer({
  port: PORT,
  host: '0.0.0.0'  // This allows connections from any IP
});

const clients = new Map();

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
  clients.set(ws, { id: userId, ip: clientIp });

  // Send welcome message
  ws.send(`SYSTEM|global|Server|Welcome ${userId}! You are connected from ${clientIp}`);

  // Notify others
  broadcast(`SYSTEM|global|Server|${userId} joined the chat from ${clientIp}`, ws);

  ws.on('message', (message) => {
    console.log(`💬 ${userId} (${clientIp}): ${message}`);
    broadcast(message.toString(), ws);
  });

  ws.on('close', () => {
    console.log(`❌ ${userId} (${clientIp}) disconnected`);
    clients.delete(ws);
    broadcast(`SYSTEM|global|Server|${userId} left the chat`);
  });

  ws.on('error', (error) => {
    console.error(`❌ WebSocket error for ${userId}:`, error);
  });
});

function broadcast(message, sender) {
  for (const [client, userInfo] of clients) {
    if (client.readyState === 1 && client !== sender) {
      client.send(message);
    }
  }
}

// Handle server errors
wss.on('error', (error) => {
  console.error('❌ Server error:', error);
});

console.log('🔧 To allow external connections, make sure:');
console.log('   1. Your firewall allows port ' + PORT);
console.log('   2. You forward port ' + PORT + ' on your router (if behind NAT)');
console.log('   3. Clients use your public IP address');