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

new Vue({
    el: '#app',
    data: {
        isDarkMode: localStorage.getItem('music_theme') !== 'light',
        currentPage: window.location.pathname,
        currentUser: null,
        isGuest: false,
        guestUsername: null,
        userPhoto: null,
        isAdmin: false,
        userTokens: 0,
        userTier: 'FREE',
        userTierExpiresAt: null,
        authProvider: 'LOCAL',
        hasLocalPassword: true,
        publicSongs: [],
        communityPlaylists: [],
        communityAlbums: [],
        profilePublicPlaylists: [],
        profilePublicAlbums: [],
        collectionDetail: { type: '', collection: null, songs: [] },
        isLoadingCollectionDetail: false,
        sessionPlaylist: [],
        favoriteSongs: [],
        isLoadingFavorites: false,
        packages: [],
        myOrders: [],
        isLoadingPackages: false,
        isLoadingOrders: false,
        paymentHistoryFilters: { status: 'ALL', year: 'ALL', month: 'ALL' },
        paymentHistoryPage: 1,
        paymentHistoryPageSize: 10,

        generationForm: {
            username: '',
            title: '',
            prompt: '',
            instrumental: true,
            genreId: null
        },
        // Wizard tạo nhạc: ghép các lựa chọn thành prompt cho API hiện tại.
        // Brief được lưu tạm trên trình duyệt để người dùng tạo phiên bản chỉnh sửa.
        wizardStep: 1,
        wizardSteps: [
            { label: 'Nhu cầu', icon: 'ti-user-heart' },
            { label: 'Bối cảnh', icon: 'ti-target-arrow' },
            { label: 'Âm thanh', icon: 'ti-wave-sine' },
            { label: 'Giọng & lời', icon: 'ti-microphone' },
            { label: 'Xác nhận', icon: 'ti-circle-check' }
        ],
        musicBrief: {
            audience: '',
            useCase: '',
            venueStyle: '',
            platform: '',
            timeOfDay: '',
            duration: '30 giây',
            genreId: null,
            genreName: '',
            mood: '',
            energy: 'Vừa phải',
            instruments: [],
            vocalMode: 'instrumental',
            vocalLanguage: 'Tiếng Việt',
            lyrics: '',
            title: '',
            note: ''
        },
        wizardCreatorUseCases: [
            { id: 'TikTok / Reels', icon: 'ti-device-mobile', description: 'Video ngắn, bắt tai' },
            { id: 'YouTube / Vlog', icon: 'ti-brand-youtube', description: 'Nhạc nền kể chuyện' },
            { id: 'Podcast', icon: 'ti-microphone-2', description: 'Mở đầu hoặc nền trò chuyện' },
            { id: 'Quảng cáo', icon: 'ti-speakerphone', description: 'Nhận diện thương hiệu' }
        ],
        wizardVenueStyles: [
            { id: 'Cà phê học bài', icon: 'ti-book-2', description: 'Tập trung, nhẹ nhàng' },
            { id: 'Cà phê sách', icon: 'ti-books', description: 'Yên tĩnh, ấm áp' },
            { id: 'Acoustic', icon: 'ti-guitar-pick', description: 'Mộc, gần gũi' },
            { id: 'Rooftop / nhà hàng', icon: 'ti-building-skyscraper', description: 'Tinh tế, hiện đại' }
        ],
        wizardMoods: ['Chill', 'Vui tươi', 'Sang trọng', 'Hoài niệm', 'Năng lượng', 'Bí ẩn'],
        wizardEnergies: ['Nhẹ nhàng', 'Vừa phải', 'Năng lượng'],
        wizardInstruments: ['Piano', 'Guitar', 'Trống nhẹ', 'Synth', 'Sáo', 'Tự động'],
        wizardDurations: ['15 giây', '30 giây', '60 giây', '2 phút'],
        wizardTimes: ['Buổi sáng', 'Buổi trưa', 'Buổi chiều', 'Buổi tối'],
        wizardEditingSongId: null,
        wizardEditingSongTitle: '',
        wizardBriefs: {},
        isGenerating: false,
        currentTrack: { id: null, title: '', prompt: '', status: '', audioUrl: '' },

        loginForm: { username: '', password: '' },
        loginError: '',
        registerForm: { username: '', fullname: '', email: '', password: '', confirmPassword: '' },
        forgotPasswordForm: { email: '', otp: '', newPassword: '', confirmPassword: '', step: 1, isSending: false },
        filters: { keyword: '' },
        selectedExploreGenreId: null,
        selectedExploreTagId: null,
        selectedExploreTagSongIds: null,
        isLoadingExploreTag: false,
        exploreSection: '',
        exploreReturnScrollY: 0,
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
        isLoadingFollowList: false,
        followList: [],
        followModalTitle: '',
        showFollowModal: false,
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
        songTags: [],
        availableTags: [],
        selectedCreateTagIds: [],
        selectedDetailTagIds: [],
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

        libraryTab: 'songs',
        librarySongFilter: 'all',
        libraryAlbums: [],
        isLoadingLibraryAlbums: false,

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
        paymentCountdownTimer: null,
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
        },
        selectedExploreTagId: function (tagId) {
            if (tagId === null) {
                this.selectedExploreTagSongIds = null;
                return;
            }
            this.isLoadingExploreTag = true;
            axios.get('/api/songs/by-tag?tagId=' + encodeURIComponent(tagId))
                .then(response => {
                    this.selectedExploreTagSongIds = (response.data || []).map(song => song.id);
                })
                .catch(error => {
                    console.error('Không thể lọc bài hát theo tag:', error);
                    this.selectedExploreTagSongIds = [];
                })
                .finally(() => { this.isLoadingExploreTag = false; });
        }
    },
    computed: {
        filteredSongs() {
            let result = [...this.exploreCatalogSongs];
            if (this.filters.keyword && this.filters.keyword.trim() !== '') {
                const kw = this.filters.keyword.toLowerCase();
                result = result.filter(s =>
                    (s.title && s.title.toLowerCase().includes(kw)) ||
                    (s.prompt && s.prompt.toLowerCase().includes(kw)) ||
                    (s.username && s.username.toLowerCase().includes(kw)) ||
                    (s.authorName && s.authorName.toLowerCase().includes(kw))
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
        trendingSongs() {
            return [...this.exploreCatalogSongs]
                .sort((a, b) => ((b.total_likes || 0) * 100 + (b.listenCount || 0))
                    - ((a.total_likes || 0) * 100 + (a.listenCount || 0)))
                .slice(0, 6);
        },
        newReleaseSongs() {
            return [...this.exploreCatalogSongs]
                .sort((a, b) => new Date(b.createdAt || b.created_at || 0)
                    - new Date(a.createdAt || a.created_at || 0))
                .slice(0, 6);
        },
        featuredCreators() {
            const creators = new Map();
            this.exploreCatalogSongs.forEach(song => {
                if (!song.username) return;
                const current = creators.get(song.username) || {
                    username: song.username,
                    name: song.authorName || song.username,
                    photo: song.authorPhoto || '',
                    songCount: 0,
                    likes: 0,
                    listens: 0,
                    newestAt: 0
                };
                current.songCount += 1;
                current.likes += Number(song.total_likes || 0);
                current.listens += Number(song.listenCount || 0);
                current.newestAt = Math.max(current.newestAt, new Date(song.createdAt || song.created_at || 0).getTime() || 0);
                if (!current.photo && song.authorPhoto) current.photo = song.authorPhoto;
                creators.set(song.username, current);
            });
            return Array.from(creators.values()).sort((a, b) =>
                ((b.likes * 100) + b.listens + (b.newestAt / 1e11))
                - ((a.likes * 100) + a.listens + (a.newestAt / 1e11)));
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
            const catalog = this.exploreCatalogSongs;
            if (this.exploreSection === 'trending') {
                return [...catalog].sort((a, b) => ((b.total_likes || 0) * 100 + (b.listenCount || 0))
                    - ((a.total_likes || 0) * 100 + (a.listenCount || 0)));
            }
            if (this.exploreSection === 'new') {
                return [...catalog].sort((a, b) =>
                    new Date(b.createdAt || b.created_at || 0)
                    - new Date(a.createdAt || a.created_at || 0));
            }
            return catalog;
        },
        suggestedSongs() {
            const currentId = this.currentTrack && this.currentTrack.id;
            return [...this.publicSongs]
                .filter(song => song.id !== currentId && song.status === 'COMPLETED')
                .sort((a, b) => ((Number(b.total_likes) || 0) * 100 + (Number(b.listenCount) || 0))
                    - ((Number(a.total_likes) || 0) * 100 + (Number(a.listenCount) || 0)))
                .slice(0, 5);
        },
        communityCollections() {
            const playlists = this.communityPlaylists.slice(0, 3).map(item => ({ ...item, type: 'PLAYLIST' }));
            const albums = this.communityAlbums.slice(0, 3).map(item => ({ ...item, type: 'ALBUM' }));
            return [...playlists, ...albums];
        },
        paymentHistoryYears() {
            const years = new Set();
            const latestYear = Math.max(2030, new Date().getFullYear());
            for (let year = 2021; year <= latestYear; year += 1) years.add(year);
            this.myOrders.forEach(order => {
                const date = new Date(order.createdAt || order.created_at);
                if (!Number.isNaN(date.getTime())) years.add(date.getFullYear());
            });
            return Array.from(years).sort((a, b) => b - a);
        },
        filteredPaymentHistoryOrders() {
            const filters = this.paymentHistoryFilters;
            return this.myOrders.filter(order => {
                const date = new Date(order.createdAt || order.created_at);
                if (filters.status !== 'ALL' && order.status !== filters.status) return false;
                if (filters.year !== 'ALL' && (Number.isNaN(date.getTime()) || date.getFullYear() !== Number(filters.year))) return false;
                if (filters.month !== 'ALL' && (Number.isNaN(date.getTime()) || date.getMonth() + 1 !== Number(filters.month))) return false;
                return true;
            });
        },
        paymentHistoryTotalPages() {
            return Math.max(1, Math.ceil(this.filteredPaymentHistoryOrders.length / this.paymentHistoryPageSize));
        },
        paginatedPaymentHistoryOrders() {
            const page = Math.min(this.paymentHistoryPage, this.paymentHistoryTotalPages);
            const start = (page - 1) * this.paymentHistoryPageSize;
            return this.filteredPaymentHistoryOrders.slice(start, start + this.paymentHistoryPageSize);
        },
        librarySongs() {
            const songs = [...this.profileGeneratedSongs];
            if (this.librarySongFilter === 'processing') {
                return songs.filter(song => song.status === 'PENDING' || song.status === 'PROCESSING');
            }
            if (this.librarySongFilter === 'completed') {
                return songs.filter(song => song.status === 'COMPLETED');
            }
            if (this.librarySongFilter === 'public') {
                return songs.filter(song => song.isPublic === true || song.is_public === true);
            }
            if (this.librarySongFilter === 'private') {
                return songs.filter(song => !(song.isPublic === true || song.is_public === true));
            }
            return songs;
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
        },
        exploreCatalogSongs() {
            let result = [...this.publicSongs];
            if (this.selectedExploreGenreId !== null) {
                result = result.filter(song => (song.genres || []).some(genre => genre.id === this.selectedExploreGenreId));
            }
            if (this.selectedExploreTagSongIds !== null) {
                result = result.filter(song => this.selectedExploreTagSongIds.includes(song.id));
            }
            return result;
        },
        wizardGenreOptions() {
            if (this.genres && this.genres.length > 0) {
                return this.genres.map(genre => ({ id: genre.id, name: genre.name }));
            }
            return [
                { id: null, name: 'Lofi' },
                { id: null, name: 'Piano' },
                { id: null, name: 'Acoustic' },
                { id: null, name: 'EDM' },
                { id: null, name: 'Cinematic' },
                { id: null, name: 'Jazz' }
            ];
        },
        wizardGeneratedPrompt() {
            const brief = this.musicBrief;
            const parts = [];
            if (brief.audience === 'creator') parts.push('Nhạc cho nhà sáng tạo nội dung');
            if (brief.audience === 'cafe') parts.push('Nhạc nền riêng cho không gian kinh doanh');
            if (brief.useCase) parts.push(`mục đích ${brief.useCase}`);
            if (brief.venueStyle) parts.push(`không gian ${brief.venueStyle}`);
            if (brief.platform) parts.push(`nền tảng ${brief.platform}`);
            if (brief.timeOfDay) parts.push(`phù hợp ${brief.timeOfDay.toLowerCase()}`);
            if (brief.genreName) parts.push(`phong cách ${brief.genreName}`);
            if (brief.mood) parts.push(`cảm xúc ${brief.mood.toLowerCase()}`);
            if (brief.energy) parts.push(`mức năng lượng ${brief.energy.toLowerCase()}`);
            if (brief.instruments.length > 0 && !brief.instruments.includes('Tự động')) {
                parts.push(`nhạc cụ chính ${brief.instruments.join(', ')}`);
            }
            parts.push(`thời lượng khoảng ${brief.duration}`);
            if (brief.vocalMode === 'instrumental') {
                parts.push('nhạc không lời');
            } else if (brief.vocalMode === 'ai-lyrics') {
                parts.push(`có giọng hát ${brief.vocalLanguage}, lời do AI gợi ý`);
            } else {
                parts.push(`có giọng hát ${brief.vocalLanguage}, dùng lời người dùng cung cấp`);
            }
            if (brief.note && brief.note.trim()) parts.push(brief.note.trim());
            return parts.length > 0 ? parts.join('. ') + '.' : 'Hãy hoàn thành các lựa chọn để hệ thống tạo mô tả âm nhạc.';
        }
    },
    created() {
        const clearExpiredSession = () => {
            localStorage.removeItem('jwt_token');
            localStorage.removeItem('music_username');
            localStorage.removeItem('music_is_admin');

            // Các trang này cho phép dùng với tư cách khách. Đừng ép người dùng
            // từ trang chủ sang login chỉ vì metadata của phiên cũ còn trong localStorage.
            const publicGuestPages = ['/', '/explore', '/create'];
            if (publicGuestPages.includes(window.location.pathname)) {
                window.location.replace(window.location.pathname);
            } else {
                window.location.href = '/login?error=expired';
            }
        };

        axios.interceptors.response.use(response => {
            const contentType = response.headers['content-type'];
            if (contentType && contentType.includes('text/html') && response.config.url.includes('/api/')) {
                if (localStorage.getItem('music_username')) {
                    clearExpiredSession();
                }
                return Promise.reject(new Error('Session expired'));
            }
            return response;
        }, error => {
            if (error.response && error.response.status === 401) {
                if (localStorage.getItem('music_username')) {
                    clearExpiredSession();
                }
            }
            return Promise.reject(error);
        });
    },
    mounted() {
        this.loadAvailableTags();
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

        this.loadWizardBriefs();

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
            const guestId = localStorage.getItem('music_guest_id');
            if (guestId) {
                this.migrateGuestSongs(guestId, userParam);
            }
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
                this.notificationPollingTimer = setInterval(() => this.loadNotifications(false), 10000);
            }, 600);
        } else {
            this.currentUser = null;
            this.isAdmin = false;
            localStorage.removeItem('music_username');
            localStorage.removeItem('jwt_token');
            localStorage.removeItem('music_is_admin');

            // Setup Guest
            let guestId = localStorage.getItem('music_guest_id');
            if (!guestId) {
                const randomHex = Math.random().toString(16).substring(2, 14);
                guestId = 'guest_' + randomHex;
                localStorage.setItem('music_guest_id', guestId);
            }
            this.isGuest = true;
            this.guestUsername = guestId;
            this.generationForm.username = guestId;
            this.userTokens = 1; // Default tokens for guest
            this.loadUserTokenBalance(guestId);
        }

        if (window.location.pathname === '/' || window.location.pathname === '/home') {
            this.loadPublicSongs();
            this.loadCommunityCollections();
        }
        else if (window.location.pathname === '/explore') {
            this.loadPublicSongs();
            this.loadCommunityCollections();
        }
        else if (window.location.pathname === '/create') {
            if (this.currentUser || this.isGuest) {
                this.loadGenres();
                this.profileUsername = this.currentUser || this.guestUsername;
                this.loadProfileGeneratedSongs();
                if (this.currentUser) {
                    this.loadProfileFavorites();
                }
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
        }
        else if (window.location.pathname === '/payment-history') {
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
        else if (window.location.pathname.startsWith('/playlists/') || window.location.pathname.startsWith('/albums/')) {
            const parts = window.location.pathname.split('/').filter(Boolean);
            const type = parts[0] === 'playlists' ? 'PLAYLIST' : 'ALBUM';
            const collectionId = parts[1];
            if (collectionId) this.loadCollectionDetail(type, collectionId);
        }
        else if (window.location.pathname === '/library') {
            this.profileUsername = this.currentUser;
            if (this.profileUsername) {
                this.profileSongPagination.size = 50;
                this.loadProfilePageData();
                this.loadProfileGeneratedSongs();
                this.loadMyPlaylists();
                this.loadLibraryAlbums();
            }
        }

        this.loadSessionPlaylist();
    },
    methods: Object.assign(
        {},
        ...Object.values(window.MusicAIModules || {}).map(module => module.methods || {})
    )
});
