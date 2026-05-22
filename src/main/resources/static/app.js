// State Management
let ws = null;
let currentNickname = "";
let activeChat = null; // { type: 'room' | 'private', target: 'CODE' | 'username' }
let joinedRooms = new Set();
let usersDb = []; // Array of User objects: { username, status, lastSeen }
let chatHistories = {}; // Key: 'room:CODE' or 'private:username' -> Array of messages
let unreadCounts = {}; // Key: 'room:CODE' or 'private:username' -> Integer
let selectedFileData = null; // Store base64, name, type, size for upload preview

// DOM Elements - Auth Panels
const loginContainer = document.getElementById("login-container");
const chatDashboard = document.getElementById("chat-dashboard");
const loginForm = document.getElementById("login-form");
const registerForm = document.getElementById("register-form");
const tabLogin = document.getElementById("tab-login");
const tabRegister = document.getElementById("tab-register");

const loginEmailInput = document.getElementById("login-email");
const loginPasswordInput = document.getElementById("login-password");
const registerUsernameInput = document.getElementById("register-username");
const registerEmailInput = document.getElementById("register-email");
const registerPasswordInput = document.getElementById("register-password");

const loginError = document.getElementById("login-error");
const errorText = document.getElementById("error-text");
const registerSuccess = document.getElementById("register-success");
const successText = document.getElementById("success-text");

// DOM Elements - Dashboard
const userAvatar = document.getElementById("user-avatar");
const userDisplayName = document.getElementById("user-display-name");
const onlineCount = document.getElementById("online-count");
const usersList = document.getElementById("users-list");
const logoutBtn = document.getElementById("logout-btn");

const roomCodeInput = document.getElementById("room-code-input");
const generateCodeBtn = document.getElementById("generate-code-btn");
const joinRoomBtn = document.getElementById("join-room-btn");
const roomsList = document.getElementById("rooms-list");

const welcomeChatView = document.getElementById("welcome-chat-view");
const activeChatView = document.getElementById("active-chat-view");
const chatTitle = document.getElementById("chat-title");
const chatSubtitle = document.getElementById("chat-subtitle");
const chatAvatar = document.getElementById("chat-avatar");
const leaveChatBtn = document.getElementById("leave-chat-btn");
const aiSummarizeBtn = document.getElementById("ai-summarize-btn");
const aiModal = document.getElementById("ai-modal");
const aiModalBody = document.getElementById("ai-modal-body");
const closeAiModalBtn = document.getElementById("close-ai-modal-btn");
const messagesContainer = document.getElementById("messages-container");
const messageForm = document.getElementById("message-form");
const messageInput = document.getElementById("message-input");

// Media UI Elements
const attachBtn = document.getElementById("attach-btn");
const mediaFileInput = document.getElementById("media-file-input");
const mediaPreviewContainer = document.getElementById("media-preview-container");
const mediaPreviewImg = document.getElementById("media-preview-img");
const mediaPreviewFile = document.getElementById("media-preview-file");
const mediaPreviewName = document.getElementById("media-preview-name");
const mediaPreviewSize = document.getElementById("media-preview-size");
const cancelMediaBtn = document.getElementById("cancel-media-btn");

