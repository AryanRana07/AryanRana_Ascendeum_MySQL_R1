import { useEffect, useRef, useState } from "react";
import { socket } from "../socket";

const COLORS = ["#ff4d6d", "#ff8fab", "#ffb3c6", "#7c3aed", "#3b82f6", "#111827"];

export default function Canvas({ onExit, partnerLeft }) {
  const canvasRef = useRef(null);
  const ctxRef = useRef(null);
  const drawingRef = useRef(false);
  const lastPointRef = useRef(null);
  const [color, setColor] = useState(COLORS[0]);
  const colorRef = useRef(color);

  useEffect(() => {
    colorRef.current = color;
  }, [color]);

  useEffect(() => {
    const canvas = canvasRef.current;
    const parent = canvas.parentElement;

    function resize() {
      const ratio = window.devicePixelRatio || 1;
      const { width, height } = parent.getBoundingClientRect();
      canvas.width = width * ratio;
      canvas.height = height * ratio;
      canvas.style.width = `${width}px`;
      canvas.style.height = `${height}px`;
      const ctx = canvas.getContext("2d");
      ctx.scale(ratio, ratio);
      ctx.lineCap = "round";
      ctx.lineJoin = "round";
      ctx.lineWidth = 6;
      ctxRef.current = ctx;
    }
    resize();
    window.addEventListener("resize", resize);
    return () => window.removeEventListener("resize", resize);
  }, []);

  useEffect(() => {
    function drawSegment({ x0, y0, x1, y1, strokeColor }) {
      const ctx = ctxRef.current;
      if (!ctx) return;
      const rect = canvasRef.current.getBoundingClientRect();
      ctx.strokeStyle = strokeColor;
      ctx.beginPath();
      ctx.moveTo(x0 * rect.width, y0 * rect.height);
      ctx.lineTo(x1 * rect.width, y1 * rect.height);
      ctx.stroke();
    }

    function onRemoteStroke(stroke) {
      drawSegment(stroke);
    }
    function onClear() {
      clearLocalCanvas();
    }

    socket.on("draw-stroke", onRemoteStroke);
    socket.on("clear-canvas", onClear);
    return () => {
      socket.off("draw-stroke", onRemoteStroke);
      socket.off("clear-canvas", onClear);
    };
  }, []);

  function getRelativePoint(e) {
    const rect = canvasRef.current.getBoundingClientRect();
    const touch = e.touches?.[0];
    const clientX = touch ? touch.clientX : e.clientX;
    const clientY = touch ? touch.clientY : e.clientY;
    return {
      x: (clientX - rect.left) / rect.width,
      y: (clientY - rect.top) / rect.height,
    };
  }

  function startDraw(e) {
    e.preventDefault();
    drawingRef.current = true;
    lastPointRef.current = getRelativePoint(e);
  }

  function moveDraw(e) {
    if (!drawingRef.current) return;
    e.preventDefault();
    const point = getRelativePoint(e);
    const last = lastPointRef.current;
    const stroke = {
      x0: last.x,
      y0: last.y,
      x1: point.x,
      y1: point.y,
      strokeColor: colorRef.current,
    };
    const ctx = ctxRef.current;
    const rect = canvasRef.current.getBoundingClientRect();
    ctx.strokeStyle = stroke.strokeColor;
    ctx.beginPath();
    ctx.moveTo(stroke.x0 * rect.width, stroke.y0 * rect.height);
    ctx.lineTo(stroke.x1 * rect.width, stroke.y1 * rect.height);
    ctx.stroke();

    socket.emit("draw-stroke", stroke);
    lastPointRef.current = point;
  }

  function endDraw() {
    drawingRef.current = false;
    lastPointRef.current = null;
  }

  function clearLocalCanvas() {
    const canvas = canvasRef.current;
    const ctx = ctxRef.current;
    if (!ctx) return;
    const rect = canvas.getBoundingClientRect();
    ctx.clearRect(0, 0, rect.width, rect.height);
  }

  function handleClear() {
    clearLocalCanvas();
    socket.emit("clear-canvas");
  }

  function handleExit() {
    socket.emit("leave-canvas");
    onExit();
  }

  return (
    <div className="canvas-screen">
      <div className="canvas-header">
        <button className="icon-btn" onClick={handleExit} aria-label="Back">
          ←
        </button>
        <span className="canvas-title">Draw something for them 💗</span>
        <button className="icon-btn" onClick={handleClear} aria-label="Clear">
          ✕
        </button>
      </div>

      {partnerLeft && (
        <div className="banner">Your partner left the canvas</div>
      )}

      <div className="canvas-wrap">
        <canvas
          ref={canvasRef}
          onMouseDown={startDraw}
          onMouseMove={moveDraw}
          onMouseUp={endDraw}
          onMouseLeave={endDraw}
          onTouchStart={startDraw}
          onTouchMove={moveDraw}
          onTouchEnd={endDraw}
        />
      </div>

      <div className="color-row">
        {COLORS.map((c) => (
          <button
            key={c}
            className={`swatch ${color === c ? "active" : ""}`}
            style={{ background: c }}
            onClick={() => setColor(c)}
            aria-label={`Color ${c}`}
          />
        ))}
      </div>
    </div>
  );
}
