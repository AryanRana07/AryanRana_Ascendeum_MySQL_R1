import { socket } from "../socket";

export default function IncomingPopup({ fromName, onResolved }) {
  function respond(accepted) {
    socket.emit("miss-you-response", { accepted });
    onResolved(accepted);
  }

  return (
    <div className="overlay">
      <div className="card popup-card">
        <div className="hero-emoji pulse">💓</div>
        <h2>{fromName || "Your partner"} misses you</h2>
        <p className="subtitle">Open your heart and draw them something back?</p>
        <div className="stack">
          <button className="btn btn-primary" onClick={() => respond(true)}>
            Accept 💕
          </button>
          <button className="btn btn-secondary" onClick={() => respond(false)}>
            Not right now
          </button>
        </div>
      </div>
    </div>
  );
}