// --- Initialization & Event Listeners ---
document.addEventListener("DOMContentLoaded", () => {
    // 1. Manage Login / Register tab switching
    tabLogin.addEventListener("click", () => showAuthTab("login"));
    tabRegister.addEventListener("click", () => showAuthTab("register"));

    // 2. Form submission handlers
    loginForm.addEventListener("submit", handleLoginSubmit);
    registerForm.addEventListener("submit", handleRegisterSubmit);
    logoutBtn.addEventListener("click", handleLogout);

    // 3. Media inputs
    attachBtn.addEventListener("click", () => mediaFileInput.click());
    mediaFileInput.addEventListener("change", handleFileSelection);
    cancelMediaBtn.addEventListener("click", clearMediaSelection);

    // 4. Room generation & submission
    generateCodeBtn.addEventListener("click", () => {
        const randomCode = Math.random().toString(36).substring(2, 8).toUpperCase();
        roomCodeInput.value = randomCode;
    });
    joinRoomBtn.addEventListener("click", joinRoomFromInput);
    roomCodeInput.addEventListener("keydown", (e) => {
        if (e.key === "Enter") joinRoomFromInput();
    });

    messageForm.addEventListener("submit", sendMessage);
    leaveChatBtn.addEventListener("click", leaveCurrentRoom);
    if (aiSummarizeBtn) aiSummarizeBtn.addEventListener("click", handleAiSummarize);
    if (closeAiModalBtn) closeAiModalBtn.addEventListener("click", () => aiModal.classList.add("hidden"));

    const backToSidebarBtn = document.getElementById("back-to-sidebar-btn");
    if (backToSidebarBtn) {
        backToSidebarBtn.addEventListener("click", () => {
            const sidebar = document.querySelector(".sidebar");
            if (sidebar) sidebar.classList.remove("mobile-hidden");
            activeChatView.classList.add("hidden");
            welcomeChatView.classList.remove("hidden");
        });
    }

    const metaAiBtn = document.getElementById("meta-ai-btn");
    if (metaAiBtn) {
        metaAiBtn.addEventListener("click", () => selectChat("private", "Meta AI"));
    }

    checkAutoLogin();
});

// --- Tab Switcher Logic ---
function showAuthTab(tab) {
    clearAuthMessages();
    if (tab === "login") {
        tabLogin.classList.add("active");
        tabRegister.classList.remove("active");
        loginForm.classList.add("active");
        registerForm.classList.remove("active");
    } else {
        tabLogin.classList.remove("active");
        tabRegister.classList.add("active");
        loginForm.classList.remove("active");
        registerForm.classList.add("active");
    }
}

function clearAuthMessages() {
    loginError.classList.add("hidden");
    registerSuccess.classList.add("hidden");
}

// --- Auth REST Endpoints Logic ---
function handleRegisterSubmit(e) {
    e.preventDefault();
    clearAuthMessages();

    const username = registerUsernameInput.value.trim();
    const email = registerEmailInput.value.trim();
    const password = registerPasswordInput.value;

    const payload = { username, email, password };

    fetch("/api/auth/register", {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify(payload)
    })
    .then(res => res.json().then(data => ({ status: res.status, body: data })))
    .then(result => {
        if (result.status === 200) {
            successText.textContent = "Registration successful! Please log in.";
            registerSuccess.classList.remove("hidden");
            registerForm.reset();
            // Switch to login tab after brief delay
            setTimeout(() => showAuthTab("login"), 1500);
        } else {
            showLoginError(result.body.error || "Registration failed.");
        }
    })
    .catch(err => {
        console.error("Registration error:", err);
        showLoginError("Server communication failed.");
    });
}

function handleLoginSubmit(e) {
    e.preventDefault();
    clearAuthMessages();

    const email = loginEmailInput.value.trim();
    const password = loginPasswordInput.value;

    const payload = { email, password };

    // Disable button during login
    const submitBtn = loginForm.querySelector("button[type='submit']");
    submitBtn.disabled = true;
    submitBtn.innerHTML = `<span>Logging in...</span> <i class="fa-solid fa-spinner fa-spin"></i>`;

    fetch("/api/auth/login", {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify(payload)
    })
    .then(res => res.json().then(data => ({ status: res.status, body: data })))
    .then(result => {
        if (result.status === 200) {
            // Save JWT in local storage
            localStorage.setItem("nexus_jwt", result.body.token);
            localStorage.setItem("nexus_username", result.body.username);

            // Connect to WebSocket with token
            connectToWebSocket(result.body.token, result.body.username);
        } else {
            showLoginError(result.body.error || "Invalid credentials.");
            submitBtn.disabled = false;
            submitBtn.innerHTML = `<span>Login</span> <i class="fa-solid fa-arrow-right-to-bracket"></i>`;
        }
    })
    .catch(err => {
        console.error("Login error:", err);
        showLoginError("Server connection failed.");
        submitBtn.disabled = false;
        submitBtn.innerHTML = `<span>Login</span> <i class="fa-solid fa-arrow-right-to-bracket"></i>`;
    });
}

