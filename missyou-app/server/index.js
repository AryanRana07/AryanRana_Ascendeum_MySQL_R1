const express = require("express");
const http = require("http");
const cors = require("cors");
const { Server } = require("socket.io");
const { nanoid } = require("nanoid");

const app = express();
app.use(cors());
app.use(express.json());

const server = http.createServer(app);
const io = new Server(server, {
  cors: { origin: "*" },
});

// In-memory state (fine for a small two-person app; resets on server restart)
// userId -> { socketId, partnerId, pairingCode, name }
const users = new Map();
// pairingCode -> userId (code owner, waiting to be claimed)
const pendingCodes = new Map();

function makeCode() {
  // 6-char, easy to read/type on a phone
  return nanoid(6).toUpperCase();
}

app.get("/health", (req, res) => res.json({ ok: true }));

io.on("connection", (socket) => {
  let userId = null;

  socket.on("identify", ({ userId: existingId, name }) => {
    userId = existingId || nanoid(10);
    const existing = users.get(userId);
    users.set(userId, {
      socketId: socket.id,
      partnerId: existing?.partnerId || null,
      pairingCode: existing?.pairingCode || null,
      name: name || existing?.name || "Someone",
    });
    socket.data.userId = userId;

    const me = users.get(userId);
    socket.emit("identified", {
      userId,
      partnerId: me.partnerId,
      paired: !!me.partnerId,
    });

    // If already paired, tell partner we're back online
    if (me.partnerId && users.has(me.partnerId)) {
      const partner = users.get(me.partnerId);
      io.to(partner.socketId).emit("partner-online");
    }
  });

  socket.on("create-pairing-code", () => {
    if (!userId || !users.has(userId)) return;
    const code = makeCode();
    const user = users.get(userId);
    user.pairingCode = code;
    pendingCodes.set(code, userId);
    socket.emit("pairing-code", { code });
  });

  socket.on("redeem-pairing-code", ({ code }) => {
    if (!userId || !users.has(userId)) return;
    const ownerId = pendingCodes.get((code || "").toUpperCase());
    if (!ownerId) {
      socket.emit("pairing-error", { message: "Invalid or expired code" });
      return;
    }
    if (ownerId === userId) {
      socket.emit("pairing-error", { message: "You can't pair with yourself" });
      return;
    }

    const me = users.get(userId);
    const owner = users.get(ownerId);
    if (!owner) {
      socket.emit("pairing-error", { message: "That person is no longer available" });
      return;
    }

    me.partnerId = ownerId;
    owner.partnerId = userId;
    pendingCodes.delete(code.toUpperCase());

    socket.emit("paired", { partnerId: ownerId, partnerName: owner.name });
    if (owner.socketId) {
      io.to(owner.socketId).emit("paired", { partnerId: userId, partnerName: me.name });
    }
  });

  socket.on("miss-you", () => {
    if (!userId || !users.has(userId)) return;
    const me = users.get(userId);
    if (!me.partnerId) return;
    const partner = users.get(me.partnerId);
    if (partner?.socketId) {
      io.to(partner.socketId).emit("miss-you-received", {
        fromName: me.name,
      });
    }
  });

  socket.on("miss-you-response", ({ accepted }) => {
    if (!userId || !users.has(userId)) return;
    const me = users.get(userId);
    if (!me.partnerId) return;
    const partner = users.get(me.partnerId);
    if (partner?.socketId) {
      io.to(partner.socketId).emit("miss-you-response-received", { accepted });
    }
    if (accepted) {
      // Both jump into drawing mode
      socket.emit("enter-canvas");
      if (partner?.socketId) io.to(partner.socketId).emit("enter-canvas");
    }
  });

  socket.on("draw-stroke", (stroke) => {
    if (!userId || !users.has(userId)) return;
    const me = users.get(userId);
    if (!me.partnerId) return;
    const partner = users.get(me.partnerId);
    if (partner?.socketId) {
      io.to(partner.socketId).emit("draw-stroke", stroke);
    }
  });

  socket.on("clear-canvas", () => {
    if (!userId || !users.has(userId)) return;
    const me = users.get(userId);
    if (!me.partnerId) return;
    const partner = users.get(me.partnerId);
    if (partner?.socketId) {
      io.to(partner.socketId).emit("clear-canvas");
    }
  });

  socket.on("leave-canvas", () => {
    if (!userId || !users.has(userId)) return;
    const me = users.get(userId);
    if (!me.partnerId) return;
    const partner = users.get(me.partnerId);
    if (partner?.socketId) {
      io.to(partner.socketId).emit("partner-left-canvas");
    }
  });

  socket.on("disconnect", () => {
    if (userId && users.has(userId)) {
      const me = users.get(userId);
      me.socketId = null;
      if (me.partnerId && users.has(me.partnerId)) {
        const partner = users.get(me.partnerId);
        if (partner.socketId) io.to(partner.socketId).emit("partner-offline");
      }
    }
  });
});

const PORT = process.env.PORT || 3001;
server.listen(PORT, () => {
  console.log(`MissYou server listening on port ${PORT}`);
});
