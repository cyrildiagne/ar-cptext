// All of the Node.js APIs are available in the preload process.
// It has the same sandbox as a Chrome extension.
const { ipcRenderer } = require("electron");

ipcRenderer.on("text", function (event, data) {
  const el = document.querySelector("h1");
  el.innerHTML = data.text.split("\n").join("<br>");
});

ipcRenderer.on("position", function (event, position) {
  const el = document.querySelector("h1");
  el.style.transform = `translate(${position.x - 150}px, ${position.y}.px)`;
});