function checkAutoLogin() {
    const token = localStorage.getItem("nexus_jwt");
    const username = localStorage.getItem("nexus_username");

    if (token && username) {
        connectToWebSocket(token, username);
    }
}

function handleLogout() {
    if (ws) {
        ws.close(); // Triggers onclose lifecycle cleanup
    } else {
        goBackToLogin();
    }
}

// --- WebSocket Connection Secured by Handshake Token ---
function connectToWebSocket(token, username) {
    const protocol = window.location.protocol === "https:" ? "wss:" : "ws:";
    const host = window.location.host || "localhost:8081";
    // Pass JWT token as a URL parameter for authentication check
    const wsUrl = `${protocol}//${host}/chat?token=${encodeURIComponent(token)}`;

    try {
        ws = new WebSocket(wsUrl);

        ws.onopen = () => {
            console.log("WebSocket connection established with authenticated token");
            // No CONNECT message type needed anymore!
        };

        ws.onmessage = (event) => {
            handleServerMessage(JSON.parse(event.data));
        };

        ws.onerror = (err) => {
            console.error("WebSocket validation or connectivity error:", err);
            showLoginError("WebSocket authentication failed.");
            handleLogoutCleanup();
        };

        ws.onclose = () => {
            console.log("WebSocket Connection Closed. Attempting auto-reconnect...");
            const token = localStorage.getItem("nexus_jwt");
            const username = localStorage.getItem("nexus_username");
            if (token && username) {
                setTimeout(() => {
                    if (!ws || ws.readyState === WebSocket.CLOSED) {
                        connectToWebSocket(token, username);
                    }
                }, 2000);
            } else {
                handleLogoutCleanup();
                goBackToLogin();
            }
        };

    } catch (e) {
        console.error("Connection attempt failed:", e);
        showLoginError("Unable to establish secure connection.");
        handleLogoutCleanup();
    }
}

function handleLogoutCleanup() {
    localStorage.removeItem("nexus_jwt");
    localStorage.removeItem("nexus_username");
    
    // Reset login forms state
    const submitBtn = loginForm.querySelector("button[type='submit']");
    submitBtn.disabled = false;
    submitBtn.innerHTML = `<span>Login</span> <i class="fa-solid fa-arrow-right-to-bracket"></i>`;
}

function showLoginError(msg) {
    errorText.textContent = msg;
    loginError.classList.remove("hidden");
}

function goBackToLogin() {
    chatDashboard.classList.add("hidden");
    loginContainer.classList.remove("hidden");
    loginContainer.classList.add("active");
    
    activeChat = null;
    joinedRooms.clear();
    usersDb = [];
    chatHistories = {};
    unreadCounts = {};
    clearMediaSelection();
    
    renderRoomsList();
    renderUserList();
}

