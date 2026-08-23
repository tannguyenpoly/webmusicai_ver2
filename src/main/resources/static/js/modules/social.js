window.MusicAIModules = window.MusicAIModules || {};
window.MusicAIModules.social = {
    methods: {
        formatPrice(price) {
            if (!price) return '0';
            return price.toString().replace(/\B(?=(\d{3})+(?!\d))/g, '.');
        },

        formatDate(dateStr) {
            if (!dateStr) return '';
            const d = new Date(dateStr);
            return d.toLocaleDateString('vi-VN') + ' ' + d.toLocaleTimeString('vi-VN', { hour: '2-digit', minute: '2-digit' });
        },

        loadComments(songId, loadMore = false) {
            if (!songId) return;
            this.isLoadingComments = true;
            const pageToLoad = loadMore ? this.commentPagination.number + 1 : 0;
            axios.get(`/api/songs/${songId}/comments?page=${pageToLoad}&size=10`)
                .then(response => {
                    if (loadMore) { response.data.content = this.commentPagination.content.concat(response.data.content); }
                    this.commentPagination = response.data;
                })
                .catch(error => { this.Toast.fire({ icon: 'error', title: 'Không thể tải bình luận.' }); })
                .finally(() => { this.isLoadingComments = false; });
        },

        postComment(songId, parentId = null) {
            if (this.isSubmittingComment) return;
            const isReply = parentId !== null;
            const content = isReply ? this.newReply.content.trim() : this.newComment.content.trim();
            if (!content) { this.Toast.fire({ icon: 'warning', title: 'Vui lòng nhập nội dung.' }); return; }
            
            this.isSubmittingComment = true;
            const payload = { content: content, parent_id: parentId };
            axios.post(`/api/songs/${songId}/comments`, payload)
                .then(response => {
                    if (parentId) {
                        const parentComment = this.commentPagination.content.find(c => c.id === parentId);
                        if (parentComment) { 
                            if (!parentComment.replies) { parentComment.replies = []; }
                            parentComment.replies.push(response.data); 
                        }
                        this.newReply.content = '';
                    } else {
                        if (!response.data.replies) { response.data.replies = []; }
                        this.commentPagination.content.unshift(response.data);
                        this.commentPagination.totalElements++;
                        this.newComment.content = '';
                    }
                    this.replyingToCommentId = null;
                    this.Toast.fire({ icon: 'success', title: 'Đã gửi bình luận!' });
                })
                .catch(error => { 
                    const msg = (error.response && error.response.data && error.response.data.message) ? error.response.data.message : 'Không thể gửi bình luận.';
                    Swal.fire({ icon: 'error', title: 'Lỗi', text: msg }); 
                })
                .finally(() => { this.isSubmittingComment = false; });
        },

        toggleReplyForm(commentId) {
            this.replyingToCommentId = (this.replyingToCommentId === commentId) ? null : commentId;
            this.newReply.content = '';
        },

        editComment(comment) {
            this.editingComment = { id: comment.id, content: comment.content };
        },

        cancelEditComment() {
            this.editingComment = null;
        },

        saveComment(originalComment) {
            if (this.isSubmittingComment) return;
            if (!this.editingComment || !this.editingComment.content.trim()) {
                this.Toast.fire({ icon: 'warning', title: 'Nội dung không được để trống.' });
                return;
            }
            this.isSubmittingComment = true;
            axios.put(`/api/songs/comments/${this.editingComment.id}`, { content: this.editingComment.content })
                .then(response => {
                    originalComment.content = response.data.content;
                    this.editingComment = null;
                    this.Toast.fire({ icon: 'success', title: 'Đã cập nhật bình luận!' });
                })
                .catch(error => {
                    Swal.fire({ icon: 'error', title: 'Lỗi', text: 'Không thể cập nhật bình luận.' });
                })
                .finally(() => { this.isSubmittingComment = false; });
        },

        deleteComment(commentId, index, parentIndex) {
            Swal.fire({ title: 'Xác nhận xóa?', text: "Bình luận này sẽ bị xóa vĩnh viễn!", icon: 'warning', showCancelButton: true, confirmButtonColor: '#dc3545', cancelButtonColor: '#6e7881', confirmButtonText: 'Xóa', cancelButtonText: 'Hủy' })
                .then((result) => {
                    if (result.isConfirmed) {
                        axios.delete(`/api/songs/comments/${commentId}`)
                            .then(() => {
                                if (parentIndex !== null) { this.commentPagination.content[parentIndex].replies.splice(index, 1); }
                                else { this.commentPagination.content.splice(index, 1); this.commentPagination.totalElements--; }
                                this.Toast.fire({ icon: 'success', title: 'Đã xóa bình luận.' });
                            })
                            .catch(error => Swal.fire('Lỗi!', 'Không thể xóa bình luận.', 'error'));
                    }
                });
        },

        formatRelativeTime(dateString) {
            if (!dateString) return '';
            const date = new Date(dateString);
            if (isNaN(date.getTime())) {
                return dateString.substring(0, 10);
            }
            const now = new Date();
            const seconds = Math.round((now - date) / 1000);
            const minutes = Math.round(seconds / 60);
            const hours = Math.round(minutes / 60);
            const days = Math.round(hours / 24);
            
            if (seconds < 0) {
                return 'Vừa xong';
            }
            if (seconds < 60) return `${seconds} giây trước`;
            if (minutes < 60) return `${minutes} phút trước`;
            if (hours < 24) return `${hours} giờ trước`;
            if (days < 7) return `${days} ngày trước`;
            return date.toLocaleDateString('vi-VN');
        },

        copyText(text) {
            navigator.clipboard.writeText(text).then(() => { this.Toast.fire({ icon: 'success', title: 'Đã sao chép!' }); });
        },

        cancelOrder() {
            Swal.fire({ title: 'Huỷ đơn hàng?', text: 'Bạn có chắc muốn huỷ đơn này không?', icon: 'warning', showCancelButton: true, confirmButtonText: 'Huỷ đơn', cancelButtonText: 'Giữ lại', confirmButtonColor: '#dc3545' })
                .then(result => { if (result.isConfirmed) window.location.href = '/orders'; });
        },
        goToOrders() { window.location.href = '/orders'; },

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
                    this.loadFriends();
                    this.Toast.fire({ icon: 'success', title: 'Đã trở thành bạn bè' });
                })
                .catch(err => Swal.fire('Không thể chấp nhận', err.response?.data?.message || 'Có lỗi xảy ra', 'error'));
        },

        declineFriendRequest() {
            if (!this.friendStatus.id) return;
            axios.put(`/api/friends/${this.friendStatus.id}/decline`)
                .then(() => {
                    this.friendStatus = { id: null, status: 'NONE' };
                    this.loadFriends();
                    this.Toast.fire({ icon: 'info', title: 'Đã từ chối lời mời kết bạn' });
                })
                .catch(err => Swal.fire('Không thể từ chối', err.response?.data?.message || 'Có lỗi xảy ra', 'error'));
        },

        respondFriendRequest(notification, accept) {
            if (!notification.refId) return;
            const request = accept
                ? axios.put(`/api/friends/${notification.refId}/accept`)
                : axios.put(`/api/friends/${notification.refId}/decline`);
            request.then(() => {
                this.notifications = this.notifications.filter(item => item.id !== notification.id);
                if (!notification.read) {
                    this.notificationUnreadCount = Math.max(0, this.notificationUnreadCount - 1);
                }
                this.loadFriends();
                if (this.friendStatus.id === notification.refId) {
                    this.friendStatus = accept
                        ? { id: notification.refId, status: 'ACCEPTED' }
                        : { id: null, status: 'NONE' };
                }
                this.Toast.fire({
                    icon: accept ? 'success' : 'info',
                    title: accept ? 'Đã đồng ý lời mời kết bạn' : 'Đã từ chối lời mời kết bạn'
                });
            }).catch(err => Swal.fire('Không thể cập nhật lời mời', err.response?.data?.message || 'Có lỗi xảy ra', 'error'));
        },

        cancelPaymentOrder(orderCode) {
            this.stopPaymentTracking();
            axios.post(`/api/orders/${encodeURIComponent(orderCode)}/cancel`)
                .then(() => {
                    this.activePaymentOrderCode = null;
                    this.loadMyOrders();
                    Swal.fire({
                        icon: 'info',
                        title: 'Đã hủy thanh toán',
                        text: 'Đơn chưa thanh toán đã được đóng.',
                        confirmButtonColor: '#16a34a'
                    });
                })
                .catch(err => {
                    Swal.fire({
                        icon: 'error',
                        title: 'Không thể hủy đơn',
                        text: err.response?.data?.message || 'Vui lòng kiểm tra lại trạng thái đơn hàng.'
                    });
                });
        },

        beginProfilePasswordReset() {
            if (!this.profileForm.email) {
                Swal.fire({ icon: 'warning', title: 'Thiếu email', text: 'Hãy cập nhật email trong hồ sơ trước.' });
                return;
            }
            this.passwordResetMode = true;
            this.forgotPasswordForm = {
                email: this.profileForm.email,
                otp: '',
                newPassword: '',
                confirmPassword: '',
                step: 1,
                isSending: false
            };
        },

        startOrderStatusPolling(orderCode) {
            this.stopPaymentTracking();
            let attempts = 0;
            const checkStatus = () => {
                attempts++;
                axios.get(`/api/orders/${encodeURIComponent(orderCode)}/status`)
                    .then(res => {
                        this.updatePaymentCountdown(res.data.remainingSeconds);
                        if (res.data.status === 'SUCCESS') {
                            this.stopPaymentTracking();
                            this.activePaymentOrderCode = null;
                            this.loadMyOrders();
                            this.loadUserTokenBalance(this.currentUser);
                            Swal.fire({
                                icon: 'success',
                                title: 'Thanh toán thành công',
                                text: 'Token đã được cộng vào tài khoản.',
                                confirmButtonColor: '#16a34a'
                            });
                        } else if (res.data.status === 'CANCELLED' || res.data.status === 'EXPIRED') {
                            this.stopPaymentTracking();
                            this.activePaymentOrderCode = null;
                            Swal.fire({
                                icon: 'info',
                                title: res.data.status === 'EXPIRED' ? 'Đơn đã hết hạn' : 'Đơn đã được hủy',
                                text: 'Không có token nào được cộng vào tài khoản.',
                                confirmButtonColor: '#16a34a'
                            });
                        } else if (res.data.status === 'REVIEW') {
                            this.stopPaymentTracking();
                            this.activePaymentOrderCode = null;
                            Swal.fire({
                                icon: 'warning',
                                title: 'Thanh toán cần đối soát',
                                text: 'Hệ thống đã nhận giao dịch nhưng số tiền hoặc trạng thái đơn cần được Admin kiểm tra.',
                                confirmButtonColor: '#16a34a'
                            });
                        } else if (attempts >= 300) {
                            this.stopPaymentTracking();
                        }
                    })
                    .catch(() => {
                        if (attempts >= 300) {
                            this.stopPaymentTracking();
                        }
                    });
            };
            checkStatus();
            this.paymentPollingTimer = setInterval(checkStatus, 3000);
        },

        stopPaymentTracking() {
            if (this.paymentPollingTimer) {
                clearInterval(this.paymentPollingTimer);
                this.paymentPollingTimer = null;
            }
            if (this.paymentCountdownTimer) {
                clearInterval(this.paymentCountdownTimer);
                this.paymentCountdownTimer = null;
            }
        },

        updatePaymentCountdown(remainingSeconds) {
            const seconds = Math.max(0, Number(remainingSeconds) || 0);
            const render = (value) => {
                const node = document.getElementById('payment-countdown');
                if (!node) return;
                const minutes = Math.floor(value / 60);
                const secs = value % 60;
                node.textContent = `${String(minutes).padStart(2, '0')}:${String(secs).padStart(2, '0')}`;
            };
            render(seconds);
            if (this.paymentCountdownTimer) clearInterval(this.paymentCountdownTimer);
            let current = seconds;
            this.paymentCountdownTimer = setInterval(() => {
                current = Math.max(0, current - 1);
                render(current);
            }, 1000);
        },

        cancelMusicGeneration(song) {
            const target = song || this.currentTrack;
            if (!target || !target.id || target.status !== 'PENDING') return;
            Swal.fire({
                icon: 'question',
                title: 'Dừng tạo nhạc?',
                text: 'Tác vụ sẽ được đánh dấu đã hủy và 1 token được hoàn lại.',
                showCancelButton: true,
                confirmButtonText: 'Dừng và hoàn token',
                cancelButtonText: 'Tiếp tục chờ',
                confirmButtonColor: '#dc3545'
            }).then(result => {
                if (!result.isConfirmed) return;
                axios.post(`/api/songs/${target.id}/cancel`)
                    .then(response => {
                        target.status = 'CANCELLED';
                        this.userTokens = response.data.remaining_tokens;
                        this.isGenerating = false;
                        if (this.currentTrack.id === target.id) {
                            this.currentTrack.status = 'CANCELLED';
                            this.currentTrack.title = 'Đã dừng tạo nhạc';
                            this.currentTrack.audioUrl = '';
                        }
                        const profileSong = this.profileGeneratedSongs.find(item => item.id === target.id);
                        if (profileSong) profileSong.status = 'CANCELLED';
                        if (this.pollingTimer) {
                            clearInterval(this.pollingTimer);
                            this.pollingTimer = null;
                        }
                        this.Toast.fire({ icon: 'success', title: 'Đã dừng và hoàn lại 1 token' });
                    })
                    .catch(error => {
                        const message = error.response?.data?.message || 'Không thể dừng tác vụ';
                        Swal.fire('Không thể dừng', message, 'error');
                    });
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

        onBellClick() {
            if (!this.currentUser) return;
            axios.get('/api/notifications?limit=20')
                .then(response => {
                    this.notifications = response.data || [];
                    if (this.notifications.some(notification => !notification.read)) {
                        axios.put('/api/notifications/read-all')
                            .then(() => {
                                this.notifications.forEach(n => { n.read = true; });
                                this.notificationUnreadCount = 0;
                            });
                    } else {
                        this.notificationUnreadCount = 0;
                    }
                })
                .catch(error => {
                    console.error('Không thể tải thông báo:', error);
                });
        },

        openNotification(notification) {
            if (notification.type === 'FRIEND_REQUEST' || notification.type === 'FRIEND_ACCEPTED') {
                if (!notification.read) {
                    axios.put(`/api/notifications/${notification.id}/read`)
                        .then(() => {
                            notification.read = true;
                            this.notificationUnreadCount = Math.max(0, this.notificationUnreadCount - 1);
                        });
                }
                return;
            }
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

        handleIncomingNotification(notification) {
            // Loại bỏ thông báo cũ nếu trùng id (để tránh lỗi Vue render danh sách)
            this.notifications = this.notifications.filter(n => n.id !== notification.id);
            this.notifications.unshift(notification);
            this.notificationUnreadCount++;
            
            // Hiển thị Toast
            if (this.Toast) {
                this.Toast.fire({
                    icon: 'info',
                    title: 'Thông báo mới',
                    text: (notification.content || '').substring(0, 50) + ((notification.content || '').length > 50 ? '...' : '')
                });
            } else if (typeof Swal !== 'undefined') {
                Swal.fire({
                    toast: true,
                    position: 'top-end',
                    icon: 'info',
                    title: 'Thông báo mới',
                    text: (notification.content || '').substring(0, 50) + ((notification.content || '').length > 50 ? '...' : ''),
                    showConfirmButton: false,
                    timer: 3000
                });
            }
        },

        removeFriendship() {
            if (!this.friendStatus.id) return;
            axios.delete(`/api/friends/${this.friendStatus.id}`)
                .then(res => {
                    this.friendStatus = { id: null, status: 'NONE' };
                    this.Toast.fire({ icon: 'success', title: res.data.message || 'Đã xóa kết bạn' });
                });
        },

        loadFriends() {
            if (!this.currentUser) return;
            axios.get('/api/friends').then(res => { this.friends = res.data || []; });
            axios.get('/api/friends/requests').then(res => { this.friendRequests = res.data || []; });
        },

        openPlaylistModal(song) {
            this.playlistTargetSong = song || null;
            this.newPlaylistForm = { name: '', isPublic: false };
            this.loadMyPlaylists();
            const element = document.getElementById('playlistManagerModal');
            if (element) bootstrap.Modal.getOrCreateInstance(element).show();
        },

        loadMyPlaylists() {
            if (!this.currentUser) return;
            this.isLoadingPlaylists = true;
            axios.get('/api/playlists/my?page=0&size=50')
                .then(res => { this.myPlaylists = res.data.content || []; })
                .finally(() => { this.isLoadingPlaylists = false; });
        },

        createPersistentPlaylist() {
            const name = (this.newPlaylistForm.name || '').trim();
            if (!name) return;
            axios.post('/api/playlists', {
                name,
                isPublic: !!this.newPlaylistForm.isPublic
            }).then(res => {
                this.myPlaylists.unshift(res.data);
                const created = res.data;
                this.newPlaylistForm = { name: '', isPublic: false };
                if (this.playlistTargetSong) {
                    return axios.post(`/api/playlists/${created.id}/songs/${this.playlistTargetSong.id}`)
                        .then(() => this.Toast.fire({ icon: 'success', title: `Đã tạo và thêm vào ${created.name}` }));
                }
                this.Toast.fire({ icon: 'success', title: 'Đã tạo danh sách phát' });
            }).catch(err => Swal.fire('Lỗi', err.response?.data?.message || 'Không thể tạo danh sách phát', 'error'));
        },

        addSongToPersistentPlaylist(playlist) {
            if (!this.playlistTargetSong) return;
            axios.post(`/api/playlists/${playlist.id}/songs/${this.playlistTargetSong.id}`)
                .then(() => this.Toast.fire({ icon: 'success', title: `Đã thêm vào ${playlist.name}` }))
                .catch(err => Swal.fire('Lỗi', err.response?.data?.message || 'Không thể thêm bài hát', 'error'));
        },

        togglePlaylistPrivacy(playlist) {
            axios.put(`/api/playlists/${playlist.id}`, { isPublic: !playlist.isPublic })
                .then(res => {
                    playlist.isPublic = res.data.isPublic;
                    this.Toast.fire({ icon: 'success', title: playlist.isPublic ? 'Danh sách phát đã công khai' : 'Danh sách phát đã chuyển riêng tư' });
                });
        },

        editPersistentPlaylist(playlist) {
            Swal.fire({ title: 'Đổi tên playlist', input: 'text', inputValue: playlist.name,
                inputAttributes: { maxlength: 100 }, showCancelButton: true,
                confirmButtonText: 'Lưu', cancelButtonText: 'Hủy',
                inputValidator: value => !value || !value.trim() ? 'Hãy nhập tên playlist.' : undefined
            }).then(result => {
                if (!result.isConfirmed) return;
                axios.put(`/api/playlists/${playlist.id}`, { name: result.value.trim() }).then(res => {
                    playlist.name = res.data.name;
                    this.Toast.fire({ icon: 'success', title: 'Đã đổi tên playlist' });
                }).catch(err => Swal.fire('Lỗi', err.response?.data?.message || 'Không thể cập nhật playlist', 'error'));
            });
        },

        deletePersistentPlaylist(playlist) {
            Swal.fire({
                title: `Xóa playlist "${playlist.name}"?`,
                icon: 'warning',
                showCancelButton: true,
                confirmButtonText: 'Xóa',
                cancelButtonText: 'Hủy'
            }).then(result => {
                if (!result.isConfirmed) return;
                axios.delete(`/api/playlists/${playlist.id}`).then(() => {
                    this.myPlaylists = this.myPlaylists.filter(item => item.id !== playlist.id);
                });
            });
        },

        // ================= METHODS CHO BOXCHAT =================
    }
};
