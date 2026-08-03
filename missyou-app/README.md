# Missing You

A small two-person mobile web app: send your partner a "missing you" nudge,
they accept, and you both draw on a shared live canvas together.

## How it works

1. Both people open the app on their phone and enter a name.
2. One person taps "Generate a pairing code" and sends the code to the other
   (text, WhatsApp, whatever).
3. The other person taps "I have a code" and enters it — you're now linked.
4. Either person taps the big heart to say "I miss you." The other gets a
   popup with Accept / Not right now.
5. If accepted, both screens open the same drawing canvas. Anything either
   person draws appears on both screens in real time.

## Structure

- `server/` — Node + Express + Socket.IO. Holds pairing codes and relays
  events (miss-you requests, responses, draw strokes) between the two
  paired sockets. State is in-memory (fine for two people; resets on
  restart).
- `client/` — React + Vite app, mobile-first.

## Running locally

```bash
# terminal 1
cd server
npm install
npm start        # listens on :3001

# terminal 2
cd client
npm install
npm run dev -- --host   # listens on :5173
```

Set `VITE_SERVER_URL` in `client/.env` if the server isn't on
`http://localhost:3001` (e.g. when testing from a phone on the same network,
point it at your machine's LAN IP).

Open the client URL on two phones (or two browser tabs) to try the full
flow.

## Notes / next steps

- Notifications are in-app only right now (delivered instantly over the
  socket connection while both apps are open). Real push notifications
  (phone buzzes even with the app closed) would need a service worker +
  Web Push subscription, which can be added later.
- Pairing is code-based with no accounts — good for two people, not meant
  to scale beyond that.