// --- Core Message Router ---
function handleServerMessage(msg) {
    console.log("Routing msg:", msg.type, msg);
    
    switch (msg.type) {
        case "CONNECTED":
            currentNickname = msg.sender;
            loginContainer.classList.remove("active");
            loginContainer.classList.add("hidden");
            chatDashboard.classList.remove("hidden");
            
            userDisplayName.textContent = currentNickname;
            userAvatar.style.background = getGradientForName(currentNickname);
            userAvatar.textContent = currentNickname.substring(0, 2).toUpperCase();
            break;

        case "ERROR":
            alert(`Error: ${msg.content}`);
            break;

        case "USER_LIST":
            usersDb = msg.userProfiles.filter(u => u.username !== currentNickname);
            const activeOnlineCount = msg.userProfiles.filter(u => u.status === 'ONLINE').length;
            onlineCount.textContent = activeOnlineCount;
            
            renderUserList();
            break;

        case "ROOM_UPDATE":
            const rCode = msg.roomCode;
            const key = `room:${rCode}`;
            if (!chatHistories[key]) chatHistories[key] = [];
            
            if (activeChat && activeChat.type === "room" && activeChat.target === rCode) {
                chatSubtitle.textContent = `${msg.users.length} members online`;
            }
            break;

        case "ROOM_JOIN_NOTIFY":
        case "ROOM_LEAVE_NOTIFY":
            const roomTarget = msg.roomCode;
            const sysKey = `room:${roomTarget}`;
            const sysMsg = {
                sender: "SYSTEM",
                content: msg.content,
                timestamp: msg.timestamp || new Date().toISOString()
            };
            
            if (!chatHistories[sysKey]) chatHistories[sysKey] = [];
            chatHistories[sysKey].push(sysMsg);
            
            if (activeChat && activeChat.type === "room" && activeChat.target === roomTarget) {
                appendMessageToUI(sysMsg);
            }
            break;

        case "CHAT_ROOM":
            const chatRoomCode = msg.roomCode;
            const roomKey = `room:${chatRoomCode}`;
            
            if (!chatHistories[roomKey]) chatHistories[roomKey] = [];
            chatHistories[roomKey].push(msg);

            if (activeChat && activeChat.type === "room" && activeChat.target === chatRoomCode) {
                appendMessageToUI(msg);
            } else {
                incrementUnread(roomKey);
                renderRoomsList();
            }
            break;

        case "CHAT_PRIVATE":
            const peer = msg.sender === currentNickname ? msg.recipient : msg.sender;
            const privateKey = `private:${peer}`;

            if (!chatHistories[privateKey]) chatHistories[privateKey] = [];
            chatHistories[privateKey].push(msg);

            if (activeChat && activeChat.type === "private" && activeChat.target === peer) {
                appendMessageToUI(msg);
            } else {
                incrementUnread(privateKey);
                renderUserList();
            }
            break;

        case "CHAT_HISTORY":
            const histKey = msg.roomCode ? `room:${msg.roomCode}` : `private:${msg.recipient}`;
            const isActive = activeChat && 
                             ((msg.roomCode && activeChat.type === "room" && activeChat.target === msg.roomCode) ||
                              (msg.recipient && activeChat.type === "private" && activeChat.target === msg.recipient));
            
            chatHistories[histKey] = msg.history;
            
            if (isActive) {
                messagesContainer.innerHTML = "";
                if (msg.history.length === 0) {
                    messagesContainer.innerHTML = `
                        <div class="system-msg-wrapper">
                            <div class="system-msg-bubble">No previous messages in this conversation. Send a message to start!</div>
                        </div>
                    `;
                } else {
                    msg.history.forEach(appendMessageToUI);
                }
                scrollToBottom();
            }
            break;
    }
}

// --- Unread Counters ---
function incrementUnread(key) {
    unreadCounts[key] = (unreadCounts[key] || 0) + 1;
}

function clearUnread(key) {
    unreadCounts[key] = 0;
}

// --- User List Rendering ---
function renderUserList() {
    usersList.innerHTML = "";
    if (usersDb.length === 0) {
        usersList.innerHTML = `<li class="sidebar-list-item" style="cursor: default; color: var(--text-muted); font-size: 0.85rem;">No registered users</li>`;
        return;
    }

    const sortedUsers = [...usersDb].sort((a, b) => {
        if (a.status === b.status) {
            return a.username.localeCompare(b.username);
        }
        return a.status === 'ONLINE' ? -1 : 1;
    });

    sortedUsers.forEach(user => {
        const key = `private:${user.username}`;
        const unread = unreadCounts[key] || 0;
        const isActive = activeChat && activeChat.type === "private" && activeChat.target === user.username;
        const isOnline = user.status === "ONLINE";

        const li = document.createElement("li");
        li.className = `sidebar-list-item ${isActive ? 'active' : ''} ${!isOnline ? 'offline' : ''}`;
        
        let subText = isOnline ? "Online" : "Offline";
        if (!isOnline && user.lastSeen) {
            subText = `Last seen ${formatTime(user.lastSeen)}`;
        }

        li.innerHTML = `
            <div class="avatar small" style="background: ${getGradientForName(user.username)}">
                ${user.username.substring(0, 2).toUpperCase()}
            </div>
            <div class="user-info" style="flex: 1; overflow: hidden;">
                <span class="item-name" style="margin: 0; font-size: 0.9rem;">${user.username}</span>
                <span class="subtitle" style="font-size: 0.7rem; color: var(--text-muted); margin-top: 1px;">${subText}</span>
            </div>
            <span class="status-indicator ${isOnline ? 'online' : 'offline'}"></span>
            ${unread > 0 ? `<span class="unread-badge">${unread}</span>` : ""}
        `;

        li.addEventListener("click", () => selectChat("private", user.username));
        usersList.appendChild(li);
    });
}

