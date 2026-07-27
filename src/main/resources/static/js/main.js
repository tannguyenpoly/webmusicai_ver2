new Vue({
    el: '#app',
    data: {
        isDarkMode: localStorage.getItem('music_theme') !== 'light',
        currentPage: window.location.pathname,
        currentUser: null,
        userPhoto: null,
        isAdmin: false,
        userTokens: 0,
        userTier: 'FREE',
        userTierExpiresAt: null,
        authProvider: 'LOCAL',
        hasLocalPassword: true,
        publicSongs: [],
        sessionPlaylist: [],
        favoriteSongs: [],
        isLoadingFavorites: false,
        packages: [],
        myOrders: [],
        isLoadingPackages: false,
        isLoadingOrders: false,

        generationForm: {
            username: '',
            prompt: '',
            instrumental: true,
            genreId: null
        },
        isGenerating: false,
        currentTrack: { id: null, title: '', prompt: '', status: '', audioUrl: '' },

        loginForm: { username: '', password: '' },
        registerForm: { username: '', fullname: '', email: '', password: '', confirmPassword: '' },
        forgotPasswordForm: { email: '', otp: '', newPassword: '', confirmPassword: '', step: 1, isSending: false },
        filters: { keyword: '' },
        exploreSection: '',
        genres: [],
        workspaceFilters: {
            liked: false,
            public: false,
            private: false,
            pending: false
        },
        workspaceSortOption: 'newest',
        sortLabels: {
            newest: 'Mới nhất',
            oldest: 'Cũ nhất',
            most_liked: 'Được thích nhiều nhất',
            least_liked: 'Được thích ít nhất'
        },
        pollingTimer: null,
        showQueue: false,
        uploadingSongId: null,
        isPlaying: false,
        isOnSongDetailPage: false,
        profileUsername: '',
        isFollowing: false,
        followersCount: 0,
        followingCount: 0,
        editingSongForm: { id: null, title: '', prompt: '', isPublic: false, coverUrl: '' },
        isSavingSongEdit: false,

        profileModalTab: 'info',
        showProfileModal: false,
        profileModalError: '',
        profileForm: { fullname: '', email: '', photo: '', authProvider: 'LOCAL' },
        changePasswordForm: { oldPassword: '', newPassword: '', confirmNewPassword: '' },
        passwordResetMode: false,

        commentPagination: { content: [], number: 0, totalPages: 1, totalElements: 0 },
        isLoadingComments: false,
        isSubmittingComment: false,
        newComment: { content: '' },
        newReply: { content: '' },
        replyingToCommentId: null,
        editingComment: null,

        profilePageData: {},
        profileStats: { total: 0, completed: 0, pending: 0, totalFavorites: 0 },
        profileTab: 'generated',
        profileGeneratedSongs: [],
        profileFavoriteSongs: [],
        isLoadingProfileSongs: false,
        isLoadingProfileFav: false,
        profileSongPagination: { page: 0, size: 10, hasMore: false },

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

        myPlaylists: [],
        playlistTargetSong: null,
        newPlaylistForm: { name: '', isPublic: false },
        isLoadingPlaylists: false,

        friendStatus: { id: null, status: 'NONE' },
        friends: [],
        friendRequests: [],
        paymentPollingTimer: null,
        activePaymentOrderCode: null,
        selectedPkg: null,

        matchingCreators: [],
        creatorSearchTimeout: null,

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
        'filters.keyword': function (newVal) {
            if (this.creatorSearchTimeout) clearTimeout(this.creatorSearchTimeout);
            if (!newVal || !newVal.trim()) {
                this.matchingCreators = [];
                return;
            }
            this.creatorSearchTimeout = setTimeout(() => {
                axios.get('/api/users/search?query=' + encodeURIComponent(newVal.trim()))
                    .then(response => {
                        this.matchingCreators = response.data || [];
                    })
                    .catch(err => {
                        console.error('Lỗi tìm kiếm creator:', err);
                        this.matchingCreators = [];
                    });
            }, 300);
        }
    },
    computed: {
        filteredSongs() {
            let result = [...this.publicSongs];
            if (this.filters.keyword && this.filters.keyword.trim() !== '') {
                const kw = this.filters.keyword.toLowerCase();
                result = result.filter(s =>
                    (s.title && s.title.toLowerCase().includes(kw)) ||
                    (s.prompt && s.prompt.toLowerCase().includes(kw))
                );
            }
            return result;
        },
        forYouSongs() {
            if (this.currentUser && this.profileGeneratedSongs.length > 0) {
                return this.profileGeneratedSongs.slice(0, 5);
            }
            return this.publicSongs.slice(0, 5);
        },
        studioSongs() {
            const studio = this.publicSongs.filter(s => s.username);
            return studio.length > 0 ? studio.slice(0, 5) : this.publicSongs.slice(0, 5);
        },
        bestSongs() {
            return [...this.publicSongs].sort((a, b) => {
                const getLikes = (song) => song.total_likes || 0;
                return getLikes(b) - getLikes(a);
            }).slice(0, 5);
        },
        userTierLabel() {
            const labels = {
                FREE: 'Miễn phí',
                CREATOR: 'Nhà sáng tạo',
                PRO: 'Chuyên nghiệp',
                STUDIO: 'Phòng thu'
            };
            return labels[this.userTier] || this.userTier || 'Miễn phí';
        },
        activeExploreSongs() {
            if (this.exploreSection === 'trending') {
                return [...this.publicSongs].sort((a, b) =>
                    (b.total_likes || 0) - (a.total_likes || 0));
            }
            if (this.exploreSection === 'new') {
                return [...this.publicSongs].sort((a, b) =>
                    new Date(b.createdAt || b.created_at || 0)
                    - new Date(a.createdAt || a.created_at || 0));
            }
            return this.publicSongs;
        },
        activeFiltersCount() {
            let count = 0;
            if (this.workspaceFilters.liked) count++;
            if (this.workspaceFilters.public) count++;
            if (this.workspaceFilters.private) count++;
            if (this.workspaceFilters.pending) count++;
            return count;
        },
        filteredProfileSongs() {
            let result = [...this.profileGeneratedSongs];
            if (this.filters.keyword && this.filters.keyword.trim() !== '') {
                const kw = this.filters.keyword.toLowerCase();
                result = result.filter(s =>
                    (s.title && s.title.toLowerCase().includes(kw)) ||
                    (s.prompt && s.prompt.toLowerCase().includes(kw))
                );
            }

            const activeOptions = [];
            if (this.workspaceFilters.public) activeOptions.push('PUBLIC');
            if (this.workspaceFilters.private) activeOptions.push('PRIVATE');

            if (activeOptions.length > 0) {
                result = result.filter(s => activeOptions.includes(
                    s.isPublic || s.is_public ? 'PUBLIC' : 'PRIVATE'));
            }

            if (this.workspaceFilters.pending) {
                result = result.filter(s => s.status === 'PENDING');
            }

            if (this.workspaceFilters.liked) {
                result = result.filter(s => this.profileFavoriteSongs.some(fav =>
                    fav.id === s.id || (fav.song && fav.song.id === s.id) || fav.songId === s.id));
            }

            if (this.workspaceSortOption === 'newest') {
                result.sort((a, b) => new Date(b.created_at || b.createdAt || 0) - new Date(a.created_at || a.createdAt || 0));
            } else if (this.workspaceSortOption === 'oldest') {
                result.sort((a, b) => new Date(a.created_at || a.createdAt || 0) - new Date(b.created_at || b.createdAt || 0));
            } else if (this.workspaceSortOption === 'most_liked') {
                const getLikes = (song) => song.total_likes || song.totalLikes || 0;
                result.sort((a, b) => getLikes(b) - getLikes(a));
            } else if (this.workspaceSortOption === 'least_liked') {
                const getLikes = (song) => song.total_likes || song.totalLikes || 0;
                result.sort((a, b) => getLikes(a) - getLikes(b));
            }

            return result;
        }
    },
    created() {
        axios.interceptors.response.use(response => {
            const contentType = response.headers['content-type'];
            if (contentType && contentType.includes('text/html') && response.config.url.includes('/api/')) {
                localStorage.removeItem('jwt_token');
                localStorage.removeItem('music_username');
                localStorage.removeItem('music_is_admin');
                window.location.href = '/login?error=expired';
                return Promise.reject(new Error('Session expired'));
            }
            return response;
        }, error => {
            if (error.response && error.response.status === 401) {
                localStorage.removeItem('jwt_token');
                localStorage.removeItem('music_username');
                localStorage.removeItem('music_is_admin');
                window.location.href = '/login?error=expired';
            }
            return Promise.reject(error);
        });
    },
    mounted() {
        this.isOnSongDetailPage = window.location.pathname.startsWith('/song/');
        if (this.isOnSongDetailPage) {
            const style = document.createElement('style');
            style.innerHTML = '.suno-sticky-player { display: none !important; }';
            document.head.appendChild(style);
        }
        this.Toast = Swal.mixin({
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

        const urlParams = new URLSearchParams(window.location.search);
        const paymentStatus = urlParams.get('status');
        if (paymentStatus) {
            if (paymentStatus === 'success') {
                Swal.fire({ icon: 'success', title: 'Thanh toán thành công!', text: 'Hệ thống đã cập nhật token vào tài khoản của bạn.', confirmButtonColor: '#16a34a' });
            } else if (paymentStatus === 'failed') {
                Swal.fire({ icon: 'error', title: 'Thanh toán thất bại!', text: 'Giao dịch chưa hoàn tất hoặc đã bị hủy.', confirmButtonColor: '#dc3545' });
            } else if (paymentStatus === 'invalid') {
                Swal.fire({ icon: 'warning', title: 'Cảnh báo', text: 'Giao dịch không hợp lệ hoặc dữ liệu bị sai lệch.', confirmButtonColor: '#ffc107' });
            }
            window.history.replaceState(null, null, window.location.pathname);
        }

        const userParam = urlParams.get('username');
        const isAdminParam = urlParams.get('isAdmin');
        const oauthStatus = urlParams.get('oauth');
        if (oauthStatus === 'success' && userParam) {
            localStorage.setItem('music_username', userParam);
            localStorage.setItem('music_is_admin', isAdminParam === 'true');
            window.history.replaceState(null, null, window.location.pathname);
            this.Toast.fire({ icon: 'success', title: `Chào mừng ${userParam} đã đăng nhập!` });
        }

        const savedUser = localStorage.getItem('music_username');

        if (savedUser) {
            this.currentUser = savedUser;
            this.isAdmin = localStorage.getItem('music_is_admin') === 'true';
            this.generationForm.username = savedUser;
            this.loadUserTokenBalance(savedUser);

            setTimeout(() => {
                this.connectWebSocket();
                this.loadRecentChats();
                this.loadTotalUnreadCount();
                this.startPresenceHeartbeat();
                this.loadFriends();
                this.loadNotifications();
                this.notificationPollingTimer = setInterval(() => this.loadNotifications(false), 30000);
            }, 600);
        } else {
            this.currentUser = null;
            this.isAdmin = false;
            localStorage.removeItem('music_username');
            localStorage.removeItem('jwt_token');
            localStorage.removeItem('music_is_admin');
        }

        if (window.location.pathname === '/' || window.location.pathname === '/home') {
            this.loadPublicSongs();
        }
        else if (window.location.pathname === '/explore') {
            this.loadPublicSongs();
        }
        else if (window.location.pathname === '/create') {
            if (this.currentUser) {
                this.loadGenres();
                this.profileUsername = this.currentUser;
                this.loadProfileGeneratedSongs();
                this.loadProfileFavorites();
                const promptParam = urlParams.get('prompt');
                if (promptParam) {
                    this.generationForm.prompt = promptParam;
                    const autoParam = urlParams.get('auto');
                    if (autoParam === 'true') {
                        setTimeout(() => {
                            this.generateMusic();
                        }, 600);
                    }
                }
            }
        }
        else if (window.location.pathname.startsWith('/favorites')) {
            this.loadFavoriteSongs();
        }
        else if (window.location.pathname.startsWith('/song/')) {
            const pathParts = window.location.pathname.split('/');
            const songId = pathParts[pathParts.length - 1];
            if (songId) {
                this.loadSingleSongAndComments(songId);
                this.loadPublicSongs();
            }
        }
        else if (window.location.pathname === '/orders') {
            this.loadPackages();
            if (this.currentUser) this.loadMyOrders();
        }
        else if (window.location.pathname === '/profile') {
            const urlParams = new URLSearchParams(window.location.search);
            const userParam = urlParams.get('username') || urlParams.get('u');
            this.profileUsername = userParam || this.currentUser;

            if (this.profileUsername) {
                this.loadProfilePageData();
                this.loadProfileGeneratedSongs();
                this.loadFriendStatus();
                if (this.currentUser && this.profileUsername === this.currentUser) {
                    this.loadProfileFavorites();
                } else {
                    this.profileTab = 'generated';
                }
                this.loadFollowStatus();
            }
        }

        this.loadSessionPlaylist();
    },
    methods: {
        randomizePrompt() {
            const prompts = [
                "Một bản pop ballad buồn bằng tiếng piano du dương, kể về câu chuyện tình cũ dưới mưa...",
                "Nhạc lofi hip hop thư giãn, nhịp điệu chậm rãi kết hợp tiếng mưa rơi ngoài cửa sổ...",
                "Nhạc pop sôi động kết hợp âm hưởng EDM hiện đại, mang năng lượng tích cực ngày mới...",
                "Nhạc cụ truyền thống sáo trúc hòa quyện nhạc điện tử EDM chillout huyền ảo...",
                "Nhạc rap nhẹ nhàng tâm trạng suy tư về cuộc sống và tương lai thành phố đêm đông...",
                "Nhạc Acoustic mộc mạc, guitar nhẹ nhàng sâu lắng viết cho buổi hoàng hôn bãi biển..."
            ];
            const randomIndex = Math.floor(Math.random() * prompts.length);
            this.generationForm.prompt = prompts[randomIndex];
            this.Toast.fire({ icon: 'info', title: 'Đã gợi ý ý tưởng ngẫu nhiên!' });
        },

        createFromHome() {
            if (!this.currentUser) {
                Swal.fire({
                    icon: 'warning',
                    title: 'Yêu cầu đăng nhập',
                    text: 'Vui lòng đăng nhập để bắt đầu sáng tạo nhạc với AI.',
                    confirmButtonText: 'Đăng nhập ngay',
                    showCancelButton: true,
                    confirmButtonColor: '#16a34a',
                    cancelButtonColor: '#6e7881'
                }).then((result) => {
                    if (result.isConfirmed) {
                        window.location.href = '/login';
                    }
                });
                return;
            }
            if (!this.generationForm.prompt.trim()) {
                Swal.fire({ icon: 'warning', title: 'Thiếu thông tin', text: 'Vui lòng nhập mô tả ý tưởng để AI tạo giai điệu!', confirmButtonColor: '#16a34a' });
                return;
            }
            window.location.href = '/create?prompt=' + encodeURIComponent(this.generationForm.prompt) + '&auto=true';
        },

        isTrackPlaying(songId) {
            return this.currentTrack && this.currentTrack.id === songId && this.isPlaying;
        },

        scrollRow(rowRef, direction) {
            const row = this.$refs[rowRef];
            if (row) {
                const scrollAmount = 600;
                row.scrollBy({
                    left: direction === 'left' ? -scrollAmount : scrollAmount,
                    behavior: 'smooth'
                });
            }
        },

        openExploreSection(section) {
            this.exploreSection = section;
            window.scrollTo({ top: 0, behavior: 'smooth' });
        },

        closeExploreSection() {
            this.exploreSection = '';
        },

        toggleFilterOption(option) {
            if (option === 'public') {
                this.workspaceFilters.public = !this.workspaceFilters.public;
                if (this.workspaceFilters.public) this.workspaceFilters.private = false;
            } else if (option === 'private') {
                this.workspaceFilters.private = !this.workspaceFilters.private;
                if (this.workspaceFilters.private) this.workspaceFilters.public = false;
            } else {
                this.workspaceFilters[option] = !this.workspaceFilters[option];
            }
        },

        resetWorkspaceFilters() {
            this.workspaceFilters.liked = false;
            this.workspaceFilters.public = false;
            this.workspaceFilters.private = false;
            this.workspaceFilters.pending = false;
        },

        formatAvatarUrl(url, name) {
            if (!url || url.trim() === '' || url.includes('/images/default-avatar.png')) {
                const displayName = name || this.currentUser || '?';
                return `https://ui-avatars.com/api/?name=${encodeURIComponent(displayName)}&background=16a34a&color=fff&rounded=true`;
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

        triggerSongCoverUpload(songId) {
            this.uploadingSongId = songId;
            this.$nextTick(() => {
                const elem = document.getElementById('songCoverFileInputHidden');
                if (elem) elem.click();
            });
        },

        uploadSongCoverFile(event) {
            const file = event.target.files[0];
            if (!file) return;

            if (!file.type.startsWith('image/')) {
                Swal.fire({ icon: 'warning', title: 'Thông báo', text: 'Vui lòng chọn file hình ảnh (.jpg, .png, .webp, .gif)!' });
                return;
            }

            if (file.size > 5 * 1024 * 1024) {
                Swal.fire({ icon: 'warning', title: 'Thông báo', text: 'Dung lượng ảnh tối đa là 5MB!' });
                return;
            }

            const formData = new FormData();
            formData.append('file', file);

            Swal.fire({
                title: 'Đang tải ảnh bìa lên...',
                text: 'Vui lòng chờ trong giây lát',
                allowOutsideClick: false,
                didOpen: () => { Swal.showLoading(); }
            });

            const songId = this.uploadingSongId;
            axios.post(`/api/songs/${songId}/cover`, formData, {
                headers: { 'Content-Type': 'multipart/form-data' }
            })
                .then(res => {
                    const coverUrl = res.data.coverUrl;

                    const song = this.publicSongs.find(s => s.id === songId);
                    if (song) Vue.set(song, 'coverUrl', coverUrl);

                    if (this.currentTrack.id === songId) Vue.set(this.currentTrack, 'coverUrl', coverUrl);

                    const playlistSong = this.sessionPlaylist.find(s => s.id === songId);
                    if (playlistSong) Vue.set(playlistSong, 'coverUrl', coverUrl);

                    const favSong = this.favoriteSongs.find(s => s.id === songId);
                    if (favSong) Vue.set(favSong, 'coverUrl', coverUrl);

                    if (this.profileGeneratedSongs) {
                        const profSong = this.profileGeneratedSongs.find(s => s.id === songId);
                        if (profSong) Vue.set(profSong, 'coverUrl', coverUrl);
                    }
                    if (this.profileFavoriteSongs) {
                        const profFav = this.profileFavoriteSongs.find(s => s.id === songId);
                        if (profFav) Vue.set(profFav, 'coverUrl', coverUrl);
                    }

                    Swal.fire({ icon: 'success', title: 'Thành công', text: 'Tải ảnh bìa mới cho bài hát thành công!' });
                })
                .catch(err => {
                    let msg = 'Tải ảnh bìa thất bại!';
                    if (err.response && err.response.data && err.response.data.message) {
                        msg = err.response.data.message;
                    }
                    Swal.fire({ icon: 'error', title: 'Lỗi', text: msg });
                });
        },

        renameSong(songId) {
            const track = this.publicSongs.find(s => s.id === songId) || this.currentTrack;
            if (!track) return;

            Swal.fire({
                title: 'Đổi tên bài hát',
                input: 'text',
                inputValue: track.title,
                inputPlaceholder: 'Nhập tên bài hát mới...',
                showCancelButton: true,
                confirmButtonText: 'Lưu',
                cancelButtonText: 'Hủy',
                confirmButtonColor: '#16a34a',
                inputValidator: (value) => {
                    if (!value || !value.trim()) {
                        return 'Tên bài hát không được để trống!';
                    }
                }
            }).then((result) => {
                if (result.isConfirmed) {
                    const newTitle = result.value.trim();
                    axios.put(`/api/songs/${songId}/setting`, { title: newTitle })
                        .then(res => {
                            if (this.currentTrack.id === songId) this.currentTrack.title = newTitle;

                            const publicSong = this.publicSongs.find(s => s.id === songId);
                            if (publicSong) publicSong.title = newTitle;

                            const favSong = this.favoriteSongs.find(s => s.id === songId);
                            if (favSong) favSong.title = newTitle;

                            const playSong = this.sessionPlaylist.find(s => s.id === songId);
                            if (playSong) playSong.title = newTitle;

                            if (this.profileGeneratedSongs) {
                                const profSong = this.profileGeneratedSongs.find(s => s.id === songId);
                                if (profSong) profSong.title = newTitle;
                            }
                            if (this.profileFavoriteSongs) {
                                const profFav = this.profileFavoriteSongs.find(s => s.id === songId);
                                if (profFav) profFav.title = newTitle;
                            }

                            this.Toast.fire({ icon: 'success', title: 'Đổi tên bài hát thành công!' });
                        })
                        .catch(err => {
                            Swal.fire('Lỗi', err.response?.data?.message || err.response?.data || 'Không thể đổi tên bài hát.', 'error');
                        });
                }
            });
        },

        goToSongDetail(songId) {
            window.location.href = `/song/${songId}`;
        },

        loadFollowStatus() {
            if (!this.profileUsername) return;
            axios.get(`/api/users/${this.profileUsername}/follow-status`)
                .then(res => {
                    this.isFollowing = res.data.isFollowing;
                    this.followersCount = res.data.followersCount;
                    this.followingCount = res.data.followingCount;
                })
                .catch(err => console.error(err));
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

        openSongEditModal(song) {
            this.editingSongForm = {
                id: song.id,
                title: song.title,
                prompt: song.prompt,
                isPublic: song.isPublic !== undefined ? song.isPublic : song.is_public,
                coverUrl: song.coverUrl
            };
            this.isSavingSongEdit = false;

            const modalElem = document.getElementById('songEditModal');
            if (modalElem) {
                const modal = new bootstrap.Modal(modalElem);
                modal.show();
            }
        },

        triggerSongEditCoverUpload() {
            const fileInput = document.getElementById('songEditCoverFileInputHidden');
            if (fileInput) fileInput.click();
        },

        uploadSongEditCoverFile(event) {
            const file = event.target.files[0];
            if (!file) return;
            if (!file.type.startsWith('image/')) {
                Swal.fire({ icon: 'warning', title: 'Thông báo', text: 'Vui lòng chọn file hình ảnh!' });
                return;
            }
            const formData = new FormData();
            formData.append('file', file);
            Swal.fire({
                title: 'Đang tải ảnh lên...',
                text: 'Vui lòng chờ',
                allowOutsideClick: false,
                didOpen: () => { Swal.showLoading(); }
            });
            axios.post(`/api/songs/${this.editingSongForm.id}/cover`, formData, {
                headers: { 'Content-Type': 'multipart/form-data' }
            })
                .then(res => {
                    this.editingSongForm.coverUrl = res.data.coverUrl;
                    Swal.fire({ icon: 'success', title: 'Thành công', text: 'Tải ảnh bìa thành công!' });
                })
                .catch(err => {
                    Swal.fire({ icon: 'error', title: 'Lỗi', text: err.response?.data?.message || 'Không thể tải ảnh lên!' });
                });
        },

        saveSongEdit() {
            this.isSavingSongEdit = true;
            axios.put(`/api/songs/${this.editingSongForm.id}/setting`, {
                title: this.editingSongForm.title,
                prompt: this.editingSongForm.prompt,
                is_public: this.editingSongForm.isPublic,
                cover_url: this.editingSongForm.coverUrl
            })
                .then(res => {
                    const songId = this.editingSongForm.id;
                    const newTitle = this.editingSongForm.title;
                    const newPrompt = this.editingSongForm.prompt;
                    const newIsPublic = this.editingSongForm.isPublic;
                    const newCoverUrl = this.editingSongForm.coverUrl;

                    const updateInList = (list) => {
                        if (!list) return;
                        const item = list.find(s => s.id === songId);
                        if (item) {
                            Vue.set(item, 'title', newTitle);
                            Vue.set(item, 'prompt', newPrompt);
                            Vue.set(item, 'isPublic', newIsPublic);
                            Vue.set(item, 'coverUrl', newCoverUrl);
                        }
                    };

                    updateInList(this.publicSongs);
                    updateInList(this.favoriteSongs);
                    updateInList(this.sessionPlaylist);
                    updateInList(this.profileGeneratedSongs);
                    updateInList(this.profileFavoriteSongs);

                    if (this.currentTrack.id === songId) {
                        Vue.set(this.currentTrack, 'title', newTitle);
                        Vue.set(this.currentTrack, 'prompt', newPrompt);
                        Vue.set(this.currentTrack, 'coverUrl', newCoverUrl);
                        Vue.set(this.currentTrack, 'isPublic', newIsPublic);
                    }

                    const modalElem = document.getElementById('songEditModal');
                    if (modalElem) {
                        const modal = bootstrap.Modal.getInstance(modalElem);
                        if (modal) modal.hide();
                    }

                    this.Toast.fire({ icon: 'success', title: 'Cập nhật bài viết thành công!' });
                    this.loadPublicSongs();
                })
                .catch(err => {
                    Swal.fire({ icon: 'error', title: 'Lỗi', text: err.response?.data?.message || err.response?.data || 'Không thể cập nhật bài viết!' });
                })
                .finally(() => {
                    this.isSavingSongEdit = false;
                });
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

        loadUserTokenBalance(username) {
            axios.get(`/api/users/${username}/profile`)
                .then(response => {
                    if (response.data) {
                        if (response.data.token_balance !== undefined) this.userTokens = response.data.token_balance;
                        if (response.data.photo) this.userPhoto = response.data.photo;
                        this.userTier = response.data.accountTier || 'FREE';
                        this.userTierExpiresAt = response.data.tierExpiresAt || null;
                        this.authProvider = response.data.authProvider || 'LOCAL';
                        this.hasLocalPassword = response.data.hasLocalPassword !== false;
                    }
                })
                .catch(error => {
                    if (error.response && (error.response.status === 401 || error.response.status === 403)) this.handleLogout(false);
                });
        },

        loadPublicSongs() {
            axios.get('/api/songs/public')
                .then(response => { this.publicSongs = Array.isArray(response.data) ? response.data : []; })
                .catch(error => { console.error(error); });
        },

        loadGenres() {
            axios.get('/api/genres')
                .then(response => {
                    this.genres = Array.isArray(response.data) ? response.data : [];
                })
                .catch(() => {
                    this.genres = [];
                });
        },

        loadFavoriteSongs() {
            if (!this.currentUser) { window.location.href = '/login'; return; }
            this.isLoadingFavorites = true;
            axios.get('/api/songs/my-favorites')
                .then(response => { this.favoriteSongs = Array.isArray(response.data) ? response.data : []; })
                .catch(error => { this.Toast.fire({ icon: 'error', title: 'Không thể tải danh sách yêu thích.' }); })
                .finally(() => { this.isLoadingFavorites = false; });
        },

        triggerAvatarUpload() {
            if (this.$refs.avatarFileInput) this.$refs.avatarFileInput.click();
            else {
                const elem = document.getElementById('avatarFileInputHidden');
                if (elem) elem.click();
            }
        },

        uploadAvatarFile(event) {
            const file = event.target.files[0];
            if (!file) return;
            if (!file.type.startsWith('image/')) {
                Swal.fire({ icon: 'warning', title: 'Thông báo', text: 'Vui lòng chọn file hình ảnh (.jpg, .png, .webp, .gif)!' });
                return;
            }
            if (file.size > 5 * 1024 * 1024) {
                Swal.fire({ icon: 'warning', title: 'Thông báo', text: 'Dung lượng ảnh tối đa là 5MB!' });
                return;
            }
            const formData = new FormData();
            formData.append('file', file);
            Swal.fire({
                title: 'Đang tải ảnh lên...',
                text: 'Vui lòng chờ trong giây lát',
                allowOutsideClick: false,
                didOpen: () => { Swal.showLoading(); }
            });
            axios.post(`/api/users/${this.currentUser}/avatar`, formData, {
                headers: { 'Content-Type': 'multipart/form-data' }
            })
                .then(res => {
                    const newPhoto = res.data.photo;
                    this.userPhoto = newPhoto;
                    if (this.profilePageData) this.profilePageData.photo = newPhoto;
                    if (this.profileForm) this.profileForm.photo = newPhoto;
                    Swal.fire({ icon: 'success', title: 'Thành công', text: 'Tải ảnh đại diện mới thành công!' });
                })
                .catch(err => {
                    let msg = 'Tải ảnh đại diện thất bại!';
                    if (err.response && err.response.data && err.response.data.message) msg = err.response.data.message;
                    Swal.fire({ icon: 'error', title: 'Lỗi', text: msg });
                });
        },

        loadProfilePageData() {
            axios.get(`/api/users/${this.profileUsername}/profile`)
                .then(res => {
                    this.profilePageData = res.data;
                    if (res.data.total_songs !== undefined) {
                        this.profileStats.total = res.data.total_songs;
                        this.profileStats.completed = res.data.completed_songs;
                        this.profileStats.pending = res.data.pending_songs;
                    }
                    if (res.data.total_favorites !== undefined) this.profileStats.totalFavorites = res.data.total_favorites;
                })
                .catch(err => console.error(err));
        },

        loadProfileGeneratedSongs(loadMore = false) {
            if (!loadMore) {
                this.profileSongPagination.page = 0;
                this.profileGeneratedSongs = [];
            }
            this.isLoadingProfileSongs = true;
            axios.get(`/api/users/${this.profileUsername}/songs?page=${this.profileSongPagination.page}&size=${this.profileSongPagination.size}`)
                .then(res => {
                    const data = res.data;
                    const content = data.content ? data.content : Array.isArray(data) ? data : [];
                    if (loadMore) this.profileGeneratedSongs = this.profileGeneratedSongs.concat(content);
                    else this.profileGeneratedSongs = content;

                    if (data.content) {
                        this.profileSongPagination.hasMore = !data.last;
                        if (this.profileStats.total === 0 || !loadMore) this.profileStats.total = data.totalElements;
                    } else {
                        this.profileSongPagination.hasMore = false;
                        this.profileStats.total = this.profileGeneratedSongs.length;
                    }
                })
                .catch(err => console.error(err))
                .finally(() => { this.isLoadingProfileSongs = false; });
        },

        loadMoreProfileSongs() {
            this.profileSongPagination.page++;
            this.loadProfileGeneratedSongs(true);
        },

        switchToFavTab() {
            this.profileTab = 'favorites';
            if (this.profileFavoriteSongs.length === 0) this.loadProfileFavorites();
        },

        loadProfileFavorites() {
            this.isLoadingProfileFav = true;
            axios.get('/api/songs/my-favorites')
                .then(res => {
                    this.profileFavoriteSongs = Array.isArray(res.data) ? res.data : [];
                    this.profileStats.totalFavorites = this.profileFavoriteSongs.length;
                })
                .catch(err => console.error(err))
                .finally(() => { this.isLoadingProfileFav = false; });
        },

        toggleProfileSongVisibility(song) {
            axios.put(`/api/songs/${song.id}/visibility`)
                .then(res => {
                    song.isPublic = res.data.isPublic !== undefined ? res.data.isPublic : !song.isPublic;
                    this.Toast.fire({ icon: 'success', title: song.isPublic ? 'Đã công khai bài hát' : 'Đã chuyển thành riêng tư' });
                })
                .catch(err => {
                    Swal.fire('Lỗi', 'Không thể đổi trạng thái bài hát', 'error');
                });
        },

        deleteGeneratedSong(song) {
            Swal.fire({
                title: 'Xác nhận xóa?',
                text: "Bài nhạc này sẽ bị xóa vĩnh viễn khỏi hệ thống!",
                icon: 'warning',
                showCancelButton: true,
                confirmButtonColor: '#dc3545',
                cancelButtonText: 'Hủy',
                confirmButtonText: 'Xóa ngay'
            }).then(result => {
                if (result.isConfirmed) {
                    axios.delete(`/api/songs/${song.id}`)
                        .then(() => {
                            this.profileGeneratedSongs = this.profileGeneratedSongs.filter(s => s.id !== song.id);
                            this.profileStats.total--;
                            if (song.status === 'COMPLETED') this.profileStats.completed--;
                            if (song.status === 'PENDING') this.profileStats.pending--;
                            this.Toast.fire({ icon: 'success', title: 'Đã xóa bài nhạc thành công.' });
                        })
                        .catch(err => Swal.fire('Lỗi', 'Không thể xóa bài nhạc.', 'error'));
                }
            });
        },

        removeFavAndUpdate(song) {
            axios.post(`/api/songs/${song.id}/like`)
                .then(res => {
                    this.profileFavoriteSongs = this.profileFavoriteSongs.filter(s => s.id !== song.id);
                    if (this.profileStats.totalFavorites > 0) this.profileStats.totalFavorites--;
                    this.Toast.fire({ icon: 'success', title: 'Đã bỏ yêu thích bài hát.' });
                })
                .catch(err => this.Toast.fire({ icon: 'error', title: 'Lỗi xử lý.' }));
        },

        generateMusic() {
            if (!this.generationForm.prompt.trim()) {
                Swal.fire({ icon: 'warning', title: 'Thiếu thông tin', text: 'Vui lòng nhập mô tả ý tưởng để AI tạo giai điệu!', confirmButtonColor: '#16a34a' });
                return;
            }
            this.isGenerating = true;
            this.generationForm.username = this.currentUser;
            axios.post('/api/songs/generate', this.generationForm)
                .then(response => {
                    const data = response.data;
                    this.Toast.fire({ icon: 'success', title: 'AI đang xử lý giai điệu ngầm...' });
                    this.userTokens = data.remaining_tokens;
                    this.currentTrack = { id: data.songId, title: "AI đang tiến hành xử lý bài hát...", prompt: this.generationForm.prompt, status: "PENDING", audioUrl: "", username: this.currentUser };

                    if (window.location.pathname === '/' || window.location.pathname === '/profile') {
                        this.profileGeneratedSongs.unshift({
                            id: data.songId,
                            title: "AI đang tiến hành xử lý bài hát...",
                            prompt: this.generationForm.prompt,
                            status: "PENDING",
                            audioUrl: "",
                            username: this.currentUser,
                            created_at: new Date().toISOString()
                        });
                    }
                    this.generationForm.prompt = '';
                    this.isGenerating = false;
                    this.startPollingStatus(data.songId);
                })
                .catch(error => {
                    this.isGenerating = false;
                    Swal.fire({ icon: 'error', title: 'Thất bại', text: error.response && error.response.data ? error.response.data : 'Lỗi kết nối lõi AI.', confirmButtonColor: '#dc3545' });
                });
        },

        startPollingStatus(songId) {
            if (this.pollingTimer) clearInterval(this.pollingTimer);
            this.pollingTimer = setInterval(() => {
                axios.get(`/api/songs/${songId}/status`)
                    .then(response => {
                        const statusData = response.data;
                        if (this.currentTrack.id === songId) this.currentTrack.status = statusData.status;
                        if (statusData.status === 'COMPLETED') {
                            clearInterval(this.pollingTimer);
                            this.currentTrack.title = statusData.title;
                            this.currentTrack.audioUrl = statusData.audioUrl;
                            this.loadPublicSongs();
                            if (window.location.pathname === '/' || (window.location.pathname === '/profile' && this.profileTab === 'generated')) {
                                this.loadProfileGeneratedSongs();
                            }
                            this.Toast.fire({ icon: 'success', title: `Sinh xong bài: ${statusData.title}!` });
                            this.$nextTick(() => { const audio = document.getElementById('audio-element'); if (audio) { audio.load(); audio.play(); } });
                        } else if (statusData.status === 'FAILED') {
                            clearInterval(this.pollingTimer);
                            Swal.fire({ icon: 'error', title: 'Lỗi', text: 'Quá trình tạo nhạc thất bại!' });
                        } else if (statusData.status === 'CANCELLED') {
                            clearInterval(this.pollingTimer);
                            this.isGenerating = false;
                            this.Toast.fire({ icon: 'info', title: 'Đã dừng tạo nhạc và hoàn token' });
                        }
                    })
                    .catch(() => { clearInterval(this.pollingTimer); });
            }, 3000);
        },

        playTrack(song) {
            if (this.currentTrack.id === song.id && this.currentTrack.status === 'COMPLETED') {
                const audio = document.getElementById('audio-element');
                if (audio) {
                    if (audio.paused) {
                        audio.play().then(() => { this.isPlaying = true; }).catch(err => console.error(err));
                    } else {
                        audio.pause();
                        this.isPlaying = false;
                    }
                    return;
                }
            }
            if (this.pollingTimer && this.currentTrack.id === song.id && this.currentTrack.status === 'PENDING') return;
            if (this.pollingTimer) clearInterval(this.pollingTimer);

            this.currentTrack = {
                id: song.id,
                title: song.title,
                prompt: song.prompt,
                status: 'COMPLETED',
                audioUrl: song.audioUrl,
                coverUrl: song.coverUrl,
                username: song.username,
                listenCount: song.listenCount || 0
            };
            this.isPlaying = true;
            this.incrementListenCount(song);
            this.$nextTick(() => {
                const audio = document.getElementById('audio-element');
                if (audio) {
                    audio.load();
                    audio.play().then(() => { this.isPlaying = true; }).catch(err => console.error(err));
                }
            });
        },

        incrementListenCount(song) {
            if (!song || !song.id) return;
            axios.post(`/api/songs/${song.id}/play`)
                .then(response => {
                    if (response.data && response.data.success) {
                        song.listenCount = response.data.listenCount;
                        if (this.currentTrack.id === song.id) this.currentTrack.listenCount = response.data.listenCount;
                    }
                })
                .catch(err => console.error("Lỗi tăng lượt nghe:", err));
        },

        loadSessionPlaylist() {
            const data = sessionStorage.getItem('music_session_playlist');
            this.sessionPlaylist = data ? JSON.parse(data) : [];
        },
        addToPlaylist(song) {
            const isExist = this.sessionPlaylist.some(item => item.id === song.id);
            if (!isExist) {
                this.sessionPlaylist.push(song);
                sessionStorage.setItem('music_session_playlist', JSON.stringify(this.sessionPlaylist));
                this.Toast.fire({ icon: 'success', title: 'Đã thêm vào danh sách phát tạm' });
            } else {
                this.Toast.fire({ icon: 'info', title: 'Bài hát đã có trong danh sách phát' });
            }
        },
        removeTrack(index) {
            this.sessionPlaylist.splice(index, 1);
            sessionStorage.setItem('music_session_playlist', JSON.stringify(this.sessionPlaylist));
            this.Toast.fire({ icon: 'warning', title: 'Đã xóa bài hát khỏi danh sách phát' });
        },
        clearPlaylist() {
            this.sessionPlaylist = [];
            sessionStorage.removeItem('music_session_playlist');
            this.Toast.fire({ icon: 'info', title: 'Đã xóa danh sách chờ phát' });
        },

        loadSingleSongAndComments(songId) {
            axios.get(`/api/songs/${songId}/status`)
                .then(response => {
                    this.currentTrack = response.data;
                    this.profileUsername = response.data.username;
                    this.loadFollowStatus();
                    this.loadComments(songId);
                })
                .catch(error => {
                    console.error("Không thể tải thông tin bài hát:", error);
                    Swal.fire('Lỗi', 'Không tìm thấy bài hát hoặc bạn không có quyền truy cập.', 'error');
                });
        },

        toggleLike(song) {
            if (!this.currentUser) {
                Swal.fire({
                    icon: 'warning',
                    title: 'Yêu cầu đăng nhập',
                    text: 'Bạn cần đăng nhập để "thả tim" cho bài hát này.',
                    confirmButtonText: 'Đăng nhập ngay',
                    showCancelButton: true,
                    cancelButtonColor: '#6e7881',
                    confirmButtonColor: '#16a34a',
                    cancelButtonText: 'Hủy'
                }).then((result) => {
                    if (result.isConfirmed) window.location.href = '/login';
                });
                return;
            }
            const originalLikedState = song.liked_by_me;
            const originalLikeCount = song.total_likes;
            song.liked_by_me = !song.liked_by_me;
            song.total_likes += song.liked_by_me ? 1 : -1;
            if (window.location.pathname.startsWith('/favorites') && !song.liked_by_me) {
                const index = this.favoriteSongs.findIndex(s => s.id === song.id);
                if (index > -1) this.favoriteSongs.splice(index, 1);
            }
            axios.post(`/api/songs/${song.id}/like`)
                .then(response => {
                    song.liked_by_me = response.data.liked;
                    song.total_likes = response.data.total_likes;
                    this.Toast.fire({ icon: 'success', title: response.data.message });
                })
                .catch(error => {
                    song.liked_by_me = originalLikedState;
                    song.total_likes = originalLikeCount;
                    if (window.location.pathname.startsWith('/favorites') && song.liked_by_me) {
                        const isExist = this.favoriteSongs.some(s => s.id === song.id);
                        if (!isExist) this.favoriteSongs.push(song);
                    }
                    this.Toast.fire({ icon: 'error', title: error.response?.data?.message || 'Đã có lỗi xảy ra' });
                });
        },

        handleLogin() {
            if (this.loginForm && !this.loginForm.username.trim()) {
                Swal.fire({ icon: 'warning', title: 'Lỗi', text: 'Vui lòng nhập tên đăng nhập!' });
                return;
            }
            if (this.loginForm && !this.loginForm.password.trim()) {
                Swal.fire({ icon: 'warning', title: 'Lỗi', text: 'Vui lòng nhập mật khẩu!' });
                return;
            }
            const btn = document.getElementById('submit-btn');
            if (btn) {
                btn.innerHTML = '<i class="ti ti-loader-2 spin"></i> Đang kết nối...';
                btn.disabled = true;
            }
            axios.post('/api/auth/login', this.loginForm)
                .then(response => {
                    localStorage.setItem('music_username', response.data.username);
                    localStorage.setItem('music_is_admin', response.data.isAdmin);
                    if (btn) {
                        btn.innerHTML = '<i class="ti ti-check"></i> Kích hoạt thành công!';
                        btn.style.background = '#15803d';
                    }
                    this.Toast.fire({ icon: 'success', title: `Khởi động hệ thống thành công! Chào mừng ${response.data.username}.` });
                    setTimeout(() => {
                        if (response.data.isAdmin) window.location.href = '/admin';
                        else window.location.href = '/';
                    }, 1000);
                })
                .catch(err => {
                    if (btn) {
                        btn.innerHTML = '<i class="ti ti-bolt"></i> Kích hoạt hệ thống';
                        btn.disabled = false;
                    }
                    let msg = 'Tài khoản hoặc mật khẩu không chính xác.';
                    if (err.response && err.response.status === 403) msg = err.response.data || 'Tài khoản đã bị khóa!';
                    else if (err.response && err.response.data) msg = err.response.data.message || err.response.data || msg;
                    Swal.fire({ icon: 'error', title: 'Đăng nhập thất bại', text: msg, confirmButtonColor: '#16a34a' });
                });
        },

        handleRegister() {
            if (!this.registerForm.username.trim() || this.registerForm.username.trim().includes(' ')) {
                Swal.fire({ icon: 'warning', title: 'Lỗi', text: 'Tên đăng nhập không hợp lệ!' });
                return;
            }
            if (!this.registerForm.fullname.trim() || !this.registerForm.email.trim() || !this.registerForm.password.trim()) {
                Swal.fire({ icon: 'warning', title: 'Lỗi', text: 'Vui lòng điền đủ thông tin!' });
                return;
            }
            if (this.registerForm.password !== this.registerForm.confirmPassword) {
                Swal.fire({ icon: 'warning', title: 'Lỗi', text: 'Mật khẩu không trùng khớp!' });
                return;
            }
            const btn = document.querySelector('button[type="submit"]');
            if (btn) { btn.innerHTML = '<i class="ti ti-loader-2 spin"></i> Đang khởi tạo...'; btn.disabled = true; }
            axios.post('/api/auth/register', this.registerForm)
                .then(() => {
                    Swal.fire({ icon: 'success', title: 'Thành công', text: 'Tạo tài khoản thành công!', confirmButtonColor: '#16a34a' })
                        .then(() => { window.location.href = '/login'; });
                })
                .catch(error => {
                    if (btn) { btn.innerHTML = '<i class="ti ti-user-plus"></i> Khởi tạo tài khoản'; btn.disabled = false; }
                    Swal.fire({ icon: 'error', title: 'Lỗi', text: error.response?.data?.message || 'Đăng ký thất bại.' });
                });
        },

        openForgotPasswordModal() {
            this.forgotPasswordForm = { email: '', otp: '', newPassword: '', confirmPassword: '', step: 1, isSending: false };
            const modalElem = document.getElementById('forgotPasswordModal');
            if (modalElem) { const modal = new bootstrap.Modal(modalElem); modal.show(); }
        },

        sendForgotPasswordOtp() {
            if (!this.forgotPasswordForm.email || !this.forgotPasswordForm.email.trim()) {
                Swal.fire({ icon: 'warning', title: 'Thông báo', text: 'Vui lòng nhập Email!' });
                return;
            }
            this.forgotPasswordForm.isSending = true;
            axios.post('/api/auth/forgot-password', { email: this.forgotPasswordForm.email.trim() })
                .then(res => {
                    Swal.fire({ icon: 'success', title: 'Thành công', text: 'Đã gửi OTP qua Email.' });
                    this.forgotPasswordForm.step = 2;
                })
                .catch(err => {
                    Swal.fire({ icon: 'error', title: 'Lỗi', text: err.response?.data?.message || 'Lỗi gửi OTP.' });
                })
                .finally(() => { this.forgotPasswordForm.isSending = false; });
        },

        submitResetPassword() {
            if (!this.forgotPasswordForm.otp || !this.forgotPasswordForm.newPassword) {
                Swal.fire({ icon: 'warning', title: 'Thiếu thông tin', text: 'Vui lòng nhập OTP và mật khẩu mới.' });
                return;
            }
            if (this.forgotPasswordForm.newPassword !== this.forgotPasswordForm.confirmPassword) {
                Swal.fire({ icon: 'error', title: 'Lỗi', text: 'Mật khẩu xác nhận không khớp.' });
                return;
            }
            this.forgotPasswordForm.isSending = true;
            axios.post('/api/auth/reset-password', {
                email: this.forgotPasswordForm.email.trim(),
                otp: this.forgotPasswordForm.otp.trim(),
                newPassword: this.forgotPasswordForm.newPassword
            })
                .then(res => {
                    Swal.fire({ icon: 'success', title: 'Thành công', text: 'Đặt lại mật khẩu thành công!' })
                        .then(() => {
                            if (this.showProfileModal) {
                                this.showProfileModal = false;
                                this.handleLogout(false);
                                return;
                            }
                            const modalElem = document.getElementById('forgotPasswordModal');
                            if (modalElem) { const modal = bootstrap.Modal.getInstance(modalElem); modal.hide(); }
                        });
                })
                .catch(err => {
                    Swal.fire({ icon: 'error', title: 'Lỗi', text: err.response?.data?.message || 'Đặt lại mật khẩu thất bại!' });
                })
                .finally(() => { this.forgotPasswordForm.isSending = false; });
        },

        handleLogout(showConfirm = true) {
            const executeLogout = () => {
                if (this.stompClient) { try { this.stompClient.disconnect(); } catch(e) {} }
                if (this.presenceHeartbeatTimer) clearInterval(this.presenceHeartbeatTimer);
                axios.post('/api/auth/logout').finally(() => {
                    localStorage.removeItem('music_username');
                    localStorage.removeItem('jwt_token');
                    localStorage.removeItem('music_is_admin');
                    window.location.href = '/';
                });
            };
            if (!showConfirm) executeLogout();
            else Swal.fire({ title: 'Xác nhận đăng xuất?', icon: 'question', showCancelButton: true, confirmButtonColor: '#16a34a', confirmButtonText: 'Đăng xuất' })
                .then(result => { if (result.isConfirmed) executeLogout(); });
        },

        openProfileModal() {
            this.profileModalTab = 'info';
            this.profileModalError = '';
            this.changePasswordForm = { oldPassword: '', newPassword: '', confirmNewPassword: '' };
            this.passwordResetMode = false;
            if (!this.currentUser) return;
            axios.get(`/api/users/${this.currentUser}/profile`)
                .then(response => {
                    const data = response.data;
                    this.profileForm.fullname = data.fullname || '';
                    this.profileForm.email = data.email || '';
                    this.profileForm.photo = data.photo || '';
                    this.profileForm.authProvider = data.authProvider || 'LOCAL';
                    this.authProvider = data.authProvider || 'LOCAL';
                    this.hasLocalPassword = data.hasLocalPassword !== false;
                    this.userTier = data.accountTier || 'FREE';
                    this.userTierExpiresAt = data.tierExpiresAt || null;
                    this.showProfileModal = true;
                })
                .catch(error => { Swal.fire({ icon: 'error', title: 'Lỗi', text: 'Không thể tải thông tin cá nhân' }); });
        },

        closeProfileModal() {
            this.showProfileModal = false;
        },

        submitUpdateProfile() {
            axios.put(`/api/users/${this.currentUser}/profile`, this.profileForm)
                .then(response => {
                    this.Toast.fire({ icon: 'success', title: 'Cập nhật hồ sơ thành công!' });
                    if (window.location.pathname === '/profile') this.loadProfilePageData();
                    this.showProfileModal = false;
                })
                .catch(error => {
                    Swal.fire({ icon: 'error', title: 'Cập nhật thất bại', text: 'Vui lòng kiểm tra lại thông tin.' });
                });
        },

        submitChangePassword() {
            if (this.changePasswordForm.newPassword !== this.changePasswordForm.confirmNewPassword) {
                Swal.fire({ icon: 'error', title: 'Lỗi', text: 'Mật khẩu không khớp!' });
                return;
            }
            axios.put(`/api/users/${this.currentUser}/change-password`, this.changePasswordForm)
                .then(response => {
                    this.showProfileModal = false;
                    Swal.fire({ icon: 'success', title: 'Thành công!', text: 'Đổi mật khẩu thành công. Vui lòng đăng nhập lại.', confirmButtonColor: '#16a34a' })
                        .then(() => { this.handleLogout(false); });
                })
                .catch(error => {
                    Swal.fire({ icon: 'error', title: 'Đổi mật khẩu thất bại', text: error.response?.data?.message || 'Có lỗi xảy ra.' });
                });
        },

        loadPackages() {
            this.isLoadingPackages = true;
            axios.get('/api/packages')
                .then(res => { this.packages = res.data; this.isLoadingPackages = false; })
                .catch(() => { this.isLoadingPackages = false; });
        },

        loadMyOrders() {
            if (!this.currentUser) return;
            this.isLoadingOrders = true;
            axios.get('/api/orders/my-orders')
                .then(res => { this.myOrders = Array.isArray(res.data) ? res.data : []; this.isLoadingOrders = false; })
                .catch(() => { this.isLoadingOrders = false; });
        },

        // --- HÀM THANH TOÁN TÍCH HỢP SEPAY QR ---
        buyPackage(pkg) {
            if (!this.currentUser) { window.location.href = '/login'; return; }
            this.selectedPkg = pkg;

            Swal.fire({
                title: 'Chọn phương thức thanh toán',
                icon: 'question',
                showCancelButton: true,
                confirmButtonText: 'SePay (Chuyển khoản QR)',
                cancelButtonText: 'VNPAY (Trực tuyến)',
                confirmButtonColor: '#16a34a',
                cancelButtonColor: '#0d6efd'
            }).then(result => {
                if (result.isConfirmed) {
                    this.confirmPayment('SEPAY');
                } else if (result.dismiss === Swal.DismissReason.cancel) {
                    this.confirmPayment('VNPAY');
                }
            });
        },

        confirmPayment(method) {
            Swal.fire({ title: 'Đang tạo đơn hàng...', allowOutsideClick: false, didOpen: () => { Swal.showLoading(); } });

            axios.post('/api/orders/create', { package_id: this.selectedPkg.id, payment_method: method })
                .then(res => {
                    if (method === "SEPAY") {
                        const data = res.data;
                        this.activePaymentOrderCode = data.order_invoice_number;
                        this.startOrderStatusPolling(data.order_invoice_number);
                        Swal.fire({
                            title: 'Quét mã QR thanh toán',
                            html: `
                                <div class="text-center">
                                    <img src="${data.qrUrl}" style="max-width: 280px; border-radius: 10px; border: 1px solid #ddd;" class="mb-3">
                                    <p class="mb-1">Số tiền: <b class="text-success">${this.formatPrice(this.selectedPkg.price)}đ</b></p>
                                    <p class="mb-1">Nội dung: <code class="text-danger">${data.order_invoice_number}</code></p>
                                    <p class="small text-muted mt-2">Đơn có hiệu lực trong 15 phút. Không đóng cửa sổ này khi đang chuyển khoản.</p>
                                    <div class="small text-success mt-3"><span class="spinner-border spinner-border-sm me-1"></span> Đang chờ ngân hàng xác nhận...</div>
                                </div>`,
                            showConfirmButton: false,
                            showDenyButton: true,
                            denyButtonText: 'Hủy thanh toán',
                            denyButtonColor: '#dc3545',
                            allowOutsideClick: false,
                            allowEscapeKey: false
                        }).then(result => {
                            if (result.isDenied && this.activePaymentOrderCode === data.order_invoice_number) {
                                this.cancelPaymentOrder(data.order_invoice_number);
                            }
                        });
                    } else {
                        if (res.data.paymentUrl) { window.location.href = res.data.paymentUrl; }
                    }
                })
                .catch(err => {
                    Swal.fire('Lỗi', err.response?.data || 'Không thể tạo đơn hàng!', 'error');
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
                    this.Toast.fire({ icon: 'success', title: 'Đã trở thành bạn bè' });
                });
        },

        cancelPaymentOrder(orderCode) {
            if (this.paymentPollingTimer) {
                clearInterval(this.paymentPollingTimer);
                this.paymentPollingTimer = null;
            }
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
            if (this.paymentPollingTimer) clearInterval(this.paymentPollingTimer);
            let attempts = 0;
            this.paymentPollingTimer = setInterval(() => {
                attempts++;
                axios.get(`/api/orders/${encodeURIComponent(orderCode)}/status`)
                    .then(res => {
                        if (res.data.status === 'SUCCESS') {
                            clearInterval(this.paymentPollingTimer);
                            this.paymentPollingTimer = null;
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
                            clearInterval(this.paymentPollingTimer);
                            this.paymentPollingTimer = null;
                            this.activePaymentOrderCode = null;
                            Swal.fire({
                                icon: 'info',
                                title: res.data.status === 'EXPIRED' ? 'Đơn đã hết hạn' : 'Đơn đã được hủy',
                                text: 'Không có token nào được cộng vào tài khoản.',
                                confirmButtonColor: '#16a34a'
                            });
                        } else if (attempts >= 300) {
                            clearInterval(this.paymentPollingTimer);
                            this.paymentPollingTimer = null;
                        }
                    })
                    .catch(() => {
                        if (attempts >= 300) {
                            clearInterval(this.paymentPollingTimer);
                            this.paymentPollingTimer = null;
                        }
                    });
            }, 3000);
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

        removeFriendship() {
            if (!this.friendStatus.id) return;
            axios.delete(`/api/friends/${this.friendStatus.id}`)
                .then(() => {
                    this.friendStatus = { id: null, status: 'NONE' };
                    this.Toast.fire({ icon: 'success', title: 'Đã cập nhật quan hệ bạn bè' });
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
                this.newPlaylistForm = { name: '', isPublic: false };
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

        scrollToBottom() {
            setTimeout(() => {
                const container = document.getElementById('chat-body-scroll');
                if (container) container.scrollTop = container.scrollHeight;
            }, 100);
        },

        // ================= XỬ LÝ CHIA SẺ NHẠC NỘI BỘ (LỊCH SỬ CHAT) =================
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
        }
    }
});
