export default function Home({ partnerName, partnerOnline, sent, waitingForResponse, onSend }) {
  return (
    <div className="screen center">
      <div className="card">
        <div className={`status-dot ${partnerOnline ? "online" : "offline"}`} />
        <p className="subtitle">
          Linked with <strong>{partnerName || "your partner"}</strong>
          {partnerOnline ? " · online" : " · offline"}
        </p>

        <button
          className="miss-you-btn"
          onClick={onSend}
          disabled={waitingForResponse}
        >
          <span className="miss-you-heart">💗</span>
          <span>{waitingForResponse ? "Sent..." : "I miss you"}</span>
        </button>

        {sent && !waitingForResponse && (
          <p className="hint fade-in">They know now 💌</p>
        )}
        {waitingForResponse && (
          <p className="hint fade-in">Waiting for them to open their heart...</p>
        )}
      </div>
    </div>
  );
}