// --- Room Management ---
function joinRoomFromInput() {
    const code = roomCodeInput.value.trim().toUpperCase();
    if (!code) return;
    
    joinRoom(code);
    roomCodeInput.value = "";
}

function joinRoom(code) {
    if (joinedRooms.has(code)) {
        selectChat("room", code);
        return;
    }

    joinedRooms.add(code);
    const joinPayload = {
        type: "JOIN_ROOM",
        sender: currentNickname,
        roomCode: code
    };
    ws.send(JSON.stringify(joinPayload));
    
    selectChat("room", code);
}

function leaveCurrentRoom() {
    if (!activeChat || activeChat.type !== "room") return;
    const roomCode = activeChat.target;
    
    const leavePayload = {
        type: "LEAVE_ROOM",
        sender: currentNickname,
        roomCode: roomCode
    };
    ws.send(JSON.stringify(leavePayload));

    joinedRooms.delete(roomCode);
    delete chatHistories[`room:${roomCode}`];
    delete unreadCounts[`room:${roomCode}`];

    activeChat = null;
    renderRoomsList();
    showPlaceholder();
}

function renderRoomsList() {
    roomsList.innerHTML = "";
    if (joinedRooms.size === 0) {
        roomsList.innerHTML = `<li class="sidebar-list-item" style="cursor: default; color: var(--text-muted); font-size: 0.85rem;">No active rooms</li>`;
        return;
    }

    joinedRooms.forEach(room => {
        const key = `room:${room}`;
        const unread = unreadCounts[key] || 0;
        const isActive = activeChat && activeChat.type === "room" && activeChat.target === room;

        const li = document.createElement("li");
        li.className = `sidebar-list-item ${isActive ? 'active' : ''}`;
        li.innerHTML = `
            <span class="room-hash">#</span>
            <span class="item-name">${room}</span>
            ${unread > 0 ? `<span class="unread-badge">${unread}</span>` : ""}
        `;

        li.addEventListener("click", () => selectChat("room", room));
        roomsList.appendChild(li);
    });
}

// --- Chat View Switcher ---
function selectChat(type, target) {
    activeChat = { type, target };
    const chatKey = `${type}:${target}`;
    
    clearUnread(chatKey);
    
    renderUserList();
    renderRoomsList();

    const sidebar = document.querySelector(".sidebar");
    if (sidebar) sidebar.classList.add("mobile-hidden");

    welcomeChatView.classList.add("hidden");
    activeChatView.classList.remove("hidden");

    if (type === "room") {
        chatTitle.textContent = `Room: #${target}`;
        chatSubtitle.textContent = "Loading members...";
        chatAvatar.textContent = "#";
        chatAvatar.style.background = "linear-gradient(135deg, hsl(200, 70%, 50%), hsl(220, 70%, 40%))";
        leaveChatBtn.classList.remove("hidden");
        if (aiSummarizeBtn) aiSummarizeBtn.classList.remove("hidden");
    } else {
        chatTitle.textContent = target;
        
        const userObj = usersDb.find(u => u.username === target);
        const isOnline = userObj ? userObj.status === "ONLINE" : false;
        chatSubtitle.textContent = isOnline ? "Online" : "Offline";
        
        chatAvatar.textContent = target.substring(0, 2).toUpperCase();
        chatAvatar.style.background = getGradientForName(target);
        leaveChatBtn.classList.add("hidden");
        if (aiSummarizeBtn) aiSummarizeBtn.classList.add("hidden");
    }

    messagesContainer.innerHTML = `
        <div class="system-msg-wrapper">
            <div class="system-msg-bubble">
                <i class="fa-solid fa-spinner fa-spin" style="margin-right: 6px;"></i> Loading chat history...
            </div>
        </div>
    `;
    
    if (ws && ws.readyState === WebSocket.OPEN) {
        const historyRequest = {
            type: "GET_HISTORY",
            sender: currentNickname
        };
        if (type === "room") {
            historyRequest.roomCode = target;
        } else {
            historyRequest.recipient = target;
        }
        ws.send(JSON.stringify(historyRequest));
    }
    
    messageInput.focus();
}

