// src/main/resources/static/js/modules/chat.js
export const chatModule = {
    data() {
        return {
            chatOpen: false,
            chatContacts: [],
            activeChatUser: null,
            chatMessages: [],
            chatInput: '',
            chatSearchQuery: '',
            chatSearchResults: [],
            stompClient: null,
            totalUnreadCount: 0,
            chatSearchTimeout: null,
            presenceHeartbeatTimer: null,
            notifications: [],
            notificationUnreadCount: 0,
            notificationPollingTimer: null,

            profileUsername: '', // This might be redundant if userProfileModule handles it
            // Thêm các thuộc tính này
            isFollowing: false,
            followersCount: 0,
            followingCount: 0,
            friendStatus: { id: null, status: 'NONE' },
            friends: [],
            friendRequests: [],

            shareModalData: {
                show: false,
                song: null,
                url: '',
                copied: false,
                userSearchQuery: '',
                userSearchResults: [],
                noteMessage: '',
                isSearchingUsers: false,
                sendingUsername: null
            }
        };
    },
    watch: {
        'shareModalData.userSearchQuery': function (newVal) {
            if (this.shareModalSearchTimeout) clearTimeout(this.shareModalSearchTimeout);
            if (!newVal || !newVal.trim()) {
                this.shareModalData.userSearchResults = [];
                return;
            }
            this.shareModalData.isSearchingUsers = true;
            this.shareModalSearchTimeout = setTimeout(() => {
                axios.get('/api/users/search?query=' + encodeURIComponent(newVal.trim()))
                    .then(res => {
                        const list = res.data || [];
                        this.shareModalData.userSearchResults = list.filter(u => u.username !== this.currentUser);
                    })
                    .catch(err => {
                        console.error('Lỗi tìm kiếm người dùng:', err);
                        this.shareModalData.userSearchResults = [];
                    })
                    .finally(() => {
                        this.shareModalData.isSearchingUsers = false;
                    });
            }, 300);
        },
    },
    methods: {
        connectWebSocket() {
            if (this.stompClient && this.stompClient.connected) return;
            const socket = new SockJS('/ws');
            this.stompClient = Stomp.over(socket);
            this.stompClient.debug = null;
            this.stompClient.connect({}, (frame) => {
                this.stompClient.subscribe('/user/queue/messages', (messageOutput) => {
                    const message = JSON.parse(messageOutput.body);
                    this.handleIncomingChatMessage(message);
                });
            }, (error) => {
                setTimeout(() => {
                    if (this.currentUser) this.connectWebSocket();
                }, 5000);
            });
        },
        isMyMessage(msg) {
            if (!msg || !msg.sender || !this.currentUser) return false;
            const senderName = typeof msg.sender === 'object' ? msg.sender.username : msg.sender;
            return String(senderName).toLowerCase() === String(this.currentUser).toLowerCase();
        },
        handleIncomingChatMessage(rawMessage) {
            if (!rawMessage) return;
            const senderUsername = (typeof rawMessage.sender === 'object' && rawMessage.sender !== null)
                ? rawMessage.sender.username
                : rawMessage.sender;
            const recipientUsername = (typeof rawMessage.recipient === 'object' && rawMessage.recipient !== null)
                ? rawMessage.recipient.username
                : rawMessage.recipient;

            const normalizedMessage = {
                id: rawMessage.id,
                sender: senderUsername,
                recipient: recipientUsername,
                content: rawMessage.content,
                timestamp: rawMessage.timestamp,
                isRead: rawMessage.isRead
            };

            if (this.activeChatUser &&
                ((senderUsername === this.activeChatUser.username && recipientUsername === this.currentUser) ||
                 (senderUsername === this.currentUser && recipientUsername === this.activeChatUser.username))) {

                this.chatMessages.push(normalizedMessage);
                this.scrollToBottom();

                if (recipientUsername === this.currentUser) {
                    axios.put(`/api/chat/messages/read-all?partner=${senderUsername}`)
                        .then(() => { this.loadRecentChats(); });
                } else {
                    this.loadRecentChats();
                }
            } else {
                this.loadRecentChats();
                this.loadTotalUnreadCount();

                if (senderUsername !== this.currentUser) {
                    const senderDisplayName = (typeof rawMessage.sender === 'object' && rawMessage.sender !== null && rawMessage.sender.fullname)
                        ? rawMessage.sender.fullname
                        : senderUsername;
                    this.Toast.fire({
                        icon: 'info',
                        title: `Tin nhắn mới từ ${senderDisplayName}`,
                        text: (rawMessage.content || '').substring(0, 30) + ((rawMessage.content || '').length > 30 ? '...' : '')
                    });
                }
            }
        },
        loadRecentChats() {
            axios.get('/api/chat/recent-chats')
                .then(response => { this.chatContacts = response.data; })
                .catch(err => console.error("Lỗi tải tin nhắn gần đây:", err));
        },
        loadTotalUnreadCount() {
            axios.get('/api/chat/unread-count')
                .then(response => { this.totalUnreadCount = response.data.unreadCount; })
                .catch(err => console.error("Lỗi tải số tin nhắn chưa đọc:", err));
        },
        toggleChat() {
            this.chatOpen = !this.chatOpen;
            if (this.chatOpen) {
                this.loadRecentChats();
                this.loadTotalUnreadCount();
                if (this.activeChatUser) this.scrollToBottom();
            }
        },
        openChatRoom(contact) {
            this.activeChatUser = {
                username: contact.username,
                fullname: contact.fullname,
                photo: contact.photo,
                online: !!contact.online,
                lastSeenAt: contact.lastSeenAt
            };
            this.chatMessages = [];
            this.chatInput = '';

            axios.get(`/api/chat/history?partner=${contact.username}`)
                .then(response => {
                    this.chatMessages = response.data;
                    this.scrollToBottom();
                    return axios.put(`/api/chat/messages/read-all?partner=${contact.username}`);
                })
                .then(() => {
                    this.loadRecentChats();
                    this.loadTotalUnreadCount();
                })
                .catch(err => console.error("Lỗi tải lịch sử chat:", err));
        },
        backToContacts() {
            this.activeChatUser = null;
            this.chatMessages = [];
            this.chatInput = '';
            this.loadRecentChats();
            this.loadTotalUnreadCount();
        },
        searchChatUsers() {
            if (this.chatSearchTimeout) clearTimeout(this.chatSearchTimeout);
            if (!this.chatSearchQuery || !this.chatSearchQuery.trim()) {
                this.chatSearchResults = [];
                return;
            }
            this.chatSearchTimeout = setTimeout(() => {
                axios.get(`/api/chat/search-users?query=${this.chatSearchQuery}`)
                    .then(response => { this.chatSearchResults = response.data; })
                    .catch(err => console.error("Lỗi tìm kiếm user:", err));
            }, 300);
        },
        clearChatSearch() {
            this.chatSearchQuery = '';
            this.chatSearchResults = [];
        },
        startChatWith(username) {
            if (!this.currentUser) {
                Swal.fire({
                    icon: 'warning',
                    title: 'Yêu cầu đăng nhập',
                    text: 'Vui lòng đăng nhập để thực hiện nhắn tin với thành viên khác!',
                    confirmButtonColor: '#16a34a'
                });
                return;
            }
            axios.get(`/api/users/${username}/profile`)
                .then(response => {
                    const u = response.data;
                    const contact = {
                        username: u.username,
                        fullname: u.fullname,
                        photo: u.photo,
                        online: !!u.online,
                        lastSeenAt: u.lastSeenAt
                    };
                    this.chatOpen = true;
                    this.openChatRoom(contact);
                })
                .catch(err => {
                    Swal.fire('Lỗi', 'Không thể bắt đầu trò chuyện với người dùng này.', 'error');
                });
        },
        startChatWithUser(user) {
            this.clearChatSearch();
            this.openChatRoom(user);
        },
        sendChatMessage() {
            if (!this.chatInput || !this.chatInput.trim() || !this.activeChatUser || !this.stompClient || !this.stompClient.connected) return;
            const chatMessage = {
                recipientUsername: this.activeChatUser.username,
                content: this.chatInput.trim()
            };
            this.stompClient.send("/app/chat.send", {}, JSON.stringify(chatMessage));
            this.chatInput = '';
        },
        formatChatTime(dateString) {
            if (!dateString) return '';
            const date = new Date(dateString);
            if (isNaN(date.getTime())) return '';
            const now = new Date();
            const isToday = date.toDateString() === now.toDateString();
            const pad = (n) => n < 10 ? '0' + n : n;
            const timeStr = `${pad(date.getHours())}:${pad(date.getMinutes())}`;
            if (isToday) return timeStr;
            return `${pad(date.getDate())}/${pad(date.getMonth() + 1)} ${timeStr}`;
        },
        startPresenceHeartbeat() {
            if (!this.currentUser) return;
            const beat = () => axios.post('/api/presence/heartbeat').catch(() => {});
            beat();
            if (this.presenceHeartbeatTimer) clearInterval(this.presenceHeartbeatTimer);
            this.presenceHeartbeatTimer = setInterval(beat, 45000);
        },
        formatPresenceStatus(user) {
            if (user && user.online) return 'Trực tuyến';
            const lastSeen = user && user.lastSeenAt ? new Date(user.lastSeenAt) : null;
            if (!lastSeen || isNaN(lastSeen.getTime())) return 'Ngoại tuyến';
            return 'Hoạt động ' + this.formatRelativeTime(lastSeen);
        },
        loadFriendStatus() {
            if (!this.currentUser || !this.profileUsername || this.profileUsername === this.currentUser) {
                this.friendStatus = { id: null, status: 'SELF' };
                return;
            }
            axios.get(`/api/friends/status/${encodeURIComponent(this.profileUsername)}`)
                .then(res => { this.friendStatus = { id: res.data.id || null, status: res.data.status || 'NONE' }; })
                .catch(() => { this.friendStatus = { id: null, status: 'NONE' }; });
        },
        toggleFollow() {
            if (!this.currentUser) {
                Swal.fire({ icon: 'warning', title: 'Đăng nhập', text: 'Vui lòng đăng nhập để thực hiện theo dõi!' });
                return;
            }
            const action = this.isFollowing ? 'unfollow' : 'follow';
            axios.post(`/api/users/${this.profileUsername}/${action}`)
                .then(res => {
                    this.isFollowing = !this.isFollowing;
                    this.loadFollowStatus();
                    this.Toast.fire({ icon: 'success', title: res.data.message });
                })
                .catch(err => {
                    Swal.fire({ icon: 'error', title: 'Thất bại', text: err.response?.data?.message || 'Có lỗi xảy ra!' });
                });
        },
        sendFriendRequest() {
            axios.post(`/api/friends/${encodeURIComponent(this.profileUsername)}`)
                .then(res => {
                    this.friendStatus = { id: res.data.id, status: res.data.status };
                    this.Toast.fire({ icon: 'success', title: 'Đã gửi lời mời kết bạn' });
                })
                .catch(err => Swal.fire('Không thể kết bạn', err.response?.data?.message || 'Có lỗi xảy ra', 'error'));
        },
        acceptFriendRequest() {
            if (!this.friendStatus.id) return;
            axios.put(`/api/friends/${this.friendStatus.id}/accept`)
                .then(() => {
                    this.friendStatus.status = 'ACCEPTED';
                    this.Toast.fire({ icon: 'success', title: 'Đã trở thành bạn bè' });
                });
        },
        removeFriendship() {
            if (!this.friendStatus.id) return;
            axios.delete(`/api/friends/${this.friendStatus.id}`)
                .then(() => {
                    this.friendStatus = { id: null, status: 'NONE' };
                    this.Toast.fire({ icon: 'success', title: 'Đã cập nhật quan hệ bạn bè' });
                });
        },
        loadNotifications(showErrors = false) {
            if (!this.currentUser) return;
            axios.get('/api/notifications?limit=20')
                .then(response => {
                    this.notifications = response.data || [];
                    this.notificationUnreadCount =
                        this.notifications.filter(notification => !notification.read).length;
                })
                .catch(error => {
                    if (showErrors) console.error('Không thể tải thông báo:', error);
                });
        },
        openNotification(notification) {
            const navigate = () => {
                if (notification.refId) window.location.href = `/song/${notification.refId}`;
            };
            if (notification.read) {
                navigate();
                return;
            }
            axios.put(`/api/notifications/${notification.id}/read`)
                .then(() => {
                    notification.read = true;
                    this.notificationUnreadCount = Math.max(0, this.notificationUnreadCount - 1);
                })
                .finally(navigate);
        },
        markAllNotificationsRead() {
            axios.put('/api/notifications/read-all')
                .then(() => {
                    this.notifications.forEach(notification => { notification.read = true; });
                    this.notificationUnreadCount = 0;
                });
        },
        loadFriends() {
            if (!this.currentUser) return;
            axios.get('/api/friends').then(res => { this.friends = res.data || []; });
            axios.get('/api/friends/requests').then(res => { this.friendRequests = res.data || []; });
        },
        openShareModal(song) {
            if (!song || !song.id) return;
            const absoluteUrl = window.location.origin + '/song/' + song.id;
            this.shareModalData = {
                show: true,
                song: song,
                url: absoluteUrl,
                copied: false,
                chatHistoryContacts: [],
                isLoadingContacts: true,
                noteMessage: `Đã chia sẻ bài hát "${song.title}"`,
                sendingUsername: null
            };

            // Nạp danh sách những người dùng có trong Lịch sử Chat gần đây
            if (this.currentUser) {
                axios.get('/api/chat/recent-chats')
                    .then(res => {
                        const list = res.data || [];
                        this.shareModalData.chatHistoryContacts = list.filter(c => c.username !== this.currentUser);
                    })
                    .catch(err => {
                        console.error('Lỗi khi tải lịch sử chat:', err);
                        this.shareModalData.chatHistoryContacts = [];
                    })
                    .finally(() => {
                        this.shareModalData.isLoadingContacts = false;
                    });
            } else {
                this.shareModalData.isLoadingContacts = false;
            }
        },
        closeShareModal() {
            this.shareModalData.show = false;
        },
        copyShareLink() {
            if (!this.shareModalData.url) return;
            navigator.clipboard.writeText(this.shareModalData.url).then(() => {
                this.shareModalData.copied = true;
                if (this.Toast) {
                    this.Toast.fire({ icon: 'success', title: 'Đã sao chép liên kết bài hát!' });
                } else if (typeof Swal !== 'undefined') {
                    Swal.fire({
                        toast: true,
                        position: 'top-end',
                        icon: 'success',
                        title: 'Đã sao chép liên kết bài hát!',
                        showConfirmButton: false,
                        timer: 2000
                    });
                }
                setTimeout(() => {
                    this.shareModalData.copied = false;
                }, 3000);
            }).catch(err => {
                console.error('Lỗi khi sao chép link:', err);
                window.prompt('Sao chép liên kết bài hát:', this.shareModalData.url);
            });
        },
        sendSongToUser(targetUser) {
            if (!this.currentUser) {
                Swal.fire({
                    icon: 'warning',
                    title: 'Yêu cầu đăng nhập',
                    text: 'Vui lòng đăng nhập để chia sẻ bài hát tới thành viên khác!',
                    confirmButtonColor: '#16a34a'
                });
                return;
            }

            if (!targetUser || !targetUser.username) return;

            const song = this.shareModalData.song;
            if (!song) return;

            this.shareModalData.sendingUsername = targetUser.username;

            const messageContent = `🎵 [CHIA SẺ BÀI HÁT] ${song.title}\n🔗 Link: ${this.shareModalData.url}\n💬 ${this.shareModalData.noteMessage || 'Nghe thử giai điệu này nhé!'}`;

            const chatMessage = {
                recipientUsername: targetUser.username,
                content: messageContent
            };

            if (this.stompClient && this.stompClient.connected) {
                this.stompClient.send("/app/chat.send", {}, JSON.stringify(chatMessage));
                this.onSendSongSuccess(targetUser);
            } else {
                axios.post('/api/chat/send', {
                    recipientUsername: targetUser.username,
                    content: messageContent
                }).then(() => {
                    this.onSendSongSuccess(targetUser);
                }).catch(err => {
                    if (this.stompClient) {
                        try {
                            this.stompClient.send("/app/chat.send", {}, JSON.stringify(chatMessage));
                            this.onSendSongSuccess(targetUser);
                            return;
                        } catch (e) {}
                    }
                    Swal.fire('Lỗi', 'Không thể gửi tin nhắn chia sẻ bài hát.', 'error');
                    this.shareModalData.sendingUsername = null;
                });
            }
        },
        onSendSongSuccess(targetUser) {
            this.Toast.fire({
                icon: 'success',
                title: `Đã chia sẻ bài hát tới ${targetUser.fullname || targetUser.username}!`
            });
            this.shareModalData.sendingUsername = null;
            // Tùy chọn: Đóng modal sau khi gửi thành công
            // this.closeShareModal();
        }
    }
};
