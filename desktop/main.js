const { app, BrowserWindow, screen, clipboard } = require("electron");
const path = require("path");
const express = require("express");
const bodyParser = require("body-parser");
const { windowManager } = require("node-window-manager");
// const { takeScreenshot } = require("electron-screencapture");
const screenshot = require("screenshot-desktop");
const sharp = require("sharp");

// There is a weird issue with Electron & Robotjs so let's just use osascript
// as temporary hack.
// const robot = require("robotjs");
const osascript = require("node-osascript");

const server = express();
server.use(bodyParser.urlencoded({ extended: true }));
const port = 3000;

let window;
let text;
let position = { x: 0, y: 0 };
let isPasting = false;

windowManager.requestAccessibility();

const script =
  'tell application "System Events" to keystroke "v" using {command down}';
osascript.execute(script, (err, result, raw) => {
  if (err) {
    console.error(err);
  }
});

// -- Electron App

function createWindow() {
  const { width, height } = screen.getPrimaryDisplay().workAreaSize;
  // Create the browser window.
  window = new BrowserWindow({
    width: width,
    height: height,
    transparent: true,
    frame: false,
    hasShadow: false,
    webPreferences: {
      // nodeIntegration: true,
      preload: path.join(__dirname, "preload.js"),
    },
  });

  // and load the index.html of the app.
  window.loadFile("index.html");
}

app.whenReady().then(() => {
  createWindow();
  app.on("activate", function () {
    if (BrowserWindow.getAllWindows().length === 0) createWindow();
  });
});

// Quit when all windows are closed.
app.on("window-all-closed", function () {
  if (process.platform !== "darwin") app.quit();
});

// -- Express HTTP Server

server.get("/position", (req, res) => {
  if (isPasting) {
    return;
  }
  if (!window.isFocused()) {
    window.show();
  }
  const { width, height } = screen.getPrimaryDisplay().workAreaSize;
  if (req.query.x && req.query.y) {
    const x = (0.5 + parseFloat(req.query.x)) * width;
    const y = (0.5 - parseFloat(req.query.y)) * height;
    if (!isNaN(x) && !isNaN(y)) {
      position.x = Math.round(x);
      position.y = Math.round(y);
      window.webContents.send("position", position);
    }
  }
  res.send("k");
});

server.get("/screen", (req, res) => {
  screenshot()
    .then(async (img) => {
      // img: Buffer filled with jpg goodness
      // ...
      const resized = await sharp(img)
        .resize(1000)
        .jpeg({
          quality: 90,
        })
        .toBuffer();
      res.set("Content-Type", "image/jpeg");
      res.write(resized);
      res.end();
      // console.log(img);
    })
    .catch((err) => {
      console.err(err);
    });
});

server.post("/text", (req, res) => {
  text = req.body.text;
  window.webContents.send("text", req.body);
  res.send("k");
});

server.get("/paste", async (req, res) => {
  console.log("paste");
  isPasting = true;
  if (!text) {
    console.log("ignore paste");
    isPasting = false;
    return;
  }
  // Write to clipboard.
  clipboard.writeText(text);
  // Blur electron window.
  window.hide();
  // Select window at current position.
  const windowActivated = activateWindowAt(position.x, position.y);
  // Send paste keystrokes.
  if (windowActivated) {
    const script =
      'tell application "System Events" to keystroke "v" using {command down}';
    osascript.execute(script, (err, result, raw) => {
      if (err) {
        console.error(err);
      }
    });
    console.log("combination sent");
  } else {
    console.log("no window at position");
  }
  // Clear text.
  window.webContents.send("text", { text: "" });
  // Resume
  isPasting = false;
});

server.listen(port, () => console.log(`Listening at http://localhost:${port}`));

// -- OS Windows

function activateWindowAt(x, y) {
  console.log("activate window at:", x, y);
  const windows = windowManager.getWindows();
  for (const window of windows) {
    const bounds = window.getBounds();
    if (window.path.indexOf("electron") != -1) {
      continue;
    }
    const isTaskBarMenu = bounds.y == 0 && bounds.height == 22;
    if (isTaskBarMenu || !window.isVisible() || !window.isWindow()) {
      continue;
    }
    console.log(window.path, bounds);
    if (
      x > bounds.x &&
      y > bounds.y &&
      x < bounds.x + bounds.width &&
      y < bounds.y + bounds.height
    ) {
      console.log("got window:", window.path, window.getTitle());
      window.bringToTop();
      return window;
    }
  }
  return false;
}