function showPlaceholder() {
    activeChatView.classList.add("hidden");
    welcomeChatView.classList.remove("hidden");
}

// --- Media Selection Logic ---
function handleFileSelection(e) {
    const file = e.target.files[0];
    if (!file) return;

    if (file.size > 5 * 1024 * 1024) {
        alert("File is too large! Maximum limit is 5MB.");
        clearMediaSelection();
        return;
    }

    const reader = new FileReader();
    reader.onload = function(evt) {
        selectedFileData = {
            data: evt.target.result, // Base64 data URL
            name: file.name,
            type: file.type,
            size: formatBytes(file.size)
        };

        mediaPreviewName.textContent = selectedFileData.name;
        mediaPreviewSize.textContent = selectedFileData.size;
        
        if (file.type.startsWith("image/")) {
            mediaPreviewImg.src = selectedFileData.data;
            mediaPreviewImg.classList.remove("hidden");
            mediaPreviewFile.classList.add("hidden");
        } else {
            mediaPreviewImg.classList.add("hidden");
            mediaPreviewFile.classList.remove("hidden");
        }
        
        mediaPreviewContainer.classList.remove("hidden");
        messageInput.focus();
    };
    reader.readAsDataURL(file);
}

function clearMediaSelection() {
    selectedFileData = null;
    mediaFileInput.value = "";
    mediaPreviewContainer.classList.add("hidden");
    mediaPreviewImg.src = "";
}

// --- UI Rendering ---
function appendMessageToUI(msg) {
    const isSystem = msg.sender === "SYSTEM";
    const isOutgoing = msg.sender === currentNickname;
    
    const wrapper = document.createElement("div");
    
    if (isSystem) {
        wrapper.className = "system-msg-wrapper";
        wrapper.innerHTML = `<div class="system-msg-bubble">${msg.content}</div>`;
    } else {
        wrapper.className = `message-wrapper ${isOutgoing ? 'outgoing' : 'incoming'}`;
        
        const timestamp = formatTime(msg.timestamp);
        const nameLabel = isOutgoing ? "You" : msg.sender;
        const avatarLetter = msg.sender.substring(0, 2).toUpperCase();
        
        let mediaHtml = "";
        if (msg.mediaData) {
            if (msg.mediaType && msg.mediaType.startsWith("image/")) {
                mediaHtml = `<img src="${msg.mediaData}" class="msg-media-img" alt="shared image" onclick="window.open('${msg.mediaData}')">`;
            } else {
                mediaHtml = `
                    <a href="${msg.mediaData}" download="${msg.mediaName}" class="msg-media-file">
                        <i class="fa-solid fa-file-arrow-down"></i>
                        <div class="file-info">
                            <span class="name">${msg.mediaName}</span>
                            <span class="action">Click to download</span>
                        </div>
                    </a>
                `;
            }
        }
        
        let rawContent = msg.content;
        if (rawContent && typeof CryptoJS !== "undefined" && rawContent.startsWith("ENC:")) {
            try {
                let key = activeChat ? activeChat.target : "nexus_key";
                let bytes = CryptoJS.AES.decrypt(rawContent.substring(4), key);
                let decrypted = bytes.toString(CryptoJS.enc.Utf8);
                if (decrypted) rawContent = decrypted;
            } catch (err) {}
        }
        const contentHtml = rawContent ? `<div class="msg-content">${escapeHTML(rawContent)}</div>` : "";
        
        wrapper.innerHTML = `
            ${!isOutgoing ? `<div class="avatar small" style="background: ${getGradientForName(msg.sender)}">${avatarLetter}</div>` : ""}
            <div class="message-bubble">
                ${mediaHtml}
                ${contentHtml}
                <div class="msg-meta">
                    ${!isOutgoing ? `<span class="sender-name">${nameLabel}</span>` : ""}
                    <span class="msg-time">${timestamp}</span>
                </div>
            </div>
        `;
    }
    
    messagesContainer.appendChild(wrapper);
    scrollToBottom();
}

