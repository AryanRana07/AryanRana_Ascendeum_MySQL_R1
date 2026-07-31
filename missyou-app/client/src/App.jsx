import { useEffect, useState } from "react";
import { socket, getOrCreateUserId, getOrSetName, setName as persistName, getStoredPartnerId, setStoredPartnerId } from "./socket";
import NameGate from "./components/NameGate";
import Pairing from "./components/Pairing";
import Home from "./components/Home";
import IncomingPopup from "./components/IncomingPopup";
import Canvas from "./components/Canvas";
import "./App.css";

export default function App() {
  const [name, setLocalName] = useState(getOrSetName());
  const [partnerId, setPartnerId] = useState(getStoredPartnerId());
  const [partnerName, setPartnerName] = useState("");
  const [partnerOnline, setPartnerOnline] = useState(false);
  const [myCode, setMyCode] = useState(null);
  const [pairingError, setPairingError] = useState("");
  const [view, setView] = useState("loading"); // loading | name | pairing | home | canvas
  const [incoming, setIncoming] = useState(null); // { fromName }
  const [sent, setSent] = useState(false);
  const [waitingForResponse, setWaitingForResponse] = useState(false);
  const [partnerLeftCanvas, setPartnerLeftCanvas] = useState(false);

  useEffect(() => {
    function onConnect() {
      socket.emit("identify", { userId: getOrCreateUserId(), name: name || undefined });
    }
    function onIdentified({ partnerId: pid, paired }) {
      if (paired && pid) {
        setPartnerId(pid);
        setStoredPartnerId(pid);
      }
      if (!name) {
        setView("name");
      } else if (paired) {
        setView("home");
      } else {
        setView("pairing");
      }
    }
    function onPairingCode({ code }) {
      setMyCode(code);
    }
    function onPaired({ partnerId: pid, partnerName: pname }) {
      setPartnerId(pid);
      setStoredPartnerId(pid);
      setPartnerName(pname);
      setPartnerOnline(true);
      setPairingError("");
      setView("home");
    }
    function onPairingError({ message }) {
      setPairingError(message);
    }
    function onMissYouReceived({ fromName }) {
      setIncoming({ fromName });
    }
    function onMissYouResponseReceived({ accepted }) {
      setWaitingForResponse(false);
      setSent(true);
      if (!accepted) {
        setTimeout(() => setSent(false), 3000);
      }
    }
    function onEnterCanvas() {
      setIncoming(null);
      setWaitingForResponse(false);
      setPartnerLeftCanvas(false);
      setView("canvas");
    }
    function onPartnerOnline() {
      setPartnerOnline(true);
    }
    function onPartnerOffline() {
      setPartnerOnline(false);
    }
    function onPartnerLeftCanvas() {
      setPartnerLeftCanvas(true);
    }

    socket.on("connect", onConnect);
    socket.on("identified", onIdentified);
    socket.on("pairing-code", onPairingCode);
    socket.on("paired", onPaired);
    socket.on("pairing-error", onPairingError);
    socket.on("miss-you-received", onMissYouReceived);
    socket.on("miss-you-response-received", onMissYouResponseReceived);
    socket.on("enter-canvas", onEnterCanvas);
    socket.on("partner-online", onPartnerOnline);
    socket.on("partner-offline", onPartnerOffline);
    socket.on("partner-left-canvas", onPartnerLeftCanvas);

    if (socket.connected) onConnect();

    return () => {
      socket.off("connect", onConnect);
      socket.off("identified", onIdentified);
      socket.off("pairing-code", onPairingCode);
      socket.off("paired", onPaired);
      socket.off("pairing-error", onPairingError);
      socket.off("miss-you-received", onMissYouReceived);
      socket.off("miss-you-response-received", onMissYouResponseReceived);
      socket.off("enter-canvas", onEnterCanvas);
      socket.off("partner-online", onPartnerOnline);
      socket.off("partner-offline", onPartnerOffline);
      socket.off("partner-left-canvas", onPartnerLeftCanvas);
    };
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [name]);

  function handleNameSubmit(newName) {
    persistName(newName);
    setLocalName(newName);
    socket.emit("identify", { userId: getOrCreateUserId(), name: newName });
    setView(partnerId ? "home" : "pairing");
  }

  function handleSendMissYou() {
    socket.emit("miss-you");
    setWaitingForResponse(true);
    setSent(false);
  }

  function handleIncomingResolved(accepted) {
    if (!accepted) setIncoming(null);
  }

  if (view === "loading") {
    return (
      <div className="screen center">
        <div className="hero-emoji pulse">💗</div>
        <p className="subtitle">Connecting...</p>
      </div>
    );
  }

  return (
    <>
      {view === "name" && <NameGate onSubmit={handleNameSubmit} />}
      {view === "pairing" && <Pairing myCode={myCode} error={pairingError} />}
      {view === "home" && (
        <Home
          partnerName={partnerName}
          partnerOnline={partnerOnline}
          sent={sent}
          waitingForResponse={waitingForResponse}
          onSend={handleSendMissYou}
        />
      )}
      {view === "canvas" && (
        <Canvas
          partnerLeft={partnerLeftCanvas}
          onExit={() => {
            setView("home");
            setPartnerLeftCanvas(false);
          }}
        />
      )}
      {incoming && <IncomingPopup fromName={incoming.fromName} onResolved={handleIncomingResolved} />}
    </>
  );
}
