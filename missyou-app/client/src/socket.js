import { io } from "socket.io-client";

const SERVER_URL = import.meta.env.VITE_SERVER_URL || "http://localhost:3001";

export const socket = io(SERVER_URL, {
  autoConnect: true,
});

export function getOrCreateUserId() {
  let id = localStorage.getItem("missyou-user-id");
  if (!id) {
    id = crypto.randomUUID();
    localStorage.setItem("missyou-user-id", id);
  }
  return id;
}

export function getOrSetName() {
  return localStorage.getItem("missyou-name") || "";
}

export function setName(name) {
  localStorage.setItem("missyou-name", name);
}

export function getStoredPartnerId() {
  return localStorage.getItem("missyou-partner-id") || null;
}

export function setStoredPartnerId(id) {
  if (id) localStorage.setItem("missyou-partner-id", id);
  else localStorage.removeItem("missyou-partner-id");
}