function sendMessage(e) {
    if (e) e.preventDefault();

    if (!activeChat) {
        alert("Please select or join a room/chat first!");
        return;
    }

    if (!ws || ws.readyState !== WebSocket.OPEN) {
        alert("Reconnecting to chat server... Please try again in 2 seconds.");
        checkAutoLogin();
        return;
    }

    const content = messageInput.value.trim();
    if (!content && !selectedFileData) return;

    const payload = {
        sender: currentNickname
    };

    if (activeChat.type === "room") {
        payload.type = "SEND_ROOM_MSG";
        payload.roomCode = activeChat.target;
    } else {
        payload.type = "SEND_PRIVATE_MSG";
        payload.recipient = activeChat.target;
    }

    if (content) {
        let finalContent = content;
        if (typeof CryptoJS !== "undefined" && !content.startsWith("@ai")) {
            try {
                let key = activeChat.target;
                finalContent = "ENC:" + CryptoJS.AES.encrypt(content, key).toString();
            } catch (err) {}
        }
        payload.content = finalContent;
    }

    if (selectedFileData) {
        payload.mediaData = selectedFileData.data;
        payload.mediaName = selectedFileData.name;
        payload.mediaType = selectedFileData.type;
    }

    ws.send(JSON.stringify(payload));
    
    messageInput.value = "";
    clearMediaSelection();
}

// --- Helper Utilities ---
function scrollToBottom() {
    messagesContainer.scrollTop = messagesContainer.scrollHeight;
}

function formatTime(timestampParam) {
    if (!timestampParam) return "";
    try {
        const date = new Date(timestampParam);
        return date.toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' });
    } catch (e) {
        return "";
    }
}

function escapeHTML(str) {
    return str.replace(/[&<>'"]/g, 
        tag => ({
            '&': '&amp;',
            '<': '&lt;',
            '>': '&gt;',
            "'": '&#39;',
            '"': '&quot;'
        }[tag] || tag)
    );
}

function getGradientForName(name) {
    let hash = 0;
    for (let i = 0; i < name.length; i++) {
        hash = name.charCodeAt(i) + ((hash << 5) - hash);
    }
    const h1 = Math.abs(hash % 360);
    const h2 = (h1 + 60) % 360;
    return `linear-gradient(135deg, hsl(${h1}, 70%, 55%), hsl(${h2}, 65%, 45%))`;
}

function formatBytes(bytes, decimals = 2) {
    if (bytes === 0) return '0 Bytes';
    const k = 1024;
    const dm = decimals < 0 ? 0 : decimals;
    const sizes = ['Bytes', 'KB', 'MB', 'GB'];
    const i = Math.floor(Math.log(bytes) / Math.log(k));
    return parseFloat((bytes / Math.pow(k, i)).toFixed(dm)) + ' ' + sizes[i];
}

// --- AI Room Summarize Handler ---
function handleAiSummarize() {
    if (!activeChat || activeChat.type !== "room") return;
    const roomCode = activeChat.target;

    aiModal.classList.remove("hidden");
    aiModalBody.innerHTML = `<p><i class="fa-solid fa-spinner fa-spin"></i> Generating intelligent room summary for <strong>#${roomCode}</strong>...</p>`;

    fetch(`/api/ai/summarize-room?roomCode=${encodeURIComponent(roomCode)}`)
        .then(res => res.json())
        .then(data => {
            if (data.summary) {
                aiModalBody.innerHTML = data.summary;
            } else {
                aiModalBody.innerHTML = `<p style="color: var(--accent-red);">${data.error || "Failed to generate summary."}</p>`;
            }
        })
        .catch(err => {
            console.error("AI Summary error:", err);
            aiModalBody.innerHTML = `<p style="color: var(--accent-red);">Failed to connect to AI service.</p>`;
        });
}
