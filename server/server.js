// server/server.js
import { WebSocketServer } from 'ws';

const PORT = 8887;
const wss = new WebSocketServer({ port: PORT });

const clients = new Map();

console.log(`✅ WebSocket Server started on ws://localhost:${PORT}`);

wss.on('connection', (ws) => {
  console.log('🔗 New client connected');

  // Assign a random ID
  const userId = `User-${Math.floor(Math.random() * 1000)}`;
  clients.set(ws, userId);

  // Send welcome message
  ws.send(`SYSTEM|global|Server|Welcome ${userId}! You are connected.`);

  // Notify others
  broadcast(`SYSTEM|global|Server|${userId} joined the chat`, ws);

  ws.on('message', (message) => {
    console.log(`💬 ${userId}: ${message}`);
    broadcast(message.toString(), ws);
  });

  ws.on('close', () => {
    console.log(`❌ ${userId} disconnected`);
    clients.delete(ws);
    broadcast(`SYSTEM|global|Server|${userId} left the chat`);
  });
});

function broadcast(message, sender) {
  for (const [client, id] of clients) {
    if (client.readyState === 1 && client !== sender) {
      client.send(message);
    }
  }
}
