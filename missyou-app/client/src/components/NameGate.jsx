import { useState } from "react";

export default function NameGate({ onSubmit }) {
  const [name, setName] = useState("");

  return (
    <div className="screen center">
      <div className="card">
        <div className="hero-emoji">💌</div>
        <h1>Missing You</h1>
        <p className="subtitle">What should your partner call you?</p>
        <form
          onSubmit={(e) => {
            e.preventDefault();
            const trimmed = name.trim();
            if (trimmed) onSubmit(trimmed);
          }}
        >
          <input
            className="text-input"
            placeholder="Your name"
            value={name}
            onChange={(e) => setName(e.target.value)}
            autoFocus
            maxLength={24}
          />
          <button className="btn btn-primary" type="submit" disabled={!name.trim()}>
            Continue
          </button>
        </form>
      </div>
    </div>
  );
}
