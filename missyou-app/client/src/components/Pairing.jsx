import { useState } from "react";
import { socket } from "../socket";

export default function Pairing({ myCode, error }) {
  const [inputCode, setInputCode] = useState("");
  const [mode, setMode] = useState("choose"); // choose | create | join

  function requestCode() {
    setMode("create");
    socket.emit("create-pairing-code");
  }

  function submitCode(e) {
    e.preventDefault();
    const code = inputCode.trim().toUpperCase();
    if (code.length >= 4) {
      socket.emit("redeem-pairing-code", { code });
    }
  }

  return (
    <div className="screen center">
      <div className="card">
        <div className="hero-emoji">💞</div>
        <h1>Link with your partner</h1>

        {mode === "choose" && (
          <div className="stack">
            <button className="btn btn-primary" onClick={requestCode}>
              Generate a pairing code
            </button>
            <button className="btn btn-secondary" onClick={() => setMode("join")}>
              I have a code
            </button>
          </div>
        )}

        {mode === "create" && (
          <div className="stack">
            <p className="subtitle">Send this code to your partner:</p>
            <div className="pairing-code">{myCode || "..."}</div>
            <p className="hint">Waiting for them to enter it...</p>
            <button className="btn btn-secondary" onClick={() => setMode("choose")}>
              Back
            </button>
          </div>
        )}

        {mode === "join" && (
          <form className="stack" onSubmit={submitCode}>
            <p className="subtitle">Enter your partner's code:</p>
            <input
              className="text-input code-input"
              placeholder="ABC123"
              value={inputCode}
              onChange={(e) => setInputCode(e.target.value.toUpperCase())}
              maxLength={8}
              autoFocus
            />
            {error && <p className="error-text">{error}</p>}
            <button className="btn btn-primary" type="submit" disabled={inputCode.trim().length < 4}>
              Link up
            </button>
            <button className="btn btn-secondary" type="button" onClick={() => setMode("choose")}>
              Back
            </button>
          </form>
        )}
      </div>
    </div>
  );
}
