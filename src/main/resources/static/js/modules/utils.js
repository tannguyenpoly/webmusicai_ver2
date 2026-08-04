// src/main/resources/static/js/modules/utils.js
export const utilsModule = {
    methods: {
        setupAxiosInterceptors(vm) {
            axios.interceptors.request.use(config => {
                const guestId = localStorage.getItem('music_guest_id');
                if (guestId) {
                    if (config.headers && typeof config.headers.set === 'function') {
                        config.headers.set('X-Guest-ID', guestId);
                    } else {
                        config.headers = config.headers || {};
                        config.headers['X-Guest-ID'] = guestId;
                    }
                }
                return config;
            }, error => {
                return Promise.reject(error);
            });

            axios.interceptors.response.use(response => {
                const contentType = response.headers['content-type'];
                if (contentType && contentType.includes('text/html') && response.config.url.includes('/api/')) {
                    if (localStorage.getItem('music_username')) {
                        localStorage.removeItem('jwt_token');
                        localStorage.removeItem('music_username');
                        localStorage.removeItem('music_is_admin');
                        window.location.href = '/login?error=expired';
                    }
                    return Promise.reject(new Error('Session expired'));
                }
                return response;
            }, error => {
                if (error.response && error.response.status === 401) {
                    if (localStorage.getItem('music_username')) {
                        localStorage.removeItem('jwt_token');
                        localStorage.removeItem('music_username');
                        localStorage.removeItem('music_is_admin');
                        window.location.href = '/login?error=expired';
                    }
                }
                return Promise.reject(error);
            });
        },
        setupToast() {
            return Swal.mixin({
                toast: true,
                position: 'top-end',
                showConfirmButton: false,
                timer: 3000,
                timerProgressBar: true,
                didOpen: (toast) => {
                    toast.addEventListener('mouseenter', Swal.stopTimer)
                    toast.addEventListener('mouseleave', Swal.resumeTimer)
                }
            });
        },
        formatPrice(price) {
            if (!price) return '0';
            return price.toString().replace(/\B(?=(\d{3})+(?!\d))/g, '.');
        },
        formatDate(dateStr) {
            if (!dateStr) return '';
            const d = new Date(dateStr);
            return d.toLocaleDateString('vi-VN') + ' ' + d.toLocaleTimeString('vi-VN', { hour: '2-digit', minute: '2-digit' });
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
        scrollToBottom() {
            setTimeout(() => {
                const container = document.getElementById('chat-body-scroll');
                if (container) container.scrollTop = container.scrollHeight;
            }, 100);
        },
        getSongIdFromMessage(content) {
            if (!content) return null;
            const match = content.match(/\/song\/(\d+)/);
            return match ? match[1] : null;
        },
        navigateToSharedSong(songId) {
            if (songId) {
                window.location.href = `/song/${songId}`;
            }
        },
        onSendSongSuccess(targetUser) {
            this.shareModalData.sendingUsername = null;
            if (this.Toast) {
                this.Toast.fire({
                    icon: 'success',
                    title: `Đã chia sẻ bài hát tới @${targetUser.username}!`
                });
            } else if (typeof Swal !== 'undefined') {
                Swal.fire({
                    toast: true,
                    position: 'top-end',
                    icon: 'success',
                    title: `Đã chia sẻ bài hát tới @${targetUser.username}!`,
                    showConfirmButton: false,
                    timer: 2500
                });
            }
        },
        formatAvatarUrl(url, name) {
            if (!url || url.trim() === '' || url.includes('/images/default-avatar.png')) {
                const displayName = name || this.currentUser || '?';
                return `<https://ui-avatars.com/api/?name=${encodeURIComponent(displayName)}&background=16a34a&color=fff&rounded=true>`;
            }
            if (url.includes('ui-avatars.com') && !url.includes('rounded=true')) {
                return url + '&rounded=true';
            }
            return url;
        },
        getSongCover(song) {
            if (!song) return 'https://images.unsplash.com/photo-1618005182384-a83a8bd57fbe?w=400&auto=format&fit=crop&q=80';
            let id = typeof song === 'object' ? song.id : song;
            let customUrl = typeof song === 'object' ? song.coverUrl : null;

            if (!customUrl && id && this.publicSongs) {
                const found = this.publicSongs.find(s => s.id === id);
                if (found && found.coverUrl) {
                    customUrl = found.coverUrl;
                }
            }
            if (!customUrl && id && this.favoriteSongs) {
                const found = this.favoriteSongs.find(s => s.id === id);
                if (found && found.coverUrl) {
                    customUrl = found.coverUrl;
                }
            }
            if (!customUrl && id && this.profileGeneratedSongs) {
                const found = this.profileGeneratedSongs.find(s => s.id === id);
                if (found && found.coverUrl) {
                    customUrl = found.coverUrl;
                }
            }

            if (customUrl && customUrl.trim() !== '') {
                if (customUrl.startsWith('/images/')) {
                    let time = Date.now();
                    if (typeof song === 'object') {
                        const dateStr = song.created_at || song.createdAt;
                        if (dateStr) {
                            const parsed = Date.parse(dateStr);
                            if (!isNaN(parsed)) time = parsed;
                        }
                    }
                    return customUrl + '?v=' + time;
                }
                return customUrl;
            }

            const covers = [
                'https://images.unsplash.com/photo-1618005182384-a83a8bd57fbe?w=400&auto=format&fit=crop&q=80',
                'https://images.unsplash.com/photo-1515621061946-eff1c2a352bd?w=400&auto=format&fit=crop&q=80',
                'https://images.unsplash.com/photo-1510915228340-29c85a43dcfe?w=400&auto=format&fit=crop&q=80',
                'https://images.unsplash.com/photo-1516223725307-6f76b9ec8742?w=400&auto=format&fit=crop&q=80',
                'https://images.unsplash.com/photo-1487058792275-0ad4aaf24ca7?w=400&auto=format&fit=crop&q=80',
                'https://images.unsplash.com/photo-1470225620780-dba8ba36b745?w=400&auto=format&fit=crop&q=80',
                'https://images.unsplash.com/photo-1507838153414-b4b713384a76?w=400&auto=format&fit=crop&q=80',
                'https://images.unsplash.com/photo-1447752875215-b2761acb3c5d?w=400&auto=format&fit=crop&q=80',
                'https://images.unsplash.com/photo-1484755560695-a4c73004ffd6?w=400&auto=format&fit=crop&q=80',
                'https://images.unsplash.com/photo-1525201548942-d8c8709e4a88?w=400&auto=format&fit=crop&q=80'
            ];
            return covers[id % covers.length];
        },
        getListensCount(song) {
            if (!song) return '0 lượt nghe';
            const realListens = typeof song === 'object' ? (song.listenCount || 0) : 0;
            return realListens + ' lượt nghe';
        },
        getLikesCount(song) {
            if (!song) return '0';
            return song.total_likes || 0;
        },
        toggleTheme() {
            this.isDarkMode = !this.isDarkMode;
            const currentTheme = this.isDarkMode ? 'dark' : 'light';
            document.documentElement.setAttribute('data-theme', currentTheme);
            localStorage.setItem('music_theme', currentTheme);
        },
        goToOrders() { window.location.href = '/orders'; },
    }
};
